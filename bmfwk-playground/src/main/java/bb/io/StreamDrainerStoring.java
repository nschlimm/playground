/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

--this class naturally has these associated events:
	onBytesRead
	onDrainingFinished
But currently there is no support for them.
+++ future ways that could support these events:
	1) have a Listener interface that defines the methods above, and have this class support said Listener registration and fire the events
		--pros: is the professional solution
		--cons: heavyweight (requires a lot of code to be written, both here and in the user class)
	2) use wait/notifyAll on the instance itself to signal other threads besides the one executing run that an event happened
		--pros: lightweight (requires less code to be written, both here and in the user class)
		--cons: somewhat problematic:
			a) there is no way to distinguish which of the events mentioned above occured if use just this instance
			b) client code has to be written by an intelligent programmer (e.g. has to know all the issues with wait/notify in Java, like synchronization, use while loop on wait with a condition predicate)
			c) speaking of condition predicates, would probably have to add an isDraining method to StreamDrainer

*/

package bb.io;

import bb.util.Check;
import bb.util.ThrowableUtil;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.junit.Assert;
import org.junit.Test;

/**
* StreamDrainer implementation that stores all bytes read from its InputStream.
* <p>
* This class is multithread safe: almost every method is synchronized.
* The sole exception is {@link #run run}, which internally synchronizes those code blocks which involve mutable state
* using the same lock as the other methods (i.e. the instance itself).
* <p>
* @author Brent Boyer
*/
public class StreamDrainerStoring implements StreamDrainer {
	
	private static final int baosSizeInitial = 16 * 1024;
	private static final int bufferSize = 4 * 1024;
	
	private InputStream in;
	private ByteArrayOutputStream baos;
	private Throwable throwable;
	
	/**
	* No-arg constructor.
	* A subsequent call to {@link #init init} must be made before {@link #run run} can be executed.
	*/
	public StreamDrainerStoring() {}
	
	/**
	* Constructor that calls {@link #init init} with in.
	* This is a convenience, because it relieves the user of having to make an additional call to init.
	* <p>
	* @throws IllegalArgumentException if in is null
	*/
	public StreamDrainerStoring(InputStream in) throws IllegalArgumentException {
		init(in);
	}
	
	@Override public synchronized void init(InputStream in) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(in);
		if (this.in != null) throw new IllegalStateException("init is being called more than once");
		
		this.in = in;
	}
	
	@Override public void run() throws IllegalStateException {
		synchronized (this) {	// must synchronize here to guard the mutable state in this block
			if (in == null) throw new IllegalStateException("init has never been called");
			if (baos != null) throw new IllegalStateException("run is being called more than once");
			baos = new ByteArrayOutputStream(baosSizeInitial);
		}
		
		try {
			byte[] buffer = new byte[bufferSize];
			while (true) {	// CRITICAL: this loop must mostly be unsynchronized to avoid long term blocking of other threads calling the getXXX methods
				int numberRead = in.read(buffer, 0, buffer.length);
				if (numberRead == -1) break;	// break only if hit End Of Stream

				synchronized (this) {	// must synchronize here to guard the mutable state in this block
					baos.write(buffer, 0, numberRead);
				}
			}
		}
		catch (Throwable t) {
			setThrowable(t);
		}
	}
	
	/**
	* Returns all the bytes that have been drained by {@link #run run}
	* since the last time this method was called.
	* <p>
	* Side effect: any stored bytes are cleared upon return.
	* <p>
	* @return all the bytes drained by run since the last call of this method; the result is never null, but may be zero-length
	* @throws IllegalStateException if run has never been called
	*/
	@Override public synchronized byte[] getBytes() throws IllegalStateException {
		if (baos == null) throw new IllegalStateException("run has never been called");
		
		byte[] bytes = baos.toByteArray();
		baos.reset();
		return bytes;
	}
	
	@Override public synchronized Throwable getThrowable() throws IllegalStateException {
		if (baos == null) throw new IllegalStateException("run has never been called");
		
		return throwable;
	}
	
	private synchronized void setThrowable(Throwable throwable) { this.throwable = throwable; }
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/** Contracts: is never null, empty, nor contains an element that is null or empty. */
		private static final byte[][] byteChunks = new byte[][] {
			new byte[] { 1 },
			new byte[] { 2, 3 },
			new byte[] { 4, 5, 6 },
			new byte[] { 7, 8, 9, 10 }
		};
		
		// confirms that one thread can continuously poll a StreamDrainerStoring instance for bytes
		// while the StreamDrainerStoring is concurrently draining an InputStream
		@Test public void test_all() throws Exception {
			PipedInputStream pipedIn = new PipedInputStream();
			PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);
			
			StreamDrainerStoring streamDrainerStoring = new StreamDrainerStoring(pipedIn);
			
			Thread writer = new Thread( new ByteChunkSender(pipedOut) );
			Thread reader = new Thread( streamDrainerStoring );
			writer.start();
			reader.start();
			
			Thread.sleep(10);
			
			for (int i = 0; i < byteChunks.length; i++) {
				byte[] bytes = streamDrainerStoring.getBytes();
				if (bytes.length > 0) {
					Assert.assertArrayEquals( byteChunks[i], bytes );
				}
				else {
					Thread.sleep(10);
				}
			}
			
			writer.join();
			reader.join();
		}
		
		/** Once every 100 ms writes an element of {@link #byteChunks} to {@link #out}, until no more elements left. */
		private static class ByteChunkSender implements Runnable {
			
			private final OutputStream out;
			
			private ByteChunkSender(OutputStream out) throws IllegalArgumentException {
				Check.arg().notNull(out);
				
				this.out = out;
			}
			
			public void run() throws RuntimeException {
				try {
					for (int i = 0; i < byteChunks.length; i++) {
						out.write( byteChunks[i] );
						out.flush();
						Thread.sleep(100);
					}
				}
				catch (Throwable t) {
					throw ThrowableUtil.toRuntimeException(t);
				}
				finally {
					StreamUtil.close(out);
				}
			}
			
		}
		
	}
	
}
