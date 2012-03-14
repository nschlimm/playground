package com.schlimm.java7.nio.threadpools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolPerformance {

	private static FileOutputStream outputfile;
	private static int td = 0;
	private static int tc = 0;
	private static String tasktype;
	private static ExecutorService pool;
	private static final String AFILE_OUT = "afile.out";

	public static void doTest(int it) throws InterruptedException, ExecutionException {
		List<Future<?>> results = new ArrayList<>();
		for (int i = 0; i < it; i++) {
			results.add(pool.submit(new AsynchronousTask(tasktype, td)));
		}
		for (Future<?> future : results) {
			future.get();
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
		try {
			init(args);
			long start = System.currentTimeMillis();
			doTest(tc);
			start = System.currentTimeMillis() - start;
			ThreadMXBean thread = ManagementFactory.getThreadMXBean();
			String output = String.format("%1$s;%2$s;%3$s;%4$s;%5$s;%6$s;%7$s;%8$s;%9$s%n", tasktype, tc, td,
					((ThreadPoolExecutor) pool).getLargestPoolSize(), thread.getTotalStartedThreadCount(),
					thread.getPeakThreadCount(), thread.getDaemonThreadCount(), thread.getThreadCount(), start);
			outputfile.write(output.getBytes());
			System.out.println(output);
		} catch (RuntimeException e) {
			e.printStackTrace();
		} finally {
			pool.shutdown();
			pool.awaitTermination(60, TimeUnit.SECONDS);
			if (args[1].equals("IO"))
				new File(AFILE_OUT).delete();
		}
	}

	private static void init(String[] args) throws FileNotFoundException {
		outputfile = new FileOutputStream(new File(args[0]), true);
		tasktype = args[1];
		switch (args[2]) {
		case "CACHED":
			pool = Executors.newCachedThreadPool();
			break;

		case "FIXED":
			pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			break;

		default:
			throw new IllegalArgumentException("Unknown pool type requested! " + args[2]);
		}
		tc = Integer.valueOf(args[3]);
		td = Integer.valueOf(args[4]);
	}

	static class AsynchronousTask implements Runnable {

		private int td;
		private String type;
		public int result;

		public AsynchronousTask(String type, int td) {
			super();
			this.td = td;
			this.type = type;
		}

		@Override
		public void run() {
			switch (type) {
			case "SLEEP":
				sleep();
				break;

			case "COMPUTE":
				compute();
				break;

			case "IO":
				write();
				break;

			default:
				break;
			}
		}

		private void write() {
			try (FileOutputStream fileos = new FileOutputStream(new File(AFILE_OUT), true)) {
				fileos.write(String.format("%1$-" + td + "s", "s").getBytes());
			} catch (NumberFormatException | IOException e) {
				e.printStackTrace();
			}
		}

		private void compute() {
			for (int i = 1; i <= td; i++)
				result += ThreadLocalRandom.current().nextInt();
		}

		private void sleep() {
			try {
				Thread.sleep(td);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}
}
