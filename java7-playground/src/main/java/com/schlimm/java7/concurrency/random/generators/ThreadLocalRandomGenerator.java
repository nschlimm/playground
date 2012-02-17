package com.schlimm.java7.concurrency.random.generators;

import java.util.concurrent.ThreadLocalRandom;

import com.schlimm.java7.benchmark.original.BenchmarkRunnable;

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
