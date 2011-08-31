package com.schlimm.threads.model;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Unsynchronized StockSynchronized object.
 * 
 * @author Niklas Schlimm
 *
 */
public class StockOwnedReentrantLock implements Stock {
	
	private final Lock lock = new ReentrantLock();
	private long units;
	
	public StockOwnedReentrantLock(int initial) {
		super();
		this.units = initial;
	}

	public long add(long quantity) {
		lock.lock();
		try {
			units += quantity;
			return units;
		} finally {
			lock.unlock();
		}
	}
	
	public long reduce(long quantity) {
		return add(-quantity);
	}

	public long getUnits() {
		lock.lock();
		try {
			return units;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Stock prototype(int initial) {
		return new StockOwnedReentrantLock(initial);
	}

}
