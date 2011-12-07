package com.schlimm.java7.concurrency.forkjoin.dip;

public abstract class ComposableResult<A> {

	protected A composedResult;

	public ComposableResult(A firstPeace) {
		super();
		composedResult = firstPeace;
	}

	public abstract ComposableResult<A> compose(ComposableResult<A> anotherPeace);
	
	public A getComposedResult() {
		return composedResult;
	}
	
}
