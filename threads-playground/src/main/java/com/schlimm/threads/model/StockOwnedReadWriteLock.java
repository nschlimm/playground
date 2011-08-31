package com.schlimm.threads.model;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Unsynchronized StockSynchronized object.
 * 
 * @author Niklas Schlimm
 *
 */
public class StockOwnedReadWriteLock implements Stock {
	
	private final ReadWriteLock lock = new ReentrantReadWriteLock();
	private long units;
	
	public StockOwnedReadWriteLock(long initial) {
		super();
		this.units = initial;
	}

	public long add(long quantity) {
		lock.writeLock().lock();
		try {
			units += quantity;
			return units;
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	public long reduce(long quantity) {
		return add(-quantity);
	}

	public long getUnits() {
		lock.readLock().lock();
		try {
			return units;
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public Stock prototype(int initial) {
		return new StockOwnedReadWriteLock(initial);
	}

}
