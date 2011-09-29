package com.schlimm.webappbenchmarker.statistic;

import java.util.*;

/**
 * Calculates the mean and average of a series of numbers.  Not the
 * most efficient algorithm, but fast enough.
 *
 * @author Heinz Kabutz
 */
public class Average {
  /** The set of values stored as doubles.  Autoboxed. */
  private Collection<Double> values = new ArrayList<Double>();

  /**
   * Add a new value to the series.  Changes the values returned by
   * mean() and stddev().
   * @param value the new value to add to the series.
   */
  public void add(double value) {
    values.add(value);
  }

  /**
   * Calculate and return the mean of the series of numbers.
   * Throws an exception if this is called before the add() method.
   * @return the mean of all the numbers added to the series.
   * @throws IllegalStateException if no values have been added yet.
   * Otherwise we could cause a NullPointerException.
   */
  public double mean() {
    int elements = values.size();
    if (elements == 0) throw new IllegalStateException("No values");
    double sum = 0;
    for (double value : values) {
      sum += value;
    }
    return sum / elements;
  }

  /**
   * Calculate and return the standard deviation of the series of
   * numbers.  See Stats 101 for more information...
   * Throws an exception if this is called before the add() method.
   * @return the standard deviation of numbers added to the series.
   * @throws IllegalStateException if no values have been added yet.
   * Otherwise we could cause a NullPointerException.
   */
  public double stddev() {
    double mean = mean();
    double stddevtotal = 0;
    for (double value : values) {
      double dev = value - mean;
      stddevtotal += dev * dev;
    }
    return Math.sqrt(stddevtotal / values.size());
  }
}