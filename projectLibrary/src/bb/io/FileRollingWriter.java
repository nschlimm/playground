/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer's notes:

+++ instead of doing explicit file writing, may want to consider this: http://www.prevayler.org/wiki.jsp
*/

package bb.io;

import bb.util.Check;
import bb.util.StringUtil;
import bb.util.logging.LogUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.Assert;
import org.junit.Test;

/**
* Similar to {@link FileWriter} except that:
* <ol>
*  <li>writes to a rolling series of files instead of just one file</li>
*  <li>buffers its output</li>
*  <li>is not multithread safe</li>
* </ol>
* At any moment in time, there is a single output file.
* When {@link #checkForRollover checkForRollover} detects that it is time to rollover to a new file,
* the current file is transparently closed and the next one in sequence is opened and used for subsequent writing.
* <p>
* All the chars supplied to a given append/write call always appear in the same file
* (i.e. they will never be split across two files).
* However, this means that the lengths of the rollover files are not necessarily exactly equal.
* <p>
* Note that checkForRollover is only automatically called if {@link #autoCheck} is true.
* In this case, it is the first action performed (after arg checking) by every one of the append/write methods.
* But if autoCheck is false, the user has to manually call checkForRollover.
* <p>
* Using autoCheck set to true is most safe and convenient, but it has a significant drawback:
* if many append operations are being used to, say, print out the components of a line of text,
* then it could happen that one of the appends causes the first part of the line
* to be in one file and the remaining part of the line to be in the next file.
* This may complicate subsequent parsing of the files.
* <p>
* There are at least two ways to prevent this.
* The first method, if want to retain autoCheck set to true, is to use but a single append/write for the entire line.
* For example, call
* <pre><code>
*	fileRollingWriter.write( linePart1 + linePart2 + '\n' );	// linePart1 and linePart2 are String variables
* </code></pre>
* The only problem with this approach is that it creates a temp StringBuilder instance and does extra char copying for each line of output,
* which causes suboptimal performance.
* The second method is to use autoCheck set to false and manually call checkForRollover after the line end char sequence has been written.
* For example, call
* <pre><code>
*	fileRollingWriter.append( linePart1 ).append( linePart2 ).append( '\n' ).checkForRollover();
* </code></pre>
* The only problem with this approach is that you must remember to always call checkForRollover.
* <p>
* The names of the files all share the same prefix and suffix, but in the middle of their name is a series number.
* The minimum number of digits of this series number may be specified.
* <blockquote>
*	For file sorting purposes, <i>it is crucial that the series numbers are represented with exactly the same number of digits</i>.
*	For example, suppose that there will 0-99 rollover files with prefix "log" and suffix ".txt".
*	Then the filenames should be log_#00.txt, log_#01.txt, ..., log_#99.txt to allow typical operating systems to sort them properly.
* </blockquote>
* <p>
* Note that the logic inside {@link #nextFileCount nextFileCount} should ensure that previously existing rollover files are never overwritten.
* <p>
* <i>Because this class does its own internal buffering, instances should not be wrapped inside a {@link BufferedWriter}.</i>
* <p>
* One use of this class is to handle really large data sets.
* If a process only writes to a single big file, the application may hold a file lock for too long.
* Furthermore, one single big file may be too big to open in, say, a text editor.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class FileRollingWriter extends Writer {
	
	// -------------------- fields --------------------
	
	/** Parent directory of the rollover files. */
	private final File directory;

	/** Prefix to append to the begining of all rollover file names.  May be null (in which case "" is substituted). */
	private final String filePrefix;

	/**
	* If > 0, specifies the minimum number of digits that must appear in each filename's series number
	* (leading zeroes will be appended to a given number if necessary to make it achieve this length).
	* Otherwise, must have the special value -1, which specifies that the series numbers are to be represented in the usual way,
	* that is, each number is written using precisely the minimum number of digits that are required.
	*/
	private final int minDigitsInSeriesNumber;

	/** Suffix to append to the end of all rollover file names.  May be null (in which case "" is substituted). */
	private final String fileSuffix;

	/**
	* Maximum number of chars already written to the <i>current</i> rollover file that will allow
	* before closing this file and start writing to a new one.
	*/
	private final long nCharsTillRollover;

	/**
	* If true, then every char writing method (e.g. append, write) will first call {@link #checkForRollover checkForRollover} before doing any output.
	* Otherwise, if false, then it is the responsibility of the user to explicitly call checkForRollover.
	*/
	private final boolean autoCheck;
	
	private boolean open = true;
	
	private Writer writer;
	private long nCharsWritten = 0;
	
	// -------------------- constructor --------------------
	
	/** Calls <code>{@link #FileRollingWriter(File, String, int, String, long, boolean) this}(directory, filePrefix, minDigitsInSeriesNumber, fileSuffix, nCharsTillRollover, true)</code>. */
	public FileRollingWriter(File directory, String filePrefix, int minDigitsInSeriesNumber, String fileSuffix, long nCharsTillRollover) throws IllegalArgumentException {
		this(directory, filePrefix, minDigitsInSeriesNumber, fileSuffix, nCharsTillRollover, true);
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* The filenames that will be produced by this instance will have the form <code>filePrefix_#<i>nnn</i>fileSuffix</code>
	* where filePrefix and fileSuffix are the args to this method,
	* while <i>nnn</i> are the series numbers represented according to minDigitsInSeriesNumber.
	* <p>
	* @param directory assigned to {@link #directory}
	* @param filePrefix assigned to {@link #filePrefix}
	* @param fileSuffix assigned to {@link #fileSuffix}
	* @param nCharsTillRollover assigned to {@link #nCharsTillRollover}
	* @param autoCheck assigned to {@link #autoCheck}
	* @throws IllegalArgumentException if directory is {@link Check#validDirectory not valid};
	* minDigitsInSeriesNumber < -1; nCharsTillRollover <= 0
	*/
	public FileRollingWriter(File directory, String filePrefix, int minDigitsInSeriesNumber, String fileSuffix, long nCharsTillRollover, boolean autoCheck) throws IllegalArgumentException {
		Check.arg().validDirectory(directory);
		if (minDigitsInSeriesNumber < -1) throw new IllegalArgumentException("minDigitsInSeriesNumber = " + minDigitsInSeriesNumber + " < -1");
		Check.arg().positive(nCharsTillRollover);
		
		this.directory = directory;
		this.filePrefix = (filePrefix != null) ? filePrefix + "_#" : "_#";
		this.minDigitsInSeriesNumber = minDigitsInSeriesNumber;
		this.fileSuffix = (fileSuffix != null) ? fileSuffix : "";
		this.nCharsTillRollover = nCharsTillRollover;
		this.autoCheck = autoCheck;
	}
	
	// -------------------- Writer methods --------------------
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public FileRollingWriter append(char c) throws SecurityException, IOException {
		write(c);
		return this;
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public FileRollingWriter append(CharSequence csq) throws SecurityException, IOException {
		if (csq == null) write("null");
		else write( csq.toString() );
		return this;
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public FileRollingWriter append(CharSequence csq, int start, int end) throws SecurityException, IOException {
		CharSequence cs = (csq == null ? "null" : csq);
		write( cs.subSequence(start, end).toString() );
		return this;
	}
	
	@Override public void close() {
		if (open) {
			StreamUtil.close(writer);
			writer = null;
			open = false;
		}
	}
	
	@Override public void flush() throws IOException {
		if (!open) throw new IOException("instance is closed");
		
		if (writer != null) writer.flush();
	}
	
	/** Calls <code>{@link #write(char[], int, int) write}( cBuffer, 0, cBuffer.length )</code>. */
	@Override public void write(char[] cBuffer) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(cBuffer);	// if do not check it here, then the call to cBuffer.length below will result in a NullPointerException
		
		write( cBuffer, 0, cBuffer.length );
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws IllegalArgumentException if cBuffer == null
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public void write(char[] cBuffer, int offset, int length) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(cBuffer);	// if do not check it here, then the call to writer.write below will result in a NullPointerException
		if (!open) throw new IOException("instance is closed");
		
		if (writer == null) makeWriter();
		if (autoCheck) checkForRollover();
		writer.write( cBuffer, offset, length );
		nCharsWritten += length;
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public void write(int c) throws SecurityException, IOException {
		if (!open) throw new IOException("instance is closed");
		
		if (writer == null) makeWriter();
		if (autoCheck) checkForRollover();
		writer.write(c);
		nCharsWritten += 1;
	}
	
	/** Calls <code>{@link #write(String, int, int) write}( s, 0, s.length() )</code>. */
	@Override public void write(String s) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(s);	// if do not check it here, then the call to s.length() below will result in a NullPointerException
		
		write( s, 0, s.length() );
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* @throws IllegalArgumentException if s == null
	* @throws SecurityException if a security manager exists and denies access to some resource
	*/
	@Override public void write(String s, int offset, int length) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(s);	// if do not check it here, then the call to writer.write below will result in a NullPointerException
		if (!open) throw new IOException("instance is closed");
		
		if (writer == null) makeWriter();
		if (autoCheck) checkForRollover();
		writer.write( s, offset, length );
		nCharsWritten += length;
	}
	
	// -------------------- new api: checkForRollover --------------------
	
	/**
	* Determines if need to close the current writer and create a new one that writes to the next rollover file.
	* This happens when the number of chars written to the current file equals or exceeds {@link #nCharsTillRollover specified limit}.
	*/
	public void checkForRollover() throws SecurityException, IOException {
		if (nCharsWritten >= nCharsTillRollover) {
			makeWriter();
		}
	}
	
	// -------------------- helper methods --------------------
	
	private void makeWriter() throws SecurityException, IOException {
		StreamUtil.close(writer);	// can handle writer == null
		
		File file = createRolloverFile( nextFileCount() );
		writer = new BufferedWriter( new OutputStreamWriter( new FileOutputStream(file) ) );	// the original version of this class had a bufferSize field and constructor param that used for the BufferedWriter here, but eliminated it when found that the default size of 8192 is about optimal and that too large of sizes not only waste memory, but actually hurt performance; see .../filePrograms/src/BenchmarkFileWriting.java
		nCharsWritten = 0;
	}
	
	/** Creates the nth rollover file. */
	private File createRolloverFile(int n) {
		String nDigits = String.valueOf(n);
		if ((minDigitsInSeriesNumber > 0) && (nDigits.length() < minDigitsInSeriesNumber)) {
			nDigits = StringUtil.toLength( n, minDigitsInSeriesNumber );
		}
		
		return new File( directory, filePrefix + nDigits + fileSuffix );
	}
	
	/**
	* Finds any previously written files inside {@link #directory} that both start with {@link #filePrefix}
	* and end with {@link #fileSuffix}, and then returns the next available file count.
	* <p>
	* This directory search is done, as opposed to tracking the next file number as a field of this class,
	* in order to support correct file numbering in the event that this class is run in multiple JVMs
	* (e.g. if a program had to be restarted for some reason).
	*/
	private int nextFileCount() throws SecurityException, IOException {
		FileFilter fileFilter = new FileFilter() {
			public boolean accept(File file) {
				String name = file.getName();
				return name.startsWith(filePrefix) && name.endsWith(fileSuffix);
			}
		};
		File[] files = DirUtil.getContents(directory, fileFilter, false);
		if (files.length == 0) return 0;
		
		Comparator<File> comparator = new Comparator<File>() {
			public int compare(File file1, File file2) throws IllegalArgumentException {
				// all args checked by calls to extractNumberFromFilename below
				
				return extractNumberFromFilename(file1) - extractNumberFromFilename(file2);
			}
		};
		Arrays.sort(files, comparator);
		
		File fileLast = files[ files.length - 1 ];
		return extractNumberFromFilename(fileLast) + 1;
	}
	
	/**
	* Parses and returns the series number from file.
	* Returns -1 if the series number is unparsable.
	* <b>Warning: does no arg or state checking, since is meant only be called by nextFileCount.</b>
	* <p>
	* @throws IllegalArgumentException if file == null
	*/
	private int extractNumberFromFilename(File file) throws IllegalArgumentException  {
		Check.arg().notNull(file);
		
		String name = file.getName();
		int index1 = filePrefix.length();
		int index2 = name.length() - fileSuffix.length();
		try {
			return Integer.parseInt( name.substring(index1, index2) );
		}
		catch (NumberFormatException nfe) {
			return -1;
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_all() throws Exception {
			File dir = LogUtil.makeLogFile("testDirectoryFileRollingWriter");
			try {
				DirUtil.ensureExists(dir);
				int n = 10;
				for (int i = 1; i <= n; i++) {
					writeRolling(i, dir);
				}
				int nFilesExpected = n * (n + 1) / 2;	// familiar formula for the sum from i = 1 to n
				Assert.assertTrue( DirUtil.getTree(dir).length == nFilesExpected );
			}
			finally {
				DirUtil.delete(dir);
			}
		}
		
		/**
		* Constructs a new FileRollingWriter instance with {@link #nCharsTillRollover} equal to n.
		* Then writes a String of length n to the FileRollingWriter n times.
		* This should result in n new output fils being produced.
		*/
		private void writeRolling(int n, File dir) throws Exception {
			FileRollingWriter frw = null;
			try {
				frw = new FileRollingWriter(dir, "frw", 2, ".txt", n);
				String nChars = StringUtil.repeatChars((char) n, n);
				for (int i = 0; i < n; i++) {
					frw.write(nChars);
				}
			}
			finally {
				StreamUtil.close(frw);
			}
		}
		
	}
	
}

