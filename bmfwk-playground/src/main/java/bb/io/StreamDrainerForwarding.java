/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.io;

import bb.util.Check;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Test;

/**
* StreamDrainer implementation that immediately forwards all bytes read from its InputStream to another OutputStream.
* <p>
* This class is multithread safe: almost every method is synchronized.
* The sole exception is {@link #run run}, which internally synchronizes those code blocks which involve mutable state
* using the same lock as the other methods (i.e. the instance itself).
* <p>
* @author Brent Boyer
*/
public class StreamDrainerForwarding implements StreamDrainer {
	
	private static final int bufferSize = 4 * 1024;
	private static final byte[] bytesEmpty = new byte[0];
	
	private final OutputStream out;
	private InputStream in;
	private boolean runCalled = false;
	private Throwable throwable;
	
	/**
	* Constructor that simply assigns {@link #out} with the correspondingparameter.
	* A subsequent call to {@link #init init} must be made before {@link #run run} can be executed.
	*/
	public StreamDrainerForwarding(OutputStream out) throws IllegalArgumentException {
		Check.arg().notNull(out);
		
		this.out = out;
	}
	
	/**
	* Constructor that assigns {@link #out} with the corresponding parameter,
	* and calls {@link #init init} with the in parameter.
	* This is a convenience, because it relieves the user of having to make an additional call to init.
	* <p>
	* @throws IllegalArgumentException if out is null; in is null
	*/
	public StreamDrainerForwarding(InputStream in, OutputStream out) throws IllegalArgumentException {
		this(out);
		init(in);
	}
	
	@Override public synchronized void init(InputStream in) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(in);
		if (this.in != null) throw new IllegalStateException("init is being called more than once");
		
		this.in = in;
	}
	
	/**
	* {@inheritDoc}
	* <p>
	* Note: when finished draining, will flush (but not close) {@link #out}.
	*/
	@Override public void run() throws IllegalStateException {
		synchronized (this) {	// must synchronize here to guard the mutable state in this block
			if (in == null) throw new IllegalStateException("init has never been called");
			if (runCalled) throw new IllegalStateException("run is being called more than once");
			runCalled = true;
		}
		
		try {
			byte[] buffer = new byte[bufferSize];
			while (true) {	// CRITICAL: this loop must mostly be unsynchronized to avoid long term blocking of other threads calling the getXXX methods
				int numberRead = in.read(buffer, 0, buffer.length);
				if (numberRead == -1) break;	// break only if hit End Of Stream
				out.write(buffer, 0, numberRead);
				out.flush();
				onBytesRead(buffer, numberRead);
			}
		}
		catch (Throwable t) {
			setThrowable(t);
		}
		finally {
			try {
				out.flush();
			}
			catch (Throwable t) {
				setThrowable(t);
			}
		}
	}
	
	/**
	* Hook method for the event that bytes were read from {@link #in} by {@link #run run}.
	* When this method is called, run will have already written those bytes to {@link #out}.
	* The implementation here does nothing, because writing to out is all that this class does.
	* Subclasses, however, may wish to do additional processing.
	* For example, if the subclass is monitoring some error stream like System.err,
	* then it may wish to do additional error notification.
	*/
	protected synchronized void onBytesRead(byte[] buffer, int numberRead) {}
	
	/**
	* Because this class never stores the bytes drained by {@link #run run},
	* this method always returns an empty byte[].
	* <p>
	* @return a zero-length byte[]
	* @throws IllegalStateException if run has never been called
	*/
	@Override public synchronized byte[] getBytes() throws IllegalStateException {
		if (!runCalled) throw new IllegalStateException("run has never been called");
		
		return bytesEmpty;
	}
	
	@Override public synchronized Throwable getThrowable() throws IllegalStateException {
		if (!runCalled) throw new IllegalStateException("run has never been called");
		
		return throwable;
	}
	
	private synchronized void setThrowable(Throwable throwable) { this.throwable = throwable; }
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/** Confirms that StreamDrainerForwarding immediately dumps to System.out all the bytes it drains from an InputStream. */
		@Test public void test_all() throws Exception {
			StreamDrainer drainer = new StreamDrainerForwarding(makeInputStream(), System.out);
			drain(drainer);
		}
		
		/** Confirms that the onBytesRead is being called by using a {@link Crashes} instance. */
		@Test(expected=RuntimeException.class) public void test_onBytesRead() throws Exception {
			StreamDrainer drainer = new Crashes(makeInputStream(), System.out);
			drain(drainer);
			Throwable t = drainer.getThrowable();
			if (t instanceof RuntimeException) throw (RuntimeException) t;
		}
		
		private InputStream makeInputStream() {
			String msg = "this text" + "\n" + "was generated by the makeInputStream method" + "\n";
			return new ByteArrayInputStream( msg.getBytes() );
		}
		
		private void drain(StreamDrainer drainer) throws Exception {
			Thread reader = new Thread(drainer);
			reader.start();
			reader.join();
		}
		
		/** StreamDrainerForwarding subclass which overrides {@link #onBytesRead onBytesRead} to always throw a RuntimeException. */
		private static class Crashes extends StreamDrainerForwarding {
			
			private Crashes(InputStream in, OutputStream out) throws IllegalArgumentException {
				super(in, out);
			}
			
			protected void onBytesRead(byte[] buffer, int numberRead) throws RuntimeException {
				throw new RuntimeException("deliberately generated");
			}
			
		}
		
	}
	
}
