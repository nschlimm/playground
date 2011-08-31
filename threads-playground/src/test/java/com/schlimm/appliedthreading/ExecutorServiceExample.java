package com.schlimm.appliedthreading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.schlimm.threads.model.Stock;
import com.schlimm.threads.model.StockArrayBlockingQueue;
import com.schlimm.threads.model.StockAtomicLong;
import com.schlimm.threads.model.StockOwnedReadWriteLock;
import com.schlimm.threads.model.StockOwnedReentrantLock;
import com.schlimm.threads.model.StockSynchronized;
import com.schlimm.threads.model.StockUnsynchronized;

public class ExecutorServiceExample {

	private final static int poolsize = Runtime.getRuntime().availableProcessors();
	private interface ExecutorServiceFactory {ExecutorService createExecutor();};
	private final static Stock[] stockObjects = new Stock[] { new StockUnsynchronized(0), new StockOwnedReadWriteLock(0), new StockOwnedReentrantLock(0), new StockSynchronized(0),
			new StockAtomicLong(0), new StockArrayBlockingQueue((int) (Runtime.getRuntime().freeMemory()/100)) };
	private final static ExecutorServiceFactory[] pools = new ExecutorServiceFactory[] { 
		new ExecutorServiceFactory() {@Override public ExecutorService createExecutor() {return Executors.newFixedThreadPool(poolsize);}}, 
		new ExecutorServiceFactory() {@Override public ExecutorService createExecutor() {return Executors.newCachedThreadPool();}}, 
		new ExecutorServiceFactory() {@Override public ExecutorService createExecutor() {return Executors.newScheduledThreadPool(poolsize);}}, 
		new ExecutorServiceFactory() {@Override public ExecutorService createExecutor() {return Executors.newSingleThreadExecutor();}}, 
		new ExecutorServiceFactory() {@Override public ExecutorService createExecutor() {return Executors.newSingleThreadScheduledExecutor();}}};

	public class StockIncreaser implements Callable<Long> {
		private volatile long added = 0;
		private boolean running = true;
		private Stock stockObject;
		
		public StockIncreaser(Stock stockObject) {
			super();
			this.stockObject = stockObject;
		}

		@Override
		public Long call() throws Exception {
			while (running) {
				try {
					stockObject.add(1);
					added += 1;
					if (Thread.currentThread().isInterrupted()) // exit safe if interrupted
						running = false;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			return added;
		}

	}

	public class StockReducer implements Callable<Long> {
		private volatile long reduced = 0;
		private boolean running = true;
		private Stock stockObject;

		public StockReducer(Stock stockObject) {
			super();
			this.stockObject = stockObject;
		}

		@Override
		public Long call() throws Exception {
			while (running) {
				try {
					stockObject.reduce(1);
					reduced -= 1;
					if (Thread.currentThread().isInterrupted()) // exit safe if interrupted
						running = false;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			return reduced;
		}
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException {
		System.out.println(String.format("%1$-110s %2$-10s %3$-12s %4$-12s %5$-14s %6$-12s", "Case", "Units", "Added", "Reduced", "Expected Units", "Difference"));
		for (int x = 0; x < pools.length; x++) {
			for (int j = 0; j < stockObjects.length; j++) {

				// need to create new stock object and thread pool on every iteration
				Stock stockObject = stockObjects[j].prototype(0);
				ExecutorService pool = pools[x].createExecutor();
				
				// add tasks to the worker pool
				Collection<Future<Long>> futures = new ArrayList<Future<Long>>();
				for (int i = 0; i < poolsize; i++) {
					Callable<Long> taskToAdd = ((i+2) % 2 == 0 ? new ExecutorServiceExample().new StockIncreaser(stockObject) : new ExecutorServiceExample().new StockReducer(stockObject));
					futures.add(pool.submit(taskToAdd));
				}

				// wait
				Thread.sleep(2000);

				// what's the actual pool size?
				int actualPoolSize = 0;
				if (pool instanceof ThreadPoolExecutor)
					actualPoolSize = ((ThreadPoolExecutor) pool).getPoolSize();

				// interrupt the threads safely and cancel non executed tasks
				pool.shutdownNow();

				// give the active threads some time to interrupt
				Thread.sleep(100);

				// give this case a identifying name
				StringBuilder thisCase = new StringBuilder(String.valueOf(x)).append("-").append(stockObject.getClass().getSimpleName()).append("-").append(pool.getClass().getSimpleName());
				if (pool instanceof ThreadPoolExecutor) {
					ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
					thisCase.append("-").append(actualPoolSize).append("-").append(executor.getCorePoolSize()).append("-").append(executor.getMaximumPoolSize()).append("-").append(executor.getQueue().getClass().getSimpleName())
							.append("-").append(executor.getKeepAliveTime(TimeUnit.MILLISECONDS)).append("-").append(executor.getThreadFactory().getClass().getSimpleName());
				}

				// extract te results of the tasks using the futures
				int added = 0;
				int reduced = 0;
				for (Future<Long> future : futures) {
					Long stockTask = null;
					try {
						stockTask = future.get(1, TimeUnit.MILLISECONDS); // need time out 'cause after shutdown non started tasks
																			// will not return a result
					} catch (TimeoutException e) {
						// don't do this in production code :)
					}
					if (stockTask != null) {
						added += (stockTask > 0 ? stockTask : 0);
						reduced += (stockTask < 0 ? -stockTask : 0);
					}
				}
				int expectedUnits = added - reduced;

				// print the result to the sysout
				System.out.println(String.format("%1$-110s %2$-10s %3$-12s %4$-12s %5$-14s %6$-12s", thisCase, stockObject.getUnits(), added, reduced, expectedUnits, stockObject.getUnits()
						- expectedUnits));

			}
		}
	}

}
