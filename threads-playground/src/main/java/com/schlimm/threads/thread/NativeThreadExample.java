package com.schlimm.threads.thread;

import com.schlimm.threads.model.Stock;
import com.schlimm.threads.model.StockAtomicLong;
import com.schlimm.threads.model.StockOwnedReadWriteLock;
import com.schlimm.threads.model.StockOwnedReentrantLock;
import com.schlimm.threads.model.StockSynchronized;
import com.schlimm.threads.model.StockUnsynchronized;

public class NativeThreadExample {

	private final static Stock[] stock = new Stock[] { new StockUnsynchronized(0), new StockOwnedReadWriteLock(0), new StockOwnedReentrantLock(0), new StockSynchronized(0), new StockAtomicLong(0) };

	public class StockIncreaser extends Thread {
		private int stockObjectIndex = 0;
		private volatile double added = 0;

		public StockIncreaser(String name, int stockObjectIndex) {
			super(name);
			this.stockObjectIndex = stockObjectIndex;
		}

		@Override
		public void run() {

			while (true) {
				if (Thread.currentThread().isInterrupted())
					break;
				stock[stockObjectIndex].add(1);
				added += 1;
			}
		}
	}

	public class StockReducer extends Thread {
		private int stockObjectIndex = 0;
		private volatile double reduced = 0;

		public StockReducer(String name, int stockObjectIndex) {
			super(name);
			this.stockObjectIndex = stockObjectIndex;
		}

		@Override
		public void run() {
			while (true) {
				if (Thread.currentThread().isInterrupted())
					break;
				stock[stockObjectIndex].reduce(1);
				reduced += 1;
			}
		}
	}

	public static void main(String[] args) throws InterruptedException {
		System.out.println(String.format("%1$-30s %2$-10s %3$-20s %4$-20s %5$-20s", "Case", "Units", "Added", "Reduced", "A-R"));
		for (int i = 0; i < stock.length; i++) {
			StockIncreaser thread1 = new NativeThreadExample().new StockIncreaser("Stock-Increaser", i);
			thread1.start();
			StockReducer thread2 = new NativeThreadExample().new StockReducer("Stock-Reducer", i);
			thread2.start();

			Thread.sleep(10000);

			thread1.interrupt();
			thread2.interrupt();

			Thread.sleep(1000);

			System.out.println(String.format("%1$-30s %2$-10s %3$-20s %4$-20s %5$-20s", stock[i].getCase(), stock[i].getUnits(), thread1.added, thread2.reduced, (thread1.added - thread2.reduced)));
		}
	}

}
