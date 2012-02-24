package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class MyAsynchronousFileChannelExample implements Runnable {

	private static AsynchronousFileChannel fileChannel;
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());
	{
		try {
			fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"),
					new HashSet(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE)), pool);
			// fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
			// StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static ByteBuffer content = ByteBuffer.wrap("Hello".getBytes());

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			MyAsynchronousFileChannelExample ex = new MyAsynchronousFileChannelExample();
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(500, ex), 1);
			System.out.println(average.mean());
			System.out.println(average.stddev());
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			while (!pool.getQueue().isEmpty()) {
				Thread.sleep(100);
			}
			fileChannel.close();
			pool.shutdown();
			pool.awaitTermination(10, TimeUnit.MINUTES);
		}
	}

	@Override
	public void run() {
		fileChannel.write(content, 0);
	}
}
