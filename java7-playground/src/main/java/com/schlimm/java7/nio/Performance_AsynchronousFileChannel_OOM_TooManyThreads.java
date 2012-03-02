package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Performance_AsynchronousFileChannel_OOM_TooManyThreads {

	private static AsynchronousFileChannel fileChannel;
	private static int position;
	
	public static void main(String[] args) throws InterruptedException, IOException {
		fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
				StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
		try {
			for (int i = 0; i < 10000000; i++) {
				fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), (position++)*5);
			}
		} finally {
			fileChannel.close();
		}
	}

}
