package com.schlimm.java7.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

/**
 * Custom cached thread group, gracefull closing
 * 
 * @author Niklas Schlimm
 * 
 */
public class Performance_Benchmark_AsynchronousFileChannel_4 implements Runnable {

	private static final String FILE_NAME = "E:/temp/afile.out";
	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static Lock closeLock = new ReentrantLock();
	private static Condition isEmpty = closeLock.newCondition();
	private static volatile boolean prepareShutdown = false;
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>());

	public static void main(String[] args) throws InterruptedException, IOException {
		outputfile = AsynchronousFileChannel.open(
				Paths.get(FILE_NAME),
				new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.DELETE_ON_CLOSE)), pool);
		try (GracefullChannelCloser closer = new GracefullChannelCloser()) {
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(500,
					new Performance_Benchmark_AsynchronousFileChannel_4()), 5);
			System.out.println("Mean: " + DecimalFormat.getInstance().format(average.mean()));
			System.out.println("Std. Deviation: " + DecimalFormat.getInstance().format(average.stddev()));
		}
	}

	static class GracefullChannelCloser implements Closeable {

		@Override
		public void close() throws IOException {
			// Closing resources
			closeLock.lock();
			try {
				prepareShutdown = true;
				if (!pool.getQueue().isEmpty()) {
					System.out.println("Waiting for signal that queue is empty ...");
					isEmpty.await();
					System.out.println("Received signal that queue is empty ... closing");
				}
			} catch (InterruptedException e) {
				Thread.interrupted();
				e.printStackTrace();
			} finally {
				closeLock.unlock();
				System.out.println("File size (bytes): " + DecimalFormat.getInstance().format(Files.size(Paths.get(FILE_NAME))));
				outputfile.close();
				System.out.println("File closed ...");
				pool.shutdown();
				try {
					pool.awaitTermination(10, TimeUnit.MINUTES);
					System.out.println("Pool closed ...");
				} catch (InterruptedException e) {
					Thread.interrupted();
					e.printStackTrace();
				}
			}
		}

	}

	private static CompletionHandler<Integer, String> defaultCompletionHandler = new CompletionHandler<Integer, String>() {
		@Override
		public void completed(Integer result, String attachment) {
			if (prepareShutdown && pool.getQueue().isEmpty()) {
				closeLock.lock();
				try {
					System.out.println("Issueing signal that queue is empty ...");
					isEmpty.signal();
				} finally {
					closeLock.unlock();
				}
			}
		}

		@Override
		public void failed(Throwable exc, String attachment) {
			exc.printStackTrace();
		}
	};

	@Override
	public void run() {
		outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5, "",
				defaultCompletionHandler);
	}
}
