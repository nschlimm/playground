package com.schlimm.webappbenchmarker.command.monitoring;

import java.util.Timer;
import java.util.TimerTask;

public class LiveLockExample implements Runnable {

	private volatile boolean expired = false;
	private long counter = 0;
	private Object mutex = new Object();

		public void run() {
			System.out.println("Trying to acquire monitor lock. Who am I: " + Thread.currentThread().getName());
			synchronized (mutex) {
				System.out.println("Acquired lock. Who am I: " + Thread.currentThread().getName());
				connect();
				System.out.println("Releasing lock. Who am I: " + Thread.currentThread().getName());
			};
		}

		public void connect() {
			final Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
					timer.cancel();
				}
			}, 30000);
			while (!expired) {
				counter++; // do some work
			}
		}

	public static void main(String[] args) throws InterruptedException {
		Thread thread1 = new Thread(new LiveLockExample(), "Worker-1");
		Thread thread2 = new Thread(new LiveLockExample(), "Worker-2");
		thread1.start();
		thread2.start();
	}
}

