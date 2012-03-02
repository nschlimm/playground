package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Performance_AsynchronousFileChannel_OOM_JavaHeapSpace {

	private static AsynchronousFileChannel fileChannel;
	private static int position;
	private static ExecutorService pool = Executors.newFixedThreadPool(1);
	
	public static void main(String[] args) throws InterruptedException, IOException {
		fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"),
				new HashSet<StandardOpenOption>(Arrays.asList(StandardOpenOption.WRITE, StandardOpenOption.CREATE)), pool );
		try {
			for (int i = 0; i < 10000000; i++) {
				fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), (position++)*5);
			}
		} finally {
			fileChannel.close();
		}
	}

}
