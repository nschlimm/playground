package com.schlimm.webappbenchmarker.command.threadingissues;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.schlimm.webappbenchmarker.command.ServerCommand;

public class ContentionJava5Locks implements ServerCommand {

	private Lock lock = new ReentrantLock();
	private volatile boolean expired;
	private long counter = 0;

	@Override
	public Object[] execute(Object... arguments) {
		lock.lock();
		try {
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
		} finally {
			lock.unlock();
		}
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
		ContentionJava5Locks contentionJava5Locks = new ContentionJava5Locks();
		Thread thread1 = new Thread(contentionJava5Locks.new Worker(), "Worker-1");
		Thread thread2 = new Thread(contentionJava5Locks.new Worker(), "Worker-2");
		thread1.start();
		thread2.start();
		Thread.currentThread().sleep(30000);
		thread1.interrupt();
		thread2.interrupt();
	}

}
