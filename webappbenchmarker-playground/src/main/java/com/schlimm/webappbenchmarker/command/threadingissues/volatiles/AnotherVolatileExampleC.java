package com.schlimm.webappbenchmarker.command.threadingissues.volatiles;

import java.util.Timer;
import java.util.TimerTask;

public class AnotherVolatileExampleC {

	private volatile WorkerStatus status = new WorkerStatus();
	private long counter = 0;
	private Object mutex = new Object();

	private class Worker implements Runnable {
		@Override
		public void run() {
			synchronized (mutex) {
				status.expired = false;
				final Timer timer = new Timer();
				timer.schedule(new TimerTask() {
					public void run() {
						status.expired = true;
						System.out.println("Timer interrupted main thread ...");
						timer.cancel();
					}
				}, 1000);
				while (!status.expired) {
					counter++; // do some work
				}
				System.out.println("Main thread was interrupted by timer ...");
			};
		}
	}

	public static void main(String[] args) throws InterruptedException {
		AnotherVolatileExampleC volatileExample = new AnotherVolatileExampleC();
		Thread thread1 = new Thread(volatileExample.new Worker(), "Worker-1");
		Thread thread2 = new Thread(volatileExample.new Worker(), "Worker-2");
		thread1.start();
		thread2.start();
	}
	
 public class WorkerStatus {
	 public boolean expired = false;
 }
}
