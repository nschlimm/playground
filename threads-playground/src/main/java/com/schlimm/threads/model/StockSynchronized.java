package com.schlimm.threads.model;

public class StockSynchronized implements Stock {
	
	private final Object monitor = new Object();
	private long units;
	
	public StockSynchronized(int initial) {
		super();
		this.units = initial;
	}

	public long add(long quantity) {
		synchronized (monitor) {
			units += quantity;
		}
		return units;
	}
	
	public long reduce(long quantity) {
		return add(-quantity);
	}

	public long getUnits() {
		// is required to ensure unit are read from heap (instead of stack)
		synchronized (monitor) {
			return units;
		}
	}

	@Override
	public String getCase() {
		return "Stock with synchronized block";
	}

}
