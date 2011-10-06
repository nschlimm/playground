package com.schlimm.webappbenchmarker.command.threadingissues;

import java.util.Timer;
import java.util.TimerTask;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class ContentionSynchronized implements ServerCommand {

	private volatile boolean expired;
	private long counter = 0;
	private Object mutext = new Object();

	@Override
	public Object[] execute(Object... arguments) {
		synchronized (mutext) {
			expired = false;
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
				}
			}, 1000);
			while (!expired) {
				counter++; // do some work
			}
			timer.cancel();
		};
		return new Object[] { counter, expired };
	}

	private class Worker implements Runnable {
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				execute();
			}
		}
	}

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws InterruptedException {
		ContentionSynchronized contentionSynchronized = new ContentionSynchronized();
		Thread thread1 = new Thread(contentionSynchronized.new Worker(), "Worker-1");
		Thread thread2 = new Thread(contentionSynchronized.new Worker(), "Worker-2");
		thread1.start();
		thread2.start();
		Thread.currentThread().sleep(60000);
		thread1.interrupt();
		thread2.interrupt();
	}

}
