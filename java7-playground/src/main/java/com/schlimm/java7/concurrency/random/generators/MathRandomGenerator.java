package com.schlimm.java7.concurrency.random.generators;

import com.schlimm.java7.benchmark.original.BenchmarkRunnable;


public class MathRandomGenerator implements BenchmarkRunnable {

	private double r;

	@Override
	public void run() {
		r = r + Math.random();
	}

	public double getR() {
		return r;
	}

	@Override
	public Object getResult() {
		return r;
	}
}
