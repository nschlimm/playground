package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;
import java.util.concurrent.RecursiveTask;

@SuppressWarnings("rawtypes")
public class GenericRecursiveTask extends RecursiveTask<ComposableResult> implements Prototype<GenericRecursiveTask, DecomposableInput> {

	/**
	 * Serial UID
	 */
	private static final long serialVersionUID = -3017444953400657168L;

	private DecomposableInput input;

	private ComputationActivity activity;
	
	private ForkAndJoinProcessor<GenericRecursiveTask> processor;

	public GenericRecursiveTask(DecomposableInput input, ComputationActivity activity, ForkAndJoinProcessor<GenericRecursiveTask> processor) {
		super();
		this.input = input;
		this.activity = activity;
		this.processor = processor;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected ComposableResult<?> compute() {

		if (input.computeDirectly()) { // task is small enough to compute linear in this thread
			return activity.compute(input);
		}

		List<DecomposableInput> decomposedInputs = input.decompose();
		return processor.forkAndJoin(this, decomposedInputs);

	}

	@Override
	public GenericRecursiveTask prototype(DecomposableInput input) {
		return new GenericRecursiveTask(input, activity, processor);
	}
	
}
