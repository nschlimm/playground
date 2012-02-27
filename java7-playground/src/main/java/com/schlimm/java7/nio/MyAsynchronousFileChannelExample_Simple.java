package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class MyAsynchronousFileChannelExample_Simple implements Runnable {

	private static volatile boolean expired;
	private static AsynchronousFileChannel log;
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>()); // May be another queue to garantee that close operation is last
													// operation
	{ // may be FIFO is correct here?
		try {
			log = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
							StandardOpenOption.DELETE_ON_CLOSE)), pool);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static ByteBuffer content = ByteBuffer.wrap("Hello".getBytes());
	private static volatile long times = 0;

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			MyAsynchronousFileChannelExample_Simple task = new MyAsynchronousFileChannelExample_Simple();
			runTask(task);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			while (!pool.getQueue().isEmpty()) { // need to do this first to ensure that all io ops are performed prior close call
				Thread.sleep(100); // find a more elegant way to wait for all operations to finish
			}
			log.close(); // find out whether close is performed as the last operation - should not cause problems with
							// fifo queue and one pool thread. Performs iocp.detachFromThreadPool which shuts channel group.
		    /** iocp.detachFromThreadPool doku
		     * For use by AsynchronousFileChannel to release resources without shutting
		     * down the thread pool.
		     */

			pool.shutdown(); // channel group is already shut down through close - now shut down thread pool
			pool.awaitTermination(10, TimeUnit.MINUTES);
		}
	}

	private static void runTask(MyAsynchronousFileChannelExample_Simple task) {
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
		System.out.println("Loops: " + numberOfLoops);
	}

	@Override
	public void run() {
		log.write(content, 0);
	}
}
