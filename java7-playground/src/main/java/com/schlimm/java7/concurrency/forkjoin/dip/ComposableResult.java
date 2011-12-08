package com.schlimm.java7.concurrency.forkjoin.dip;

public abstract class ComposableResult<A> {

	protected A rawResult;

	public ComposableResult(A firstPeace) {
		super();
		rawResult = firstPeace;
	}

	public abstract ComposableResult<A> compose(ComposableResult<A> anotherPeace);
	
	public A getRawResult() {
		return rawResult;
	}
	
}
