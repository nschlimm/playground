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
			int numberOfLoops = 0;
			MyAsynchronousFileChannelExample_Simple task = new MyAsynchronousFileChannelExample_Simple();
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
			System.out.println("Last time: " + times);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			while (!pool.getQueue().isEmpty()) {
				Thread.sleep(100); // find a more elegant way to wait for all operations to finish
			}
			log.close(); // find out whether close is performed as the last operation - should not cause problems with
							// fifo queue and one pool thread. Performas detachFromThreadPool which disconnects channel
							// from channel group and thread pool.
			pool.shutdown(); // channel group is already shut down through close
			pool.awaitTermination(10, TimeUnit.MINUTES);
		}
	}

	@Override
	public void run() {
		log.write(content, 0);
	}
}
