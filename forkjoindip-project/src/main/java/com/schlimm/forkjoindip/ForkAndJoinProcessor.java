package com.schlimm.forkjoindip;

import java.util.List;
import java.util.concurrent.ForkJoinTask;

/**
 * Abstract class that isolates the logic how a list of input objects can be distributed across multiple threads using
 * {@link ForkJoinTask#fork()} operations.
 * 
 * @author Niklas Schlimm
 * 
 * @param <A>
 *            the original {@link ForkJoinTask} in charge of the computation (for recursive invocation)
 */
public abstract class ForkAndJoinProcessor<A extends ForkAndJoinTaskPrototype<?>> {

	protected A forkAndJoinTask;

	public ForkAndJoinProcessor(A forkAndJoinTask) {
		super();
		this.forkAndJoinTask = forkAndJoinTask;
	}

	/**
	 * Subclasses implement this method to decide how to distribute processing of the decomposed input objects.
	 * 
	 * @param decomposedInput the list of input objects to distribute across threads using {@link ForkJoinTask#fork()}
	 * @return a result of subtasks composed by using {@link ForkJoinTask#join()}
	 */
	public abstract ComposableResult<?> forkAndJoin(List<DecomposableInput<?>> decomposedInput);

}
