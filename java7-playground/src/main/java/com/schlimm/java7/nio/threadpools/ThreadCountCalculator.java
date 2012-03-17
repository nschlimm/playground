package com.schlimm.java7.nio.threadpools;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.schlimm.java7.nio.threadpools.ThreadPoolPerformance.AsynchronousTask;

public class ThreadCountCalculator {

	public static void main(String[] args) throws InterruptedException {
		for (int i = 0; i < 1000; i++) {
			AsynchronousTask task = new AsynchronousTask(i, "IO2", 100);
			task.run();
		}
		System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc();
		System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); 
		Thread.sleep(100);
		long start = System.nanoTime();
		long cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		for (int i = 0; i < 1000; i++) {
			AsynchronousTask task = new AsynchronousTask(i, "IO2", 100);
			task.run();
		}
		start = System.nanoTime() - start;
		cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpu;
		System.out.println(start);
		System.out.println(cpu);
		long wait = start - cpu;
		System.out.println(wait);
		BigDecimal waitbd = new BigDecimal(wait);
		BigDecimal cpubd = new BigDecimal(cpu);
		BigDecimal countcpubd = new BigDecimal(Runtime.getRuntime().availableProcessors());
		BigDecimal utilbd = new BigDecimal(args[0]);
		BigDecimal result1 = new BigDecimal(1).add(waitbd.divide(cpubd, RoundingMode.HALF_UP));
		BigDecimal result = countcpubd.multiply(utilbd).multiply(result1);
		System.out.println(result);
	}

}
