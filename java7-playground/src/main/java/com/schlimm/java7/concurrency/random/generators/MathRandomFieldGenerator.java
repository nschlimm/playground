package com.schlimm.java7.concurrency.random.generators;

import java.util.Random;

import com.schlimm.java7.benchmark.original.BenchmarkRunnable;

public class MathRandomFieldGenerator implements BenchmarkRunnable {
	
	private double r;
	
	private static Random random = new Random();

	@Override
	public void run() {
		r = r + random.nextDouble();
	}

	public double getR() {
		return r;
	}

	@Override
	public Object getResult() {
		return r;
	}
}
