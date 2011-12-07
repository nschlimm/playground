package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import com.schlimm.java7.concurrency.forkjoin.dip.ComposableResult;
import com.schlimm.java7.concurrency.forkjoin.dip.ComputationActivity;
import com.schlimm.java7.concurrency.forkjoin.dip.DecomposableInput;

@SuppressWarnings("rawtypes")
public class ForkJoinTaskExample_Generic extends RecursiveTask<ComposableResult> {

	/**
	 * Serial UID
	 */
	private static final long serialVersionUID = -3017444953400657168L;

	private DecomposableInput input;

	private ComputationActivity activity;

	public ForkJoinTaskExample_Generic(DecomposableInput input, ComputationActivity activity) {
		super();
		this.input = input;
		this.activity = new PricingEngine();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ComposableResult<?> compute() {

		if (input.computeDirectly()) { // task is small enough to compute linear in this thread
			return activity.compute(input);
		}

		List<DecomposableInput> decomposableInputs = input.decompose();
		return forkAndJoin(decomposableInputs);

	}

	@SuppressWarnings("unchecked")
	private ComposableResult<?> forkAndJoin(List<DecomposableInput> decomposableInputs) {
		ForkJoinTaskExample_Generic f1 = new ForkJoinTaskExample_Generic(decomposableInputs.get(0), activity);
		f1.fork(); 
		ForkJoinTaskExample_Generic f2 = new ForkJoinTaskExample_Generic(decomposableInputs.get(1), activity);
		ComposableResult<?> first = f2.compute();
		ComposableResult<?> second = first.compose(f1.join());
		return second;
	}

	public static void main(String[] args) {
		List<Proposal> proposalsList = new ArrayList<>();
		proposalsList.add(new Proposal("Niklas", "Schlimm", "7909","AAL", true, true, true));
		proposalsList.add(new Proposal("Andreas", "Fritz", "0005", "432", true, true, true));
		proposalsList.add(new Proposal("Christian", "Toennessen", "0583", "442", true, true, true));
		proposalsList.add(new Proposal("Frank","Hinkel", "4026", "AAA", true, true, true));
		ListOfProposals proposals = new ListOfProposals(proposalsList);
		ForkJoinTaskExample_Generic task = new ForkJoinTaskExample_Generic(proposals, new PricingEngine());
		ForkJoinPool pool = new ForkJoinPool();
		System.out.println(new Date());
		System.out.println(pool.invoke(task));
		System.out.println(new Date());
	}
}
