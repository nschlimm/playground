package com.schlimm.java7.nio.investigation.closing;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
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
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static ThreadPoolExecutor pool = new DefensiveThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>(10000));
	/** System wide log file */
	public static AsynchronousFileChannel GLOBAL_LOG_FILE;

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		try {
			GLOBAL_LOG_FILE = AsynchronousFileChannel.open(
					Paths.get(FILE_NAME),
					new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
							StandardOpenOption.DELETE_ON_CLOSE)), pool);
			for (int i = 0; i < 10000; i++) {
				GLOBAL_LOG_FILE.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			closeGracefully();
		}
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
	 * {@link #closeGracefully()} method where ever you want.
	 * 
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static void closeGracefully() throws IOException, InterruptedException, ExecutionException {
		// Closing resources
		closeLock.lock();
		try {
			prepareShutdown = true;
			// TODO: make sure write to asyncchannel does not actually write or throws runtime exception
			if (!pool.getQueue().isEmpty()) { // only wait if queue isn't empty
				System.out.println("Waiting for signal that queue is empty ...");
				isEmpty.await();
				System.out.println("Received signal that queue is empty ... closing");
			}
		} catch (InterruptedException e) {
			Thread.interrupted();
			e.printStackTrace();
		} finally {
			closeLock.unlock();
			GLOBAL_LOG_FILE.force(false);
			long size = Files.size(Paths.get(FILE_NAME));
			System.out.println("File size (bytes): " + size);
			GLOBAL_LOG_FILE.close();
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

	/**
	 * Custom {@link ThreadPoolExecutor} that supports graceful closing of asynchronous I/O channels.
	 */
	private static class DefensiveThreadPoolExecutor extends ThreadPoolExecutor {

		private Semaphore bouncer = new Semaphore(1);

		public DefensiveThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}

		/**
		 * Issues a signal if queue is empty after task processing was completed.
		 */
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			if (prepareShutdown) {
				closeLock.lock(); // wait here until main thread awaits signal (and thereby releases close lock)
				if (pool.getQueue().isEmpty()) {
					try {
						System.out.println("Issueing signal that queue is empty ...");
						isEmpty.signal();
					} finally {
						closeLock.unlock();
					}
				}
			}
			super.afterExecute(r, t);
		}
	}

}
