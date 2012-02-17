package com.schlimm.java7.concurrency.random.heinzoriginal;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.BenchmarkRunnable;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;
import com.schlimm.java7.concurrency.random.generators.MathRandomGenerator;
import com.schlimm.java7.concurrency.random.generators.ThreadLocalRandomGenerator;

public class FirstBenchmark {

	private static List<BenchmarkRunnable> benchmarkTargets = Arrays.asList((BenchmarkRunnable)new ThreadLocalRandomGenerator(), (BenchmarkRunnable)new MathRandomGenerator());

	public static void main(String[] args) {
		DecimalFormat df = new DecimalFormat("#.##");
		for (BenchmarkRunnable runnable : benchmarkTargets) {
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000, runnable), 5);
			System.out.println("Benchmark target: " + runnable.getClass().getSimpleName());
			System.out.println("Mean execution count: " + df.format(average.mean()));
			System.out.println("Standard deviation: " + df.format(average.stddev()));
			System.out.println("To avoid dead code optimization: " + runnable.getResult());
		}
	}
	
}
