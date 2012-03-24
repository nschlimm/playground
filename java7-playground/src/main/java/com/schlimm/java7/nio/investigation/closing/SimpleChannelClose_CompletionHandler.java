package com.schlimm.java7.nio.investigation.closing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleChannelClose_CompletionHandler {

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
		for (int i = 0; i < 10000; i++) {
			outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5, "", defaultCompletionHandler);
		}
		outputfile.close();
		pool.shutdown();
		pool.awaitTermination(60, TimeUnit.SECONDS);
	}

	private static CompletionHandler<Integer, String> defaultCompletionHandler = new CompletionHandler<Integer, String>() {
		@Override
		public void completed(Integer result, String attachment) {
			// NOP
		}

		@Override
		public void failed(Throwable exc, String attachment) {
			System.out.println("Do something to avoid data loss ...");
		}
	};

}

