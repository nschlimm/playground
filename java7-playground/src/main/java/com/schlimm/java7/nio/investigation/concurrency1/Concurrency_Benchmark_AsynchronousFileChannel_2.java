package com.schlimm.java7.nio.investigation.concurrency1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.concurrent.ConcurrentBenchmark;
import com.schlimm.java7.benchmark.original.BenchmarkRunnable;

/**
 * Custom thread group
 * 
 * @author Niklas Schlimm
 * 
 */
public class Concurrency_Benchmark_AsynchronousFileChannel_2 implements BenchmarkRunnable {

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
			System.out.println("Test: " + Concurrency_Benchmark_AsynchronousFileChannel_2.class.getSimpleName());
			outputfile = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
							StandardOpenOption.DELETE_ON_CLOSE)), pool);
			new ConcurrentBenchmark().benchmark(4, 1000, 5, new Concurrency_Benchmark_AsynchronousFileChannel_2());
		} finally {
			new SystemInformation().printThreadInfo(true);
			outputfile.close();
		}
	}

	@Override
	public void run() {
		outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
	}

	@Override
	public Object getResult() {
		return null;
	}
}
