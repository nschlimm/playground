package com.schlimm.java7.nio.investigation.closing.graceful;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class MyLoggingClient {
	private static AtomicInteger fileindex = new AtomicInteger(0);
	private static final String FILE_URI = "file:/E:/temp/afile.out";

	public static void main(String[] args) throws IOException {
		new Thread(new Runnable() { // arbitrary thread that writes stuff into an asynchronous I/O data sink

					@Override
					public void run() {
						try {
							for (;;) {
								GracefulAsynchronousFileChannel.get(FILE_URI).write(ByteBuffer.wrap("Hello".getBytes()),
										fileindex.getAndIncrement() * 5);
							}
						} catch (NonWritableChannelException e) {
							System.out.println("Deal with the fact that the channel was closed asynchronously ... "
									+ e.toString());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();

		Timer timer = new Timer(); // asynchronous channel closer
		timer.schedule(new TimerTask() {
			public void run() {
				try {
					GracefulAsynchronousFileChannel.get(FILE_URI).close();
					long size = Files.size(Paths.get("E:/temp/afile.out"));
					System.out.println("Expected file size (bytes): " + (fileindex.get() - 1) * 5);
					System.out.println("Actual file size (bytes): " + size);
					if (size == (fileindex.get() - 1) * 5)
						System.out.println("No write operation was lost!");
					Files.delete(Paths.get("E:/temp/afile.out"));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}, 1000);
		

	}
}
