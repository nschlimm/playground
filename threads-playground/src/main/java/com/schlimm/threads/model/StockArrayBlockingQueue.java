package com.schlimm.threads.model;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class StockArrayBlockingQueue implements Stock {
	
	private BlockingQueue<Long> stock = new ArrayBlockingQueue<Long>(0);
	
	public StockArrayBlockingQueue(long initial) {
		super();
		this.stock.addAll(Arrays.asList(Arrays.copyOf(new Long[]{initial}, 9)));
	}

	public long add(long quantity) {
		stock.offer(quantity);
		return stock.size();
	}
	
	public long reduce(long quantity) throws InterruptedException {
		return stock.poll(1000, TimeUnit.MILLISECONDS);
	}

	public long getUnits() {
		return stock.size();
	}

	@Override
	public Stock prototype(int initial) {
		return new StockArrayBlockingQueue(initial);
	}

}
