package com.schlimm.appliedthreading;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.threads.model.Stock;
import com.schlimm.threads.model.StockOwnedReadWriteLock;
import com.schlimm.threads.model.StockOwnedReentrantLock;
import com.schlimm.threads.model.StockSynchronized;
import com.schlimm.threads.model.StockUnsynchronized;

public class TimerExample {

	private final static Stock[] stock = new Stock[] { new StockUnsynchronized(0), new StockOwnedReadWriteLock(0), new StockOwnedReentrantLock(0), new StockSynchronized(0) };

	public class StockIncreaser extends TimerTask {
		private int stockObjectIndex = 0;
		private volatile double added = 0;
		public StockIncreaser(int stockObjectIndex) {
			super();
			this.stockObjectIndex = stockObjectIndex;
		}
		@Override
		public void run() {
			try {
				stock[stockObjectIndex].add(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			added += 1;
		}
	}

	public class StockReducer extends TimerTask {
		private int stockObjectIndex = 0;
		private volatile double reduced = 0; 
		public StockReducer(int stockObjectIndex) {
			super();
			this.stockObjectIndex = stockObjectIndex;
		}
		@Override
		public void run() {
			try {
				stock[stockObjectIndex].reduce(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			reduced += 1;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		System.out.println(String.format("%1$-30s %2$-7s %3$-7s %4$-7s %5$-7s", "Case", "Units", "Added", "Reduced", "A-R"));
		for (int i = 0; i < stock.length; i++) {
			StockIncreaser task1 = new TimerExample().new StockIncreaser(i);
			Timer timer1 = new Timer("Timer-Increaser");
			timer1.schedule(task1, 0, 1);
			StockReducer task2 = new TimerExample().new StockReducer(i);
			Timer timer2 = new Timer("Timer-Reducer");
			timer2.schedule(task2, 0, 1);
			
			Thread.sleep(20000);
			
			timer1.cancel();
			timer2.cancel();
			
			Thread.sleep(1000);
			
			System.out.println(String.format("%1$-30s %2$-7s %3$-7s %4$-7s %5$-7s", stock[i].getClass().getSimpleName(), stock[i].getUnits(), task1.added, task2.reduced, (task1.added - task2.reduced)));
		}
	}

}
