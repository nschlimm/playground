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

/**
 * Demonstrate gracefull shutdown of asynchronous file channel with custom thread pool and non-deamon threads.
 * 
 * @author Niklas Schlimm
 * 
 */
public class GracefulChannelClose {

	private static final String FILE_NAME = "E:/temp/afile.out";
	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static Lock closeLock = new ReentrantLock();
	private static Condition isEmpty = closeLock.newCondition();
	private static volatile boolean prepareShutdown = false;
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());

	public static void main(String[] args) throws InterruptedException, IOException {
		outputfile = AsynchronousFileChannel.open(
				Paths.get(FILE_NAME),
				new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.DELETE_ON_CLOSE)), pool);
		try (GracefullChannelCloser closer = new GracefullChannelCloser()) {
			for (int i = 0; i < 1000; i++) {
				outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5, "",
						defaultCompletionHandler);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * {@link Closeable} that closes asynchronous channel group when queue is empty.
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
	 * Issues a signal if queue is empty after task processing was completed.
	 */
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

}
