package com.schlimm.java7.benchmark.concurrent;

import java.util.concurrent.Callable;

/**
 * This class calculates the performance of a PerformanceChecker instance, based on the given number of runs.
 * 
 * @author Heinz Kabutz, minor changes by Niklas Schlimm
 */
public class PerformanceHarness implements Callable<Average> {

	private PerformanceChecker check;
	private int runs;

	public PerformanceHarness(PerformanceChecker check, int runs) {
		super();
		this.check = check;
		this.runs = runs;
	}

	/**
	 * We calculate the average number of times that the check executed, together with the standard deviation.
	 * 
	 * @param check
	 *            The test that we want to evaluate
	 * @param runs
	 *            How many times it should be executed
	 * @return an average number of times that test could run
	 */
	private Average calculatePerf() {
		Average avg = new Average();
		for (int i = 0; i < runs; i++) {
			long count = check.start(true);
			avg.add(count);
		}
		return avg;
	}

	public long warmUp() {
		return check.start(false);
	}

	@Override
	public Average call() throws Exception {
		return calculatePerf();
	}
}
