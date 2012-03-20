package com.schlimm.java7.nio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

public class Performance_Benchmark_ConventionalFileAccessExample_1 implements Runnable {

	private static FileOutputStream outputfile;
	private static byte[] content = "Hello".getBytes();

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			outputfile = new FileOutputStream(new File("E:/temp/afile.out"), true);
			Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000, new Performance_Benchmark_ConventionalFileAccessExample_1()), 5);
			System.out.println("Mean: " + average.mean());
			System.out.println("Std. Deviation: " + average.stddev());
		} finally {
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
