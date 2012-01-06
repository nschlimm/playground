package com.schlimm.java7.concurrency.random.heinzoriginal;

import java.util.concurrent.ThreadLocalRandom;

public class RandomTest {

	public static void main(String[] args) {
		System.out.println(ThreadLocalRandom.current().nextDouble());
		System.out.println(ThreadLocalRandom.current().nextDouble());
	}
	
}
