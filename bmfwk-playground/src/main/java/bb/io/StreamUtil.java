/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io;

import bb.util.Check;
import bb.util.DateUtil;
import bb.util.ReflectUtil;
import bb.util.StringUtil;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides miscellaneous static utility methods for dealing with streams.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* This class checks for thread interruption only before major blocking operations
* in order to achieve a reasonable balance between cancellation responsiveness and program performance
* (see Doug Lea <cite>Concurrent Programming in Java Second Edition</cite> p. 170).
* <p>
* @author Brent Boyer
*/
public final class StreamUtil {
	
	// -------------------- constants --------------------
	
	/**
	* Used by {@link #drain(InputStream)} when it calls {@link #calcBufferSize calcBufferSize}
	* to set a lower bound on the size of the buffer used by drain.
	* <p>
	* The size of this value needs to balance creating buffers that are too small (which causes resizes inside drain)
	* versus being too large and wasting memory when the stream has only a little data.
	*/
	private static final int bufferSizeDrainMin = 4 * 1024;
// +++ concerning buffer size and I/O read performance--look at the 2007/8/6 comment by briand in this post:
//	http://forums.java.net/jive/thread.jspa?threadID=29337&tstart=15
// NEED TO REREAD THAT COMMENT AND SEE IF NEED TO REWRITE ALL THE SECTIONS OF MY CODE THAT CURRENTLY HAVE HUGE BUFFERS...
	
	private static final int bufferSizeTransfer = 32 * 1024;
	
	/** Cached value used by {@link #close(Object) close}. */
	private static final Class[] classArray = new Class[0];
	
	/** Cached value used by {@link #close(Object) close}. */
	private static final Object[] parameterArray = new Object[0];
	
	// -------------------- readFully --------------------
	
	/**
	* Fully reads into the entire supplied byte[].
	* Is a convenience method that simply calls <code>readFully(in, bytes, 0, bytes.length)</code>
	* <p>
	* @throws IllegalArgumentException if in or bytes == null
	* @throws EOFException if Hit End Of Stream before reading all the requested bytes
	* @throws IOException if an I/O problem occurs
	*/
	public static void readFully(InputStream in, byte[] bytes) throws IllegalArgumentException, EOFException, IOException {
		readFully(in, bytes, 0, bytes.length);
	}
	
	/**
	* Tries to read <i>exactly</i> the requested length from in into bytes.
	* The writing of bytes starts at the index == offset
	* and continues up to (but not including) the index == offset + length.
	* <p>
	* This method is required because {@link InputStream#read(byte[], int, int)}
	* does not guarantee that requested length will be read,
	* whereas this method does (unless an Exception is thrown first).
	* <p>
	* <i>Note:</i> this method does not close in when finished.
	* <p>
	* @throws IllegalArgumentException if in or bytes == null, offset or length are negative,
	* or offset + length are greater than bytes.length
	* @throws EOFException if Hit End Of Stream before reading all the requested bytes
	* @throws IOException if an I/O problem occurs
	*/
	public static void readFully(InputStream in, byte[] bytes, int offset, int length) throws IllegalArgumentException, EOFException, IOException {
		Check.arg().notNull(in);
		Check.arg().notNull(bytes);
		Check.arg().notNegative(offset);
		Check.arg().notNegative(length);
		if ((offset + length) > bytes.length) throw new IllegalArgumentException("offset + length = " + (offset + length) + " are > bytes.length = " + bytes.length);
		
		int offsetLimit = offset + length;
		while (offset < offsetLimit) {
			int bytesYetToRead = offsetLimit - offset;
			
			int bytesJustRead = in.read(bytes, offset, bytesYetToRead);
			if (bytesJustRead == -1) throw new EOFException("Hit End Of Stream before reading all the requested bytes");
			
			offset += bytesJustRead;
		}
	}
	
