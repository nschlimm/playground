package com.schlimm.threads.model;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class StockArrayBlockingQueue implements Stock {
	
	private BlockingQueue<Long> stock = null;
	
	public StockArrayBlockingQueue(int initial) {
		super();
		this.stock=new ArrayBlockingQueue<Long>(initial);
	}

	public void add(long quantity) throws InterruptedException {
		stock.put(quantity);
	}
	
	public void reduce(long quantity) throws InterruptedException {
		add(-quantity);
	}

	public long getUnits() {
		return stock.size();
	}

	@Override
	public Stock prototype(int initial) {
		return new StockArrayBlockingQueue((int) (Runtime.getRuntime().freeMemory()/100));
	}

}
