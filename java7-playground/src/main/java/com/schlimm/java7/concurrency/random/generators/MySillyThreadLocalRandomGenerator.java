package com.schlimm.java7.concurrency.random.generators;

import com.schlimm.java7.benchmark.original.BenchmarkRunnable;
import com.schlimm.java7.concurrency.random.MySillyThreadLocalRandom;


public class MySillyThreadLocalRandomGenerator implements BenchmarkRunnable {

	private double r;
	
	@Override
	public void run() {
		r = r + MySillyThreadLocalRandom.current().nextDouble();
	}

	public double getR() {
		return r;
	}

	@Override
	public Object getResult() {
		return r;
	}
		
}
