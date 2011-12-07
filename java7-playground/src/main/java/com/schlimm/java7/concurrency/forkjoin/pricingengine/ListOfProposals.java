package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.List;

import com.schlimm.java7.concurrency.forkjoin.dip.DecomposableInput;

public class ListOfProposals extends DecomposableInput<List<Proposal>> {

	public ListOfProposals(List<Proposal> proposals) {
		super(proposals);
	}

	@Override
	public boolean computeDirectly() {
		return composition.size()==1;
	}

	@Override
	public List<DecomposableInput<List<Proposal>>> decompose() {
		int split = composition.size() / 2;
		List<DecomposableInput<List<Proposal>>> decomposedListOfProposals = new ArrayList<>();
		decomposedListOfProposals.add(new ListOfProposals(composition.subList(0, split)));
		decomposedListOfProposals.add(new ListOfProposals(composition.subList(split, composition.size())));
		return decomposedListOfProposals;
	}

}
