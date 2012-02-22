package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MyAsynchronousFileChannelExample_CallGraph_CustomPool implements Runnable {

	private static AsynchronousFileChannel fileChannel;
	private static ExecutorService pool = Executors.newFixedThreadPool(50);

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			new MyAsynchronousFileChannelExample_CallGraph_CustomPool().run();
		} finally {
			pool.shutdown();
			fileChannel.close();
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void run() {
		try {
			fileChannel = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet(Arrays.asList(StandardOpenOption.READ, StandardOpenOption.WRITE,
							StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE)), pool);
			Future<Integer> future = fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			future.get();
		} catch (InterruptedException | ExecutionException | IOException e) {
			e.printStackTrace();
		}
	}
}
