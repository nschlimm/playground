package com.schlimm.java7.consurrency.random;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalRandomContentionBenchmark {

	public static void main(String[] args) throws InterruptedException, ExecutionException, InstantiationException, IllegalAccessException {
		Class<?> runnable = ThreadLocalRandomGenerator.class;
		performContentionBenchmark(runnable);
		runnable = MathRandomGenerator.class;
		performContentionBenchmark(runnable);
	}

	@SuppressWarnings("unchecked")
	private static void performContentionBenchmark(Class<?> callable) throws InstantiationException,
			IllegalAccessException, InterruptedException, ExecutionException {
		System.out.println("**********************************************");
		System.out.println("*********** Contention Benchmark *************");
		System.out.println("**********************************************");
		System.out.println("** Callable Class is: " + callable.getName());
		for (int j = 0; j < 5; j++) {
			ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
			ExecutorService pool = Executors.newFixedThreadPool(20);
			List<Future<Double>> futures = new ArrayList<>();
			for (int i = 0; i < 100000; i++) {
				futures.add(pool.submit(((Callable<Double>)callable.newInstance())));
			}
			for (Future<Double> future : futures) {
				future.get();
			}
			ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(
					ManagementFactory.getThreadMXBean().getAllThreadIds());
			Average waitedCount = new Average();
			Average waitedTime = new Average();
			for (ThreadInfo threadInfo : infos) {
				if (threadInfo.getThreadName().startsWith("pool-")) {
					waitedCount.add(threadInfo.getWaitedCount());
					waitedTime.add(threadInfo.getWaitedTime());
				}
			}
			System.out.println("Thread Contention Benchmark Results - Iteration " + j + ":");
			System.out.println("Average wait count mean: " + waitedCount.mean());
			System.out.println("Average wait count devi: " + Math.round(waitedCount.stddev()*100)/100);
			System.out.println("Average wait time mean: " + waitedTime.mean());
			System.out.println("Average wait time devi: " + Math.round(waitedTime.stddev()*100)/100);
			pool.shutdown();
		}
	}

}
