package com.schlimm.webappbenchmarker.command.threadingissues.contention;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;

public class AnotherContentionExampleA {

	@SuppressWarnings("static-access")
	public static void main(String[] args) throws InterruptedException {
		ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(true);
		SomeResource resource = new SomeResource();
		Thread thread1 = new Thread(new SomeClientThread(resource), "Worker-1");
		Thread thread2 = new Thread(new SomeClientThread(resource), "Worker-2");
		thread1.start();
		thread2.start();
		Thread.currentThread().sleep(10000);
		ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(new long[]{thread1.getId(), thread2.getId()});
		for (ThreadInfo threadInfo : infos) {
			System.out.println(threadInfo.getThreadName());
			System.out.println(threadInfo.getBlockedCount());
			System.out.println(threadInfo.getBlockedTime());
			System.out.println(threadInfo.getWaitedCount());
			System.out.println(threadInfo.getWaitedTime());
		}
		thread1.interrupt();
		thread2.interrupt();
		System.out.println(resource.getCounter());
	}
}
