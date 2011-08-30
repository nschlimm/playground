package com.schlimm.threads;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.schlimm.threads.model.Stock;
import com.schlimm.threads.model.StockAtomicLong;
import com.schlimm.threads.model.StockOwnedReadWriteLock;
import com.schlimm.threads.model.StockOwnedReentrantLock;
import com.schlimm.threads.model.StockSynchronized;
import com.schlimm.threads.model.StockUnsynchronized;

public class ExecutorFixedThreadPoolExampleExample {
	
	private final static Stock[] stock = new Stock[] { new StockUnsynchronized(0), new StockOwnedReadWriteLock(0), new StockOwnedReentrantLock(0), new StockSynchronized(0), new StockAtomicLong(0) };
	private final static ExecutorService[] pools = new ExecutorService[] { Executors.newFixedThreadPool(2), Executors.newCachedThreadPool(), Executors.newScheduledThreadPool(2), Executors.newSingleThreadExecutor(), Executors.newSingleThreadScheduledExecutor()};
	
	public class StockIncreaser implements Callable<Long> {
		private int stockObjectIndex = 0;
		private volatile long added = 0;
		private boolean running = true;

		public StockIncreaser(int stockObjectIndex) {
			super();
			this.stockObjectIndex = stockObjectIndex;
		}

		@Override
		public Long call() throws Exception {
			while (running) {
				if (Thread.currentThread().isInterrupted())
					running = false;
				stock[stockObjectIndex].add(1);
				added += 1;
			}
			return added;
		}

	}

	public class StockReducer implements Callable<Long> {
		private int stockObjectIndex = 0;
		private volatile long reduced = 0;
		private boolean running = true;

		public StockReducer(int stockObjectIndex) {
			super();
			this.stockObjectIndex = stockObjectIndex;
		}

		@Override
		public Long call() throws Exception {
			while (running) {
				if (Thread.currentThread().isInterrupted())
					running = false;
				stock[stockObjectIndex].reduce(1);
				reduced -= 1;
			}
			return reduced;
		}
	}
	
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		System.out.println(String.format("%1$-50s %2$-10s %3$-12s %4$-12s %5$-14s %6$-12s", "Case", "Units", "Added", "Reduced", "Expected Units", "Difference"));
		int poolsize = Runtime.getRuntime().availableProcessors();
		for (int x = 0; x < pools.length; x++) {
			for (int j = 0; j < stock.length; j++) {
				boolean taskSwitch = true;
				ExecutorService pool = pools[x];
				Collection<Future<Long>> futures = new ArrayList<Future<Long>>();
				for (int i = 0; i < poolsize; i++) {
					Callable<Long> taskToAdd = (taskSwitch ? new ExecutorFixedThreadPoolExampleExample().new StockIncreaser(j) : new ExecutorFixedThreadPoolExampleExample().new StockReducer(j));
					futures.add(pool.submit(taskToAdd));
					taskSwitch = (taskSwitch ? false : true);
				}
				Thread.sleep(10000);
				pool.shutdownNow();
				int added = 0; int reduced = 0;
				for (Future<Long> future : futures) {
					Long stockTask = future.get();
					added += (stockTask > 0 ? stockTask : 0);
					reduced += (stockTask < 0 ? -stockTask : 0);
				}
				int expectedUnits = added - reduced;
				System.out.println(String.format("%1$-50s %2$-10s %3$-12s %4$-12s %5$-14s %6$-12s", pools.getClass().getSimpleName().concat("-").concat(stock[j].getClass().getSimpleName()), stock[j].getUnits(), added, reduced, expectedUnits, stock[j].getUnits() - expectedUnits));
			}
		}
	}

}
