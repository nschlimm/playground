package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class MyAsynchronousFileChannelExample implements Runnable {
	
	private static AsynchronousFileChannel fileChannel;
	{
		try {
			fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), 
					StandardOpenOption.READ, StandardOpenOption.WRITE,
					StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    public static void main(String[] args) throws InterruptedException, IOException {
    	Thread thread1 = new Thread(new MyAsynchronousFileChannelExample());
    	Thread thread2 = new Thread(new MyAsynchronousFileChannelExample());
    	thread1.start();
    	thread2.start();
    	Thread.sleep(10000);
    	thread1.interrupt();
    	thread2.interrupt();
    	fileChannel.close();
    }

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			try {
				fileChannel.write(ByteBuffer.wrap("Hello".getBytes()), fileChannel.size());
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
}
