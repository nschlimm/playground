package com.schlimm.java7.nio.threadpools;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ThreadCountCalculator {

	public static void main(String[] args) throws InterruptedException {
		for (int i = 0; i < 1000; i++) {
			AsynchronousTask task = new AsynchronousTask(i, "IO2", 30);
			task.run();
		}
		System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc();
		System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); System.gc(); 
		Thread.sleep(100);
		long start = System.nanoTime();
		long cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		for (int i = 0; i < 1000; i++) {
			AsynchronousTask task = new AsynchronousTask(i, "IO2", 30);
			task.run();
		}
		start = System.nanoTime() - start;
		cpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpu;
		long wait = start - cpu;
		BigDecimal waitTime = new BigDecimal(wait);
		BigDecimal computeTime = new BigDecimal(cpu);
		BigDecimal numberOfCPU = new BigDecimal(Runtime.getRuntime().availableProcessors());
		BigDecimal targetUtilization = new BigDecimal(args[0]);
		BigDecimal optimalthreadcount = numberOfCPU.multiply(targetUtilization).multiply(new BigDecimal(1).add(waitTime.divide(computeTime, RoundingMode.HALF_UP)));
		System.out.println("Number of CPU: "+numberOfCPU);
		System.out.println("Target utilization: "+targetUtilization);
		System.out.println("Elapsed time: "+start);
		System.out.println("Compute time: "+cpu);
		System.out.println("Wait time: "+wait);
		System.out.println("Formula: " + numberOfCPU + " * " + targetUtilization + " * (1 + " + waitTime + " / " + computeTime + ")");
		System.out.println("Optimal thread count: "+ optimalthreadcount);
	}

}
