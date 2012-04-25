package com.schlimm.java7.nio.investigation.concurrency;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

public class UnderstandTheContract {

	
	private static AsynchronousFileChannel outputfile;

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			outputfile = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE);
			writeFully(outputfile, (ByteBuffer)ByteBuffer.allocateDirect(1048576).put(new byte[1048576]).flip(), 0L);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			outputfile.close();
		}
	}
	
	static void writeFully(final AsynchronousFileChannel ch, final ByteBuffer src, long position) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);

		ch.write(src, position, position, new CompletionHandler<Integer, Long>() {
			public void completed(Integer result, Long position) {
				int n = result;
				if (src.hasRemaining()) {
					long p = position + n;
					ch.write(src, p, p, this); // will use this thread
				} else {
					latch.countDown();
				}
			}

			public void failed(Throwable exc, Long position) {
			}
		});
		
		latch.await();
		
	}
	
}
