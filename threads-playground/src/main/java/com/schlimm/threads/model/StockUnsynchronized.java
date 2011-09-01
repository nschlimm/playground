package com.schlimm.threads.model;

public class StockUnsynchronized implements Stock {
	
	private volatile long units; // always read value from heap memory -> only race condition will result in inconsistent state
	
	public StockUnsynchronized(int initial) {
		super();
		this.units = initial;
	}

	public void add(long quantity) {
		units += quantity;
	}
	
	public void reduce(long quantity) {
		add(-quantity);
	}

	public long getUnits() {
		return units;
	}

	@Override
	public Stock prototype(int initial) {
		return new StockUnsynchronized(initial);
	}

}
