package com.schlimm.webappbenchmarker.command.threadingissues.contention;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SomeResource {

	private Lock lock = new ReentrantLock(true);
	private volatile boolean expired;
	private long counter = 0;

	public Object[] access(Object... someArguments) {
		lock.lock();
		try {
			expired = false;
			final Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				public void run() {
					expired = true;
					timer.cancel();
				}
			}, 1000);
			while (!expired) {
				counter++; // do some work
			}
		} finally {
			lock.unlock();
		}
		return new Object[] { getCounter(), expired };
	}

	public long getCounter() {
		return counter;
	}
}

