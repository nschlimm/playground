/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--joint notes concerning MemoryMeasurer/MemoryMonitor and the new JVMTI technology:

	+++ in the future, when 1.5 comes out, use JVMTI:
		"Creating a Debugging and Profiling Agent with JVMTI"
		http://java.sun.com/jsp_utils/PrintPage.jsp?url=http%3A%2F%2Fjava.sun.com%2Fdeveloper%2FtechnicalArticles%2FProgramming%2Fjvmti%2F

		--there is a ton of cool monitoring stuff in 1.5, e.g.:
			http://java.sun.com/j2se/1.5.0/docs/guide/management/index.html
			http://java.sun.com/j2se/1.5.0/docs/api/java/lang/management/package-summary.html

		--it looks like the new jdk 1.5 tool JConsole does everything already:
			http://java.sun.com/developer/technicalArticles/J2SE/jconsole.html
			http://java.sun.com/j2se/1.5.0/docs/guide/management/jconsole.html
		Well, it does it in a gui which then requires manual inspection, whereas I typically want automatic inspection.
		
		--see also:
			http://blogs.sun.com/CoreJavaTechTips/entry/the_attach_api

		--to show how to do it automatically (in this case, auto plotting), see this demo code:
			<JDK home>/demo/management/MemoryMonitor/src/MemoryMonitor.java
		This will involve updating my MemoryMeasurer class to use java.lang.management.MemoryMXBean just like my new ThreadMeasurer class does

	The advantages of my current code are:
		--it works with any JDK version
		--it operates on the basis of the total memory, and does not distinguish between the various pools
			--this could be a bug or a feature, depending on what you want

	The advantages of using the new 1.5 JVMTI stuff are:
		--you can have this monitoring be done in a separate JVM and have it even monitor a remote JVM
		--the new MemoryUsage class gives more info, like init and committed memory
		--they have built in support for event notification as well as polling
			+++ does this obsolete my MemoryMonitor class or not?
		--can individually access all the pools, both heap and non-heap
			--this could be a bug or a feature, depending on what you want
*/

