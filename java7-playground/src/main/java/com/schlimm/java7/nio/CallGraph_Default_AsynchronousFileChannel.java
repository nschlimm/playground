package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CallGraph_Default_AsynchronousFileChannel {

	private static AsynchronousFileChannel fileChannel;

	public static void main(String[] args) throws InterruptedException, IOException, ExecutionException {
		try {
			fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
					StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
			Future<Integer> future = fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), 0L);
			future.get();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			fileChannel.close();
		}
	}
}
