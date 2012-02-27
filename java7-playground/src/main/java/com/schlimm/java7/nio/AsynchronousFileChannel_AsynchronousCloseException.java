package com.schlimm.java7.nio;

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

@SuppressWarnings({ "unchecked", "rawtypes" })
public class AsynchronousFileChannel_AsynchronousCloseException implements Runnable {

	private static volatile boolean expired;
	private static AsynchronousFileChannel log;
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
	private static ByteBuffer content = ByteBuffer.wrap("Hello".getBytes());

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			AsynchronousFileChannel_AsynchronousCloseException task = new AsynchronousFileChannel_AsynchronousCloseException();
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
		} finally {
			log.close();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.MINUTES);
		}
	}

	@Override
	public void run() {
		log.write(content, 0, "", new CompletionHandler<Integer, String>() {

			@Override
			public void completed(Integer result, String attachment) {
				// NOP
			}

			@Override
			public void failed(Throwable exc, String attachment) {
				exc.printStackTrace();
			}
		});
	}
}
