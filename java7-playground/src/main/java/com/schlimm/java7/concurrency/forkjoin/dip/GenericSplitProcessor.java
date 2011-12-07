package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public class GenericSplitProcessor implements ForkAndJoinProcessor<GenericRecursiveTask> {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComposableResult<?> forkAndJoin(GenericRecursiveTask forkAndJoinTask, List<DecomposableInput> decomposedInput) {
		GenericRecursiveTask f1 = forkAndJoinTask.prototype(decomposedInput.get(0));
		f1.fork();
		GenericRecursiveTask f2 = forkAndJoinTask.prototype(decomposedInput.get(1));
		ComposableResult<?> first = f2.compute();
		ComposableResult<?> second = first.compose(f1.join());
		return second;
	}

}
