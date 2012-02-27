package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class AsynchronousFileChannel_OOMException {

	private static AsynchronousFileChannel fileChannel;
	
	{
		try {
			fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.READ,
					StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		new AsynchronousFileChannel_OOMException();
		try {
			for (int i = 0; i < 10000000; i++) {
				fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			}
		} finally {
			fileChannel.close();
		}
	}

}