	/**
	* Fully reads into the entire supplied char[].
	* Is a convenience method that simply calls <code>readFully(in, chars, 0, chars.length)</code>
	* <p>
	* @throws IllegalArgumentException if in or chars == null
	* @throws EOFException if Hit End Of Stream before reading all the requested chars
	* @throws IOException if an I/O problem occurs
	*/
	public static void readFully(Reader in, char[] chars) throws IllegalArgumentException, EOFException, IOException {
		readFully(in, chars, 0, chars.length);
	}
	
	/**
	* Tries to read <i>exactly</i> the requested length from in into chars.
	* The writing of chars starts at the index == offset
	* and continues up to (but not including) the index == offset + length.
	* <p>
	* This method is required because {@link Reader#read(char[], int, int)}
	* does not guarantee that requested length will be read,
	* whereas this method does (unless an Exception is thrown first).
	* <p>
	* <i>Note:</i> this method does not close in when finished.
	* <p>
	* @throws IllegalArgumentException if in or chars == null, offset or length are negative,
	* or offset + length are greater than chars.length
	* @throws EOFException if Hit End Of Stream before reading all the requested chars
	* @throws IOException if an I/O problem occurs
	*/
	public static void readFully(Reader in, char[] chars, int offset, int length) throws IllegalArgumentException, EOFException, IOException {
		Check.arg().notNull(in);
		Check.arg().notNull(chars);
		Check.arg().notNegative(offset);
		Check.arg().notNegative(length);
		if ((offset + length) > chars.length) throw new IllegalArgumentException("args offset + length = " + (offset + length) + " are > chars.length = " + chars.length);
		
		int offsetLimit = offset + length;
		while (offset < offsetLimit) {
			int charsYetToRead = offsetLimit - offset;
			
			int charsJustRead = in.read(chars, offset, charsYetToRead);
			if (charsJustRead == -1) throw new EOFException("Hit End Of Stream before reading all the requested chars");
			
			offset += charsJustRead;
		}
	}
	
	// -------------------- drain, calcBufferSize, drainAsciiToChars, drainAsciiToChars, drainIntoString --------------------
	
	/**
	* Returns {@link #drain(InputStream, int) drain}(in, Integer.MAX_VALUE).
	* <p>
	* @throws IllegalArgumentException if in == null
	* @throws IllegalStateException if in holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	*/
	public static byte[] drain(InputStream in) throws IllegalArgumentException, IllegalStateException, IOException {
		return drain(in, Integer.MAX_VALUE);
	}
	
	/**
	* Attempts to read all the bytes from in until End Of Stream is reached.
	* If the number of bytes read is <= lengthMax, then they are returned as a byte[];
	* this result is never null, but may be zero-length.
	* Otherwise, if in contains more than lengthMax bytes, an IllegalStateException is thrown.
	* <p>
	* This method blocks for an unlimited time until End Of Stream is reached;
	* see {@link #drain(InputStream, int, long)} for a version with timeout.
	* <p>
	* <i>Note</i>: the final action is to close in.
	* <p>
	* <b>Warning</b>: be careful with using this method on large capacity streams (e.g. GB sized files),
	* since could run out of memory.
	* <p>
	* An alternative to this method might be to use NIO's Channels and ByteBuffers (especially mapped ones);
	* see <a href="http://java.sun.com/j2se/1.4/docs/guide/nio/example/Sum.java">this sample code</a>.
	* <p>
	* @throws IllegalArgumentException if in == null; lengthMax <= 0
	* @throws IllegalStateException if in holds more than lengthMax bytes
	* @throws IOException if an I/O problem occurs
	*/
	public static byte[] drain(InputStream in, int lengthMax) throws IllegalArgumentException, IllegalStateException, IOException {
		Check.arg().notNull(in);
		Check.arg().positive(lengthMax);
		
		try {
			byte[] buffer = new byte[ calcBufferSize(in, lengthMax) ];	// for top efficiency, especially with InputStreams like from files whose entire contents can be read in one swoop, work directly with a byte[] and NOT with a ByteArrayOutputStream
			int offset = 0;
			
			while (true) {
				int spaceFree = buffer.length - offset;
				int numberRead = in.read(buffer, offset, spaceFree);
				if (numberRead == -1) break;	// break only if hit End Of Stream
				
				offset += numberRead;
				
				if (offset == buffer.length) {	// i.e. if buffer is filled up
						// try to read the next byte:
					int next = in.read();
						// break only if hit End Of Stream:
					if (next == -1) break;
						// else check that have not hit lengthMax:
					else if (offset == lengthMax) throw new IllegalStateException("have read lengthMax = " + lengthMax + ", but there are still more bytes on the stream");
						// otherwise resize buffer, add next to it, and continue:
					else {
						int lengthNew = Math.min(2 * buffer.length, lengthMax);	// double the length, with lengthMax as an upper bound
						buffer = Arrays.copyOf(buffer, lengthNew);
						buffer[offset++] = (byte) next;
					}
				}
			}
			
				// return buffer if it is fully filled up:
			if (offset == buffer.length) return buffer;
				// otherwise have to return a new byte[] of precisely the right length:
			else {
				return Arrays.copyOf(buffer, offset);
			}
		}
		finally {
			close(in);
		}
	}
	
