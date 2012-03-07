package com.schlimm.java7.benchmark.original;

/**
 * This class calculates the performance of a PerformanceChecker
 * instance, based on the given number of runs.
 *
 * @author Heinz Kabutz
 */
public class PerformanceHarness {
  /**
   * We calculate the average number of times that the check
   * executed, together with the standard deviation.
   * @param check The test that we want to evaluate
   * @param runs How many times it should be executed
   * @return an average number of times that test could run
   */
  public Average calculatePerf(PerformanceChecker check, int runs) {
    Average avg = new Average();
    System.out.println("Warming up ...");
    check.start(); check.start();
    System.out.println("Starting test intervall ...");
    for(int i=0; i < runs; i++) {
      long count = check.start();
      avg.add(count);
    }
    return avg;
  }
}
