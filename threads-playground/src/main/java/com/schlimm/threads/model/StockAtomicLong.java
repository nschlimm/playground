package com.schlimm.threads.model;

import java.util.concurrent.atomic.AtomicLong;

public class StockAtomicLong implements Stock {
	
	private AtomicLong units = new AtomicLong();
	
	public StockAtomicLong(long initial) {
		super();
		this.units.addAndGet(initial);
	}

	public long add(long quantity) {
		units.getAndAdd(quantity);
		return units.get();
	}
	
	public long reduce(long quantity) {
		return add(-quantity);
	}

	public long getUnits() {
		return units.get();
	}

}
