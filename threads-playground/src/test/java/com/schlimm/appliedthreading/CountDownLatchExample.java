package com.schlimm.appliedthreading;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountDownLatchExample {

	public static void main(String[] args) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(3);
		ExecutorService pool = Executors.newFixedThreadPool(5);
		for (int i = 0; i < 5; i++) {
			pool.execute(new Runnable() {
				@Override
				public void run() {
					System.out.println("Waiting ...");
					try {
						latch.await();	// puts thread in wait state until latch is counted down to zero - interruptibly
					} catch (InterruptedException e) { // actual interrupted state is now false ('cause its still running! enforces state model)
						Thread.currentThread().interrupt();
						return;
					}
					System.out.println("... finished");
				}
			});
			Thread.sleep(5000);
			latch.countDown(); // when latch reaches 0 the first three threads are released -> ... finnished
							   // subsequent threads do not await anymore on latch.await 'cause the latch is already at zero
		}
		pool.shutdown();
	}
}
