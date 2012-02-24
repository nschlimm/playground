package com.schlimm.java7.nio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MyConventionalFileAccessExample_Simple implements Runnable {

	private static volatile boolean expired;
	private static FileOutputStream fileos;
	{
		try {
			fileos = new FileOutputStream(new File("E:/temp/afile.out"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		int numberOfLoops = 0;
		MyConventionalFileAccessExample_Simple task = new MyConventionalFileAccessExample_Simple();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			public void run() {
				expired = true;
			}
		}, 1000);
		while (!expired) {
			task.run();
			numberOfLoops++;
		}
		timer.cancel();
		System.out.println("Loops: " + numberOfLoops);
		fileos.close();
		new File("E:/temp/afile.out").delete();
	}

	@Override
	public void run() {
		try {
			fileos.write("Hello".getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
