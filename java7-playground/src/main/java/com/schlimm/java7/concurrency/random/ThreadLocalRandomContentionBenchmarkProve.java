package com.schlimm.java7.concurrency.random;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThreadLocalRandomContentionBenchmarkProve {

	public static void main(String[] args) throws InterruptedException, ExecutionException, InstantiationException, IllegalAccessException {
		Class<? extends Callable<Long>> runnable = ThreadLocalRandomGenerator.class;
		performContentionBenchmark(2, runnable);
		runnable = MathRandomGenerator.class;
		performContentionBenchmark(2, runnable);
	}

	private static void performContentionBenchmark(int threadCount, Class<? extends Callable<Long>> callable) throws InstantiationException,
			IllegalAccessException, InterruptedException, ExecutionException {
		System.out.println("** Callable Class is: " + callable.getName());
		DecimalFormat df = new DecimalFormat("#.##");
		Average average = new Average();
		for (int j = 0; j < 2; j++) {
			doWork(threadCount, callable, average);
		}
		average = new Average();
		for (int j = 0; j < 5; j++) {
			doWork(threadCount, callable, average);
		}
		System.out.println("Mean execution count: " + df.format(average.mean()));
		System.out.println("Standard deviation: " + df.format(average.stddev()));
	}

	@SuppressWarnings("static-access")
	private static void doWork(int threadCount, Class<? extends Callable<Long>> callable, Average average) throws InstantiationException,
			IllegalAccessException, InterruptedException, ExecutionException {
		ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		List<Future<Long>> futures = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			futures.add(pool.submit(callable.newInstance()));
		}
		Thread.currentThread().sleep(3000);
		pool.shutdownNow();
		pool.awaitTermination(10, TimeUnit.SECONDS);
		long i = 0;
		for (Future<Long> future : futures) {
			i += future.get();
		}
		Thread.sleep(3000); // Pause to calm down
		average.add(i);
	}

}
