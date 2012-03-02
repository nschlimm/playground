package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default thread group
 * 
 * @author Niklas Schlimm
 * 
 */
public class Performance_Profiling_AsynchronousFileChannel_1 {

	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger fileindex = new AtomicInteger(0);

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			outputfile = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
			for (int i = 0; i < 100000; i++) {
				run();
			}
		} finally {
			outputfile.close();
		}
	}

	public static void run() {
		outputfile.write(ByteBuffer.wrap("Hello".getBytes()), fileindex.getAndIncrement() * 5);
	}
}
