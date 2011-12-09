package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ThreadLocalRandom;

public class ForkJoinTaskExample extends RecursiveTask<List<Double>> {

	private List<Double> max;

	private static final long serialVersionUID = -2703342063482619328L;

	public ForkJoinTaskExample(List<Double> max) {
		super();
		this.max = max;
	}

	@Override
	protected List<Double> compute() {

		if (max.size() == 1) { // task is small enough to compute linear in this thread
			return Arrays.asList(computeDirectly(max.get(0)));
		}

		// task is to large for one thread to execute efficiently, split the task
		// make sure splitting of tasks makes sense! tasks must not be too small ...

		int split = max.size() / 2;

		ForkJoinTaskExample f1 = new ForkJoinTaskExample(max.subList(0, split));
		f1.fork(); // generate task for some other thread that can execute on some other CPU
		ForkJoinTaskExample f2 = new ForkJoinTaskExample(max.subList(split, max.size()));
		List<Double> newList = new ArrayList<>();
		newList.addAll(f2.compute()); // compute this sub task in the current thread
		newList.addAll(f1.join()); // join the results of the other sub task
		return newList;

	}

	private Double computeDirectly(Double max) {
		return ThreadLocalRandom.current().nextDouble(max);
	}

	public static void main(String[] args) {
		// Calculate four proposals
		ForkJoinTaskExample task = new ForkJoinTaskExample(Arrays.asList(100.0, 200.0, 300.0, 400.0));
		ForkJoinPool pool = new ForkJoinPool();
		System.out.println(new Date());
		System.out.println(pool.invoke(task));
		System.out.println(new Date());
	}

}
