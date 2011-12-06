/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

High performance is achieved in the following ways:
	--this class is not synchronized
	--a single internal char[] is used to buffer all reads, store pushback chars, and perform skips.
		The buffer may be initially populated with data before being supplied to a
		{@link #ParseReader(Reader, char[], int, int) constructor}.
		This allows, for example, fast parsing of files
		(whose data can be read in one swoop using, say, {@link FileUtil#readChars FileUtil.readChars}).
	--methods are often big from inlined code
		+++ this class was written for jdk 1.2, but nowadays, with hotspot so much more optimizing, this is a mistake, since they will now get inlined automatically; change the code to the natural implementation whichis more elegant
	--methods often have tweaked custom implementations
	--methods sometimes use multiple algorithms (especially ones that exploit if the buffer has sufficient data)

+++ implement mark/reset?

+++ keep track of char position as well as line numbers?

+++ regular expression support in some of the token search etc methods?
	--desperately need a skipTillRegexNext method
*/


package bb.io;


import bb.util.CharUtil;
import bb.util.Check;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import org.junit.Assert;
import org.junit.Test;


/**
* A Reader class designed for convenient and high performance parsing.
* <p>
* This class satisfies the exact same API as the {@link PushbackReader} class,
* so that it can be a drop-in replacement.
* The main differences between this class and PushbackReader are:
* <ol>
*  <li>the pushback capacity is not fixed at construction, but is dynamically increased as necessary, totally freeing the programmer from worry</li>
*  <li>the skip method is overridden to correctly count line numbers as they are skipped over</li>
*  <li>this class is not synchronized</li>
* </ol>
* <p>
* This class also satisfies the exact same line number API as the {@link LineNumberReader} class.
* In particular, whenever data is read from the stream, it increments the line number count
* if have just read thru a <i>complete</i> line terminator sequence.
* (This class uses the same set of line termination sequences as LineNumberReader).
* The main differences between this class and LineNumberReader are:
* <ol>
*  <li><i>all</i> line termination chars are exactly preserved by reads (LineNumberReader drops '\r' chars)</li>
*  <li><i>the mark and reset methods are currently unsupported</i></li>
* </ol>
* <p>
* In addition to the above APIs, this class adds some useful parsing methods like
* <ul style="list-style-type: none;">
*  <li>{@link #skipFully skipFully}</li>
*  <li>{@link #skipWhitespace skipWhitespace}</li>
*  <li>{@link #hasData hasData}</li>
*  <li>{@link #isTokenNext(String, boolean) isTokenNext}</li>
*  <li>{@link #skipTillTokenNext(String, boolean) skipTillTokenNext}</li>
*  <li>{@link #confirmTokenNext(String, boolean) confirmTokenNext}</li>
*  <li>{@link #readThruToken(String, boolean, boolean) readThruToken}</li>
* </ul>
* <p>
* See also {@link #ParseReader(Reader, char[], int, int) this constructor} for more discussion on the optimal Reader type.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class ParseReader extends Reader {
	
	
	private static final int lineNumberInitial_default = 1;
	
	
	private static final int bufferLength_default = 32*1024;
	
	
	/**
	* @see <a href="http://mindprod.com/jgloss/encoding.html">encoding : Java Glossary</a>
	*/
	private static final String charEncoding_default = System.getProperty( "file.encoding" );
	
	
	/**
	* The underlying character-input stream.
	* May initially be null, in which case all reads come solely from data initially in {@link #buffer}.
	* Will be null after this instance is closed.
	*/
	private Reader in;
	
	
	/**
	* The single buffer simulataneously used for read ahead, pushback, and skip purposes.
	* Will never be null, except after this instance is closed.
	* Will never be zero-length.
	*/
	private char[] buffer;
	
	
	/**
	* Index (inclusive) of the position in {@link #buffer} where data starts.
	* Along with {@link #end}, this field defines where valid data exists in buffer, namely,
	* for indices in the interval [start, end).
	* <i>All other space in buffer is considered to be free, and may be overwritten at will</i>.
	* <p>
	* The relation start < end is always satisfied except (a) when there is no more data in the buffer,
	* which is always signified by start == end or (b) when this instance has been closed, in which case start > end.
	* In every case, start and end must both always be a value in the range [0, buffer.length].
	* <p>
	* The next read will usually return the char at start (and start will subsequently be incremented).
	* Exception: if there is no data in buffer, then buffer will first be populated from {@link #in},
	* with start (and end) assigned appropriate values for the data read in.
	* <p>
	* The next unread will usually write to start - 1 (and start will subsequently be decremented).
	* Exception: if start == 0, then buffer will first be resized to allow pushback near the beginning of the array,
	* with start incremented appropriate for the resize.
	*/
	private int start;
	
	
	/**
	* Index (exclusive) of the position in {@link #buffer} where data ends.
	* See documentation on {@link #start} for more details on buffer's data format.
	* <p>
	* Like start, end must always be a value in the range [0, buffer.length].
	*/
	private int end;
	
	
	/** Records how much space at the beginning of {@link #buffer} should be reserved for future pushbacks. */
	private int pushbackCapacity = 0;
	
	
	/**
	* Current line number.
	* There are no restrictions on its value.
<!-- +++ should it not always be >= 0 ? -->
	*/
	private int lineNumber = 0;
	
	
	// -------------------- constructors --------------------
	
	
	/**
	* The fundamental constructor.
	* <p>
	* @param in the underlying Reader from which characters will be read; may be null (i.e. reads solely come from contents of buffer)
	* @param buffer assigned to the {@link #buffer} field;
	* for peak performance, user should ensure that it is sufficiently large;
	* see also {@link #start} for further details concerning the data format of buffer
	* @param start will be assigned to the start field
	* @param lineNumber will be assigned to the {@link #lineNumber} field
	* @throws IllegalArgumentException if buffer == null; buffer.length == 0; start < 0; start > buffer.length;
	* (in == null) && (start == buffer.length)
	*/
	public ParseReader(Reader in, char[] buffer, int start, int end, int lineNumber) throws IllegalArgumentException {
		super();
		
		Check.arg().notEmpty(buffer);
		Check.arg().notNegative(start);
		if (end > buffer.length) throw new IllegalArgumentException("end = " + end + " is > buffer.length = " + buffer.length);
		if (start > end) throw new IllegalArgumentException("start = " + start + " is > end = " + end);
		if ((in == null) && (start == end)) throw new IllegalArgumentException("(in == null) && (start == end = " + start + "); this means that no data can ever be read");
		
		this.in = in;
		this.buffer = buffer;
		this.start = start;
		this.end = end;
		setLineNumber(lineNumber);
	}
	
	
	/**
	* Calls {@link #ParseReader(Reader, char[], int, int, int) this}( in, buffer, start, end, {@link #lineNumberInitial_default} ).
	* <p>
	* <i>Note:</i> the best performance is obtained when Reader is an unbuffered "low Level" Reader (e.g. FileReader)
	* and <i>not when it is a higher level buffered Reader</i> (e.g. BufferedReader).
	* This is because this class always does it own buffering, so any buffering done by Reader will simply
	* waste memory and involve extra method calls.
	* <p>
	* @param in Reader from which chars will be read
	* @throws IllegalArgumentException if buffer == null; buffer.length == 0; start < 0; start > buffer.length;
	* if (in == null) && (start == buffer.length)
	*/
	public ParseReader(Reader in, char[] buffer, int start, int end) throws IllegalArgumentException {
		this( in, buffer, start, end, lineNumberInitial_default );
	}
	
	
	/**
	* Calls {@link #ParseReader(Reader, char[], int, int) this}( null, buffer, 0, buffer.length ).
	* <p>
	* @param buffer an array into which <i>all chars have already been read</i>
	* @throws IllegalArgumentException if buffer == null
	*/
	public ParseReader(char[] buffer) throws IllegalArgumentException {
		this( null, buffer, 0, buffer.length );
	}
	
	
	/**
	* Calls {@link #ParseReader(Reader, char[], int, int) this}( in, new char[{@link #bufferLength_default}], 0, 0 ).
	* <p>
	* @param in Reader from which chars will be read
	* @throws IllegalArgumentException if in == null
	*/
	public ParseReader(Reader in) throws IllegalArgumentException {
		this( checkReader(in), new char[bufferLength_default], 0, 0 );
	}
	
	private static Reader checkReader(Reader in) throws IllegalArgumentException {
		Check.arg().notNull(in);
		return in;
	}
	
	
	/**
	* Calls {@link #ParseReader(Reader) this}( new {@link InputStreamReader}(in, charEncoding) ).
	* <p>
	* @param in File InputStream which chars will be read
	* @param charEncoding name of the {@link java.nio.charset.Charset} to use to decode bytes into chars
	* @throws UnsupportedEncodingException if charEncoding is not supported
	*/
	public ParseReader(InputStream in, String charEncoding) throws UnsupportedEncodingException {
		this( new InputStreamReader(in, charEncoding) );
	}
	
	
	/**
	* Calls {@link #ParseReader(InputStream, String) this}(in, {@link #charEncoding_default}).
	* <p>
	* @param in File InputStream which chars will be read
	* @throws UnsupportedEncodingException if {@link #charEncoding_default} is not supported (this should never happen)
	*/
	public ParseReader(InputStream in) throws UnsupportedEncodingException {
		this(in, charEncoding_default);
	}
	
	
	/**
	* Calls {@link #ParseReader(InputStream) this}( new {@link FileInputStream}(file) ).
	* <p>
	* @param file File from which chars will be read
	* @throws FileNotFoundException if file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	* @throws SecurityException if a security manager exists and its checkRead method denies read access to file
	* @throws UnsupportedEncodingException if {@link #charEncoding_default} is not supported (this should never happen)
	*/
	public ParseReader(File file) throws FileNotFoundException, SecurityException, UnsupportedEncodingException {
		this( new FileInputStream(file) );
	}
	
	
	// -------------------- accessors & mutators --------------------
	
	
	/** Get the current line number. */
	public int getLineNumber() { return lineNumber; }
	
	
	/** Set the current line number. */
	public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }
	
	
	// -------------------- ensureXXX --------------------
	
	
	/** Checks that the stream has not been closed. */
	private void ensureOpen() throws IOException {
		if (buffer == null) throw new IOException("Stream closed");
	}
	
	
	/**
	* Checks that buffer has at least 1 char of data.
	* <p>
	* Will read from in, if it exists, if necessary.
	* If it reads from in, it will, of course, change the start and end values as appropriate.
	* In this case, however, this method guarantees that pushbackCapacity will be respected
	* (i.e. after a read from in, start = pushbackCapacity).
	* <p>
	* @throws RuntimeException if unable to guarantee that buffer has data
	* @throws IOException if an I/O problem occurs
	*/
	private void ensureBufferHasData() throws RuntimeException, IOException {
		if (start < end) return;
		
		if (in == null) throw new RuntimeException("ensureBufferHolds failed (buffer holds no more data and in == null)");
		
		//assert ((0 <= pushbackCapacity) && (pushbackCapacity < buffer.length)) : "pushbackCapacity = " + pushbackCapacity + " is outside its valid range of [0, " + buffer.length + ")";
		start = pushbackCapacity;	// CRITICAL: as per our method contract, reserve space for pushback
		end = start;	// CRITICAL: update this too so that it never gets out of whack if an exception is thrown below
		int numberRead = in.read(buffer, start, buffer.length - start);
		if (numberRead == -1) throw new RuntimeException("ensureBufferHolds failed (buffer holds no more data and underlying Reader hit end of stream)");
		end = start + numberRead;
		//assert ((start < end) && (end <= buffer.length)) : "end = " + end + " is outside its valid range of (" + start + ", " + buffer.length + "]";
	}
	
	
	/** Checks that buffer has the requested free space, increasing its size if necessary. */
	private void ensurePushbackCapacity(int pushbackCapacityNeeded) {
		//assert (0 <= pushbackCapacityNeeded) : "pushbackCapacityNeeded = " + pushbackCapacityNeeded + " is < 0";
		if (pushbackCapacityNeeded <= start) return;
		
		pushbackCapacity = 1024 * ((int) Math.ceil(pushbackCapacityNeeded / 1024.0));	// is the minimum number of KB which covers pushbackCapacity
		int capacityNew = 1024 * ((int) Math.ceil((pushbackCapacity + buffer.length) / 1024.0));	// is the minimum number of KB which covers pushbackCapacity + buffer.length
		resizeBuffer(capacityNew);
	}
	
	
	// -------------------- resizeBuffer --------------------
	
	
	/**
	* Resizes buffer to the requested (greater) capacity.
	* Data from the current buffer is transfered to the end of the new array (this reserves space for pushbacks).
	*/
	private void resizeBuffer(int capacityNew) {
		//assert (capacityNew > buffer.length) : "capacityNew = " + capacityNew + " <= buffer.length = " + buffer.length;
		
			// create new buffer:
		char[] bufferNew = new char[capacityNew];
			// transfer data to end of new array:
		int lengthData = end - start;
		int startNew = bufferNew.length - lengthData;
		System.arraycopy(buffer, start, bufferNew, startNew, lengthData);	// Note: must use System.arraycopy and not Arrays.copyOf because of how the data needs to be put at the end of the new array
			// update fields:
		buffer = bufferNew;
		start = startNew;
		end = bufferNew.length;
		//assert ((0 <= start) && (start <= end)) : "start = " + start + " is outside its valid range of [0, " + end + "]";
		//assert ((start <= end) && (end <= buffer.length)) : "end = " + end + " is outside its valid range of [" + start + ", " + buffer.length + "]";
	}
	
	
	// -------------------- read, readLine --------------------
	
	
	/**
	* Returns the next char from the stream.
	* Additionally, it increments the line number count if the <i>last</i> char of a line terminator sequence was just read.
	* <p>
	* @return the char read, or -1 if the end of stream has been reached
	* @throws IOException if an I/O problem occurs
	*/
	public int read() throws IOException {
		if (start >= end) {
			ensureOpen();
			try {
				ensureBufferHasData();
			}
			catch (RuntimeException re) {
				return -1;
			}
		}
		
		char c = buffer[start++];
		
			// inlined (for performance) code to check if must increment lineNumber:
		if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
		else if (c == '\r') {
			if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
				if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
			}
			else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
				if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
			}
		}
		
		return c;
	}
	
	
	/**
	* Attempts to read chars into the specified portion of cbuf.
	* Will only block if no char initially available; will never block merely to read all the requested chars.
	* Increments the line number count each time the <i>last</i> char of a line terminator sequence is encountered on the stream.
	* <p>
	* @param cbuf destination buffer
	* @param offset offset in cbuf at which to start writing chars
	* @param length maximum number of chars to read
	* @return the number of chars actually read into cbuf, or -1 if the end of stream has already been reached
	* @throws IllegalArgumentException if cbuf == null; offset < 0; length <= 0; offset + length > cbuf.length
	* @throws IOException if an I/O problem occurs
	*/
	public int read(char[] cbuf, int offset, int length) throws IllegalArgumentException, IOException {
		Check.arg().notNull(cbuf);
		Check.arg().notNegative(offset);
		Check.arg().positive(length);
		if (offset + length > cbuf.length) throw new IllegalArgumentException("offset = " + offset + " + length = " + length + " is > cbuf.length = " + cbuf.length);
		
		ensureOpen();
		
		int count = 0;
		while (true) {
			//assert (count < length) : "count = " + count + " is >= length = " + length;	// Note: arg check ensures that length > 0
			if (count == 0) {	// only block if no data read yet
				try {
					ensureBufferHasData();
				}
				catch (RuntimeException re) {
					return -1;
				}	// failure to read data at this point means have hit end of stream
			}
			else {	// if have read some data, only proceed if ready guarantees will not block
				if (!ready()) return count;
				else ensureBufferHasData();	// this should never throw a RuntimeException since ready will have returned true
			}
			
			for ( ; start < end; ) {
				char c = buffer[start++];
				
					// inlined (for performance) code to check if must increment lineNumber:
				if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
				else if (c == '\r') {
					if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
						if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
					else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
						if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
				}
				
				cbuf[offset++] = c;
				++count;
				if (count == length) return count;
			}
		}
	}
	
	
	// read(char[]) will get redirected to the above one
	
	
	/**
	* Reads all the characters in the stream up to <i>but not including</i> the next line termination sequence or until end of stream is hit.
	* Additionally, it always increments the line number count (unless end of stream was encountered).
	* <p>
	* @return a String containing the contents of the line, but <i>not</i> including any line-termination characters;
	* result will be zero length if the next char(s) on the stream are a line termination sequence;
	* result will be null if end of stream is immediately encountered
	* @throws IOException if an I/O problem occurs
	*/
	public String readLine() throws IOException {
			// speed optimization: if know that have some data still in buffer, which also only happens when instance is still open, then see if can avoid the more expensive code below
		if (start < end) {
			int lineLength = -1;
			int i = start;
			for ( ; i < end - 1; i++) {	// use end - 1, not end, because of the look ahead code below
				if (buffer[i] == '\n') {	// hit an isolated \n (i.e. no \r precedes it)
					lineLength = i - start;
					break;
				}
				else if (buffer[i] == '\r') {
					if (buffer[i + 1] == '\n') {	// hit a \r\n pair
						lineLength = i - start;
						++i;	// must increment i to cover the following \n char
						break;
					}
					else {	// hit an isolated \r (i.e. no \n succedes it)
						lineLength = i - start;
						break;
					}
				}
			}
			//assert (i < end) : "i = " + i + " is >= end = " + end;
			if (lineLength >= 0) {
				//assert ((buffer[i] == '\n') || (buffer[i] == '\r')) : "buffer[" + i + "] is not a line termination char";
				String result = new String(buffer, start, lineLength);
				start = i + 1;
				//assert ((0 <= start) && (start <= end)) : "start = " + start + " is outside its valid range of [0, " + end + "]";
				++lineNumber;
				//assert (indexEndOfLineChar(result) == -1) : "result somehow contained an end of line char at index = " + indexEndOfLineChar(result);
				return result;
			}
		}
		
			// if buffer does not have data or hold a complete line, then must use the more expensive algorithm below:
		ensureOpen();
		
		StringBuilder sb = new StringBuilder(128);	// use StringBuilder and not CharArrayWriter since it converts into a String more efficiently
// +++ is the above comment about StringBuilder being more efficient still true in 1.5+?  recall reading that sun changed the code because of some subtle MT bug...
		while (true) {
			try {
				ensureBufferHasData();
			}
			catch (RuntimeException re) {
				return (sb.length() > 0) ? sb.toString() : null;
			}
			
			for ( ; start < end; ) {
				char c = buffer[start++];
				
					// inlined (for performance) code to check if must increment lineNumber and then return sb:
				if (c == '\n') {	// always increment lineNumber when read a newline char
					++lineNumber;
					//assert (indexEndOfLineChar(sb.toString()) == -1) : "result somehow contained an end of line char at index = " + indexEndOfLineChar(sb.toString());
					return sb.toString();
				}
				else if (c == '\r') {	// always increment lineNumber when read a carriage return char
						// but first see if need to skip over any subsequent newline:
					if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
						if (buffer[start] == '\n') ++start;
					}
					else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
						if (isNewLineNext()) ++start;
					}
					++lineNumber;
					//assert (indexEndOfLineChar(sb.toString()) == -1) : "result somehow contained an end of line char at index = " + indexEndOfLineChar(sb.toString());
					return sb.toString();
				}
				
				sb.append(c);
			}
		}
	}
