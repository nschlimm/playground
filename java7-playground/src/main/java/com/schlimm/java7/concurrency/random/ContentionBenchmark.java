package com.schlimm.java7.concurrency.random;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;

import com.schlimm.java7.concurrency.random.heinz.Average;
import com.schlimm.java7.concurrency.random.heinz.PerformanceChecker;
import com.schlimm.java7.concurrency.random.heinz.PerformanceHarness;

public class ContentionBenchmark {
	
	private Map<String, Average> results = new ConcurrentHashMap<>();
	
	private void benchmark(String benchmarkName, final PerformanceHarness harness, int threadCount) throws InterruptedException {

		final CountDownLatch latch = new CountDownLatch(threadCount);
		
		harness.warmUp();
		
		final Phaser phaser = new Phaser(1){  
			   protected boolean onAdvance(int phase, int registeredParties) {  
				    return phase >= 0 || registeredParties == 0;  
			   }  
		};  
		
		for (int i = 0; i < threadCount; i++) {
			phaser.register();
			new Thread() {
				public void run() {
					do {
						phaser.arriveAndAwaitAdvance();
						try {
							Average average = harness.call();
							results.put(Thread.currentThread().getName(), average);
							latch.countDown();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					} while(!phaser.isTerminated());
				};
			}.start();
		}
		
		phaser.arriveAndDeregister();
		latch.await();
		
		System.out.println(benchmarkName);
		DecimalFormat df = new DecimalFormat("#.##");
		for (String threadName : results.keySet()) {
			Average average = results.get(threadName);
			System.out.println("Thread: " + threadName);
			System.out.println("Average run count: " + df.format(average.mean()));
			System.out.println("Standard deviation: " + df.format(average.stddev()));
		}
						
	}
	
	public static void main(String[] args) throws InterruptedException {
		PerformanceHarness harness = new PerformanceHarness(new PerformanceChecker(1000, new ThreadLocalRandomGenerator(), 2), 5);
		new ContentionBenchmark().benchmark(" - ThreadLocalRandom - ", harness, 2);
		harness = new PerformanceHarness(new PerformanceChecker(1000, new MathRandomGenerator(), 2), 5);
		new ContentionBenchmark().benchmark(" - Math.random() - ", harness, 2);
	}
	
}
