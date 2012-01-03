package com.schlimm.java7.concurrency.random;

import java.util.concurrent.ThreadLocalRandom;

public class ThreadLocalRandomGenerator implements BenchmarkRunnable {

	private double r;

	@Override
	public void run() {
		r = r + ThreadLocalRandom.current().nextDouble();
	}

	public double getR() {
		return r;
	}

	@Override
	public Object getResult() {
		return r;
	}
		
}
