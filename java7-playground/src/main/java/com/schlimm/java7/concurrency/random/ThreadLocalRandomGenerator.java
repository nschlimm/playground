package com.schlimm.java7.concurrency.random;

import java.util.concurrent.ThreadLocalRandom;

public class ThreadLocalRandomGenerator implements Runnable {

	private double r;

	@Override
	public void run() {
		r = r + ThreadLocalRandom.current().nextDouble();
	}
		
}
