package com.schlimm.java7.concurrency.forkjoin.pricingengine;

import java.util.ArrayList;
import java.util.List;

import com.schlimm.java7.concurrency.forkjoin.dip.DecomposableInput;

public class ListOfProposals implements DecomposableInput {

	private List<DecomposableInput> proposals;
	
	public ListOfProposals(List<DecomposableInput> proposals) {
		super();
		this.proposals = proposals;
	}

	@Override
	public boolean computeDirectly() {
		return proposals.size()==1;
	}

	@Override
	public List<DecomposableInput> disassemble() {
		int split = proposals.size() / 2;
		List<DecomposableInput> decomposedListOfProposals = new ArrayList<>();
		decomposedListOfProposals.add(new ListOfProposals(proposals.subList(0, split)));
		decomposedListOfProposals.add(new ListOfProposals(proposals.subList(split, proposals.size())));
		return decomposedListOfProposals;
	}

	public List<DecomposableInput> getProposals() {
		return proposals;
	}

}
