package com.schlimm.java7.concurrency.random;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;

import com.schlimm.java7.concurrency.random.heinz.Average;
import com.schlimm.java7.concurrency.random.heinz.PerformanceChecker;
import com.schlimm.java7.concurrency.random.heinz.PerformanceHarness;

public class ConcurrentBenchmark {
	
	private void benchmark(int threadCount, int testIntervallTime, int testRuns, BenchmarkRunnable runnable) throws InterruptedException {

		PerformanceHarness harness = new PerformanceHarness(new PerformanceChecker(testIntervallTime, runnable, threadCount), testRuns);

		System.out.println(runnable.getClass().getSimpleName() + " - threadcount: " + threadCount + " - testTestIntervallTime: " + testIntervallTime + " - testRuns: " + testRuns);
		
		System.out.println("First warm-up starting ...");
		Map<String, Average> results = doTest(harness, threadCount);
		printResults(results, " - Results, first warm-up - ");
		System.out.println("Second warm-up starting ...");
		results = doTest(harness, threadCount);
		printResults(results, " - Results, second warm-up - ");
		System.out.println("Benchmark intervall starting ...");
		results = doTest(harness, threadCount);
		printResults(results, " - Benchmark intervall results - ");
						
	}

	private void printResults(Map<String, Average> results, String resultHeading) {
		System.out.println(resultHeading);
		DecimalFormat df = new DecimalFormat("#.##");
		for (String threadName : results.keySet()) {
			Average average = results.get(threadName);
			System.out.println("Thread: " + threadName);
			System.out.println("Average run count: " + df.format(average.mean()));
			System.out.println("Standard deviation: " + df.format(average.stddev()));
		}
	}

	private Map<String, Average> doTest(final PerformanceHarness harness, int threadCount)
			throws InterruptedException {
		
		final Map<String, Average> results = new ConcurrentHashMap<>();
		final CountDownLatch latch = new CountDownLatch(threadCount);

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
		
		return results;
		
	}
	
	public static void main(String[] args) throws InterruptedException {
		new ConcurrentBenchmark().benchmark(4, 5000, 5, new MathRandomGenerator());
//		harness = new PerformanceHarness(new PerformanceChecker(1000, new MathRandomGenerator(), 2), 5);
//		new ConcurrentBenchmark().benchmark(" - Math.random() - ", harness, 2);
	}
	
}
