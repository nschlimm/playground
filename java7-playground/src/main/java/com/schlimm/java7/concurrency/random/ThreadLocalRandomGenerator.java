package com.schlimm.java7.concurrency.random;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

public class ThreadLocalRandomGenerator implements Callable<Long> {

	private double r;
	private long i;
	@Override
	public Long call() throws Exception {
		do {
			r = r + ThreadLocalRandom.current().nextDouble();
			i++;
		} while (!Thread.currentThread().isInterrupted());
		return i;
	}
	
}
