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
import java.util.concurrent.ThreadFactory;

/**
 * Custom thread group setting threads as deamon so they terminate correctly.
 * 
 * @author Niklas Schlimm
 *
 */
public class CallGraph_CustomPool_2_AsynchronousFileChannel {

	private static AsynchronousFileChannel fileChannel;
	private static ExecutorService pool = Executors.newFixedThreadPool(50, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
       }
    });

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		try {
			fileChannel = AsynchronousFileChannel.open(
					Paths.get("E:/temp/afile.out"),
					new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.READ, StandardOpenOption.WRITE,
							StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE)), pool);
			Future<Integer> future = fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			future.get();
		} finally {
			fileChannel.close();
		}
	}

}
