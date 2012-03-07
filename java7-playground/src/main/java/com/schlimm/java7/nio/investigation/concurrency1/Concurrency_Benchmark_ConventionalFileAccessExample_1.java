package com.schlimm.java7.nio.investigation.concurrency1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.concurrent.ConcurrentBenchmark;
import com.schlimm.java7.benchmark.original.BenchmarkRunnable;

public class Concurrency_Benchmark_ConventionalFileAccessExample_1 implements BenchmarkRunnable {

	private static FileOutputStream outputfile;
	private static byte[] content = "Hello".getBytes();

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			System.out.println("Test: " + Concurrency_Benchmark_ConventionalFileAccessExample_1.class.getSimpleName());
			outputfile = new FileOutputStream(new File("E:/temp/afile.out"));
			new ConcurrentBenchmark().benchmark(4, 1000, 5, new Concurrency_Benchmark_ConventionalFileAccessExample_1());
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

	@Override
	public Object getResult() {
		return null;
	}
}