	/**
	* First checks that in.available <= lengthMax, throwing an IllegalStateException if that constraint is not obeyed,
	* since if it is violated then drain cannot work.
	* Then checks that bufferSizeDrainMin <= lengthMax, returning lengthMax if that condition is not obeyed,
	* since the result should never exceed lengthMax.
	* <p>
	* Assuming those checks are passed, then returns <code>Math.max(in.available(), {@link #bufferSizeDrainMin})</code>.
	* In other words, the result is normally the amount of data available on the stream
	* except when that value is too small, in which case a min value is returned instead.
	* <p>
	* The reason why the amount of data available on the stream should normally be returned
	* is to handle InputStreams where the length should static
	* and fully determinable by calling the InputStream's available method (e.g. InputStreams from files).
	* In this case, {@link #drain(InputStream)} should be extremely efficient
	* because only a single buffer need be used with no further allocations or copying.
	* <p>
	* The reason why you need bufferSizeDrainMin to establish a lower bound
	* is because some InputStreams (e.g. from sockets) cannot return
	* an accurate value for the eventual amount of data that will be read.
	* <p>
	* @throws IOException if an I/O problem occurs
	* @throws IllegalStateException if in.available() > lengthMax
	*/
	private static int calcBufferSize(InputStream in, int lengthMax) throws IOException, IllegalStateException {
		if (in.available() > lengthMax) throw new IllegalStateException("in.available() = " + in.available() + " > lengthMax = " + lengthMax);
		else if (bufferSizeDrainMin > lengthMax) return lengthMax;
		else return Math.max(in.available(), bufferSizeDrainMin);
	}
	
