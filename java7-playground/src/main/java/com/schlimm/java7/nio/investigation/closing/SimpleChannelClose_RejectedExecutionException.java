package com.schlimm.java7.nio.investigation.closing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple shutdown of asynchronous channel with custom thread pool and non-deamon threads.
 * 
 * @author Niklas Schlimm
 * 
 */
public class SimpleChannelClose_RejectedExecutionException {

	private static final String FILE_NAME = "E:/temp/afile.out";
	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		outputfile = AsynchronousFileChannel.open(
				Paths.get(FILE_NAME),
				new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE,
						StandardOpenOption.DELETE_ON_CLOSE)), pool);
		List<Future<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 10000; i++) {
			futures.add(outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5));
		}
		pool.shutdown();
		pool.awaitTermination(60, TimeUnit.SECONDS);
		outputfile.close();
		for (Future<Integer> future : futures) {
			System.out.println(future.get());
		}
	}
}
