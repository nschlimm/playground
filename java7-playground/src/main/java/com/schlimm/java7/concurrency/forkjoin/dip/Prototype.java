package com.schlimm.java7.concurrency.forkjoin.dip;

public interface Prototype<A> {
	
	A prototype(DecomposableInput<?> input);
	ForkAndJoinProcessor<? extends A> createForkAndJoinProcessor();
	
}
