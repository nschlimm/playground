package com.schlimm.forkjoindip;

import java.util.concurrent.ForkJoinTask;

/**
 * Abstract class that wraps the result of a fork and join task.
 * 
 * @author Niklas Schlimm
 * 
 * @param <A>
 *            The result type returned by the future's of a {@link ForkJoinTask}
 */
public abstract class ComposableResult<A> {

	/**
	 * The raw {@link ForkJoinTask} output
	 */
	protected A rawResult;

	/**
	 * Standard constructor.
	 * 
	 * @param firstPeace
	 *            the first peace of a composed result
	 */
	public ComposableResult(A firstPeace) {
		super();
		rawResult = firstPeace;
	}

	/**
	 * Adds another peace to the composed result
	 * 
	 * @param anotherPeace
	 *            the peace to add
	 * @return the composed result
	 */
	public abstract ComposableResult<A> compose(ComposableResult<A> anotherPeace);

	public A getRawResult() {
		return rawResult;
	}

}