// +++ for the highest in performance, should add another readLine method:
// --takes as arg an object which holds ref to a char[] and also has start and stop int fields
//		instead of returning a String, you simply fill in the fields of this object; the char[] could be the buffer of this class itself if the line is on the current buffer or it could be a new char[]
//		this could avoid any String object creation as well as allow the caller to directly look at the chars


// +++ need to add a readLineFull method that includes the end of line chars


/*
only used in some of the asserts above:

	private int indexEndOfLineChar(String s) {
		int r = s.indexOf('\r');
		if (r >= 0) return r;
		
		int n = s.indexOf('\n');
		if (n >= 0) return n;
		
		return -1;
	}
*/
	
	
	// -------------------- skip, skipFully, skipWhitespace --------------------
	
	
	/**
	* This method <i>attempts</i> to skip over the requested number of characters.
	* Will only block if no char initially available; will never block merely to skip all the requested chars.
	* Increments the line number count each time the <i>last</i> char of a line terminator sequence is encountered on the stream.
	* <p>
	* @param n the number of chars to attempt to skip
	* @return the number of characters actually skipped
	* @throws IllegalArgumentException if n < 0
	* @throws IOException if an I/O problem occurs
	* @see #skipFully skipFully
	*/
	public long skip(long n) throws IllegalArgumentException, IOException {
		Check.arg().notNegative(n);
		if (n == 0) return 0;
		
		// code below is almost identical to read(cbuf...)
		
		ensureOpen();
		
		int count = 0;
		while (true) {
			//assert (count < n) : "count = " + count + " >= n = " + n;	// Note: code above ensures that n > 0 if reach here
			if (count == 0) {	// only block if no data read yet
				try {
					ensureBufferHasData();
				}
				catch (RuntimeException re) {
					return 0;	// failure to read data at this point means have hit end of stream; NOTE: return 0 so as behave like Reader, and not -1 like read method does
				}
			}
			else {	// if have read some data, only proceed if ready guarantees will not block
				if (!ready()) return count;
				ensureBufferHasData();
			}
			
			for ( ; start < end; ) {
				char c = buffer[start++];
				
					// inlined (for performance) code to check if must increment lineNumber:
				if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
				else if (c == '\r') {
					if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
						if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
					else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
						if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
				}
				
				++count;
				if (count == n) return count;
			}
		}
	}
	
	
	/**
	* This method <i>guarantees</i> to skip over the specified number of chars.
	* Will block as often as needed in order to guarantee the requested chars are skipped.
	* If end of stream is encountered first, it throws an EOFException.
	* <p>
	* @param n the number of chars required to skip
	* @throws IOException if an I/O problem occurs
	* @throws EOFException if hit end of stream before skipping n chars
	* @see #skip skip
	*/
	public void skipFully(long n) throws IOException, EOFException {
		// code below is almost identical to skip except in blocking behavior and how it handles failure to fully skip:
		
		Check.arg().notNegative(n);
		if (n == 0) return;
		
		ensureOpen();
		
		int count = 0;
		while (true) {
			//assert (count < n) : "count = " + count + " >= n = " + n;	// Note: code above ensures that n > 0 if reach here
			try {
				ensureBufferHasData();
			}
			catch (RuntimeException re) {
				throw new EOFException("hit end of stream after skipping " + count + " chars instead of the required " + n + " chars");
			}
			
			for ( ; start < end; ) {
				char c = buffer[start++];
				
					// inlined (for performance) code to check if must increment lineNumber:
				if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
				else if (c == '\r') {
					if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
						if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
					else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
						if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
				}
				
				++count;
				if (count == n) return;
			}
		}
	}
	
	
	/**
	* Skips over all whitespace on the stream until hit first non-whitespace char (or end of stream);
	* that first non-whitespace char will be what is next read from the stream.
	* <p>
	* {@link Character#isWhitespace Character.isWhitespace} defines what constitutes whitespace.
	* <p>
	* @return the number of whitespace chars skipped over; will be >= 0
	* @throws IOException if an I/O problem occurs
	*/
	public int skipWhitespace() throws IOException {
		ensureOpen();
		
		int count = 0;
		while (true) {
			try {
				ensureBufferHasData();
			}
			catch (RuntimeException re) {
				return count;
			}
			
			for ( ; start < end; ) {
				char c = buffer[start++];
				
					// inlined (for performance) code to check if must increment lineNumber:
				if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
				else if (c == '\r') {
					if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
						if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
					else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
						if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
					}
				}
				
				if ( Character.isWhitespace(c) )
					++count;
				else {
					unread(c);
					return count;
				}
			}
		}
	}
	
	
	// -------------------- unread --------------------
	
	
	/**
	* Pushes back the supplied char to the stream.
	* Additionally, it decrements the line number count if have just unread the <i>last</i> char of a line terminator sequence.
	* <p>
	* If charAsInt equals -1, this method immediately returns without doing anything.
	* Thus, this method can always undo a call to {@link #read() read} if supply the result returned by read.
	* <p>
	* @throws IllegalArgumentException if charAsInt is neither a legitimate char value nor -1
	* @throws IOException if an I/O problem occurs
	*/
	public void unread(int charAsInt) throws IllegalArgumentException, IOException {
		if (charAsInt == -1) return;
		if (!CharUtil.isChar(charAsInt)) throw new IllegalArgumentException("charAsInt = " + charAsInt + " cannot be represented by a Java char type");

		ensureOpen();
		ensurePushbackCapacity(1);

		char c = (char) charAsInt;

			// inlined (for performance) code to check if must decrement lineNumber:
			// NOTE: the code below is done in the exact opposite order of the read method
		if (c == '\r') {
			if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
				if (buffer[start] != '\n') --lineNumber;	// only decrement lineNumber when unread a carriage return char if it is NOT followed by a newline
			}
			else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
				if (!isNewLineNext()) --lineNumber;	// only decrement lineNumber when unread a carriage return char if it is NOT followed by a newline
			}
		}
		else if (c == '\n') --lineNumber;	// always decrement lineNumber when unread a newline char

		buffer[--start] = c;
		//assert ((0 <= start) && (start <= end)) : "start = " + start + " is outside its valid range of [0, " + end + "]";
	}


	/**
	* Pushes back the specified portion of cbuf to the stream.
	* Additionally, it decrements the line number count each time the <i>last</i> char of a line terminator sequence goes by.
	* <p>
	* After this method returns, the next chars to be read will be cbuf[offset], cbuf[offset+1], etc.
	* Thus, this method can undo a call to {@link #read(char[], int, int) read} if supply the char[] just read into.
	* <p>
	* @param cbuf char array
	* @param offset offset of first char to push back
	* @param length number of chars to push back
	* <p>
	* @throws IllegalArgumentException if cbuf == null; offset < 0; length <= 0; offset + length > cbuf.length
	* @throws IOException if an I/O problem occurs
	*/
	public void unread(char[] cbuf, int offset, int length) throws IllegalArgumentException, IOException {
		Check.arg().notNull(cbuf);
		Check.arg().notNegative(offset);
		Check.arg().positive(length);
		if (offset + length > cbuf.length) throw new IllegalArgumentException("offset = " + offset + " + length = " + length + " is > cbuf.length = " + cbuf.length);

		ensureOpen();
		ensurePushbackCapacity(length);

		int indexLast = offset + length - 1;
		for (int i = indexLast; i >= offset; i--) {
			char c = cbuf[i];

				// inlined (for performance) code to check if must decrement lineNumber:
				// NOTE: the code below is done in the exact opposite order of the read method
			if (c == '\r') {
				if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
					if (buffer[start] != '\n') --lineNumber;	// only decrement lineNumber when unread a carriage return char if it is NOT followed by a newline
				}
				else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
					if (!isNewLineNext()) --lineNumber;	// only decrement lineNumber when unread a carriage return char if it is NOT followed by a newline
				}
			}
			else if (c == '\n') --lineNumber;	// always decrement lineNumber when unread a newline char

			buffer[--start] = c;
			//assert ((0 <= start) && (start <= end)) : "start = " + start + " is outside its valid range of [0, " + end + "]";
		}
	}


	/**
	* Convenience method that simply calls <code>{@link #unread(char[], int, int) unread}(cbuf, 0, cbuf.length)</code>.
	* <p>
	* @throws IllegalArgumentException if there is a problem with one of the args
	* @throws IOException If an I/O problem occurs
	*/
	public void unread(char[] cbuf) throws IllegalArgumentException, IOException {
		unread(cbuf, 0, cbuf.length);
	}


	// -------------------- misc Reader methods --------------------


	/**
	* Sets start > end, since that is one signal of the closed state.
	* Nulls the reference to buffer, which both allows it to be garbage collected as well as also signals the closed state.
	* Finally, closes {@link #in} then nulls the reference to it.
	*/
	@Override public void close() {
		start = buffer.length;	// recall that buffer.length > 0 always, so start > end will be satisfied
		end = 0;
		buffer = null;
		StreamUtil.close(in);
		in = null;
	}


	/**
	* Mark the present position in the stream.
	* <p>
* <i>The implementation here always throws an IOException.</i>
	* <p>
	* @throws IOException since mark is not supported
	*/
	public void mark(int readAheadLimit) throws IOException {
		throw new IOException("mark/reset not supported");
	}


	/**
	* Tell whether this stream supports the mark() operation.
	* <p>
* <i>The implementation here always returns false.</i>
	*/
	public boolean markSupported() {
		return false;
	}


	/**
	* Tell whether this stream is ready to be read.
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	public boolean ready() throws IOException {
		ensureOpen();
		if (start < end) return true;
		if (in != null) return in.ready();
		return false;
	}


	/**
	* Reset the stream.
	* <p>
* <i>The implementation here always throws an IOException.</i>
	* <p>
	* @throws IOException since reset is not supported
	*/
	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}


	// -------------------- parsing convenience methods: isNewLineNext, hasData, isTokenNext, confirmTokenNext --------------------


	/**
	* Reports if the next character on the stream is a newline char (i.e. '\n') or not.
	* <p>
	* From the caller's perspective, the stream's state is unaffected by this method.
	* (Internally, reads into the buffer etc may have to be performed.)
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	private boolean isNewLineNext() throws IOException {
			// speed optimization: if know that have enough data still in buffer, which also only happens when instance is still open, then can avoid the slightly more expensive code below
		if (start < end) return (buffer[start] == '\n');

		ensureOpen();
		try {
			ensureBufferHasData();
		}
		catch (RuntimeException re) {
			return false;
		}

		return (buffer[start] == '\n');
	}
// +++ is private for now since is currently only used internally for lineNumber look ahead purposes; any need to make public?


	/**
	* Reports whether or not data can still be read.
	* Unlike  {@link #ready ready}, <i>this method will block if necessary,</i>
	* because it returns false only if end of stream has been reached,
	* which is a stronger guarantee than ready provides.
	* <p>
	* From the caller's perspective, the stream's state is unaffected by this method.
	* (Internally, reads into the buffer etc may have to be performed.)
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	public boolean hasData() throws IOException {
			// speed optimization: if know that have some data still in buffer, which also only happens when instance is still open, then can immediately return true:
		if (start < end) return true;

		ensureOpen();
		try {
			ensureBufferHasData();
		}
		catch (RuntimeException re) {
			return false;
		}
		return true;	// only get here if buffer now has data
	}


	/** Returns <code>{@link #isTokenNext(String, boolean) isTokenNext}(token, true)</code> (i.e. is always case sensitive). */
	public boolean isTokenNext(String token) throws IllegalArgumentException, IOException {
		return isTokenNext(token, true);
	}


	/**
	* Determines if token's chars next occur on the stream.
	* <p>
	* From the caller's perspective, the stream's state is unaffected by this method.
	* (Internally, various buffer operations may have to be performed.)
	* <p>
	* @param isCaseSensitive if true, specifies that case matters in matching the chars of token; false means that case is irrelevant
	* @throws IllegalArgumentException if token is null or zero-length
	* @throws IOException if an I/O problem occurs
	*/
	public boolean isTokenNext(String token, boolean isCaseSensitive) throws IllegalArgumentException, IOException {
		Check.arg().notNull(token);
		if (token.length() == 0) throw new IllegalArgumentException("token is zero-length");

			// speed optimization: if know that have enough data still in buffer, which also only happens when instance is still open, then can avoid the more expensive code below
		if (token.length() < end - start) {
			if (isCaseSensitive) {
				for (int i = 0; i < token.length(); i++) {
					if (token.charAt(i) != buffer[start + i]) return false;
				}
				return true;
			}
			else {
				for (int i = 0; i < token.length(); i++) {
					if (!CharUtil.matches(token.charAt(i), buffer[start + i], isCaseSensitive)) return false;
				}
				return true;
			}
		}

			// if buffer does not have enough data, then must use the more expensive algorithm below:
		ensureOpen();
		int i = 0;
		try {
			while (true) {
				try {
					ensureBufferHasData();
				}
				catch (RuntimeException re) {
					return false;
				}

				for ( ; start < end; ) {
					char c = buffer[start];
					if (isCaseSensitive) {
						if (token.charAt(i) != c) return false;
					}
					else {
						if (!CharUtil.matches(token.charAt(i), c, isCaseSensitive)) return false;
					}
					
					++start;
					++i;
					if (i == token.length()) return true;
				}
			}
		}
		finally {
				// When get here, i will always point to the first index of token for which token failed to match the stream (or token.length if all chars matched)
				// and start will always point to next index of buffer after the last char which matched token.
				// Unread all the token chars which matched, unreading them in reverse order that read:
			ensurePushbackCapacity(i);
			for (int j = i - 1; j >= 0; j--) {
				buffer[--start] = token.charAt(j);
			}
		}
	}


	/** Returns <code>{@link #skipTillTokenNext(String, boolean) skipTillTokenNext}(token, true)</code> (i.e. is always case sensitive). */
	public long skipTillTokenNext(String token) throws IllegalArgumentException, IOException {
		return skipTillTokenNext(token, true);
	}


	/**
	* Skips over as many chars as necessary until token is next on the stream.
	* <p>
	* @param isCaseSensitive if true, specifies that case matters in matching the chars of token; false means that case is irrelevant
	* @return the number of chars skipped over before token was found; returns -1 if hit end of stream first
	* @throws IllegalArgumentException if token is null or zero-length
	* @throws IOException if an I/O problem occurs
	*/
	public long skipTillTokenNext(String token, boolean isCaseSensitive) throws IllegalArgumentException, IOException {
		for (long countSkip = 0; ; countSkip++) {
			if (isTokenNext(token, isCaseSensitive)) return countSkip;
			if (read() == -1) return -1;	// pop off the next char, immediately returning -1 if hit end of stream
		}
	}
