package com.schlimm.java7.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AsynchronousFileChannel_GracefullClose implements Runnable {

	private static volatile boolean expired;
	private static AsynchronousFileChannel log;
	private static Lock closeLock = new ReentrantLock();
	private static Condition isEmpty = closeLock.newCondition();
	private static volatile boolean prepareShutdown = false;
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());
	{
		try {
			log = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
							StandardOpenOption.DELETE_ON_CLOSE)), pool);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static ByteBuffer fixedlengthcontent = ByteBuffer.wrap("Hello".getBytes());
	private AtomicInteger line = new AtomicInteger(1);

	public static void main(String[] args) throws InterruptedException, IOException {
		try (GracefullChannelCloser closer = new GracefullChannelCloser()) {
			AsynchronousFileChannel_GracefullClose task = new AsynchronousFileChannel_GracefullClose();
			int numberOfLoops = 0;
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
				}
			}, 1000);
			while (!expired) {
				task.run();
				numberOfLoops++;
			}
			timer.cancel();
			System.out.println("Performed " + numberOfLoops + " write operations ...");
		} catch (Exception e) {
			e.printStackTrace();
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
				e.printStackTrace();
			} finally {
				closeLock.unlock();
			}
			log.close();
			pool.shutdown();
			try {
				pool.awaitTermination(10, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void run() {
		log.write(fixedlengthcontent, line.getAndIncrement()/**line ermitteln*/, "", new CompletionHandler<Integer, String>() {

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
				System.out.println(exc.getMessage());
			}
		});
	}
}
