/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;


import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CyclicBarrier;
import javax.management.MBeanServerConnection;
import org.junit.Assert;
import org.junit.Test;


/**
* Class which measures the threads of a JVM.
* <p>
* This class was adapted from the class
* <code>&lt;JDK_HOME>/demo/management/FullThreadDump/src/ThreadMonitor.java</code>
* written by Mandy Chung.
* <p>
* This class is multithread safe: the public static methods do not use any state of this class,
* and the public instance methods are all synchronized.
* The protected instance methods are not synchronized,
* and <i>subclass authors should ensure that they are only called from public synchronized methods</i>.
* <p>
* @author Brent Boyer
*/
public class ThreadMeasurer {
	
	
	// -------------------- fields --------------------
	
	
	/** Contract: is never null. */
	private final ThreadMXBean threadMXBean;
	
	private final boolean reportThreadTimes;
	
	private final boolean reportThreadContention;
	
	
	// -------------------- static methods --------------------
	
	
	/**
	* Returns a ThreadMXBean instance for the JVM specified by server.
	* <p>
	* @throws IllegalArgumentException if server == null
	* @throws IOException if a communication problem occurrs when accessing server
	*/
	public static ThreadMXBean getThreadMXBean(MBeanServerConnection server) throws IllegalArgumentException, IOException {
		Check.arg().notNull(server);
		
		return ManagementFactory.newPlatformMXBeanProxy(server, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
	}
	
	
	/**
	* Trys to ensure that threadMXBean has enabled thread cpu time measurement.
	* (i.e. for collecting per-thread statistics about cpu usage).
	* If this measurement is already enabled, this method does nothing.
	* Else if the JVM supports it but it was disabled when this method was called, then it will be enabled by this method.
	* Otherwise, if the JVM does not supports it, this method does nothing.
	* <p>
	* @return true if upon return thread cpu time measurement is enabled, false otherwise
	* @throws IllegalArgumentException if threadMXBean == null
	*/
	public static boolean attemptCpuTimeMeasurement(ThreadMXBean threadMXBean) throws IllegalArgumentException {
		Check.arg().notNull(threadMXBean);
		
		if (threadMXBean.isThreadCpuTimeSupported()) {
			if (!threadMXBean.isThreadCpuTimeEnabled()) {
				threadMXBean.setThreadCpuTimeEnabled(true);	// the UnsupportedOperationException thrown by setThreadCpuTimeEnabled should never occur given that we first check isThreadCpuTimeSupported
			}
			return true;
		}
		else {
			return false;
		}
	}
	
	
	/**
	* Trys to ensure that threadMXBean has enabled thread contention monitoring
	* (i.e. for collecting per-thread statistics about monitor contention).
	* If this monitoring is already enabled, this method does nothing.
	* Else if the JVM supports it but it was disabled when this method was called, then it will be enabled by this method.
	* Otherwise, if the JVM does not supports it, this method does nothing.
	* <p>
	* @return true if upon return thread contention monitoring is enabled, false otherwise
	* @throws IllegalArgumentException if threadMXBean == null
	*/
	public static boolean attemptThreadContentionMonitoring(ThreadMXBean threadMXBean) throws IllegalArgumentException {
		Check.arg().notNull(threadMXBean);
		
		if (threadMXBean.isThreadContentionMonitoringSupported()) {
			if (!threadMXBean.isThreadContentionMonitoringEnabled()) {
				threadMXBean.setThreadContentionMonitoringEnabled(true);	// the UnsupportedOperationException thrown by setThreadCpuTimeEnabled should never occur given that we first check isThreadCpuTimeSupported
			}
			return true;
		}
		else {
			return false;
		}
	}
	
	
	// -------------------- constructors --------------------
	
	
	/** Constructs an instance which gets thread information from the local JVM. */
	public ThreadMeasurer() {
		this( ManagementFactory.getThreadMXBean() );
	}
	
	
	/**
	* Constructs an instance which gets thread information from the JVM specified by server.
	* Typically, server refers to a remote JVM.
	* <p>
	* @throws IllegalArgumentException if server == null
	* @throws IOException if a communication problem occurrs when accessing server
	*/
	public ThreadMeasurer(MBeanServerConnection server) throws IllegalArgumentException, IOException {
		this( getThreadMXBean(server) );
	}
	
	
	/**
	* Constructs an instance which gets thread information from threadMXBean.
	* <p>
	* @throws IllegalArgumentException if threadMXBean == null
	*/
	public ThreadMeasurer(ThreadMXBean threadMXBean) throws IllegalArgumentException {
		Check.arg().notNull(threadMXBean);
		
		this.threadMXBean = threadMXBean;
		this.reportThreadTimes = attemptCpuTimeMeasurement(threadMXBean);
		this.reportThreadContention = attemptThreadContentionMonitoring(threadMXBean);
	}
	
	
	// -------------------- public instance api: --------------------
	
	
	/**
	* Returns a String description of the thread state of the monitored JVM.
	* <p>
	* Contract: the result is never null.
	* <p>
	* @throws UnsupportedOperationException if {@link #reportThreadTimes} was set to true in the constructor
	* but some other class called <code>{@link #threadMXBean}.{@link ThreadMXBean#setThreadCpuTimeEnabled setThreadCpuTimeEnabled}(false)</code>.
	*/
	public synchronized String getThreadState() throws UnsupportedOperationException {
		StringBuilder sb = new StringBuilder();
		sb.append("Thread state:" + '\n');
		
		int total = threadMXBean.getThreadCount();
		int daemon = threadMXBean.getDaemonThreadCount();
		int nondaemon = total - daemon;
		sb.append('\n');
		sb.append("High level statistics:" + '\n');
		sb.append('\t').append("Total number of live threads = ").append(total).append(" (").append(daemon).append(" daemon, ").append(nondaemon).append(" nondaemon)").append('\n');
		sb.append('\t').append("Peak thread count = ").append(threadMXBean.getPeakThreadCount()).append('\n');
		sb.append('\t').append("Total number of threads started = ").append(threadMXBean.getTotalStartedThreadCount()).append('\n');
		
		sb.append('\n');
		sb.append("Live threads:" + '\n');
		long[] threadIds = threadMXBean.getAllThreadIds();
		ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
		appendThreadInfoArray(threadInfos, sb);
		return sb.toString();
	}
	
	
	/**
	* Checks if any threads are deadlocked.
	* If any are, returns a String that fully describes the state of the deadlocked threads.
	* Otherwise, returns null.
	* <p>
	* This method will attempt to find deadlocks in both monitors as well as
	* <a href="http://java.sun.com/javase/6/docs/api/java/lang/management/LockInfo.html">ownable synchronizers</a>
	* if possible.
	* But if the platform does not support finding deadlock detection in ownable synchronizers,
	* it falls back to examining just monitors.
	* <p>
	* @throws SecurityException if a security manager exists and the caller does not have ManagementPermission("monitor")
	*/
	public synchronized String getDeadlockState() throws SecurityException {
		long[] threadIds = threadMXBean.isSynchronizerUsageSupported() ? threadMXBean.findDeadlockedThreads() : threadMXBean.findMonitorDeadlockedThreads();
		if (threadIds == null) return null;
		
		StringBuilder sb = new StringBuilder();
		sb.append("Deadlock found in these threads:" + '\n');
		ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, Integer.MAX_VALUE);
		appendThreadInfoArray(threadInfos, sb);
		return sb.toString();
	}
	
	
	// -------------------- protected helper methods: toStringThreadInfo, appendThreadInfo --------------------
	
	
	/**
	* Appends the data in threadInfos to sb.
	* <p>
	* Skips over any element that is null.
	* This behavior is critical, because if the threadInfos arg came as a result of calling
	* {@link ThreadMXBean#getThreadInfo ThreadMXBean.getThreadInfo}, then null elements may be present
	* for threads which died by the time that getThreadInfo was called.
	* <p>
	* @throws UnsupportedOperationException if {@link #reportThreadTimes} was set to true in the constructor
	* but some other class called <code>{@link #threadMXBean}.{@link ThreadMXBean#setThreadCpuTimeEnabled setThreadCpuTimeEnabled}(false)</code>.
	*/
	protected void appendThreadInfoArray(ThreadInfo[] threadInfos, StringBuilder sb) throws UnsupportedOperationException {
		for (ThreadInfo threadInfo : threadInfos) {
			sb.append('\n');
			if (threadInfo != null) appendThreadInfo(threadInfo, sb);	// skip as per method contract
		}
	}
	
	
	/**
	* Appends all of the data in threadInfo to sb.
	* <p>
	* @throws UnsupportedOperationException if {@link #reportThreadTimes} was set to true in the constructor
	* but some other class called <code>{@link #threadMXBean}.{@link ThreadMXBean#setThreadCpuTimeEnabled setThreadCpuTimeEnabled}(false)</code>.
	*/
	protected void appendThreadInfo(ThreadInfo threadInfo, StringBuilder sb) throws UnsupportedOperationException {
		sb.append('"').append(threadInfo.getThreadName()).append('"').append(" Id = ").append(threadInfo.getThreadId());
		
		sb.append(" in ").append(threadInfo.getThreadState());
		if (threadInfo.getLockName() != null) {
			sb.append(" on lock = ").append(threadInfo.getLockName());
			if (threadInfo.getLockOwnerName() != null) {
				sb.append(" owned by ").append(threadInfo.getLockOwnerName()).append(" Id = ").append(threadInfo.getLockOwnerId());
			}
		}
		if (threadInfo.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (threadInfo.isInNative()) {
			sb.append(" (running in native)");
		}
		sb.append('\n');
		
		if (reportThreadTimes) {
			long cpuTime = threadMXBean.getThreadCpuTime(threadInfo.getThreadId());
			long userTime = threadMXBean.getThreadUserTime(threadInfo.getThreadId());
			sb.append('\t').append("cpu time = ").append(cpuTime).append(" ns").append(", user time = ").append(userTime).append(" ns");
		}
		else {
			sb.append(", (cpu time unavailable");
		}
		sb.append('\n');
		
		sb.append('\t').append("blocked count = ").append(threadInfo.getBlockedCount());
		if (reportThreadContention) {
			sb.append(", blocked time = ").append(threadInfo.getBlockedTime()).append(" ms");
		}
		else {
			sb.append(", (blocked time unavailable");
		}
		sb.append('\n');
		
		sb.append('\t').append("waited count = ").append(threadInfo.getWaitedCount());
		if (reportThreadContention) {
			sb.append(", waited time = ").append(threadInfo.getWaitedTime()).append(" ms");
		}
		else {
			sb.append(", (waited time unavailable");
		}
		sb.append('\n');
		
		for (StackTraceElement ste : threadInfo.getStackTrace()) {
			sb.append('\t').append("at ").append(ste.toString()).append('\n');
		}
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getThreadState() {
			System.out.println("Output from calling threadMeasurer.getThreadState():");
			ThreadMeasurer threadMeasurer = new ThreadMeasurer();
			System.out.println(threadMeasurer.getThreadState());
		}
		
		/** <b>Warning:</b> after this method returns, there will be dealocked threads in existence, but they will have daemon status. */
		@Test public void test_getDeadlockState() throws Exception {
			System.out.println("Checking that threadMeasurer.getDeadlockState() returns null when there is no deadlock...");
			ThreadMeasurer threadMeasurer = new ThreadMeasurer();
			Assert.assertNull( threadMeasurer.getDeadlockState() );
			
			System.out.println("Checking that threadMeasurer.getDeadlockState() returns a String when there is deadlock...");
			establishDeadlocks();
			Assert.assertNotNull( threadMeasurer.getDeadlockState() );
		}
// +++ in the future when get jdk 1.6, add a deadlock test for the util.concurrent stuff which the 1.6 jvm apparently is supposed to detect
		
		/**
		* Creates 2 threads which deadlock.
		* Is public so that other classes may access.
		*/
		public static void establishDeadlocks() throws InterruptedException {
			Object lock1 = new Object();
			Object lock2 = new Object();
			
			CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
			
			Thread t1 = new Thread( new DoubleLockAquirer(lock1, lock2, cyclicBarrier), "DoubleLockAquirerThread1-2" );
			t1.setDaemon(true);
			t1.setPriority( Thread.NORM_PRIORITY );
			
			Thread t2 = new Thread( new DoubleLockAquirer(lock2, lock1, cyclicBarrier), "DoubleLockAquirerThread2-1" );
			t2.setDaemon(true);
			t2.setPriority( Thread.NORM_PRIORITY );
			
			t1.start();
			t2.start();
			
			while (cyclicBarrier.getNumberWaiting() > 0) {
				Thread.sleep(1);
			}
			Thread.sleep(100);	// CRITICAL: just because t1 and t2 have both gotten past cyclicBarrier does not mean that they have actually deadlocked yet, so must wait
		}
		
		private static class DoubleLockAquirer implements Runnable {
			
			private final Object lockFirst;
			private final Object lockSecond;
			private final CyclicBarrier cyclicBarrier;
			
			private DoubleLockAquirer(Object lockFirst, Object lockSecond, CyclicBarrier cyclicBarrier) {
				this.lockFirst = lockFirst;
				this.lockSecond = lockSecond;
				this.cyclicBarrier = cyclicBarrier;
			}
			
			public void run() {
				try {
					synchronized (lockFirst) {
						System.out.println( '\t' + "Thread " + Thread.currentThread().toString() + " has just synchronized on lockFirst = " + lockFirst );
						cyclicBarrier.await();
						synchronized (lockSecond) {
							System.out.println( '\t' + "Thread " + Thread.currentThread().toString() + " has just synchronized on lockSecond = " + lockSecond );
						}
					}
				}
				catch (Throwable t) {
					t.printStackTrace(System.err);
				}
			}
			
		}
		
	}
	
	
}
