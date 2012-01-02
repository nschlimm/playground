package com.schlimm.java7.concurrency.random;


public class MathRandomGenerator implements Runnable {

	private double r;

	@Override
	public void run() {
		r = r + Math.random();
	}
}
