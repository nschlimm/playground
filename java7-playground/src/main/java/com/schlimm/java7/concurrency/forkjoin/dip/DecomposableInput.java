package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public abstract class DecomposableInput<A> {
	
	protected A composition;
	
	public DecomposableInput(A composition) {
		super();
		this.composition = composition;
	}

	public A getComposition() {
		return composition;
	}
	
	public abstract boolean computeDirectly();
	public abstract List<DecomposableInput<A>> decompose();

}
