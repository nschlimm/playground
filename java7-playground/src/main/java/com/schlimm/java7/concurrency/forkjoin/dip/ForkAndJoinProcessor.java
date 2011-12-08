package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public abstract class ForkAndJoinProcessor<A extends Prototype<?>> {
	
	protected A forkAndJoinTask;
	
	public ForkAndJoinProcessor(A forkAndJoinTask) {
		super();
		this.forkAndJoinTask = forkAndJoinTask;
	}

	public abstract ComposableResult<?> forkAndJoin(List<DecomposableInput<?>> decomposedInput);

}
