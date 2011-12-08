package com.schlimm.java7.concurrency.forkjoin.dip;

public interface ComputationActivity<A, B> {
	
	ComposableResult<B> compute(DecomposableInput<A> input);

}
