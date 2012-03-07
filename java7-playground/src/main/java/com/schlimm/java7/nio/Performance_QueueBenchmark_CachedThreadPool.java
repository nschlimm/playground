package com.schlimm.java7.nio;

import java.text.DecimalFormat;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

public class Performance_QueueBenchmark_CachedThreadPool implements Runnable {

	private static AtomicInteger counter = new AtomicInteger(0);
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
			new SynchronousQueue<Runnable>(), new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					t.setDaemon(true);
					return t;
				}
			});

	public static void main(String[] args) {
		Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000,
				new Performance_QueueBenchmark_CachedThreadPool()), 5);
		System.out.println("Mean: " + DecimalFormat.getInstance().format(average.mean()));
		System.out.println("Std. Deviation: " + DecimalFormat.getInstance().format(average.stddev()));
		System.out.println(counter.get());
		new SystemInformation().printThreadInfo(true);
	}

	class WorkerTask implements Runnable {
		@Override
		public void run() {
			counter.getAndIncrement();
		}
	}

	@Override
	public void run() {
		pool.execute(new WorkerTask());
	}
}
