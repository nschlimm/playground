package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinTaskExample_Plain extends RecursiveTask<List<Map<String, Double>>> {

	private List<Proposal> proposals;

	private static final long serialVersionUID = -2703342063482619328L;

	public ForkJoinTaskExample_Plain(List<Proposal> proposals) {
		super();
		this.proposals = proposals;
	}

	@Override
	protected List<Map<String, Double>> compute() {

		if (proposals.size() == 1) { // task is small enough to compute linear in this thread
			return Arrays.asList(computeDirectly(proposals.get(0)));
		}

		// task is to large for one thread to execute efficiently, split the task
		// make sure splitting of tasks makes sense! tasks must not be too small ...

		int split = proposals.size() / 2;

		ForkJoinTaskExample_Plain f1 = new ForkJoinTaskExample_Plain(proposals.subList(0, split));
		f1.fork(); // generate task for some other thread that can execute on some other CPU
		ForkJoinTaskExample_Plain f2 = new ForkJoinTaskExample_Plain(proposals.subList(split, proposals.size()));
		List<Map<String, Double>> newList = new ArrayList<>();
		newList.addAll(f2.compute()); // compute this sub task in the current thread
		newList.addAll(f1.join()); // join the results of the other sub task
		return newList;

	}

	private Map<String, Double> computeDirectly(Proposal proposal) {
		return new PricingEngine().calculatePrices(proposal);
	}

	public static void main(String[] args) {
		// Calculate four proposals
		ForkJoinTaskExample_Plain task = new ForkJoinTaskExample_Plain(Arrays.asList(new Proposal("Niklas", "Schlimm", "7909",
				"AAL", true, true, true), new Proposal("Andreas", "Fritz", "0005", "432", true, true, true),
				new Proposal("Christian", "Toennessen", "0583", "442", true, true, true), new Proposal("Frank",
						"Hinkel", "4026", "AAA", true, true, true)));
		ForkJoinPool pool = new ForkJoinPool();
		System.out.println(new Date());
		System.out.println(pool.invoke(task));
		System.out.println(new Date());
	}

}
