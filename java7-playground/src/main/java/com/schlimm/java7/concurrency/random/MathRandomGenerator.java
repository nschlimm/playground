package com.schlimm.java7.concurrency.random;

import java.util.concurrent.Callable;

public class MathRandomGenerator implements Callable<Long>{

	private double r;
	private long i;
	@Override
	public Long call() throws Exception {
		do {
			r = r + Math.random();
			i++;
		} while (!Thread.currentThread().isInterrupted());
		return i;
	}
}
