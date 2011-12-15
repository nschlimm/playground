package com.schlimm.forkjoindip;

import java.util.List;
import java.util.concurrent.ForkJoinTask;

/**
 * Abstract class that wraps the input of a {@link ForkJoinTask} task.
 * 
 * @author Niklas Schlimm
 * 
 * @param <A>
 *            the input type of that {@link ForkJoinTask}
 */
public abstract class DecomposableInput<A> {

	/**
	 * Raw input. E.g. a list of offers to calculate.
	 */
	protected A rawInput;

	/**
	 * Standard constructor.
	 * 
	 * @param rawInput
	 *            the raw input to the {@link ForkJoinTask}
	 */
	public DecomposableInput(A rawInput) {
		super();
		this.rawInput = rawInput;
	}

	public A getRawInput() {
		return rawInput;
	}

	/**
	 * Subclasses implement this method to decide if the raw input can be computed directly.
	 * 
	 * @return true if raw input is small enough to compute directly in this thread
	 */
	public abstract boolean computeDirectly();

	/**
	 * Subclasses implement this method to decompose the input into smaller peaces if task is too large to be computed
	 * directly.
	 * 
	 * @return a decomposition of the raw input that can be calculated in different threads using
	 *         {@link ForkJoinTask#fork()}
	 */
	public abstract List<DecomposableInput<A>> decompose();

}
