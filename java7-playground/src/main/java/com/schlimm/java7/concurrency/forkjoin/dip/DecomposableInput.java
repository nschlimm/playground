package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public abstract class DecomposableInput<A> {
	
	protected A rawInput;
	
	public DecomposableInput(A rawInput) {
		super();
		this.rawInput = rawInput;
	}

	public A getRawInput() {
		return rawInput;
	}
	
	public abstract boolean computeDirectly();
	public abstract List<DecomposableInput<A>> decompose();

}
