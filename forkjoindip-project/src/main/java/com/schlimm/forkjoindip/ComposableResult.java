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
	 * @param firstPiece
	 *            the first peace of a composed result
	 */
	public ComposableResult(A firstPiece) {
		super();
		rawResult = firstPiece;
	}

	/**
	 * Adds another piece to the composed result
	 * 
	 * @param anotherPiece
	 *            the piece to add
	 * @return the composed result
	 */
	public abstract ComposableResult<A> compose(ComposableResult<A> anotherPiece);

	public A getRawResult() {
		return rawResult;
	}

}
