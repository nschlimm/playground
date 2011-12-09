package com.schlimm.java7.concurrency.random;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;


public class ThreadLocalRandomGenerator implements Callable<Double> {
	@Override
	public Double call() throws Exception {
		Double r = 0.0;
		for (int i = 0; i < 100; i++) {
			r = r + ThreadLocalRandom.current().nextDouble();
		}
		return r;
	}

}
