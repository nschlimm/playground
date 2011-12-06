/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.gui.SwingUtil;
import bb.util.BufferFixed;
import bb.util.Check;
import bb.util.Execute;
import java.awt.EventQueue;
import java.util.concurrent.Callable;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
* Logs to a GUI window.
* <p>
* This class obviously differs from {@link HandlerConsole} in that it uses a GUI instead of a console.
* Furthermore, it also differs in that data updates are completely controlled by the user.
* In particular, if the user is looking at a snapshot of {@link LogRecord}s in the GUI,
* and new LogRecords are received by this instance (via {@link #publish publish}),
* then those new LogRecords are stored in an internal {@link BufferFixed} field
* and the GUI is merely updated to notify the user that new data is present.
* But it is always up to the user to decide when to replace the currently displayed information with the newest.
* <p>
* This class can be used as a GUI alternative to the <a href="http://en.wikipedia.org/wiki/Tail_%28Unix%29">UNIX tail command</a>.
* It can be useful to attach an instance to the root Logger, and have it publish serious logs.
* <p>
* If the GUI window is closed by the user, then it will be recreated if a new LogRecord arrives.
* However, if the GUI window is closed by a call to {@link #close close}, then it will be permanently closed.
* <p>
* <i>It is unclear whether or not this class is multithread safe.</i>
* There may be issues with its superclass; see {@link HandlerAbstract} for more discussion.
* Concerning the state added by this class, every non-GUI method is synchronized,
* while the GUI methods are ensured to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* <p>
* @author Brent Boyer
*/
public class HandlerGui extends HandlerAbstract {
	
	// -------------------- instance fields --------------------
	
	/**
	* Accumulates the next snapshot of LogRecords that is to be displayed in the GUI.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private BufferFixed<LogRecord> bufferFixed;
	
	/**
	* The GUI window where logs get displayed.
	* <p>
	* Contract: is null until lazy initialized by {@link #publish publish}, then is nulled out once and for all when {@link #close close} is first called.
	*/
	private FrameLog frameLog;
	
	// -------------------- constructors --------------------
	
	/**
	* Convenience constructor.
	* <p>
	* All this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure},
	* except for the {@link BufferFixed#sizeMax} field which is assigned from calling
	* <code>{@link #getIntProperty getIntProperty}(getClass().getName() + ".sizeMax", 1000)</code>.
	* In other words, {@link BufferFixed#sizeMax} is set to whatever is defined for it in the logging properties file,
	* else it defaults to 1000 if not defined there.
	*/
	public HandlerGui() {
		synchronized (this) {	// needed to safely publish bufferFixed, at a minimum (and maybe some of the state mutated by configure)
			bufferFixed = new BufferFixed<LogRecord>( getIntProperty(HandlerGui.class.getName() + ".sizeMax", 1000) );
			configure();
		}
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* All this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure},
	* except for the {@link BufferFixed#sizeMax} field which is assigned from the param.
	* <p>
	* @throws IllegalArgumentException if sizeMax <= 0
	*/
	public HandlerGui(int sizeMax) throws IllegalArgumentException {
		// sizeMax checked by BufferFixed below
		
		synchronized (this) {	// needed to safely publish bufferFixed, at a minimum (and maybe some of the state mutated by configure)
			bufferFixed = new BufferFixed<LogRecord>(sizeMax);
			configure();
		}
	}
	
	// -------------------- overriden Handler methods --------------------
	
	/**
	* First call releases all resources used by this instance, including permanently closing the GUI window.
	* All future calls to {@link #publish publish} will be silently ignored.
	*/
	@Override public synchronized void close() {
		if (!isAlive()) return;
		
		super.close();
		
		bufferFixed = null;
		
		Runnable closer = new Runnable() {
			public void run() {
				releaseGui();
			}
		};
		SwingUtil.invokeNowIfEdt(closer);
	}
	
	/** Implementation here does nothing, since the user has total control over when the GUI gets updated. */
	@Override public synchronized void flush() {}
	
	/**
	* If {@link #isAlive isAlive} or {@link #isLoggable isLoggable}(record) returns false, then immediately returns.
	* Else adds record to {@link #bufferFixed} and notifies the GUI that a new record has arrived.
	*/
	@Override public synchronized void publish(LogRecord record) {
		try {
			if (!isAlive()) return;
			if (!isLoggable(record)) return;	// Note: isLoggable checks if a) record is null b) record's Level passes this instance's Level, and c) record passes this instance's Filter
			
			bufferFixed.add(record);
			
			SwingUtil.invokeNowIfEdt( new Runnable() {
				public void run() {
					initGuiIfNeeded();
					frameLog.onLogNewArrived();
				}
			} );
		}
		catch (Exception e) {
			reportError(null, e, ErrorManager.GENERIC_FAILURE);	// report the exception to any registered ErrorManager
		}
	}
	
	// -------------------- overriden HandlerAbstract methods --------------------
	
	/**
	* Implementation here returns a new {@link FormatterFull} instance.
	*/
	@Override protected synchronized Formatter getFormatterDefault() {
		return new FormatterFull();
	}
	
	// -------------------- getLogRecords --------------------
	
	synchronized String getLogRecords() {
		if (!isAlive()) return "NO MORE LOGS: this HandlerGui instance has been closed";
		
		StringBuilder sb = new StringBuilder();
		
		BufferFixed.State<LogRecord> state = bufferFixed.getAndResetState();
		
		sb.append( state.getDescription() ).append("\n");
		
		sb.append("\n");
		for (LogRecord record : state.deque) {
			String msg = getFormatter().format(record);
			sb.append(msg);
			if (!(msg.endsWith("\n") || msg.endsWith("\r"))) sb.append("\n");
		}
		
		return sb.toString();
	}
	
	// -------------------- initGuiIfNeeded, releaseGui --------------------
	
	private void initGuiIfNeeded() {
		Check.state().edt();
		
		if (frameLog == null) {
			frameLog = new FrameLog(this);
		}
	}
	
	void releaseGui() {
		Check.state().edt();
		
		if (frameLog.isShowing()) frameLog.dispose();
		frameLog = null;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest extends HandlerAbstract.UnitTest {
		
		public static void main(final String[] args) throws Exception {
			Execute.thenContinue( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				UnitTest unitTest = new UnitTest();
				//unitTest.test_all_makeLogsSeriallySlowly( 20, new HandlerGui() );
				unitTest.test_all_makeLogsConcurrentlyRapidly( Integer.MAX_VALUE, new HandlerGui() );
				
				return null;
			} } );
		}
		
	}
	
}
