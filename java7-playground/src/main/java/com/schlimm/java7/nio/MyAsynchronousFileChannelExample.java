package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceHarness;
import com.schlimm.java7.benchmark.original.PerformanceChecker;

public class MyAsynchronousFileChannelExample implements Runnable {

	private static AsynchronousFileChannel fileChannel;
	private static ExecutorService pool = Executors.newCachedThreadPool();
	{
		try {
			fileChannel = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet(Arrays.asList(StandardOpenOption.READ, StandardOpenOption.WRITE,
							StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE)), pool);
			// fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
			// StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000,
					new MyAsynchronousFileChannelExample()), 5);
			System.out.println(average.mean());
			System.out.println(average.stddev());
			System.out.println(fileChannel.size());
		} finally {
			pool.shutdown();
			fileChannel.close();
		}
	}

	@Override
	public void run() {
		try {
			fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
