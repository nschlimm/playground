package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class MyAsynchronousFileChannelExample_CallGraph_Default implements Runnable {

	private static AsynchronousFileChannel fileChannel;

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			new MyAsynchronousFileChannelExample_CallGraph_Default().run();
		} finally {
			fileChannel.close();
		}
	}

	@Override
	public void run() {
		try {
			fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
					StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
			fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			Future<Integer> future = fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			future.get();
		} catch (InterruptedException | ExecutionException | IOException e) {
			e.printStackTrace();
		}
	}
}
