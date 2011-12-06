/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
* Extension of ThreadPoolExecutor
* that makes it more convenient to construct instances suited for a specific concurrent scenario.
* <p>
* The relevant scenario is when some primary thread(s) simply get data
* and submit it to a thread pool which will do all the remaining data processing.
* <p>
* Typically, there is just a single primary thread generating data
* because there is usually only a single source of data
* (e.g. hard drive or network that is read, or computational tasks that are generated).
* Furthermore, that single data generating thread usually does not fully utilize even 1 CPU
* (e.g. because disk and network bandwidth rarely approaches the data processing capabilities of modern CPUs).
* <p>
* In contrast, there are usually multiple threads in the thread pool, one per CPU,
* so that they can concurrently execute data processing tasks, which are often time consuming.
* <p>
* The usual motivations for having the primary thread(s)
* submit the actual data processing work to a thread pool all apply here.
* First, it allows the primary thread(s) to do minimal work,
* which allows them to remain responsive (e.g. for reading new incoming data).
* This is especially useful when spikes in the data rate occur.
* Second, the potential concurrency offered by the pool
* may enable optimal use of all CPUs, which can greatly increase data throughput.
* <p>
* This class adds no new methods to ThreadPoolExecutor.
* Instead, its public api is solely in its constructors which simplify the creation of ThreadPoolExecutors.
* The javadocs for the {@link #ThreadPoolExecutor2(int, int) fundamental constructor} (and the methods it links to)
* explain why it supports the above concurrent scenario.
* <p>
* @author Brent Boyer
*/
public class ThreadPoolExecutor2 extends ThreadPoolExecutor {
	
	// -------------------- constants --------------------
	
	/**
	* Default value for the numberCpusReserved param
	* that can be passed to the {@link #ThreadPoolExecutor2(int, int) fundamental constructor}.
	* This value is 0, which is appropriate for a single primary thread reading data
	* which is not fully using even one CPU.
	*/
	public static final int numberCpusReserved_default = 0;
	
	/**
	* Default value for the maxBackupPerPoolThread param
	* that can be passed to the {@link #ThreadPoolExecutor2(int, int) fundamental constructor}.
	* This value is 3, which allows each thread in the pool to have, on average, at most 3 tasks piled up for it
	* before the calling thread will start executing tasks.
	*/
	public static final int maxBackupPerPoolThread_default = 3;
	
	// -------------------- constructors --------------------
	
	/** Simply calls <code>{@link #ThreadPoolExecutor2(int) this}({@link #maxBackupPerPoolThread_default})</code>. */
	public ThreadPoolExecutor2() {
		this( maxBackupPerPoolThread_default );
	}
	
	/**
	* Simply calls <code>{@link #ThreadPoolExecutor2(int, int) this}({@link #numberCpusReserved_default}, maxBackupPerPoolThread)</code>.
	* <p>
	* @throws IllegalArgumentException if maxBackupPerPoolThread < 0
	*/
	public ThreadPoolExecutor2(int maxBackupPerPoolThread) throws IllegalArgumentException {
		this( numberCpusReserved_default, maxBackupPerPoolThread );
	}
	
	/**
	* First calls super using these params:
	* <ol>
	*  <li>
	*		corePoolSize == maximumPoolSize == {@link #poolSize poolSize}(numberCpusReserved)
	*		(so the pool always has a constant number of threads
	*		that equals the number of free CPUs or 1 if none free)
	*  </li>
	*  <li>
	*		keepAliveTime == {@link Long#MAX_VALUE Long.MAX_VALUE}
	*		(so timeout is practically infinite)
	*  </li>
	*  <li>unit == {@link TimeUnit#SECONDS TimeUnit.SECONDS}</li>
	*  <li>
	*		workQueue == new {@link ArrayBlockingQueue#ArrayBlockingQueue(int) ArrayBlockingQueue}
	*		with an initial capacity determined by a call to
	*		{@link #queueSize queueSize}(numberCpusReserved, maxBackupPerPoolThread)
	*		(so tasks will only pile up to a specified limit before being rejected by the queue)
	*  </li>
	*  <li>
	*		handler == new {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy ThreadPoolExecutor.CallerRunsPolicy}
	*		(so tasks rejected by a full queue will be executed by the calling thread)
	*  </li>
	* </ol>
	* Then, a call is made to the pool's {@link #prestartAllCoreThreads prestartAllCoreThreads} method.
	* <p>
	* @throws IllegalArgumentException if numberCpusReserved < 0; maxBackupPerPoolThread < 0
	*/
	public ThreadPoolExecutor2(int numberCpusReserved, int maxBackupPerPoolThread) throws IllegalArgumentException {
		// all args checked by calls to poolSize and queueSize below
		
		super(
			poolSize(numberCpusReserved),	// corePoolSize - the number of threads to keep in the pool, even if they are idle
			poolSize(numberCpusReserved),	// maximumPoolSize - the maximum number of threads to allow in the pool
			Long.MAX_VALUE,	// keepAliveTime - when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating
			TimeUnit.SECONDS, // unit - the time unit for the keepAliveTime argument
			new ArrayBlockingQueue<Runnable>( queueSize(numberCpusReserved, maxBackupPerPoolThread) ),	// workQueue - the queue to use for holding tasks before they are executed. This queue will hold only the Runnable tasks submitted by the execute method
			new ThreadPoolExecutor.CallerRunsPolicy()	// handler - the handler to use when execution is blocked because the thread bounds and queue capacities are reached
		);
		
		prestartAllCoreThreads();
	}
	
	// -------------------- numberCpus, poolSize, queueSize --------------------
	
// +++ could make all of these methods be protected and non-static so subclasses can override,
// but would need to write a contract that they cannot use any instance state, since constructor calls them
	
	/**
	* Returns <code>{@link Math#max Math.max}( {@link #numberCpus numberCpus} - numberCpusReserved, 1 )</code>
	* (i.e. <code>numberCpus - numberCpusReserved</code> if <code>numberCpus > numberCpusReserved</code>,
	* or 1 if <code>numberCpus <= numberCpusReserved</code>).
	* <p>
	* Reasoning: <code>numberCpus - numberCpusReserved</code> is the number of CPUs freely and fully available for the pool.
	* Assuming that each pool thread should have its own free CPU, then that difference is also the optimal pool size.
	* The pool, of course, has to have at least 1 thread, which is why 1 is the lower bound of the result.
	* <p>
	* Note that numberCpusReserved can be used to reserve CPUs for arbitrary reasons,
	* but typically it is done to reserve 1 or more CPUs for the primary data reading thread(s)
	* (see class javadocs).
	* <p>
	* @throws IllegalArgumentException if numberCpusReserved < 0
	*/
	private static int poolSize(int numberCpusReserved) throws IllegalArgumentException {
		Check.arg().notNegative(numberCpusReserved);
		
		return Math.max( numberCpus() - numberCpusReserved, 1 );
	}
	
	/**
	* Returns <code>{@link Math#max Math.max}( {@link #poolSize poolSize}(numberCpusReserved) * maxBackupPerPoolThread, 1 )</code>.
	* <p>
	* Reasoning: each pool thread should have, on average, no more than maxBackupPerPoolThread tasks in the queue,
	* hence poolSize * maxBackupPerPoolThread should be the normal queue size.
	* The queue, of course, has to have a capacity of at least 1, which is why 1 is the lower bound of the result.
	* <p>
	* @throws IllegalArgumentException if numberCpusReserved < 0; maxBackupPerPoolThread < 0
	*/
	private static int queueSize(int numberCpusReserved, int maxBackupPerPoolThread) throws IllegalArgumentException {
		// numberCpusReserved checked by call to poolSize below
		Check.arg().notNegative(maxBackupPerPoolThread);
		
		return Math.max( poolSize(numberCpusReserved) * maxBackupPerPoolThread, 1 );
	}
	
	/** Returns the total number of CPUs that are available to this JVM. */
	private static int numberCpus() {
		return Runtime.getRuntime().availableProcessors();
	}
	
}
