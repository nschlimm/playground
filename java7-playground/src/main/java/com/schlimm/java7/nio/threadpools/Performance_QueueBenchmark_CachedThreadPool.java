package com.schlimm.java7.nio.threadpools;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.schlimm.java7.benchmark.addon.SystemInformation;

public class Performance_QueueBenchmark_CachedThreadPool implements Runnable {

	private static int td = 150000;
	private static int tc = 1000;
	private static AtomicLong result = new AtomicLong();
	private static WorkerTask task = new WorkerTask();
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		}
	});

	private static void collectGarbage() {
		for (int i = 0; i < 3; i++) {
			System.gc();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	public static void doTest(int it) {
		for (int i = 0; i < it; i++) {
			pool.execute(task);
		}
	}

	public static void main(String[] args) {
		// Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(tc, new
		// Performance_QueueBenchmark_CachedThreadPool()), 5);
		// System.out.println("Mean: " + DecimalFormat.getInstance().format(average.mean()));
		// System.out.println("Std. Deviation: " + DecimalFormat.getInstance().format(average.stddev()));
		System.out.println("Warm up ...");
		doTest(1000);
		collectGarbage();
		doTest(1000);
		collectGarbage();
		System.out.println("Test intervall ...");
		doTest(tc);
		System.out.println("Collecting garbage ...");
		collectGarbage();
		System.out.println(result);
		new SystemInformation().printThreadInfo(true);
	}

	static class WorkerTask implements Runnable {
		@Override
		public void run() {
			for (int i = 1; i <= td; i++)
				result.addAndGet(ThreadLocalRandom.current().nextLong());
		}
	}

	@Override
	public void run() {
		pool.execute(task);
	}
}