package bb.util;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
* Class which measures memory state in a JVM.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class MemoryMeasurer {
	
	// -------------------- constants --------------------
	
	/** Specifies the maximum number of loops that {@link #restoreJvm restoreJvm} will execute. */
	private static final int maxRestoreJvmLoops = 100;
	
	/** Specifies the number of milliseconds that {@link #restoreJvm restoreJvm} will sleep for between loop iterations. */
	private static final long pauseTime = 1L;
	
	// -------------------- fields --------------------
	
	/** If true, causes {@link #getMemoryState getMemoryState} to first call {@link #restoreJvm restoreJvm} before measuring memory. */
	private final boolean callRestoreJvm;
	
	// -------------------- perform --------------------
	
	/** Returns <code>{@link #perform(boolean) perform}(false)</code>. */
	public static MemoryState perform() {
		return perform(false);
	}
	
	/**
	* Returns <code>new {@link #MemoryMeasurer(boolean) MemoryMeasurer}(callRestoreJvm).{@link #getMemoryState() getMemoryState}()</code>.
	* This is a convenience method for users who just want a few memory measurements.
	* Users who wish to make many measurements should create a dedicated MemoryMeasurer instance instead.
	*/
	public static MemoryState perform(boolean callRestoreJvm) {
		return new MemoryMeasurer(callRestoreJvm).getMemoryState();
	}
	
	// -------------------- restoreJvm, memoryUsed --------------------
	
	/**
	* Tries to restore the JVM to as clean a state as possible.
	* <p>
	* The first technique is a request for object finalizers to run
	* (via a call to {@link System#runFinalization System.runFinalization}).
	* The second technique is a request for garbage collection to run
	* (via a call to {@link System#gc System.gc}).
	* <p>
	* These calls are done in a loop that executes at least once,
	* and at most {@link #maxRestoreJvmLoops} times,
	* but will execute fewer times if no more objects remain to be finalized and heap memory usage becomes constant.
	* <p>
	* <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip130.html">This article</a>
	* suggested the idea to aggressively call for garbage collection many times,
	* and to use heap memory as a metric for deciding when can stop garbage collecting.
	*/
	public static void restoreJvm() {
		long memUsedPrev = memoryUsed();
		for (int i = 0; i < maxRestoreJvmLoops; i++) {
			System.runFinalization();	// see also: http://java.sun.com/developer/technicalArticles/javase/finalization/
			System.gc();
			
			//Thread.currentThread().yield();
			//Thread.sleep(pauseTime);
			
			long memUsedNow = memoryUsed();
			if (	// break early if have no more finalization and get constant mem used
				(ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount() == 0) &&
				(memUsedNow >= memUsedPrev)
			) {
				//System.out.println("ZERO objects pending finalization and ACHIEVED STABLE MEMORY (memUsedNow = " + memUsedNow + " >= memUsedPrev = " + memUsedPrev + ") at i = " + i);
				break;
			}
			else {
				//System.out.println(ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount() + " objects pending finalization and memory not stable (memUsedNow = " + memUsedNow + " < memUsedPrev = " + memUsedPrev + ") at i = " + i);
				memUsedPrev = memUsedNow;
			}
		}
	}
	
	/** Returns how much memory on the heap is currently being used. */
	public static long memoryUsed() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}
	
	// -------------------- constructor --------------------
	
	/** Calls <code>{@link #MemoryMeasurer(boolean) this}(false)</code>. */
	public MemoryMeasurer() {
		this(false);
	}
	
	/** Fundamental constructor. */
	public MemoryMeasurer(boolean callRestoreJvm) {
		this.callRestoreJvm = callRestoreJvm;
	}
	
	// -------------------- getMemoryState --------------------
	
	/**
	* Returns a new MemoryState instance which describes the current memory state.
	* <p>
	* <i>No guarantee can be made regarding the accuracy of the result</i>
	* since it is impossible to get an atomic snapshot of the JVM's memory state.
	* Furthermore, the memory state can change rapidly, so that the result may soon be obsolete.
	* <p>
	* @see #callRestoreJvm
	*/
	public MemoryState getMemoryState() {
		if (callRestoreJvm) restoreJvm();
		
		Runtime rt = Runtime.getRuntime();
		return new MemoryState( rt.freeMemory(), rt.totalMemory(), rt.maxMemory() );
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final double fillPoint = 0.5;
		
		private static final int arraySize = 1000 * 1000;
		
		@Test public void test_getMemoryState() throws Exception {
			MemoryMeasurer measurer = new MemoryMeasurer();
			
			System.out.println();
			System.out.println("Restoring the JVM to a pristine state...");
			MemoryMeasurer.restoreJvm();
			
			double usedRatioInitial = measurer.getMemoryState().getUsedRatio();
			
			System.out.println();
			System.out.println("Memory state initial:");
			printMemoryState( measurer.getMemoryState() );
			
			System.out.println();
			System.out.println("Filling memory up to fillPoint = " + fillPoint + "...");
			MemoryFiller filler = new MemoryFiller(fillPoint, measurer);
			filler.fill();
			System.out.println("Memory state after memory has been filled:");
			printMemoryState( measurer.getMemoryState() );
			
			System.out.println();
			System.out.println("Freeing the memory that was previously filled...");
			filler.free();
			System.out.println("Performing explicit garbage collection...");
			MemoryMeasurer.restoreJvm();
			System.out.println("Memory state after garbage collection has finished:");
			printMemoryState( measurer.getMemoryState() );
			
			double usedRatioFinal = measurer.getMemoryState().getUsedRatio();
			Assert.assertEquals("usedRatioFinal !~= usedRatioInitial", usedRatioInitial, usedRatioFinal, 0.01);
			
			System.out.println();
			System.out.println("Sleeping for 5 s to see how the JVM's memory changes while idle...");
			Thread.sleep( 5 * TimeLength.second );
			System.out.println("Memory state after JVM has been idle for 1 s:");
			printMemoryState( measurer.getMemoryState() );
		}
		
		private static void printMemoryState(MemoryState state) {
			System.out.println("state: " + state.toString());
			System.out.println("availableRatio: " + state.getAvailableRatio());
		}
		
		/**
		* Class which attempts to fill up memory to a {@link #fillPoint specified point}.
		* Is public so that other classes may access.
		*/
		public static class MemoryFiller {
			
			private static final long interval_default = 0;	// 0 means no wait between fill loops in run below
			
			private static final int numObjsCreatedPerLoop = 1000;
			
			private final double fillPoint;
			private final long interval;
			private final MemoryMeasurer measurer;
			private final List<Object> objects = new LinkedList<Object>();
			
			/** Returns <code>this(fillPoint, {@link #interval_default}, measurer)</code>. */
			public MemoryFiller(double fillPoint, MemoryMeasurer measurer) throws IllegalArgumentException {
				this(fillPoint, interval_default, measurer);
			}
			
			/**
			* Fundamental constructor.
			* <p>
			* @throws IllegalArgumentException if fillPoint <= 0; fillPoint >= 1; interval < 0; measurer == null
			*/
			public MemoryFiller(double fillPoint, long interval, MemoryMeasurer measurer) throws IllegalArgumentException {
				if ((fillPoint <= 0) || (fillPoint >= 1)) throw new IllegalArgumentException("fillPoint = " + fillPoint + " is outside of the acceptable range (0, 1)");
				Check.arg().notNull(measurer);
				
				this.fillPoint = fillPoint;
				this.interval = interval;
				this.measurer = measurer;
			}
			
			/**
			* Executes a loop which fills up the memory.
			* The first action inside the loop is to sleep for {@link #interval}.
			* Next, it creates several new Object instances and adds them to a List local variable
			* (so that they cannot be garbage collected as long as this method is being executed);
			* {@link #numObjsCreatedPerLoop} specifies how many Objects are created and added each time.
			* The loop executes as long as {@link #measurer}.{@link MemoryMeasurer#getMemoryState getMemoryState}.{@link MemoryState#getAvailableRatio getAvailableRatio} > fillPoint.
			* Once the method returns, all the memory that was allocated will be eligible for garbage collection.
			*/
			public void fill() throws InterruptedException {
				while (measurer.getMemoryState().getUsedRatio() < fillPoint) {
					Thread.sleep(interval);
					for (int i = 0; i < numObjsCreatedPerLoop; i++) {
						objects.add( new Object() );
					}
				}
			}
			
			/** Removes references to all the objects created by {@link #fill fill} so that they can be garbage collected. */
			public void free() {
				objects.clear();
			}
			
		}
		
		/**
		* Results on 2009-06-10 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_14 server jvm):
		* <pre><code>
			each array element of an Object uses 12.000016 bytes
			which implies that just an Object uses 8.000016 bytes (since the size of the reference in the array is 4 bytes)
		</code></pre>
		*/
		@Test public void test_perform_Object() throws Exception {
			long usedStart = MemoryMeasurer.perform(true).getUsed();
			Object[] array = new Object[arraySize];
			for (int i = 0; i < arraySize; i++) {
				array[i] = new Object();
			}
			long usedEnd = MemoryMeasurer.perform(true).getUsed();
			double bytesPer = (usedEnd - usedStart) / ((double) arraySize);
			System.out.println();
			System.out.println("each array element of an Object uses " + bytesPer + " bytes");
			System.out.println("which implies that just an Object uses " + (bytesPer - 4) + " bytes (since the size of the reference in the array is 4 bytes)");
			System.out.println("array.hashCode() = " + array.hashCode());	// CRITICAL: need this line to keep array from getting garbage collected before the final call to MemoryMeasurer.perform
		}
		
		/**
		* Results on 2009-06-10 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_14 server jvm):
		* <pre><code>
			each array element of a Date uses 28.398416 bytes
			which implies that just a Date uses 24.398416 bytes (since the size of the reference in the array is 4 bytes)
		</code></pre>
		*/
		@Test public void test_perform_Date() throws Exception {
			long usedStart = MemoryMeasurer.perform(true).getUsed();
			Date[] array = new Date[arraySize];
			for (int i = 0; i < arraySize; i++) {
				array[i] = new Date(i);
			}
			long usedEnd = MemoryMeasurer.perform(true).getUsed();
			double bytesPer = (usedEnd - usedStart) / ((double) arraySize);
			System.out.println();
			System.out.println("each array element of a Date uses " + bytesPer + " bytes");
			System.out.println("which implies that just a Date uses " + (bytesPer - 4) + " bytes (since the size of the reference in the array is 4 bytes)");
			System.out.println("array.hashCode() = " + array.hashCode());	// CRITICAL: need this line to keep array from getting garbage collected before the final call to MemoryMeasurer.perform
		}
		
	}
	
}
