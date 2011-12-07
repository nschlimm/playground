package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public interface ForkAndJoinProcessor<A extends Prototype<?,?>> {
	
	@SuppressWarnings("rawtypes")
	public abstract ComposableResult<?> forkAndJoin(A forkAndJoinTask, List<DecomposableInput> decomposedInput);

}
