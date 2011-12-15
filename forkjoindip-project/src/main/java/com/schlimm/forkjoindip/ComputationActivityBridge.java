package com.schlimm.forkjoindip;

import java.util.concurrent.ForkJoinTask;

/**
 * Bridge to the component that implements the actual computation. This could be a pricing engine when calculating
 * offers.
 * 
 * @author Niklas Schlimm
 * 
 * @param <A>
 *            The input into the computation
 * @param <B>
 *            The output of the computation. Typically equivalent to the result type returned by the future's of a
 *            {@link ForkJoinTask}
 */
public abstract class ComputationActivityBridge<A, B> {

	/**
	 * Subclasses implement this method to perform the actual computation
	 * 
	 * @param input
	 *            the input into the computation
	 * @return the output of the computation, that can be composed into the complete result of the {@link ForkJoinTask}
	 */
	public abstract ComposableResult<B> compute(DecomposableInput<A> input);

}
