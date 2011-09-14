package com.schlimm.master.threading.model;

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

	public void add(long quantity) throws InterruptedException  {
		lock.writeLock().lockInterruptibly();
		try {
			units += quantity;
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	public void reduce(long quantity) throws InterruptedException {
		add(-quantity);
	}

	public long getUnits() throws InterruptedException {
		lock.readLock().lockInterruptibly();
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