// +++ above impl is simple but VERY suboptimal in performance
// rewrite using better algorithm which looks deep down the stream using buffer
// to do this, copy the code from skip and customize it

// +++ when have the above method working, add it to my search & replace tool


	/** Returns <code>{@link #confirmTokenNext(String, boolean) confirmTokenNext}(token, true)</code> (i.e. is always case sensitive). */
	public void confirmTokenNext(String token) throws IllegalArgumentException, IOException, ParseException {
		confirmTokenNext(token, true);
	}


	/**
	* Confirms that the supplied token's chars next occur on the stream.
	* Chars are read off the stream until either a mismatch is encountered or all of token's chars have been matched.
	* <p>
	* If token is fully read, then <i>the stream's state is affected by this method:</i>
	* all the values are read off and the line number count may be increased.
	* <i>If a mismatch is encountered, however, the stream state is restored before return</i>
	* which, in this case, will be abnormal termination with a ParseException thrown.
	* Abnormal termination thru any other exception, however, may leave the stream in an indeterminate state.
	* <p>
	* @throws IllegalArgumentException if token is null or zero-length
	* @throws IOException if an I/O problem occurs
	* @throws ParseException if token is not fully matched by the stream's next contents;
	* the stream's state will be restored before return
	*/
	public void confirmTokenNext(String token, boolean isCaseSensitive) throws IllegalArgumentException, IOException, ParseException {
		Check.arg().notNull(token);
		if (token.length() == 0) throw new IllegalArgumentException("token is zero-length");

		ensureOpen();
		int i = 0;
		try {
			while (true) {
				try {
					ensureBufferHasData();
				}
				catch (RuntimeException re) {
					throw new ParseException("Failed to confirm token on the stream; first discrepancy occured at char #" + i, getLineNumber());
				}

				for ( ; start < end; ) {
					char c = buffer[start++];

						// inlined (for performance) code to check if must increment lineNumber:
					if (c == '\n') ++lineNumber;	// always increment lineNumber when read a newline char
					else if (c == '\r') {
						if (start < end) {	// if know that have data still in buffer, then can avoid call to isNewLineNext
							if (buffer[start] != '\n') ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
						}
						else {	// if have run out of data in buffer then have to restock it (is done in call to isNewLineNext)
							if (!isNewLineNext()) ++lineNumber;	// only increment lineNumber when read a carriage return char if it is NOT followed by a newline
						}
					}

					if (isCaseSensitive) {
						if (token.charAt(i) != c) throw new ParseException("Failed to confirm token on the stream; first discrepancy occured at char #" + i, getLineNumber());
					}
					else {
						if (!CharUtil.matches(token.charAt(i), c, isCaseSensitive)) throw new ParseException("Failed to confirm token on the stream; first discrepancy occured at char #" + i, getLineNumber());
					}
					if (++i == token.length()) return;
				}
			}
		}
		catch (ParseException pe) {
				// unread all the token chars which read, unreading them in reverse order that read:
				// when get here, i will always point to the first index of token for which token failed to match the stream
			ensurePushbackCapacity(i);
			for (int j = i - 1; j >= 0; j--) {
				unread( token.charAt(j) );
			}

			throw pe;
		}
	}


	/** Returns <code>{@link #readThruToken(String, boolean, boolean) readThruToken}(token, true, false)</code> (i.e. is always case sensitive and excludes token from result). */
	public String readThruToken(String token) throws IllegalArgumentException, IOException, IllegalStateException {
		return readThruToken(token, true, false);
	}


	/**
	* Reads over as many chars as necessary until token is read thru.
	* <p>
	* @param isCaseSensitive if true, specifies that case matters in matching the chars of token; false means that case is irrelevant
	* @param includeToken if true, specifies that token's chars are included (at the end) of the result; false means that they are left out;
	* note that if includeToken is true and isCaseSensitive is false, then the case of these token chars included in the result
	* may differ from the case of what occurred on the stream
	* @return all chars which were read up to token plus, if includeToken is true, token itself (otherwise token is excluded)
	* @throws IllegalArgumentException if token is null or zero-length
	* @throws IOException if an I/O problem occurs
	* @throws IllegalStateException if fail to read thru token
	*/
	public String readThruToken(String token, boolean isCaseSensitive, boolean includeToken) throws IllegalArgumentException, IOException, IllegalStateException {
		StringBuilder sb = new StringBuilder();

		while (!isTokenNext(token, isCaseSensitive)) {
			int c = read();
			if (c == -1) throw new IllegalStateException("hit end of stream before ever encountering token = " + token);

			sb.append((char) c);
		}
		
		skipFully(token.length());
		if (includeToken) sb.append(token);

		return sb.toString();
	}


	// -------------------- UnitTest (static inner class) --------------------


