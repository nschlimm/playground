package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

public class AnotherVolatileExampleB {

	private boolean expired = false;
	private long counter = 0;
	private Object mutex = new Object();

	private class Worker implements Runnable {
		@Override
		public void run() {
			final Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
					System.out.println("Timer interrupted main thread ...");
					timer.cancel();
				}
			}, 1000);
			boolean tmpExpired = false;
			while (!tmpExpired) {
				synchronized (mutex) {
					tmpExpired = expired;
				}
				counter++; // do some work
			}
			System.out.println("Main thread was interrupted by timer ...");
		}
	}

	public static void main(String[] args) throws InterruptedException {
		AnotherVolatileExampleB volatileExample = new AnotherVolatileExampleB();
		Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
		thread1.start();
	}
}
