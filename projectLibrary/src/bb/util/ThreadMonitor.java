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
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.junit.Test;


/**
* Class which actively monitors the thread state of a JVM looking for issues.
* Reports events as they are detected to any registered {@link ThreadMonitorListener}s.
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
* These methods are called internally by this class and interact with arbitrary external ThreadMonitorListener classes.
* As a strategy to prevent deadlock,
* it is critical that the thread which calls these fireXXX methods initially owns no locks.
* Thus, these methods must be unsynchronized, since otherwise the lock on this ThreadMonitor instance would be held.
* <p>
* @author Brent Boyer
*/
public class ThreadMonitor {
	
	
	// -------------------- constants --------------------
	
	
	/**
	* Default value for {@link #interval}.
	* <p>
	* The current value for this constant is 10 seconds.
	* See {@link UnitTest#benchmark_impactOfMonitoring benchmark_impactOfMonitoring} for why it was determined that this value should not burden a JVM excessively.
	*/
	private static final long interval_default = 10 * TimeLength.second;
	
	
	// -------------------- static fields --------------------
	
	
	/**
	* The next ThreadMonitor instance's {@link #instanceId} field.
	* <p>
	* Contract: is initialized to 0, and if this field has that value, it means that no instances have been created.
	*/
	private static final AtomicLong instanceIdNext = new AtomicLong();
	
	
	// -------------------- instance fields --------------------
	
	
	/** This instance's Id. */
	private final long instanceId = instanceIdNext.incrementAndGet();
	
	
	/**
	* The next {@link #timer} value's Id.
	* <p>
	* Contract: is initialized to 1, and if this field has that value, it means that timer has never been assigned.
	*/
	private int nextTimerId = 1;
	
	
	private Timer timer = null;
	
	
	/**
	* {@link MonitorTask} used to measure the thread state.
	* <p>
	* Contract: is never null.
	*/
	private final MonitorTask monitorTask;
	
	
	/**
	* Specifies how often the thread state should be checked.
	* <p>
	* Units: milliseconds.
	* <p>
	* Contract: is > 0.
	*/
	private final long interval;
	
	
	/**
	* Last thread state that was detected.
	* Will be null if no such state was ever detected (e.g. if monitoring was never started).
	*/
	private String state = null;
	
	
	/** Records whether or not thread deadlock has been detected. */
	private boolean deadlocked = false;
	
	
	/**
	* Set of all {@link ThreadMonitorListener}s that are interested in thread events.
	* <p>
	* Contract: is never null nor contains null elements.
	*/
	private final Set<ThreadMonitorListener> listeners = new HashSet<ThreadMonitorListener>();
	
	
	/**
	* The next {@link #logger2}'s Id.
	* <p>
	* Contract: is initialized to 1, and if this field has that value, it means that logger2 has never been assigned.
	*/
	private int nextLoggerId = 1;
	
	
	/**
	* Logger where certain information (e.g. otherwise unhandleable errors) gets written to.
	* <p>
	* <i>Note:</i> this field is lazy initialized inside {@link #getLogger2 getLogger2}
	* (because this field will never be used in well behaved applications),
	* may be flushed in {@link #flushLoggerIfCreated flushLoggerIfCreated} (if previously initialized),
	* and is closed and nulled out in {@link #stopMonitoring stopMonitoring} (if previously initialized).
	* <i>All other code should always use getLogger2 and never directly access this field.</i>
	*/
	private Logger2 logger2;
	
	
	// -------------------- constructors --------------------
	
	
	/** Simply calls <code>{@link #ThreadMonitor(ThreadMeasurer, long) this}(new {@link ThreadMeasurer#ThreadMeasurer()}, {@link #interval_default})</code>. */
	public ThreadMonitor() {
		this(new ThreadMeasurer(), interval_default);
	}
	
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if measurer == null; interval <= 0
	*/
	public ThreadMonitor(ThreadMeasurer measurer, long interval) throws IllegalArgumentException {
		// measurer checked by the MonitorTask constructor below
		Check.arg().positive(interval);
		
		this.monitorTask = new MonitorTask(measurer);
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
		return "ThreadMonitor#" + instanceId + "_timer#" + (nextTimerId++);
	}
	
	
	/** Reports whether or not this instance is actively monitoring threads. */
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
	
	
	// -------------------- getThreadState, setThreadState, isDeadlocked, getDeadlocked, setDeadlocked --------------------
	
	
	/**
	* Returns the thread state that was detected at the last check cycle.
	* <p>
	* This method never causes a new thread state measurement to be made,
	* so it executes extremely quickly, and may be called frequently with low impact on the JVM.
	* On the other hand, the result may be obsolete,
	* especially if this instance was constructed with a large value for {@link #interval}.
	* <p>
	* @throws IllegalStateException if instance is currently not monitoring (i.e. {@link #isMonitoring isMonitoring} returns false)
	*/
	public synchronized String getThreadState() throws IllegalStateException {
		if (!isMonitoring()) throw new IllegalStateException("instance is not currently monitoring");
		
		return state;
	}
	
	
	private synchronized void setThreadState(String state) { this.state = state; }
	
	
	/**
	* Reports whether or not thread deadlock was detected at the last check cycle.
	* <p>
	* This method never causes a new thread state measurement to be made,
	* so it executes extremely quickly, and may be called frequently with low impact on the JVM.
	* On the other hand, the result may be obsolete,
	* especially if this instance was constructed with a large value for {@link #interval}.
	* <p>
	* @throws IllegalStateException if instance is currently not monitoring (i.e. {@link #isMonitoring isMonitoring} returns false)
	*/
	public synchronized boolean isDeadlocked() throws IllegalStateException {
		if (!isMonitoring()) throw new IllegalStateException("instance is not currently monitoring");
		
		return deadlocked;
	}
	
	
	private synchronized boolean getDeadlocked() { return deadlocked; }
	
	
	private synchronized void setDeadlocked(boolean deadlocked) { this.deadlocked = deadlocked; }
	
	
	// -------------------- getListeners, addListener, removeListener --------------------
	
	
	/**
	* Returns a defensive copy of {@link #listeners} for use by the fireXXX methods.
	* This is important both for multithread safety (see this class's javadocs)
	* and for reasons explained in <a href="http://forum.java.sun.com/thread.jsp?forum=4&thread=406834&tstart=0&trange=15">this forum posting</a>.
	*/
	private synchronized ThreadMonitorListener[] getListeners() {
		return listeners.toArray( new ThreadMonitorListener[listeners.size()] );
	}
	
	
	/**
	* Attempts to add listener to an internal set.
	* <p>
	* @return true if listener was added by this call to the internal set, false if already present
	* @throws IllegalArgumentException if listener == null
	*/
	public synchronized boolean addListener(ThreadMonitorListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);
		
