package com.schlimm.forkjoindip;

import java.util.List;
import java.util.concurrent.ForkJoinTask;

/**
 * Generic implementation of a scenario where an input is decomposed into two parts that get distributed accross two
 * threads using {@link ForkJoinTask#fork()}
 * 
 * @author Niklas Schlimm
 * 
 */
public class GenericSplitProcessor extends ForkAndJoinProcessor<GenericRecursiveTask> {

	public GenericSplitProcessor(GenericRecursiveTask forkAndJoinTask) {
		super(forkAndJoinTask);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComposableResult forkAndJoin(List<DecomposableInput<?>> decomposedInput) {
		if (decomposedInput.size() != 2)
			throw new IllegalArgumentException("Expected exactly two list entries!");
		GenericRecursiveTask f1 = forkAndJoinTask.prototype(decomposedInput.get(0));
		f1.fork();
		GenericRecursiveTask f2 = forkAndJoinTask.prototype(decomposedInput.get(1));
		ComposableResult first = f2.compute();
		ComposableResult second = first.compose(f1.join());
		return second;
	}

}
