/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;


import bb.io.StreamUtil;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.io.File;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;


/**
* Simple implementation of {@link MemoryMonitorListener} which logs most events to its own internal Logger.
* The one exception is {@link #onMemoryState onMemoryState}, which writes the data to a separate file instead.
* <p>
* This class is multithread safe: every public method is synchronized.
*/
public class MemoryMonitorListenerImpl implements MemoryMonitorListener {
	
	
	// -------------------- constants --------------------
	
	
	private static final String separator = "\t";
	
	
	private static final AtomicLong instanceIdNext = new AtomicLong();
	
	
	// -------------------- instance fields --------------------
	
	
	private final long instanceId = instanceIdNext.incrementAndGet();
	
	
	private int countState = 0;
	private final PrintWriter pw;
	
	
	private final Logger2 logger2;
	
	
	// -------------------- toStringHeader --------------------
	
	
	/**
	* Returns a description of the data written by {@link #onMemoryState onMemoryState}.
	* <p>
	* @throws IllegalArgumentException if separator == null or separator.length() == 0
	*/
	public static String toStringHeader(String separator) throws IllegalArgumentException {
		Check.arg().notNull(separator);
		if (separator.length() == 0) throw new IllegalArgumentException("separator.length() == 0");
		
		return "date" + separator + "stateNumber" + separator + MemoryState.toStringHeader(separator);
	}
	
	
	// -------------------- constructor and helper methods --------------------
	
	
	public MemoryMonitorListenerImpl() throws RuntimeException {
		try {
			pw = new PrintWriter( makeFile() );
			logger2 = LogUtil.makeLogger2( MemoryMonitorListenerImpl.class, String.valueOf(instanceId) );
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	
	private File makeFile() {
		return LogUtil.makeLogFile("memory_" + DateUtil.getTimeStampForFile() + ".txt");
	}
	
	
	// -------------------- MemoryMonitorListener api --------------------
	
	
	@Override public synchronized void onMonitoringStarted() {
		logger2.logp(Level.INFO, "MemoryMonitorListenerImpl", "onMonitoringStarted", "memory monitoring started");
	}
	
	
	@Override public synchronized void onMonitoringStopped() {
		logger2.logp(Level.WARNING, "MemoryMonitorListenerImpl", "onMonitoringStopped", "memory monitoring stopped");
	}
	
	
	@Override public synchronized void onMonitoringError(Throwable t) {
		logger2.logp(Level.SEVERE, "MemoryMonitorListenerImpl", "onMonitoringError", "memory monitoring encountered an error", t);
	}
	
	
	@Override public synchronized void onMemoryState(MemoryState state) {
		try {
			++countState;
			if (countState == 1) {
				pw.append( toStringHeader(separator) ).append('\n');
			}
			pw.append( DateUtil.getTimeStamp() ).append( separator ).append( String.valueOf( countState ) ).append( separator ).append( state.toString(separator, false) ).append( '\n' );
			pw.flush();
		}
		catch (Throwable t) {
			logger2.logp(Level.SEVERE, "MemoryMonitorListenerImpl", "onMemoryState", "an error occurred while writing the memory state", t);
		}
	}
	
	
	@Override public synchronized void onMemoryLow(MemoryState state) throws RuntimeException {
		logger2.logp(Level.WARNING, "MemoryMonitorListenerImpl", "onMemoryLow", "the memory low state has just been detected:" + '\n' + state.toString());
	}
	
	
	@Override public synchronized void onMemoryNotLow(MemoryState state) {
		logger2.logp(Level.INFO, "MemoryMonitorListenerImpl", "onMemoryNotLow", "the memory not low state has just been detected:" + '\n' + state.toString());
	}
	
	
	@Override public synchronized void close() {
		logger2.logp(Level.WARNING, "MemoryMonitorListenerImpl", "close", "this MemoryMonitorListenerImpl closed");
		StreamUtil.close(pw);
		LogUtil.close(logger2);
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	// None: is tested in MemoryMonitor.UnitTest
	
	
}
