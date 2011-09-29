package com.schlimm.webappbenchmarker.statistic;

import java.util.*;

public class PerformanceChecker {

  private volatile boolean expired = false;
  private final long testTime;
  private final Runnable task;
  /**
   * Accuracy of test.  It must finish within 20ms of the testTime
   * otherwise we retry the test.  This could be configurable.
   */
  public static final int EPSILON = 20;
  
  private static final int MAXIMUM_ATTEMPTS = 3;

  public PerformanceChecker(long testTime, Runnable task) {
    this.testTime = testTime;
    this.task = task;
  }

  public long start() {
    long numberOfLoops;
    long start;
    int runs = 0;
    do {
      if (++runs > MAXIMUM_ATTEMPTS) {
        throw new IllegalStateException("Test not accurate");
      }
      collectGarbage();
      expired = false;
      start = System.currentTimeMillis();
      numberOfLoops = 0;
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
        public void run() {
          expired = true;
        }
      }, testTime);
      while (!expired) {
        getTask().run();
        numberOfLoops++;
      }
      start = System.currentTimeMillis() - start;
      timer.cancel();
    } while (Math.abs(start - testTime) > EPSILON);
    collectGarbage();
    return numberOfLoops;
  }

  private void collectGarbage() {
    for (int i = 0; i < 3; i++) {
      System.gc();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

public Runnable getTask() {
	return task;
}
}