/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import bb.io.StreamUtil;
import bb.science.FormatUtil;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Class which actively monitors the memory state of a JVM looking for issues.
* Reports events as they are detected to any registered {@link MemoryMonitorListener}s.
* <p>
* Monitoring is started by calling {@link #startMonitoring startMonitoring},
* is stopped by calling {@link #stopMonitoring stopMonitoring},
* and {@link #isMonitoring isMonitoring} reports if monitoring is currently active.
* It is legitimate to start and stop monitoring as often as desired.
* <p>
* There are certain situations where the code has to record information
* but no reference to an appropriate external handler class is available.
* A prime example is if during the monitoring or listener callback a Throwable is caught.
* Rather than write the information to a console that could disappear when the application quits,
* each instance of this class instead maintains a {@link #logger2} field where this information is written to.
* The current implementation logs to a file in the {@link LogUtil#getLogDirectory standard log directory}.
* <p>
* This class is multithread safe: almost every method (including private ones) is synchronized.
* The sole exceptions are the private fireXXX methods.
* These methods are called internally by this class and interact with arbitrary external MemoryMonitorListener classes.
* As a strategy to prevent deadlock,
* it is critical that the thread which calls these fireXXX methods initially owns no locks.
* Thus, these methods must be unsynchronized, since otherwise the lock on this MemoryMonitor instance would be held.
* <p>
* @author Brent Boyer
*/
public class MemoryMonitor {
	
	// -------------------- constants --------------------
	
	/**
	* Default value for the callRestoreJvm param of some of the constructors.
	* <p>
	* The value for this constant is false (i.e. {@link MemoryMeasurer#restoreJvm restoreJvm} will not be called before each memory measurement).
	*/
	private static final boolean callRestoreJvm_default = false;
	
	/**
	* Default value for {@link #interval}.
	* <p>
	* The value for this constant is 1 second.
	* See {@link UnitTest#benchmark_impactOfMonitoring benchmark_impactOfMonitoring} for why it was determined that this value should not burden a JVM excessively.
	*/
	private static final long interval_default = 1 * TimeLength.second;
	
	/**
	* A default value for the memoryLowTrigger arg to the {@link #MemoryMonitor(MemoryMeasurer, double, long) MemoryMonitor} constructor
	* (which is subsequently assigned to the {@link MonitorTask#memoryLowTrigger} field).
	*/
	private static final double memoryLowTrigger_default = 0.10;
	
	// -------------------- static fields --------------------
	
	/**
	* The next MemoryMonitor instance's {@link #instanceId} field.
	* <p>
	* Contract: is initialized to 0, and if this field has that value, it means that no instances have been created.
	*/
	private static final AtomicLong instanceIdNext = new AtomicLong();
	
	// -------------------- instance fields --------------------
	
	/** Records this instance's Id. */
	private final long instanceId = instanceIdNext.incrementAndGet();
	
	private Timer timer = null;
	
	/**
	* The next {@link #timer} value's Id.
	* <p>
	* Contract: is initialized to 1, and if this field has that value, it means that timer has never been assigned.
	*/
	private int nextTimerId = 1;
	
	/**
	* {@link MonitorTask} used to measure the memory state.
	* <p>
	* Contract: is never null.
	*/
	private final MonitorTask monitorTask;
	
	/**
	* Specifies how often the memory state should be checked.
	* <p>
	* Units: milliseconds.
	* <p>
	* Contract: is > 0.
	*/
	private final long interval;
	
	/**
	* Last memory state that was detected.
	* Will be null if no such state was ever detected (e.g. if monitoring was never started).
	*/
	private MemoryState state = null;
	
	/** Records whether or not low memory has been detected. */
	private boolean memoryLow = false;
	
	/**
	* Set of all {@link MemoryMonitorListener}s that are interested in memory events.
	* <p>
	* Contract: is never null nor contains null elements.
	*/
	private final Set<MemoryMonitorListener> listeners = new HashSet<MemoryMonitorListener>();
	
	/**
	* Logger2 where certain information (e.g. otherwise unhandleable errors) gets written to.
	* <p>
	* <i>Note:</i> this field is lazy initialized inside {@link #getLogger2 getLogger2}
	* (because this field will never be used in well behaved applications),
	* may be flushed in {@link #flushLoggerIfCreated flushLoggerIfCreated} (if previously initialized),
	* and is closed and nulled out in {@link #stopMonitoring stopMonitoring} (if previously initialized).
	* <i>All other code should always use getLogger2 and never directly access this field.</i>
	*/
	private Logger2 logger2;
	
	/**
	* The next {@link #logger2}'s Id.
	* <p>
	* Contract: is initialized to 1, and if this field has that value, it means that logger2 has never been assigned.
	*/
	private int nextLoggerId = 1;
	
	// -------------------- constructors --------------------
	
	/** Simply calls <code>{@link #MemoryMonitor(boolean, long) this}({@link #callRestoreJvm_default}, {@link #interval_default})</code>. */
	public MemoryMonitor() {
		this(callRestoreJvm_default, interval_default);
	}
	
	/** Simply calls <code>{@link #MemoryMonitor(MemoryMeasurer, double, long) this}(new {@link MemoryMeasurer#MemoryMeasurer()}, {@link #memoryLowTrigger_default}, interval)</code>. */
	public MemoryMonitor(boolean callRestoreJvm, long interval) {
		this( new MemoryMeasurer(callRestoreJvm), memoryLowTrigger_default, interval );
	}
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if measurer == null; memoryLowTrigger is NaN, infinite, or outside the range [0, 1]; interval <= 0
	*/
	public MemoryMonitor(MemoryMeasurer measurer, double memoryLowTrigger, long interval) throws IllegalArgumentException {
		// measurer and memoryLowTrigger checked by the MonitorTask constructor below
		Check.arg().positive(interval);
		
		this.monitorTask = new MonitorTask(measurer, memoryLowTrigger);
		this.interval = interval;
	}
	
	// -------------------- startMonitoring, isMonitoring, stopMonitoring and helper methods --------------------
	
	/**
	* Starts monitoring if it is currently not happening.
	* Specifically, it will create a daemon {@link Timer} that executes {@link #monitorTask} at a rate specified by {@link #interval}.
	* <p>
	* This method may safely be called multiple times (if the first call succeeds, subsequent calls do nothing).
	* <p>
	* @return true if this call actually started the monitoring, false if this instance was already monitoring when called
	*/
	public synchronized boolean startMonitoring() {
		if (isMonitoring()) return false;
		
		timer = new Timer( getTimerName(), true );
		timer.schedule( monitorTask, 0, interval ) ;	// WARNING: used to use scheduleAtFixedRate, but in practice (e.g. on windoze boxes with inaccurate clocks), it turned out to have problems: when interval is really small (e.g. 1) you could get many tasks firing which overlapped in time instead of having at least some time delay between them
		fireOnMonitoringStarted();
		return true;
	}
	
	/**
	* Returns a name to assign to {@link #timer} whenever it is assigned.
	* Attempts to return a value that is both unique to this instance
	* as well as unique to each of this instance's timer values.
	*/
	private synchronized String getTimerName() {
		return "MemoryMonitor#" + instanceId + "_timer#" + (nextTimerId++);
	}
	
	/** Reports whether or not this instance is actively monitoring memory. */
	public synchronized boolean isMonitoring() { return (timer != null); }
	
	/**
	* Stops monitoring if it is currently happening.
	* <p>
	* This method may safely be called multiple times (if the first call succeeds, subsequent calls do nothing).
	* <p>
	* @return true if this call actually stopped the monitoring, false if this instance was not monitoring when called
	*/
	public synchronized boolean stopMonitoring() {
		if (!isMonitoring()) return false;
		
		timer.cancel();
		timer = null;
		fireOnMonitoringStopped();
		if (logger2 != null) {
			LogUtil.close(logger2);
			logger2 = null;
		}
		return true;
	}
	
	// -------------------- getMemoryState, setMemoryState, isMemoryLow, getMemoryLow, setMemoryLow --------------------
	
	/**
	* Returns the memory state that was detected at the last check cycle.
	* <p>
	* This method never causes a new memory measurement to be made,
	* so it executes extremely quickly, and may be called frequently with low impact on the JVM.
	* On the other hand, the result may be obsolete,
	* especially if this instance was constructed with a large value for {@link #interval}.
	* <p>
	* @throws IllegalStateException if instance is currently not monitoring (i.e. {@link #isMonitoring isMonitoring} returns false)
	*/
	public synchronized MemoryState getMemoryState() throws IllegalStateException {
		if (!isMonitoring()) throw new IllegalStateException("instance is not currently monitoring");
		
		return state;
	}
	
	private synchronized void setMemoryState(MemoryState state) { this.state = state; }
	
	/**
	* Reports whether or not the low memory state was detected at the last check cycle.
	* <p>
	* This method never causes a new memory measurement to be made,
	* so it executes extremely quickly, and may be called frequently with low impact on the JVM.
	* On the other hand, the result may be obsolete,
	* especially if this instance was constructed with a large value for {@link #interval}.
	* <p>
	* @throws IllegalStateException if instance is currently not monitoring (i.e. {@link #isMonitoring isMonitoring} returns false)
	*/
	public synchronized boolean isMemoryLow() throws IllegalStateException {
		if (!isMonitoring()) throw new IllegalStateException("instance is not currently monitoring");
		
		return memoryLow;
	}
	
	private synchronized boolean getMemoryLow() { return memoryLow; }
	
	private synchronized void setMemoryLow(boolean memoryLow) { this.memoryLow = memoryLow; }
	
	// -------------------- getListeners, addListener, removeListener --------------------
	
	/**
	* Returns a defensive copy of {@link #listeners} for use by the fireXXX methods.
	* This is important both for multithread safety (see this class's javadocs)
	* and for reasons explained in <a href="http://forum.java.sun.com/thread.jsp?forum=4&thread=406834&tstart=0&trange=15">this forum posting</a>.
	*/
	private synchronized MemoryMonitorListener[] getListeners() {
		return listeners.toArray( new MemoryMonitorListener[listeners.size()] );
	}
	
	/**
	* Attempts to add listener to an internal set.
	* <p>
	* @return true if listener was added by this call to the internal set, false if already present
	* @throws IllegalArgumentException if listener == null
	*/
	public synchronized boolean addListener(MemoryMonitorListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);
		
		return listeners.add(listener);
	}
	
	/**
	* Attempts to remove listener from an internal set.
	* <p>
	* @return true if listener was removed by this call from the internal set, false if already absent
	* @throws IllegalArgumentException if listener == null
	*/
	public synchronized boolean removeListener(MemoryMonitorListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);
		
		return listeners.remove(listener);
	}
	
	// -------------------- fireXXX methods --------------------
	
	// CRITICAL: every method in this section must be unsynchronized, as per the class contract
	
	private void fireOnMonitoringStarted() {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringStarted();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMonitoringStarted", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	private void fireOnMonitoringStopped() {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringStopped();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMonitoringStopped", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	private void fireOnMonitoringError(Throwable t) {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringError(t);
			}
			catch (Throwable t2) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMonitoringError", "UNEXPECTED Throwable caught", t2);
			}
		}
	}
	
	private void fireOnMemoryState(MemoryState state) {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMemoryState(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMemoryState", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	private void fireOnMemoryLow(MemoryState state) {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMemoryLow(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMemoryLow", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	private void fireOnMemoryNotLow(MemoryState state) {
		for (MemoryMonitorListener listener : getListeners()) {
			try {
				listener.onMemoryNotLow(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor", "fireOnMemoryNotLow", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	// -------------------- getLogger2, getLoggerSuffix, flushLoggerIfCreated --------------------
	
	/** Returns {@link #logger2}, lazy initializing it if necessary. */
	private synchronized Logger2 getLogger2() {
		if (logger2 == null) {
			logger2 = LogUtil.makeLogger2( MemoryMonitor.class, getLoggerSuffix() );
		}
		return logger2;
	}
	
	/**
	* Returns a suffix to assign to {@link #logger2}'s filename whenever it is assigned.
	* Attempts to return a value that is both unique to this instance
	* as well as unique to each of this instance's logger2 values.
	*/
	private synchronized String getLoggerSuffix() {
		return "#" + instanceId + "_logger2#" + (nextLoggerId++);
	}
	
	/** Flushes {@link #logger2} if and only if it has been created. */
	private synchronized void flushLoggerIfCreated() {
		if (logger2 != null) {
			LogUtil.flush( logger2 );
		}
	}
	
	// -------------------- MonitorTask (instance inner class) --------------------
	
	/**
	* Class which detects memory issues and calls the appropriate fireXXX event notification method.
	* <p>
	* This class is not multithread safe: it expects to be run by only a single thread.
	*/
	private class MonitorTask extends TimerTask {

		/**
		* MemoryMeasurer used to measure the memory state.
		* <p>
		* Contract: is never null.
		*/
		private final MemoryMeasurer measurer;
		
		/**
		* If the ratio of the available memory to the maximum memory falls below this value, the low memory state is triggered.
		* Must be non-NaN, non-infinite, and in the range [0, 1].
		*/
		private final double memoryLowTrigger;
		
		/**
		* Constructor.
		* <p>
		* @throws IllegalArgumentException if measurer == null; memoryLowTrigger is NaN, infinite, or outside the range [0, 1]
		*/
		private MonitorTask(MemoryMeasurer measurer, double memoryLowTrigger) throws IllegalArgumentException {
			Check.arg().notNull(measurer);
			Check.arg().normalNotNegative(memoryLowTrigger);
			if (memoryLowTrigger > 1) throw new IllegalArgumentException("memoryLowTrigger = " + memoryLowTrigger + " > 1");
			
			this.measurer = measurer;
			this.memoryLowTrigger = memoryLowTrigger;
		}
		
		/**
		* Detects any memory issues and calls the appropriate fireXXX event notification method.
		* <p>
		* If any Throwable is caught:
		* it is logged by {@link #getLogger2 the logger2},
		* {@link #fireOnMonitoringError fireOnMonitoringError} is called,
		* and then {@link #stopMonitoring stopMonitoring} is called.
		*/
		public void run() {
			try {
				MemoryState state = measurer.getMemoryState();	// with no arg, this means that callRestoreJvm = false
				setMemoryState(state);
				fireOnMemoryState(state);
				
				if (state.getAvailableRatio() < memoryLowTrigger) {
					if (getMemoryLow()) {
						// do nothing: have already identified the low memory state
					}
					else {
						setMemoryLow(true);	// this is a new low memory signal
						fireOnMemoryLow(state);
					}
				}
				else {
					if (getMemoryLow()) {
						setMemoryLow(false);	// this is a new not low memory signal
						fireOnMemoryNotLow(state);
					}
					else {
						// do nothing: have already identified the not low memory state
					}
				}
				
				flushLoggerIfCreated();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "MemoryMonitor.MonitorTask", "run", "UNEXPECTED Throwable caught", t);
				fireOnMonitoringError(t);
				stopMonitoring();	// remember that this this will flush and then close logger2
				// CRITICAL: do the lines above in the exact order that they currently are, in particular, do stopMonitoring last because it will kill logger2 while the other lines above still need it
			}
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Contract: this constant always has the smallest possible value allowed for {@link MemoryMonitor#interval}, namely 1.
		* Reason: this stresses both the correctness testing of the code in {@link #test_onMemoryXXX test_onMemoryXXX}
		* as well as the impact of monitoring in {@link #benchmark_impactOfMonitoring benchmark_impactOfMonitoring}.
		*/
		private static final long interval_test = 1;
		
		// Implementation note: because of all the different threads that can simultaneously access System.out
		// in the method below, all calls to System.out are done "atomically"
		// (i.e. just a single call is made with all the info that should go together packed into the argument)
		@Test public void test_onMemoryXXX() throws Exception {
			MemoryMonitor monitor = null;
			ListenerTest listener = null;
			try {
				System.out.println('\n' + "Performing explicit garbage collection to start off the memory in a pristine state...");
				MemoryMeasurer.restoreJvm();
				
				MemoryMeasurer measurer = new MemoryMeasurer();
				
				double fillAmountLimit = 50 * 1024 * 1024;
				double max = measurer.getMemoryState().getMax();
				double fillPoint = Math.min( fillAmountLimit / max, 0.5 );	// fillPoint is normally fillAmountLimit / max, however, set 0.5 as its upper bound
				double memoryLowTrigger = 1 - (0.5 * fillPoint);	// i.e. low memory occurs when the used memory is 50% of what WILL fill to gets used
				
				listener = new ListenerTest();
				
				monitor = new MemoryMonitor(measurer, memoryLowTrigger, interval_test);
				monitor.addListener(listener);
				monitor.startMonitoring();
				
				System.out.println('\n' + "Filling memory up to fillPoint = " + fillPoint + "...");
				MemoryMeasurer.UnitTest.MemoryFiller filler = new MemoryMeasurer.UnitTest.MemoryFiller(fillPoint, interval_test, measurer);
				filler.fill();
				System.out.println("Finished filling memory");
				Thread.sleep(10);	// I found that the test below sometimes failed, so I guessed and added this pause; has not failed again...
				Assert.assertTrue( "onMemoryLow FAILED to be detected", listener.onMemoryLowDetected() );
				System.out.println("onMemoryLow was detected");
				
				System.out.println('\n' + "Freeing the memory that was previously filled...");
				filler.free();
				System.out.println("Performing explicit garbage collection...");
				MemoryMeasurer.restoreJvm();
				Assert.assertTrue( "onMemoryNotLow FAILED to be detected", listener.onMemoryNotLowDetected() );
				System.out.println("onMemoryNotLow was detected");
			}
			finally {
				ReflectUtil.callLogError(monitor, "stopMonitoring");
				StreamUtil.close(listener);
			}
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Benchmarking task with no monitoring of memory...
			task first = 11.278 ms, mean = 7.722 ms (CI deltas: -1.224 us, +1.550 us), sd = 86.509 us (CI deltas: -17.095 us, +36.614 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE

			Benchmarking task while monitoring memory but with NO MemoryMonitorListeners...
			task first = 7.708 ms, mean = 7.723 ms (CI deltas: -887.239 ns, +923.811 ns), sd = 57.218 us (CI deltas: -8.818 us, +13.918 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
			Extra execution time due to monitoring: 1.0437390625004858 us = 0.012065113074846893 standard deviations (of the original measurement)

			Benchmarking task while monitoring memory AND with a simple MemoryMonitorListener...
			task first = 8.621 ms, mean = 7.726 ms (CI deltas: -1.353 us, +1.511 us), sd = 90.391 us (CI deltas: -13.799 us, +20.687 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
			Extra execution time due to monitoring: 4.475211393229622 us = 0.05173125490178093 standard deviations (of the original measurement)
		* </code></pre>
		* <p>
		* It seems clear that monitoring the memory has very little impact on a cpu intensive operation.
		* (Note: this result was obtained on jdk 6, but when previously tested it on jdk 5, found a modest impact.)
		* <p>
		* One issue: when the memory state file that is produced by the listener while task runs was examined,
		* it only contained 4,069 entries.
		* Given that interval is being set to {@link #interval_test} = 1 ms,
		* would have expected ~40 * 1000 ~= 40,000 entries if the monitoring task truly was executed every millisecond.
		* Since writing to the file perhaps takes more than 1 ms, maybe that is part of the explanation.
		* Another factor might be that my PC's clock is so inaccurate that it can only schedule with resolutions down to 10-20 ms.
		*/
		@Test public void benchmark_impactOfMonitoring() throws InterruptedException {
			MemoryMonitor monitor = null;
			MemoryMonitorListener listener = null;
			try {
				Runnable task = new Runnable() {
					private static final int n = 128 * 1024;
					private double state;	// needed to prevent DCE since this is a Runnable
					 
					@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
					
					public void run() {
						for (int i = 0; i < n; i++) {
							state += Math.random();
						}
					}
				};
				
				System.out.println('\n' + "Benchmarking task with no monitoring of memory...");
				Benchmark b1 = new Benchmark(task);
				System.out.println("task " + b1);
				
				monitor = new MemoryMonitor(false, interval_test);
				monitor.startMonitoring();
				System.out.println('\n' + "Benchmarking task while monitoring memory but with NO MemoryMonitorListeners...");
				Benchmark b2 = new Benchmark(task);
				System.out.println("task " + b2);
				printExtraTime(b1.getMean(), b2.getMean(), b1.getSd());
				
				listener = new MemoryMonitorListenerImpl();
				monitor.addListener(listener);
				System.out.println('\n' + "Benchmarking task while monitoring memory AND with a simple MemoryMonitorListener...");
				Benchmark b3 = new Benchmark(task);
				System.out.println("task " + b3);
				printExtraTime(b1.getMean(), b3.getMean(), b1.getSd());
			}
			finally {
				ReflectUtil.callLogError(monitor, "stopMonitoring");
				StreamUtil.close(listener);
			}
		}
		
		private static void printExtraTime(double timeNoMon, double timeMon, double sd) {
			double timeExtra = Math.max(timeMon - timeNoMon, 0);
			System.out.println("Extra execution time due to monitoring: " + FormatUtil.toEngineeringTime(timeExtra) + " = " + (timeExtra / sd) + " standard deviations (of the original measurement)");
		}
		
		/** Simple subclass of {@link MemoryMonitorListenerImpl} which can be interrogated to see if events have occured. */
		private static class ListenerTest extends MemoryMonitorListenerImpl {
			
			private volatile boolean detected_onMemoryLow = false;
			private volatile boolean detected_onMemoryNotLow = false;
			
			private ListenerTest() {}
			
			public synchronized void onMemoryLow(MemoryState state) throws RuntimeException {
				super.onMemoryLow(state);
				detected_onMemoryLow = true;
			}
			
			public synchronized void onMemoryNotLow(MemoryState state) {
				super.onMemoryNotLow(state);
				detected_onMemoryNotLow = true;
			}
			
			private boolean onMemoryLowDetected() throws InterruptedException {
				for (int i = 0; i < 100; i++) {
					if (detected_onMemoryLow) break;
					else Thread.sleep(10);
				}
				return detected_onMemoryLow;
			}
			
			private boolean onMemoryNotLowDetected() throws InterruptedException {
				for (int i = 0; i < 100; i++) {
					if (detected_onMemoryNotLow) break;
					else Thread.sleep(10);
				}
				return detected_onMemoryNotLow;
			}
			
		}
		
	}
	
}
