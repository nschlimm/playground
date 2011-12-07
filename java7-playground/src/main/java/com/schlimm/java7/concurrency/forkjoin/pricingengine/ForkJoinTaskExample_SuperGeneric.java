package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import com.schlimm.java7.concurrency.forkjoin.dip.GenericRecursiveTask;
import com.schlimm.java7.concurrency.forkjoin.dip.GenericSplitProcessor;

public class ForkJoinTaskExample_SuperGeneric {

	public static void main(String[] args) {
		List<Proposal> proposalsList = new ArrayList<>();
		proposalsList.add(new Proposal("Niklas", "Schlimm", "7909","AAL", true, true, true));
		proposalsList.add(new Proposal("Andreas", "Fritz", "0005", "432", true, true, true));
		proposalsList.add(new Proposal("Christian", "Toennessen", "0583", "442", true, true, true));
		proposalsList.add(new Proposal("Frank","Hinkel", "4026", "AAA", true, true, true));
		ListOfProposals proposals = new ListOfProposals(proposalsList);
		GenericRecursiveTask task = new GenericRecursiveTask(proposals, new PricingEngine(), new GenericSplitProcessor());
		ForkJoinPool pool = new ForkJoinPool();
		System.out.println(pool.invoke(task));
	}
}