	/**
	* Returns {@link #drain(InputStream, int) drain}(in, lengthMax),
	* if that call executes within timeout, else throws a TimeoutException.
	* <p>
	* From the caller's perspective, this is a synchronous method call.
	* Internally, however, a new worker thread is created that does the actual call to drain
	* which enables the calling thread to detect timeout.
	* <p>
	* @param in an arbitrary InputStream
	* @param lengthMax the maximum number of bytes that the stream should contain
	* @param timeout the length of time (in ms) to allow for draining to occur
	* @return all the bytes from in
	* @throws Throwable (or some subclass) if any problem occurs; in particular, note that throws
	* <ul>
	*  <li>an IllegalArgumentException if in == null or timeout <= 0</li>
	*  <li>a TimeoutException if timeout occurs</li>
	*  <li>any Throwable caught by the internal draining thread will be rethrown by this method</li>
	* </ul>
	*/
	public static byte[] drain(InputStream in, int lengthMax, long timeout) throws Throwable {
		// in, lengthMax checked by Drainer below
		Check.arg().positive(timeout);
		
		Drainer drainer = new Drainer(in, lengthMax);
		
		Thread worker = new Thread( drainer, "StreamUtil_Drainer#" + drainer.instanceId );
		worker.setPriority( Thread.NORM_PRIORITY );
		try {
			worker.start();
			worker.join(timeout);
		}
		finally {
			if (worker.isAlive()) {
					// interrupt worker to try to end it soon:
				try {
					worker.interrupt();
				}
				catch (Throwable t) {	// usually this will be an InterruptedException, but regardless, do not want anything stop subsequent actions below, so just log it
					LogUtil.getLogger2().logp(Level.WARNING, "StreamUtil", "drain", "encountered a problem interrupting the temporary worker thread", t);
				}
				
					// wait again for it die, now that it has been interrupted:
				try {
					worker.join(timeout);
				}
				catch (Throwable t) {	// usually this will be an InterruptedException, but regardless, do not want anything stop subsequent actions below, so just log it
					LogUtil.getLogger2().logp(Level.WARNING, "StreamUtil", "drain", "encountered a problem waiting for the temporary worker thread to die", t);
				}
				
					// if still alive best can do is minimize its damage by deprioritizing it:
				try {
					if (worker.isAlive()) {
						LogUtil.getLogger2().logp(Level.WARNING, "StreamUtil", "drain", "the temporary worker thread does not appear to have died");
						worker.setPriority( Thread.MIN_PRIORITY );
					}
				}
				catch (Throwable t) {	// HAVE seen setPriority throw a NullPointerException when it is in the process of being shutdown
					LogUtil.getLogger2().logp(Level.WARNING, "StreamUtil", "drain", "encountered a problem when trying to deprioritize the temporary worker thread", t);
				}
				
				throw new TimeoutException("Timeout exceeded");	// if bytes and throwable unassigned, then must have broken the above while loop because of timeout
			}
		}
		
		if (drainer.throwable != null) throw drainer.throwable;
		else if (drainer.bytes != null) return drainer.bytes;
		else throw new IllegalStateException("unable to determine what happened");	// should never happen
	}
	
	/** Solely used by the internal worker thread of the above drain method. */
	private static class Drainer implements Runnable {
		
		private static final AtomicLong instanceIdNext = new AtomicLong();
		
		private final long instanceId = instanceIdNext.incrementAndGet();
		private final InputStream in;
		private final int lengthMax;
		
		private volatile byte[] bytes = null;	// CRITICAL: must be volatile so that it is immediately visible to the thread calling drain, which is different from the thread executing this instance's run
		private volatile Throwable throwable = null;	// CRITICAL: must be volatile so that it is immediately visible to the thread calling drain, which is different from the thread executing this instance's run
		
		private Drainer(InputStream in, int lengthMax) throws IllegalArgumentException {
			Check.arg().notNull(in);
			Check.arg().positive(lengthMax);
			
			this.in = in;
			this.lengthMax = lengthMax;
		}
		
		public void run() {
			try {
				bytes = drain(in, lengthMax);
			}
			catch (Throwable t) {
				throwable = t;
			}
		}
		
	}
	
	/**
	* Passes in to the {@link #drain(InputStream)} method.
	* Converts the returned byte[] into a char[] (using {@link StringUtil#asciiBytesToChars StringUtil.asciiBytesToChars}),
	* and returns that char[].
	* The data inside in must solely consist of US-ASCII bytes (i.e. no negative values).
	* <p>
	* @throws IllegalArgumentException if in == null; a non-ascii byte (i.e. a negative value) is encountered
	* @throws IllegalStateException if in holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	*/
	public static char[] drainAsciiToChars(InputStream in) throws IllegalArgumentException, IllegalStateException, IOException {
		return StringUtil.asciiBytesToChars( drain(in) );
	}
	
	/**
	* Passes in to the {@link #drain(InputStream)} method.
	* Converts the returned byte[] into a String using the spacified char encoding, and returns that String.
	* <p>
	* @throws IllegalArgumentException if in == null
	* @throws IllegalStateException if in holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	* @throws UnsupportedEncodingException if charEncoding is not supported
	*/
	public static String drainIntoString(InputStream in, String charEncoding) throws IllegalArgumentException, IllegalStateException, IOException, UnsupportedEncodingException {
		return new String( drain(in), charEncoding );
	}
	
