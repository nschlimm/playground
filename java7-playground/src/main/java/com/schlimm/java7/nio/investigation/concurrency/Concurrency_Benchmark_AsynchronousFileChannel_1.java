package com.schlimm.java7.nio.investigation.concurrency;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;

import com.schlimm.java7.benchmark.addon.SystemInformation;
import com.schlimm.java7.benchmark.concurrent.ConcurrentBenchmark;
import com.schlimm.java7.benchmark.original.BenchmarkRunnable;

public class Concurrency_Benchmark_AsynchronousFileChannel_1 implements BenchmarkRunnable {

	private static AsynchronousFileChannel outputfile;
	private static AtomicInteger globalFilePosition = new AtomicInteger(0);
	private final static String stringToAdd = "Hello";

	public Concurrency_Benchmark_AsynchronousFileChannel_1() {
		super();
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		try {
			System.out.println("Test: " + Concurrency_Benchmark_AsynchronousFileChannel_1.class.getSimpleName());
			outputfile = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), StandardOpenOption.WRITE,
					StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
			new ConcurrentBenchmark().benchmark(4, 1000, 5, new Concurrency_Benchmark_AsynchronousFileChannel_1());
		} finally {
			new SystemInformation().printThreadInfo(true);
			outputfile.close();
		}
	}

	@Override
	public void run() {
		outputfile.write(ByteBuffer.wrap(stringToAdd.getBytes()), globalFilePosition.getAndAdd(stringToAdd.length()));
	}

	@Override
	public Object getResult() {
		return null;
	}
}