// +++ class below currently only tests for correctness;
// should also add benchmarking test to compare performance
// (On 2004/4/7 in my class bb.finance.VendorTickData,
// I found that ParseReader was ~10% faster at readLine then LineNumberReader when it was supplied with the full char[]
// HOWEVER, when both were supplied with a StringReader, then LineNumberReader was ~1-2% faster; not sure why, but this should not have happened...)

	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final java.util.Random random = new java.util.Random();	// used to determine random actions
		
		private static final String[] lineTerminators = {"\n", "\r", "\r\n"};
		
		private static final int numberLines = 10*1000;
		private static final String testString = makeTestString(numberLines);
		
		private static final int numberTests = 100*1000;
		
		@Test public void test_all() throws Exception {
			ParseReader parseReader = null;
			try {
				parseReader = new ParseReader( new StringReader(testString), new char[16], 0, 0, 1 );	// CRITICAL: best to supply a very small buffer during testing, to force lots of buffer operations
				
				StringBuilder readStorage = new StringBuilder();	// will hold the results of ALL reads (including skips)
				
					// do various random operations on the ParseReader:
				for (int i = 0; i < numberTests; i++) {
					switch (random.nextInt(10)) {
						case 0:
							doManySingleReads(parseReader, readStorage);
							break;
						
						case 1:
							doArrayRead(parseReader, readStorage);
							break;
						
						case 2:
							doReadLine(parseReader, readStorage);
							doConsistencyCheck(parseReader, readStorage);
						// Note: doConsistencyCheck tests the following methods: ready, hasData, getLineNumber, isTokenNext, confirmTokenNext
							break;
						
						case 3:
							doManySingleUnreads(parseReader, readStorage);
							break;
						
						case 4:
							doArrayUnRead(parseReader, readStorage);
							break;
						
						case 5:
							doSkip(parseReader, readStorage);
						// Note: doSkip tests the following methods: readLine, hasData, getLineNumber, skip
							break;
						
						case 6:
							doSkipFully(parseReader, readStorage);
						
						// Note: doSkipFully tests the following methods: readLine, hasData, getLineNumber, skipFully, isTokenNext
							break;
						
						case 7:
							doSkipWhitespace(parseReader, readStorage);
						// Note: doSkipWhitespace tests the following methods: readLine, hasData, getLineNumber, confirmTokenNext, skipWhitespace
							break;
						
						case 8:
							doReadLine(parseReader, readStorage);
							doSkipTillTokenNext(parseReader, readStorage);
							break;
						
						case 9:
							doReadThruToken(parseReader, readStorage);
							break;
						
						default:
							throw new RuntimeException("somehow hit the default action");
					}
				}
				
					// since no information should be lost--should be stored either in readStorage or pushed back to parseReader--then fully read out parseReader into readStorage and confirm that readStorage equals testString:
				while (readStorage.length() < testString.length()) {
					readStorage.append( (char) parseReader.read() );
				}
				Assert.assertEquals( testString, readStorage.toString() );
			}
			finally {
				StreamUtil.close(parseReader);
			}
		}
		
		/**
		* Make a test String which has the property that every line consists of the line number's
		* characters followed by a line termination sequence determined by the getNthLineTerminator method.
		*/
		private static String makeTestString(int length) {
			StringBuilder sb = new StringBuilder();
			for (int n = 1; n <= length; n++) {	// note that n starts at 1, not 0
				sb.append(n);
				sb.append( getNthLineTerminator(n) );
			}
			return sb.toString();
		}
		
		/** Returns the value from lineTerminators at index = n % lineTerminators.length. */
		private static String getNthLineTerminator(int n) {
			int index = n % lineTerminators.length;
			return lineTerminators[index];
		}
		
		/** Perform the operation of reading a single char some random number of times. */
		private static void doManySingleReads(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			int numberAvailable = testString.length() - readStorage.length();
			if (numberAvailable == 0) return;
			
			int numberReadsDoNow = random.nextInt(numberAvailable);
			
			for (int i = 0; i < numberReadsDoNow; i++) {
				readStorage.append( (char) parseReader.read() );
			}
			System.out.println("doManySingleReads: " + numberReadsDoNow);
		}
		
		/** Perform the operation of reading a char[] of some random size. */
		private static void doArrayRead(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			int numberAvailable = testString.length() - readStorage.length();
			if (numberAvailable == 0) return;
			
			int sizeReadArray = random.nextInt(numberAvailable);
			char[] array = new char[sizeReadArray];
			if (array.length == 0) return;	// CRITICAL: must do this check because Random.nextInt can return 0, and ParseReader objects to 0 lengths
			
			int numberRead = parseReader.read(array);
			readStorage.append( array, 0, numberRead );
			System.out.println("doArrayRead: " + array.length);
		}
		
		/** Perform the operation of reading 1 line of data. */
		private static void doReadLine(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			String eol = getNthLineTerminator( parseReader.getLineNumber() );
			String line = parseReader.readLine();
			if (line == null) return;
			else if (line.length() == 0) {	// means that \r or \n was next on the stream
					// nasty special case: must detect if, before readLine was called, a \r was previously read off and \n was next
					// if this is the case, then only append a single following \n:
				char cLastRead = readStorage.charAt( readStorage.length() - 1 );
				if (cLastRead == '\r') readStorage.append('\n');
					// otherwise append the complete line termination sequence:
				else readStorage.append( eol );
			}
			else
				readStorage.append( line ).append( eol );
				
			System.out.println("doReadLine: " + line.length());
		}
		
		/**
		* First checks that parseReader has data left to read.
		* <p>
		* Then confirms that parseReader's idea of lineNumber is consistent with the value on the current line.
		* <i>This implies that this method may only be called when have just read thru a line termination sequence,
		* so that the start of a line is what is immediately next on the stream.</i>
		* For example, this method may always safely be called after calling readLine.
		* <p>
		* Next, confirms that the line terminator sequence of the current line is correct.
		* If the line terminator sequence is what is expected, then it is read thru so that the stream now points to the start
		* of the next line (i.e. the one following the line that used to be next on the stream when method was called).
		* If a different line terminator sequence is encountered, however, then it is NOT read thru but is left next on the stream.
		* <p>
		* This method tests the following methods: ready, hasData, getLineNumber, isTokenNext, confirmTokenNext.
		* <p>
		* <b>Warning:</b> this method assumes that the ParseReader was constructed with the result of makeTestString.
		* <p>
		* @throws RuntimeException if any discrepancy is encountered
		*/
		private static void doConsistencyCheck(ParseReader parseReader, StringBuilder readStorage) throws IOException, ParseException, RuntimeException {
			int numberAvailable = testString.length() - readStorage.length();
			if (numberAvailable == 0) return;
			
			if (!parseReader.ready()) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("DISCREPANCY: ready returned false when " + numberAvailable + " chars should still be available to read off of the stream");
			}
			if (!parseReader.hasData()) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("DISCREPANCY: hasData returned false when " + numberAvailable + " chars should still be available to read off of the stream");
			}
			
			String lineValueExpected = String.valueOf( parseReader.getLineNumber() );
			if (!parseReader.isTokenNext(lineValueExpected, random.nextBoolean())) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("DISCREPANCY: was expecting the current line to have the value " + lineValueExpected + " when it actually has the value " + parseReader.readLine());
			}
			
			parseReader.confirmTokenNext(lineValueExpected);
			readStorage.append(lineValueExpected);
			
			String lineEndExpected = getNthLineTerminator( parseReader.getLineNumber() );
			if (!parseReader.isTokenNext(lineEndExpected, random.nextBoolean())) {
				printLastSeveralCharsRead(readStorage);
				
				String vExpected = null;
				if (lineEndExpected.equals("\r\n")) vExpected = "\\r\\n";
				else if (lineEndExpected.equals("\n")) vExpected = "\\n";
				else if (lineEndExpected.equals("\r")) vExpected = "\\r";
				
				String vActual = null;
				if (parseReader.isTokenNext("\r\n", random.nextBoolean())) vActual = "\\r\\n";
				else if (parseReader.isTokenNext("\n", random.nextBoolean())) vActual = "\\n";
				else if (parseReader.isTokenNext("\r", random.nextBoolean())) vActual = "\\r";
				
				throw new RuntimeException("DISCREPANCY: was expecting the current line to end with " + vExpected + " when it actually ends with " + vActual);
			}
			
			parseReader.confirmTokenNext(lineEndExpected);
			readStorage.append(lineEndExpected);
			
			System.out.println("consistency check passed at line number = " + parseReader.getLineNumber());
		}
