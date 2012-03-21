package com.schlimm.java7.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Graceful shutdown that garantees that submitted tasks will be processed prior shutdown and that denies submission of
 * new tasks during "prepare-shutdown" phase.
 * 
 * @author Niklas Schlimm
 * 
 */
public class SimpleChannelClose_Graceful {

	private static final String FILE_NAME = "E:/temp/afile.out";
	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static ThreadPoolExecutor pool = new DefensiveThreadPoolExecutor(100, 100, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(10000));

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		outputfile = AsynchronousFileChannel.open(
				Paths.get(FILE_NAME),
				new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.DELETE_ON_CLOSE)), pool);
		try (GracefullChannelCloser closer = new GracefullChannelCloser()) {
			for (int i = 0; i < 10000; i++) {
				outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Future<Integer> future = outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
		future.get();
	}

	/**
	 * Avoid race condition of closing thread and "last" task of the queue that issues the "isEmpty" event.
	 */
	private static Lock closeLock = new ReentrantLock();

	/**
	 * Condition to coordinate closing thread and "last" task that issues "isEmpty" event.
	 */
	private static Condition isEmpty = closeLock.newCondition();

	/**
	 * Transfers the considered channel in "prepare-shudown" phase.
	 */
	private static volatile boolean prepareShutdown = false;

	/**
	 * {@link Closeable} that closes asynchronous channel group when queue is empty. You could place the
	 * {@link #close()} method where ever you want.
	 * 
	 * @author Niklas Schlimm
	 * 
	 */
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
				System.out.println("File size (bytes): "
						+ DecimalFormat.getInstance().format(Files.size(Paths.get(FILE_NAME))));
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

	/**
	 * Custom {@link ThreadPoolExecutor} that supports graceful closing of asynchronous I/O channels.
	 */
	private static class DefensiveThreadPoolExecutor extends ThreadPoolExecutor {

		public DefensiveThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}

		/**
		 * Issues a signal if queue is empty after task processing was completed.
		 */
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			if (prepareShutdown && pool.getQueue().isEmpty()) {
				closeLock.lock();
				try {
					System.out.println("Issueing signal that queue is empty ...");
					isEmpty.signal();
				} finally {
					closeLock.unlock();
				}
			}
			super.afterExecute(r, t);
		}

		/**
		 * Throws an {@link IllegalStateException} if clients try to submit tasks in prepare-shutdown phase.
		 */
		@Override
		public void execute(Runnable command) {
			if (prepareShutdown)
				throw new IllegalStateException("Prepare-State - no tasks can be submitted!");
			super.execute(command);
		}

	}

}