	/**
	* Passes in to the {@link #drain(InputStream)} method.
	* Converts the returned byte[] into a String using the platform's default char encoding, and returns that String.
	* <p>
	* @throws IllegalArgumentException if in == null
	* @throws IllegalStateException if in holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	*/
	public static String drainIntoString(InputStream in) throws IllegalArgumentException, IllegalStateException, IOException {
		return new String( drain(in) );
	}
	
	// -------------------- transfer -------------------
	
	/**
	* Simply calls <code>transfer(in, out, null)</code>.
	* <p>
	* @throws IllegalArgumentException if in == null or if out == null
	* @throws IOException if an I/O problem occurs
	*/
	public static void transfer(InputStream in, OutputStream out) throws IllegalArgumentException, IOException {
		transfer(in, out, null);
	}
	
	/**
	* Transfers all the data from in to out,
	* that is, reads bytes from in and writes them to out until End Of Stream with in is reached.
	* If logger is non-null, then feedback on the transfer process is written to it.
	* <p>
	* <b>Warning</b>: this method does not close the streams; that is the caller's responsibility.
	* <p>
	* @throws IllegalArgumentException if in == null or if out == null
	* @throws IOException if an I/O problem occurs
	*/
	public static void transfer(InputStream in, OutputStream out, PrintWriter logger) throws IllegalArgumentException, IOException {
		Check.arg().notNull(in);
		Check.arg().notNull(out);
		
		byte[] buffer = new byte[bufferSizeTransfer];
		TransferProgressReporter reporter = (logger != null) ? new TransferProgressReporter(logger) : null;
		while (true) {
			int bytesRead = in.read(buffer);
			if (bytesRead == -1) break;
			
			out.write(buffer, 0, bytesRead);
			if (reporter != null) reporter.update(bytesRead, in.available());
		}
	}
	
	/** Solely used for reporting progress of the transfer method. */
	private static class TransferProgressReporter {
		
		private final PrintWriter logger;
		private final DecimalFormat formatter = new DecimalFormat("0.#E0");
		private final long start = System.currentTimeMillis();
		private long last = start;
		private long totalTransferred = 0;
		
		private TransferProgressReporter(PrintWriter logger) throws IllegalArgumentException {
			Check.arg().notNull(logger);
			
			this.logger = logger;
			logger.println("date" + "\t" + "done (bytes)" + "\t" + "available (bytes)" + "\t" + "rateOverall (bytes/sec)" + "\t" + "rateLast (bytes/sec)");
		}
		
		private void update(int bytesTransferred, int available) {
			totalTransferred += bytesTransferred;
			long now = System.currentTimeMillis();
			String rateOverall = getRate(start, now, totalTransferred);
			String rateLast = getRate(last, now, bytesTransferred);
			logger.println(DateUtil.getTimeStamp( new Date(now) ) + "\t" + totalTransferred + "\t" + available + "\t" + rateOverall + "\t" + rateLast);
			last = now;
		}
		
		/** Note: the time params are assumed to be in milliseconds, but the result has units of 1/second. */
		private String getRate(long t1, long t2, long number) throws IllegalStateException {
			if (t1 > t2)
				//throw new IllegalStateException("t1 = " + t1 + " is > t2 = " + t2 + " which should never happen");
				return "(Cannot determine: t1 = " + t1 + " > t2 = " + t2 + " which should never happen";	// return this instead of throw an Exception because I HAVE seen the system clock run backwards for a short bit; this is a known problem with many computer clocks
			else if (t1 == t2)
				return "(Cannot determine: insufficient time resolution)";
			else {
				double rate = number / ((t2 - t1) / 1000.0);
				return formatter.format( rate );
			}
		}
		
	}
	
	// -------------------- close --------------------
	
