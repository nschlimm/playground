package com.schlimm.java7.nio.investigation.performance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

public class Performance_Benchmark_ConventionalFileAccessExample_1 implements Runnable {

	private static FileOutputStream outputfile;
	private static byte[] content = "Hello".getBytes();

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			System.out.println("Test: " + Performance_Benchmark_ConventionalFileAccessExample_1.class.getSimpleName());
			outputfile = new FileOutputStream(new File("E:/temp/afile.out"), true);
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000, new Performance_Benchmark_ConventionalFileAccessExample_1()), 5);
			System.out.println("Mean: " + DecimalFormat.getInstance().format(average.mean()));
			System.out.println("Std. Deviation: " + DecimalFormat.getInstance().format(average.stddev()));
		} finally {
			new SystemInformation().printThreadInfo(true);
			outputfile.close();
			new File("E:/temp/afile.out").delete();
		}
	}

	@Override
	public void run() {
		try {
			outputfile.write(content); // append content
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
