package com.schlimm.java7.consurrency.random;

import java.util.concurrent.Callable;


public class MathRandomGenerator implements Callable<Double> {

	@Override
	public Double call() throws Exception {
		Double r = 0.0;
		for (int i = 0; i < 100; i++) {
			r = r + Math.random();
		}
		return r;
	}

}
