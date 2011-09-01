package com.schlimm.threads.model;

import java.util.concurrent.atomic.AtomicLong;

public class StockAtomicLong implements Stock {
	
	private AtomicLong units = new AtomicLong();
	
	public StockAtomicLong(long initial) {
		super();
		this.units.addAndGet(initial);
	}

	public void add(long quantity) {
		units.getAndAdd(quantity);
	}
	
	public void reduce(long quantity) {
		add(-quantity);
	}

	public long getUnits() {
		return units.get();
	}

	@Override
	public Stock prototype(int initial) {
		return new StockAtomicLong(initial);
	}

}