// Old implementation.
// May still be valuable to keep around because it does NOT rely on readLine, ready, hasData, getLineNumber, isTokenNext, confirmTokenNext all working:
		/**
		* Confirm that the ParseReader's idea of lineNumber is consistent with the value
		* recorded on the last line read. Do this by comparing the line number value
		* that have most recently read and stored in the token buffer with what the ParseReader reports.
		* <p>
		* Also confirm that the line terminator sequence is correct.
		* <p>
		* <b>Warning:</b> this method assumes that the ParseReader was constructed with the result of makeTestString.
		*/
/*
		private static void doConsistencyCheck(ParseReader parseReader, StringBuilder readStorage) throws IOException {
				// the procedure below assumes that will be able to go back over a certain number of chars
			if (readStorage.length() < 10) return;
			
				// start with the last char just read
			int index = readStorage.length() - 1;
			
				// need to determine a factor for the algorithm below (it accounts for whether will cross a line termination boundary or not):
			int adjustmentFactor;
			if ( (readStorage.charAt(index) == '\r') && parseReader.isNewLineNext() )
				adjustmentFactor = 0;
			else
				adjustmentFactor = 1;
				
				// first skip back over any value chars on this line (which will NOT be whitespace), if they are present:
			while ( !Character.isWhitespace( readStorage.charAt(index) ) ) {
				--index;
			}
			
				// now skip back over line terminators (which will be whitespace):
			while ( Character.isWhitespace( readStorage.charAt(index) ) ) {
				--index;
			}
			
				// now record the value chars that describe an int (which will NOT be whitespace):
			StringBuilder prevChars = new StringBuilder();
			while ( !Character.isWhitespace( readStorage.charAt(index) ) ) {
				prevChars.append( readStorage.charAt( index-- ) );
			}
			
				// determine the int value represented by those chars:
			int lineValue = Integer.parseInt( prevChars.reverse().toString() );
			
				// confirm that it agrees with the ParseReader's value:
			if (lineValue + adjustmentFactor != parseReader.getLineNumber()) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("hit a line number discrepancy: lineValue + adjustmentFactor = " + (lineValue + adjustmentFactor) + ", parseReader.getLineNumber() = " + parseReader.getLineNumber());
			}
			
				// now record the previous line termination chars (which will be whitespace):
			StringBuilder prevLineEnd = new StringBuilder();
			while ( Character.isWhitespace( readStorage.charAt(index) ) ) {
				prevLineEnd.append( readStorage.charAt( index-- ) );
			}
			
				// confirm that the line terms are what they are supposed to be:
			if ( !prevLineEnd.reverse().toString().equals( getNthLineTerminator(lineValue - 1) ) ) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("hit a line terminator discrepancy!");
			}
			
			System.out.println("consistency check passed at line number = " + lineValue);
		}
*/

		private static void printLastSeveralCharsRead(StringBuilder readStorage) {
			System.out.println();
			System.out.println("Below are the last several chars read (printed on separate lines for convenience):");
			int begin = Math.max(readStorage.length() - 50, 0);
			for (int i = begin; i < readStorage.length(); i++) {
				char c1 = readStorage.charAt(i);
				char c2 = (i + 1 < readStorage.length()) ? readStorage.charAt(i + 1) : 0;
				if (c1 == '\n') System.out.println("\\n");
				else if (c1 == '\r') {
					System.out.print("\\r");
					if (c2 != '\n') System.out.println();
				}
				else System.out.print(c1);
			}
			System.out.println();
		}
		
		/** Perform the operation of unreading a single char some random number of times. */
		private static void doManySingleUnreads(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			int numberAvailable = readStorage.length();
			if (numberAvailable == 0) return;
			
			int numberUnreadsDoNow = random.nextInt(numberAvailable);
			
			for (int i = 0; i < numberUnreadsDoNow; i++) {
				int lastIndex = readStorage.length() - 1;
				char lastChar = readStorage.charAt( lastIndex );
				readStorage.deleteCharAt( lastIndex );
				parseReader.unread( lastChar );
			}
			System.out.println("doManySingleUnreads: " + numberUnreadsDoNow);
		}
		
		/** Perform the operation of unreading a char[] of some random size. */
		private static void doArrayUnRead(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			int numberAvailable = readStorage.length();
			if (numberAvailable == 0) return;
			
			int sizeUnreadArray = random.nextInt(numberAvailable);
			char[] array = new char[sizeUnreadArray];
			if (array.length == 0) return;
			
			int srcBegin = readStorage.length() - array.length;
			int srcEnd = readStorage.length();
			readStorage.getChars(srcBegin, srcEnd, array, 0);
			readStorage.delete(srcBegin, srcEnd);
			parseReader.unread(array);
			System.out.println("doArrayUnRead: " + array.length);
		}
		
		/**
		* First calls doReadLine so that stream is positioned at the known beginning of a line.
		* Then calls skip for a number of chars that should precisely be the data on the next line,
		* based on knowing what that line should look like given its number.
		*/
		private static void doSkip(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			doReadLine(parseReader, readStorage);
			if (!parseReader.hasData()) return;
			
			int n = parseReader.getLineNumber();
			String dataShouldBe = String.valueOf(n);
			long numberSkipped = parseReader.skip( dataShouldBe.length() );
			readStorage.append( dataShouldBe.substring(0, (int) numberSkipped) );
			
			System.out.println("doSkip: request = " + dataShouldBe.length() + ", numberSkipped = " + numberSkipped);
		}
		
		/**
		* First calls doReadLine so that stream is positioned at the known beginning of a line.
		* Then calls skipFully for a number of chars that should precisely be the complete next line,
		* based on knowing what that line should look like given its number.
		* Then confirms that the next line after the skip looks like what it is supposed to.
		*/
		private static void doSkipFully(ParseReader parseReader, StringBuilder readStorage) throws IOException {
			doReadLine(parseReader, readStorage);
			if (!parseReader.hasData()) return;
			
			int n = parseReader.getLineNumber();
			String lineShouldBe = String.valueOf(n) + getNthLineTerminator(n);
			parseReader.skipFully( lineShouldBe.length() );
			readStorage.append(lineShouldBe);
			
			if (n == numberLines) return;
			
			String lineShouldFollow = String.valueOf(n + 1) + getNthLineTerminator(n + 1);
			if (!parseReader.isTokenNext(lineShouldFollow, random.nextBoolean())) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("DISCREPANCY: doSkipFully expected to see the following line:" + "\n" + lineShouldFollow);
			}
			
			System.out.println("doSkipFully: " + n);
		}
		
		/**
		* First calls doReadLine so that stream is positioned at the known beginning of a line.
		* Then calls confirmTokenNext for the chars that should precisely be the data on the next line,
		* based on knowing what that line should look like given its number.
		* Then calls skipWhitespace, which should skip precisely over the line termination char(s).
		* Then confirms that skipWhitespace skipped over the correct number of chars.
		*/
		private static void doSkipWhitespace(ParseReader parseReader, StringBuilder readStorage) throws IOException, ParseException {
			doReadLine(parseReader, readStorage);
			if (!parseReader.hasData()) return;
			
			int n = parseReader.getLineNumber();
			String dataShouldBe = String.valueOf(n);
			parseReader.confirmTokenNext( dataShouldBe );
			readStorage.append(dataShouldBe);
			int numberSkipped = parseReader.skipWhitespace();
			if (numberSkipped != getNthLineTerminator(n).length()) {
				printLastSeveralCharsRead(readStorage);
				throw new RuntimeException("DISCREPANCY: doSkipWhitespace expected to skip over " + getNthLineTerminator(n).length() + " line termination char(s), but actually skipped over " + numberSkipped + " chars");
			}
			readStorage.append(getNthLineTerminator(n));
			
			System.out.println("doSkipWhitespace: " + n);
		}
		
		/**
		* Tests skipTillTokenNext.
		* <p>
		* As a token, selects one of the remaining line numbers still ahead on the stream.
		* Because of the need to store the skipped items in readStorage
		* (which is done based on knowing what the tokens should look like),
		* <i>this method may only be called when have just read thru a line termination sequence,
		* so that the start of a line is what is immediately next on the stream.</i>
		* For example, this method may always safely be called after calling readLine.
		*/
		private static void doSkipTillTokenNext(ParseReader parseReader, StringBuilder readStorage) throws IOException, ParseException, RuntimeException {
			if (!parseReader.hasData()) return;
			
			int lineNumber = parseReader.getLineNumber();
			int range = numberLines - lineNumber + 1;
			int lineNumberNext = lineNumber + random.nextInt(range);
			
			long numberSkipped = parseReader.skipTillTokenNext( String.valueOf(lineNumberNext), random.nextBoolean() );
			if (numberSkipped == -1) throw new RuntimeException("DISCREPANCY: doSkipTillTokenNext skipped over -1 chars when it should have skipped >= 0");
			
				// remember to append skipped stuff to readStorage:
			int lengthInitial = readStorage.length();
			for (int i = lineNumber; i < lineNumberNext; i++) {
				readStorage.append(String.valueOf(i));
				readStorage.append(getNthLineTerminator(i));
			}
			int numberAppended = readStorage.length() - lengthInitial;
			
			if (numberSkipped != numberAppended) {
				throw new RuntimeException("DISCREPANCY: doSkipTillTokenNext skipped over " + numberSkipped + " chars, but appended " + numberAppended + " chars to readStorage");
			}
			
			System.out.println("doSkipTillTokenNext, lineNumberNext: " + lineNumberNext);
		}
		
		/**
		* Tests readThruToken.
		* <p>
		* As a token, selects one of the remaining line numbers still ahead on the stream.
		*/
		private static void doReadThruToken(ParseReader parseReader, StringBuilder readStorage) throws IOException, ParseException, RuntimeException {
			if (!parseReader.hasData()) return;
			
			int lineNumber = parseReader.getLineNumber();
			int range = numberLines - lineNumber + 1;
			int lineNumberNext = lineNumber + random.nextInt(range);
			if (lineNumberNext == lineNumber) return;
			
			String token = String.valueOf(lineNumberNext);
			boolean tokenIncluded = random.nextBoolean();
			String charsRead = parseReader.readThruToken(token, random.nextBoolean(), tokenIncluded );
			if (tokenIncluded && !charsRead.endsWith(token)) throw new RuntimeException("DISCREPANCY: the result of doReadThruToken should have ended with token = " + token);
			readStorage.append(charsRead);
			if (!tokenIncluded) readStorage.append(token);
			
			System.out.println("doReadThruToken, lineNumberNext: " + lineNumberNext);
		}
		
	}
	
	
}