		return listeners.add(listener);
	}
	
	
	/**
	* Attempts to remove listener from an internal set.
	* <p>
	* @return true if listener was removed by this call from the internal set, false if already absent
	* @throws IllegalArgumentException if listener == null
	*/
	public synchronized boolean removeListener(ThreadMonitorListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);
		
		return listeners.remove(listener);
	}
	
	
	// -------------------- fireXXX methods --------------------
	
	
	// CRITICAL: every method in this section must be unsynchronized, as per the class contract
	
	
	private void fireOnMonitoringStarted() {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringStarted();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnMonitoringStarted", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	
	private void fireOnMonitoringStopped() {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringStopped();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnMonitoringStopped", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	
	private void fireOnMonitoringError(Throwable t) {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onMonitoringError(t);
			}
			catch (Throwable t2) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnMonitoringError", "UNEXPECTED Throwable caught", t2);
			}
		}
	}
	
	
	private void fireOnThreadState(String state) {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onThreadState(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnThreadState", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	
	private void fireOnDeadlocked(String state) {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onDeadlocked(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnDeadlocked", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	
	private void fireOnNotDeadlocked(String state) {
		for (ThreadMonitorListener listener : getListeners()) {
			try {
				listener.onNotDeadlocked(state);
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor", "fireOnNotDeadlocked", "UNEXPECTED Throwable caught", t);
			}
		}
	}
	
	
	// -------------------- getLogger2, getLoggerSuffix, flushLoggerIfCreated --------------------
	
	
	/** Returns {@link #logger2}, lazy initializing it if necessary. */
	private synchronized Logger2 getLogger2() {
		if (logger2 == null) {
			logger2 = LogUtil.makeLogger2( ThreadMonitor.class, getLoggerSuffix() );
		}
		return logger2;
	}
	
	
	/**
	* Returns a suffix to assign to {@link #logger2}'s filename whenever it is assigned.
	* Attempts to return a value that is both unique to this instance
	* as well as unique to each of this instance's logger2 values.
	*/
	private synchronized String getLoggerSuffix() {
		return "#" + instanceId + "_logger#" + (nextLoggerId++);
	}
	
	
	/** Flushes {@link #logger2} if and only if it has been created. */
	private synchronized void flushLoggerIfCreated() {
		if (logger2 != null) {
			LogUtil.flush( logger2 );
		}
	}
	
	
	// -------------------- MonitorTask (instance inner class) --------------------
	
	
	/**
	* Class which detects thread issues and calls the appropriate fireXXX event notification method.
	* <p>
	* This class is not multithread safe: it expects to be run by only a single thread.
	*/
	private class MonitorTask extends TimerTask {

		/**
		* ThreadMeasurer used to measure the thread state.
		* <p>
		* Contract: is never null.
		*/
		private final ThreadMeasurer measurer;
		
		/**
		* Constructor.
		* <p>
		* @throws IllegalArgumentException if measurer == null
		*/
		private MonitorTask(ThreadMeasurer measurer) throws IllegalArgumentException {
			Check.arg().notNull(measurer);
			
			this.measurer = measurer;
		}
		
		/**
		* Detects any thread issues and calls the appropriate fireXXX event notification method.
		* <p>
		* If any Throwable is caught:
		* it is logged by {@link #getLogger2 the logger2},
		* {@link #fireOnMonitoringError fireOnMonitoringError} is called,
		* and then {@link #stopMonitoring stopMonitoring} is called.
		*/
		public void run() {
			try {
				String state = measurer.getThreadState();
				setThreadState(state);
				fireOnThreadState(state);
				
				String deadlockState = measurer.getDeadlockState();
				if (deadlockState != null) {
					if (getDeadlocked()) {
						// do nothing: have already identified the deadlocked state
					}
					else {
						setDeadlocked(true);	// this is a new deadlocked signal
						fireOnDeadlocked(deadlockState);
					}
				}
				else {
					if (getDeadlocked()) {
						setDeadlocked(false);	// this is a new not deadlocked signal
						fireOnNotDeadlocked(state);	// CRITICAL: supply state and NOT deadlockState
					}
					else {
						// do nothing: have already identified the not deadlocked state
					}
				}
				
				flushLoggerIfCreated();
			}
			catch (Throwable t) {
				getLogger2().logp(Level.SEVERE, "ThreadMonitor.MonitorTask", "run", "UNEXPECTED Throwable caught", t);
				fireOnMonitoringError(t);
				stopMonitoring();	// remember that this this will flush and then close logger2
				// CRITICAL: do the lines above in the exact order that they currently are, in particular, do stopMonitoring last because it will kill logger2 while the other lines above still need it
			}
		}
		
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		//private static final long interval = 1 * TimeLength.second;	// this gloabal constant is no longer used, because different values are needed in different methods: the smallest value of 1 is needed in confirmCodeWorks to stress the correctness testing, while larger values, like 1000, are needed in measureImpactOfMonitoring
		/**
		* Contract: this constant always has the smallest possible value allowed for {@link MemoryMonitor#interval}, namely 1.
		* Reason: this stresses both the correctness testing of the code in {@link #test_onXXX test_onXXX}
		* as well as the impact of monitoring in {@link #benchmark_impactOfMonitoring benchmark_impactOfMonitoring}.
		*/
		private static final long interval_test = 1;
		
		// Implementation note: because of all the different threads that can simultaneously access System.out
		// in the method below, all calls to it are done "atomically"
		// (i.e. just a single call is made with all the info that should go together packed into the argument)
		@Test public void test_onXXX() throws Exception {
			ThreadMonitor monitor = null;
			ListenerTest listener = null;
			try {
				ThreadMeasurerTest measurer = new ThreadMeasurerTest();
				monitor = new ThreadMonitor(measurer, interval_test);
				listener = new ListenerTest();
				monitor.addListener(listener);
				monitor.startMonitoring();
				
				CyclicBarrier barrier = new CyclicBarrier(2);	// the 2 threads are always this main thread and the Timer thread
				listener.setBarrier(barrier);
				System.out.println('\n' + "Creating 2 threads which will deadlock");
				ThreadMeasurer.UnitTest.establishDeadlocks();
				barrier.await();	// wait for onDeadlocked to be detected
				
				barrier = new CyclicBarrier(2);
				listener.setBarrier(barrier);
				System.out.println('\n' + "Deliberately configuring the ThreadMonitorListener's onThreadState method to fail with a RuntimeException" + '\n' + "Expected side effects when this failure occurs:" + '\n' + "1) the RuntimeException should be printed out in the log file");
				listener.makeFail();
				barrier.await();	// wait for onThreadState to throw its RuntimeException when called
				
				barrier = new CyclicBarrier(2);
				listener.setBarrier(barrier);
				System.out.println('\n' + "Deliberately configuring the monitoring (via its ThreadMeasurer) to fail with a RuntimeException" + '\n' + "Expected side effects when this failure occurs:" + '\n' + "1) the RuntimeException should be printed out in the log file" + '\n' + "2) should fire onMonitoringError (the ThreadMonitorListener will print out the RuntimeException below)" + '\n' + "3) should cause monitoring to stop");
				measurer.makeFail();
				barrier.await();	// wait for onMonitoringError to be detected
				while (monitor.isMonitoring()) {	// wait till stopMonitoring has finished
					Thread.sleep(10);
				}
			}
			finally {
				ReflectUtil.callLogError(monitor, "stopMonitoring");
				StreamUtil.close(listener);
			}
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Benchmark of task with no monitoring of threads...
			task first = 11.376 ms, mean = 8.826 ms (CI deltas: -1.960 us, +2.169 us), sd = 92.218 us (CI deltas: -14.265 us, +21.880 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
			
			Benchmarking task while monitoring threads but with NO ThreadMonitorListeners...
			task first = 11.208 ms, mean = 10.096 ms (CI deltas: -833.043 ns, +966.141 ns), sd = 40.019 us (CI deltas: -5.339 us, +3.472 us) WARNING: SD VALUES MAY BE INACCURATE
			Extra execution time due to monitoring: 1.2692279098958318 ms = 13.763310986781851 standard deviations (of the original measurement)
			
			Benchmarking task while monitoring threads AND with a simple ThreadMonitorListener...
			task first = 10.159 ms, mean = 10.096 ms (CI deltas: -886.358 ns, +1.093 us), sd = 43.350 us (CI deltas: -5.941 us, +12.268 us) WARNING: SD VALUES MAY BE INACCURATE
			Extra execution time due to monitoring: 1.2693616809895773 ms = 13.764761579815543 standard deviations (of the original measurement)
		* </code></pre>
		* <p>
		* It seems clear that monitoring the memory has very little impact on a cpu intensive operation.
		* (Note: this result was obtained on jdk 6, but when previously tested it on jdk 5, found a major impact.)
		* <p>
		* One issue: when the memory state file that is produced by the listener while task runs was examined,
		* it only contained 182,001 lines ~= 3,000 entries (assuming ~60 lines per entry).
		* Given that interval is being set to {@link #interval_test} = 1 ms,
		* would have expected ~40 * 1000 ~= 40,000 entries if the monitoring task truly was executed every millisecond.
		* Since writing to the file perhaps takes more than 1 ms, maybe that is part of the explanation.
		* Another factor might be that my PC's clock is so inaccurate that it can only schedule with resolutions down to 10-20 ms.
		*/
		@Test public void benchmark_impactOfMonitoring() throws Exception {
			ThreadMonitor monitor = null;
			ListenerTest listener = null;
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
				
				System.out.println("Benchmark of task with no monitoring of threads...");
				Benchmark b1 = new Benchmark(task);
				System.out.println("task " + b1);
				
				monitor = new ThreadMonitor(new ThreadMeasurer(), interval_test);
				monitor.startMonitoring();
				System.out.println("Benchmarking task while monitoring threads but with NO ThreadMonitorListeners...");
				Benchmark b2 = new Benchmark(task);
				System.out.println("task " + b2);
				printExtraTime(b1.getMean(), b2.getMean(), b1.getSd());
				
				listener = new ListenerTest();
				monitor.addListener(listener);
				System.out.println("Benchmarking task while monitoring threads AND with a simple ThreadMonitorListener...");
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
		
		/** Extends ThreadMeasurer to deliberately throw a RuntimeException when {@link #makeFail makeFail} called. */
		private static class ThreadMeasurerTest extends ThreadMeasurer {
			
			private boolean fail = false;
			
			private ThreadMeasurerTest() {
				super();
			}
			
			public synchronized String getThreadState() throws RuntimeException {
				if (fail) {
					fail = false;	// reset to false to stop any further errors from happening
					throw new RuntimeException("getThreadState deliberately generated this RuntimeException for testing purposes");
				}
				else return super.getThreadState();
			}
			
			private synchronized void makeFail() {
				fail = true;
			}
			
		}
		
		/**
		* Simple ThreadMonitorListener implementation meant for test purposes:
		* it prints all events to System.out
		* (except for {@link #onThreadState onThreadState}, which outputs to {@link #pw}),
		* it uses {@link #barrier} to coordinate actions with the main thread,
		* and {@link #onThreadState onThreadState} can be configured to deliberately throw a RuntimeException when {@link #makeFail makeFail} called.
		*/
		private static class ListenerTest implements Closeable, ThreadMonitorListener {
			
			private static AtomicLong instanceIdNext = new AtomicLong();
			
			private final long instanceId = instanceIdNext.incrementAndGet();
			private final PrintWriter pw;
			private CyclicBarrier barrier;
			private boolean fail = false;
			
			private ListenerTest() throws IOException {
				File fileOutput = LogUtil.makeLogFile("threads_#" + instanceId + ".txt");
				pw = new PrintWriter(fileOutput);
			}
			
			@Override public synchronized void onMonitoringStarted() {
				System.out.println('\n' + "Event: onMonitoringStarted");
			}
			
			@Override public synchronized void onMonitoringStopped() {
				System.out.println('\n' + "Event: onMonitoringStopped");
			}
			
			@Override public synchronized void onMonitoringError(Throwable t) {
				System.out.println('\n' + "Event: onMonitoringError");
				t.printStackTrace(System.out);
				await();
			}
			
			@Override public synchronized void onThreadState(String state) {
				if (fail) {
					fail = false;	// reset to false to stop any further errors from happening
					await();
					throw new RuntimeException("onThreadState deliberately generated this RuntimeException for testing purposes");
				}
				else {
					try {
						pw.println();
						pw.println();
						pw.println("--------------------------------------------------");
						pw.println();
						pw.println();
						pw.println( state );
						pw.flush();
					}
					catch (Throwable t) {
						t.printStackTrace(System.err);
					}
				}
			}
			
			@Override public synchronized void onDeadlocked(String state) throws RuntimeException {
				System.out.println('\n' + "Event: onDeadlocked" + '\n' + state);
				await();
			}
			
			@Override public synchronized void onNotDeadlocked(String state) {
				System.out.println('\n' + "Event: onNotDeadlocked" + '\n' + state);
				await();
			}
			
			private synchronized void setBarrier(CyclicBarrier barrier) {
				this.barrier = barrier;
			}
			
			/** Waits for the main thread to reach a common barrier point with the Timer thread executing this method. */
			private synchronized void await() {
				try {
					if (barrier != null) {
						barrier.await();
						barrier = null;	// will only use it once; it must be reset via setBarrier if is to be used again
					}
				}
				catch (Throwable t) {
					t.printStackTrace(System.err);
				}
			}
			
			private synchronized void makeFail() {
				fail = true;
			}
			
			@Override public synchronized void close() {
				StreamUtil.close(pw);
			}
			
		}
		
	}
	
	
}
