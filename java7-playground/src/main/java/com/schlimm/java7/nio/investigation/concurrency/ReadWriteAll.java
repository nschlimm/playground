package com.schlimm.java7.nio.investigation.concurrency;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;

public class ReadWriteAll {

	public static void main(String[] args) throws InterruptedException, IOException {
		try (AsynchronousFileChannel outputfile = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"),
				StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.DELETE_ON_CLOSE)) {
			writeFully(outputfile, (ByteBuffer) ByteBuffer.allocateDirect(1048576).put(new byte[1048576]).flip(), 0L);
			readAll(outputfile, (ByteBuffer) ByteBuffer.allocateDirect(1000).put(new byte[1000]).flip(), 1000L);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static void readAll(final AsynchronousFileChannel ch, final ByteBuffer dst, long filePosition)
			throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);

		ch.read(dst, filePosition, filePosition, new CompletionHandler<Integer, Long>() {
			public void completed(Integer bytesTransferred, Long filePosition) {
				if (bytesTransferred > 0) {
					long p = filePosition + bytesTransferred;
					ch.read(dst, p, p, this);
				} else {
					latch.countDown();
				}
			}

			public void failed(Throwable exc, Long position) {
			}
		});

		latch.await();
	}
	
	static void writeFully(final AsynchronousFileChannel ch, final ByteBuffer src, long filePosition)
			throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		ch.write(src, filePosition, filePosition, new CompletionHandler<Integer, Long>() {
			public void completed(Integer bytesTransferred, Long filePosition) {
				if (src.hasRemaining()) {
					long newFilePosition = filePosition + bytesTransferred;
					ch.write(src, newFilePosition, newFilePosition, this);
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
