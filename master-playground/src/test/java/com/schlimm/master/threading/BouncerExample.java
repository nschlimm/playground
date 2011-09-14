package com.schlimm.master.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class BouncerExample {

	public static void main(String[] args) throws InterruptedException {
		long time = System.currentTimeMillis();
		final Semaphore bouncer = new Semaphore(25); // allows 25 threads to access secured area
		ExecutorService pool = Executors.newCachedThreadPool();
		for (int i = 0; i < 100; i++) {
			pool.execute(new Runnable() {
				@Override
				public void run() {
					try {
						bouncer.acquire();
						try {
							Thread.sleep(1000);
						} finally {
							bouncer.release(); // releases a permit, returning it to the semaphore (next thread can enter)
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
			});
		}
		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);
		time = System.currentTimeMillis() - time;
		System.out.println(time + "ms");
	}
	
}
