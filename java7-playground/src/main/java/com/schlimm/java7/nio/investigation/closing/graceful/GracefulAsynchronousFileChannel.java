package com.schlimm.java7.nio.investigation.closing.graceful;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Custom file channel that supports the following requirements:<br>
 * - if a write task was submitted successfully, guarantee that the write operation is executed<br>
 * - if the channel is closing then throw {@link NonWritableChannelException} to notify the client, that this channel
 * isn't writable anymore
 * 
 * @author Niklas Schlimm
 * 
 */
public class GracefulAsynchronousFileChannel extends AsynchronousFileChannel {

	/**
	 * Singleton Map for convenient instantiation.
	 */
	private static Map<String, GracefulAsynchronousFileChannel> singletonMap = new ConcurrentHashMap<>();
	private static Lock singletonLock = new ReentrantLock();

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

	/**
	 * Channel that the {@link GracefulAsynchronousFileChannel} works on.
	 */
	private volatile AsynchronousFileChannel innerChannel;

	/**
	 * The thread pool for asynchronous task execution.
	 */
	private DefensiveThreadPoolExecutor pool;

	/**
	 * The URI to the file that this channel works on.
	 */
	private URI uri;

	/**
	 * Constructor for {@link GracefulAsynchronousFileChannel}.
	 * 
	 * @param poolSize
	 *            count of worker threads that process asynchronous tasks
	 * @param workQueue
	 *            queue that holds asynchronous tasks
	 * @param fileUri
	 *            uri to the file, e.g. "file:/E:/temp/afile.out"
	 * @param options
	 *            {@link StandardOpenOption} for the file channel
	 */
	public GracefulAsynchronousFileChannel(int poolSize, LinkedBlockingQueue<Runnable> workQueue, URI fileUri,
			Set<StandardOpenOption> options) {
		super();
		this.pool = new DefensiveThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, workQueue,
				Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
		this.uri = fileUri;
		try {
			this.innerChannel = AsynchronousFileChannel.open(Paths.get(fileUri), options, pool);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The factory method for creating {@link GracefulAsynchronousFileChannel}.
	 * 
	 * @param fileUri
	 *            the {@link URI} to the file
	 * @return the created file channel
	 */
	public static GracefulAsynchronousFileChannel get(String fileUri) {
		if (singletonMap.get(fileUri) == null) {
			singletonLock.lock();
			try {
				if (singletonMap.get(fileUri) == null) {
					singletonMap.put(
							fileUri,
							new GracefulAsynchronousFileChannel(100, new LinkedBlockingQueue<Runnable>(), new URI(
									fileUri), new HashSet<>(Arrays.asList(StandardOpenOption.CREATE,
									StandardOpenOption.READ, StandardOpenOption.WRITE))));
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} finally {
				singletonLock.unlock();
			}
		}
		return singletonMap.get(fileUri);
	}

/**
 * Method that closes this file channel gracefully without loosing any data.
 */
@Override
public void close() throws IOException {
	AsynchronousFileChannel writeableChannel = innerChannel;
	System.out.println("Starting graceful shutdown ...");
	closeLock.lock();
	try {
		state = PREPARE;
		innerChannel = AsynchronousFileChannel.open(Paths.get(uri),
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
		innerChannel.close(); // close the read-only channel
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
			LinkedBlockingQueue<Runnable> workQueue, ThreadFactory factory, RejectedExecutionHandler handler) {
		super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, factory, handler);
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
					state = SHUTDOWN; // -> no other thread can issue empty-signal
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
		return innerChannel.isOpen();
	}

	@Override
	public long size() throws IOException {
		return innerChannel.size();
	}

	@Override
	public AsynchronousFileChannel truncate(long size) throws IOException {
		return innerChannel.truncate(size);
	}

	@Override
	public void force(boolean metaData) throws IOException {
		innerChannel.force(metaData);
	}

	@Override
	public <A> void lock(long position, long size, boolean shared, A attachment,
			CompletionHandler<FileLock, ? super A> handler) {
		innerChannel.lock(position, size, shared, attachment, handler);
	}

	@Override
	public Future<FileLock> lock(long position, long size, boolean shared) {
		return innerChannel.lock(position, size, shared);
	}

	@Override
	public FileLock tryLock(long position, long size, boolean shared) throws IOException {
		return innerChannel.tryLock(position, size, shared);
	}

	@Override
	public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		innerChannel.read(dst, position, attachment, handler);
	}

	@Override
	public Future<Integer> read(ByteBuffer dst, long position) {
		return innerChannel.read(dst, position);
	}

	@Override
	public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
		innerChannel.write(src, position, attachment, handler);
	}

	@Override
	public Future<Integer> write(ByteBuffer src, long position) {
		return innerChannel.write(src, position);
	}

}
