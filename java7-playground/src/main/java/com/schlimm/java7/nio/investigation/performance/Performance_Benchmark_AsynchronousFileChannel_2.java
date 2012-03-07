package com.schlimm.java7.nio.investigation.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

/**
 * Custom thread group
 * 
 * @author Niklas Schlimm
 * 
 */
public class Performance_Benchmark_AsynchronousFileChannel_2 implements Runnable {

	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "Pooled-Thread");
					t.setDaemon(true);
					return t;
				}
			});

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			System.out.println("Test: " + Performance_Benchmark_AsynchronousFileChannel_2.class.getSimpleName());
			outputfile = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
							StandardOpenOption.DELETE_ON_CLOSE)), pool);
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000,
					new Performance_Benchmark_AsynchronousFileChannel_2()), 5);
			System.out.println("Mean: " + DecimalFormat.getInstance().format(average.mean()));
			System.out.println("Std. Deviation: " + DecimalFormat.getInstance().format(average.stddev()));
		} finally {
			new SystemInformation().printThreadInfo(true);
			outputfile.close();
		}
	}

	@Override
	public void run() {
		outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
	}
}