	/**
	* Immediately returns if obj is null.
	* Otherwise, calls {@link Closeable#close closeable.close}.
	* <p>
	* Contract: this method should never throw a Throwable.
	* Any Throwable that is raised is caught and {@link Logger2#log(LogRecord) logged robustly} to the {@link LogUtil#getLogger2 default Logger}.
	*/
	public static void close(Closeable closeable) {
		try {
			if (closeable == null) return;
			
			closeable.close();
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.SEVERE, "StreamUtil", "close", "caught an unexpected Throwable", t);
		}
	}
	
	/**
	* Immediately returns if obj is null.
	* Otherwise, calls <code>{@link ReflectUtil#callLogError(Object, String)}(obj, "close")</code>.
	*/
	public static void close(Object obj) {
		if (obj == null) return;
		
		ReflectUtil.callLogError(obj, "close");
	}
// +++ eliminate this method, since only the Closeable version should be needed when jdk 7 comes out,
// because then Sun has Socket/ etc implement java.io.Closeable?  See
//	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6499348
//	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6389768
	
// +++ any need for corresponding flush methods?  at this point, i think not: flush methods are rarely called in finally blocks like close, so need for the logic like above
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private StreamUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_drain_shouldPass1() throws Throwable {
			byte[] bytesOriginal = new byte[] {1, 2, 3, 4, 5, 6, 7};
			InputStream in = new ByteArrayInputStream( bytesOriginal );
			int lengthMax = bytesOriginal.length;
			byte[] bytesDrained = drain(in, lengthMax);
			Assert.assertArrayEquals(bytesOriginal, bytesDrained);
		}
		
		@Test public void test_drain_shouldPass2() throws Throwable {
			InputStream in = new URL("http://www.google.com").openStream();
			int lengthMax = 10*1000;
			long timeout = 1000L;
			byte[] bytes = drain(in, lengthMax, timeout);
			System.out.println("result of draining " + in + ", converted to a String:");
			System.out.println( new String(bytes) );
		}
		
		@Test(expected=Exception.class) public void test_drain_shouldFail() throws Throwable {
			InputStream in = null;
			try {
				final long timeout = 1L;
				in = new InputStream() {	// create an artifical InputStream that indefinitely blocks on the read used by drain:
					public int 	read() {
						throw new UnsupportedOperationException();
					}
					public int read(byte[] bytes, int off, int len) {
						for (int i = 0; i < 2; i++) {	// do 2 sleeps, because the first should be interrupted by drain
							try {
								Thread.sleep( 10L * timeout );
							}
							catch (InterruptedException ie) {
								Thread.currentThread().interrupt();	// never swallow this; see http://www-128.ibm.com/developerworks/java/library/j-jtp05236.html
							}
						}
						return -1;
					}
				};
				drain(in, 1, timeout);
			}
			finally {
				close(in);
			}
		}
		
		@Test public void test_transfer() throws Exception {
			InputStream in = null;
			ByteArrayOutputStream baos = null;
			try {
				final byte[] bytesOriginal = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
				in = new InputStream() {	// create an artifical InputStream that returns bytesOriginal, one byte at a time, at a rate of 1 byte/sec:
					private int count = 0;
					public int 	available() { return bytesOriginal.length - count; }
					public int 	read() {
						throw new UnsupportedOperationException();
					}
					public int read(byte[] bytes, int off, int len) {
						if (count < bytesOriginal.length) {
							try {
								Thread.sleep(1000);	// sleep for 1 second in order to establish a data rate of 1 byte/sec
							}
							catch (InterruptedException ie) {
								Thread.currentThread().interrupt();	// never swallow this; see http://www-128.ibm.com/developerworks/java/library/j-jtp05236.html
							}
							bytes[off] = bytesOriginal[count++];
							return 1;
						}
						else return -1;
					}
				};
				
				baos = new ByteArrayOutputStream();
				transfer(in, baos, new PrintWriter(System.out));
				Assert.assertArrayEquals( bytesOriginal, baos.toByteArray() );
			}
			finally {
				close(in);
				close(baos);
			}
		}
		
// +++ write lots more tests
	}
	
}
