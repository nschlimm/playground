package com.schlimm.threads.model;

public class StockSynchronized implements Stock {
	
	private final Object monitor = new Object();
	private long units;
	
	public StockSynchronized(int initial) {
		super();
		this.units = initial;
	}

	public void add(long quantity) {
		synchronized (monitor) {
			units += quantity;
		}
	}
	
	public void reduce(long quantity) {
		add(-quantity);
	}

	public long getUnits() {
		// is required to ensure unit are read from heap (instead of stack)
		synchronized (monitor) {
			return units;
		}
	}

	@Override
	public Stock prototype(int initial) {
		return new StockSynchronized(initial);
	}

}
