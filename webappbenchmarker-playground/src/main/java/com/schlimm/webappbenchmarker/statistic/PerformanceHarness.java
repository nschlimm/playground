package com.schlimm.webappbenchmarker.statistic;

import java.lang.management.ManagementFactory;

/**
 * This class calculates the performance of a PerformanceChecker instance, based on the given number of runs.
 * 
 * @author Heinz Kabutz
 */
public class PerformanceHarness {
	/**
	 * We calculate the average number of times that the check executed, together with the standard deviation.
	 * 
	 * @param check
	 *            The test that we want to evaluate
	 * @param runs
	 *            How many times it should be executed
	 * @return an average number of times that test could run
	 */
	public Statistics calculatePerf(PerformanceChecker check, int runs) {
		Statistics avg = new Statistics();
		// first we warm up the hotspot compiler
		check.start();
		check.start();
		System.out.println("Starting test harness for: " + check.getTask().getClass());
		long jitBefore = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
		long classesLoadedPrior = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
		for (int i = 0; i < runs; i++) {
			long count = check.start();
			avg.add(count);
		}
		long jitAfter = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
		long classesLoadedAfter = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
		System.out.println("Stopped test harness for: " + check.getTask().getClass());
		avg.setJitTimeBeforeHarness(jitBefore);
		avg.setJitTimeAfterHarness(jitAfter);
		avg.setClassesLoadedBeforeHarness(classesLoadedPrior);
		avg.setClassesLoadedAfterHarness(classesLoadedAfter);
		return avg;
	}
}