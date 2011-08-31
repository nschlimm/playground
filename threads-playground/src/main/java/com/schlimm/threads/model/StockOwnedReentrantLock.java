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

	public void add(long quantity) throws InterruptedException {
		lock.lockInterruptibly();
		try {
			units += quantity;
		} finally {
			lock.unlock();
		}
	}
	
	public void reduce(long quantity) throws InterruptedException {
		add(-quantity);
	}

	public long getUnits() throws InterruptedException {
		lock.lockInterruptibly();
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
