package com.schlimm.java7.nio.investigation.closing.graceful;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalAsynchronousFileChannel extends AsynchronousFileChannel {

	private static GlobalAsynchronousFileChannel singleton = null;
	private static Lock singletonLock = new ReentrantLock();
	private static final String FILE_URI = "file:/E:/temp/afile.out";

	/**
	 * Lifecycle of graceful shutdown.
	 */
	public static final int RUNNING = 0;
	public static final int PREPARE = 1;
	public static final int SHUTDOWN = 2;

	/**
	 * Transfers the considered channel in "prepare-shudown" phase.
	 */
	private volatile int state = RUNNING;

	/**
	 * Avoid race condition of closing thread and "last" task of the queue that issues the "isEmpty" event.
	 */
	private Lock closeLock = new ReentrantLock();

	/**
	 * Condition to coordinate closing thread and "last" task that issues "isEmpty" event.
	 */
	private Condition isEmpty = closeLock.newCondition();

	private volatile AsynchronousFileChannel channel;

	private ThreadPoolExecutor pool;

	private GlobalAsynchronousFileChannel() {
		super();
		this.pool = new DefensiveThreadPoolExecutor(100, 100, 0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
		try {
			this.channel = AsynchronousFileChannel
					.open(Paths.get(new URI(FILE_URI)),
							new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE,
									StandardOpenOption.CREATE)), pool);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public static AsynchronousFileChannel get() {
		if (singleton == null) {
			singletonLock.lock();
			try {
				if (singleton == null) {
					singleton = new GlobalAsynchronousFileChannel();
				}
			} finally {
				singletonLock.unlock();
			}
		}
		return singleton;
	}

	@Override
	public void close() throws IOException {
		AsynchronousFileChannel writeableChannel = channel;
		System.out.println("Starting graceful shutdown ...");
		closeLock.lock();
		try {
			state = PREPARE;
			channel = AsynchronousFileChannel.open(Paths.get(new URI(FILE_URI)),
					new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.READ)), pool);
			System.out.println("Channel blocked for write access ...");
			if (!pool.getQueue().isEmpty()) {
				System.out.println("Waiting for signal that queue is empty ...");
				isEmpty.await();
				System.out.println("Received signal that queue is empty ... closing");
			} else {
				System.out.println("Don't have to wait, queue is empty ...");
			}
		} catch (InterruptedException e) {
			Thread.interrupted();
			throw new RuntimeException("Interrupted on awaiting Empty-Signal!", e);
		} catch (Exception e) {
			throw new RuntimeException("Unexpected error" + e);
		} finally {
			closeLock.unlock();
			writeableChannel.force(false);
			writeableChannel.close(); // close the writable channel
			channel.close(); // close the read-only channel
			System.out.println("File closed ...");
			pool.shutdown(); // allow clean up tasks from previous close() operation to finish safely
			try {
				pool.awaitTermination(1, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				Thread.interrupted();
				throw new RuntimeException("Could not terminate thread pool!", e);
			}
			System.out.println("Pool closed ...");
		}
	}

	/**
	 * Custom {@link ThreadPoolExecutor} that supports graceful closing of asynchronous I/O channels.
	 */
	private class DefensiveThreadPoolExecutor extends ThreadPoolExecutor {

		public DefensiveThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
				BlockingQueue<Runnable> workQueue) {
			super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
		}

		/**
		 * "Last" task issues a signal that queue is empty after task processing was completed.
		 */
		@Override
		protected void afterExecute(Runnable r, Throwable t) {
			if (state == PREPARE) {
				closeLock.lock(); // only one thread will pass when closer thread is awaiting signal
				try {
					if (getQueue().isEmpty() && state < SHUTDOWN) {
						System.out.println("Issueing signal that queue is empty ...");
						isEmpty.signal();
						state = SHUTDOWN; // -> no other thread issue empty-signal
					}
				} finally {
					closeLock.unlock();
				}
			}
			super.afterExecute(r, t);
		}
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public long size() throws IOException {
		return channel.size();
	}

	@Override
	public AsynchronousFileChannel truncate(long size) throws IOException {
		return channel.truncate(size);
	}

	@Override
	public void force(boolean metaData) throws IOException {
		channel.force(metaData);
	}

	@Override
	public <A> void lock(long position, long size, boolean shared, A attachment,
			CompletionHandler<FileLock, ? super A> handler) {
		channel.lock(position, size, shared, attachment, handler);
	}

	@Override
	public Future<FileLock> lock(long position, long size, boolean shared) {
		return channel.lock(position, size, shared);
	}

	@Override
	public FileLock tryLock(long position, long size, boolean shared) throws IOException {
		return channel.tryLock(position, size, shared);
	}

	@Override
	public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		channel.read(dst, position, attachment, handler);
	}

	@Override
	public Future<Integer> read(ByteBuffer dst, long position) {
		return channel.read(dst, position);
	}

	@Override
	public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		channel.write(src, position, attachment, handler);
	}

	@Override
	public Future<Integer> write(ByteBuffer src, long position) {
		return channel.write(src, position);
	}

}
