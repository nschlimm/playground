package com.schlimm.threads.model;

public class StockUnsynchronized implements Stock {
	
	private volatile long units; // always read value from heap memory -> only race condition will result in inconsistent state
	
	public StockUnsynchronized(int initial) {
		super();
		this.units = initial;
	}

	public long add(long quantity) {
		units += quantity;
		return units;
	}
	
	public long reduce(long quantity) {
		return add(-quantity);
	}

	public long getUnits() {
		return units;
	}

	@Override
	public String getCase() {
		return "Unsynchronized Stock";
	}

}
