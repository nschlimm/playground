package com.schlimm.java7.nio.threadpools;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MyPoolSizeCalculator extends PoolSizeCalculator {

	public static void main(String[] args) throws InterruptedException, InstantiationException, IllegalAccessException,
			ClassNotFoundException {
		MyPoolSizeCalculator calculator = new MyPoolSizeCalculator();
		calculator.calculateBoundaries(new BigDecimal(args[0]), new BigDecimal(args[1]));
	}

	protected long getCurrentThreadCPUTime() {
		return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
	}

	protected Runnable creatTask() {
		return new AsynchronousTask(0, "IO", 1000000);
	}
	
	protected BlockingQueue<Runnable> createWorkerQueue() {
		return new LinkedBlockingQueue<>();
	}

}
