package com.schlimm.java7.nio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.schlimm.java7.benchmark.original.Average;
import com.schlimm.java7.benchmark.original.PerformanceChecker;
import com.schlimm.java7.benchmark.original.PerformanceHarness;

public class MyConventionalFileAccessExample implements Runnable {

	private static FileOutputStream fileos;
	{
		try {
			fileos = new FileOutputStream(new File("E:/temp/afile.out"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		Average average = new PerformanceHarness().calculatePerf(new PerformanceChecker(1000,
				new MyConventionalFileAccessExample()), 5);
		System.out.println(average.mean());
		System.out.println(average.stddev());
		fileos.flush();
		System.out.println(fileos.getChannel().size());
		fileos.close();
		new File("E:/temp/afile.out").delete();
	}

	@Override
	public void run() {
		try {
			fileos.write("Hello".getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
