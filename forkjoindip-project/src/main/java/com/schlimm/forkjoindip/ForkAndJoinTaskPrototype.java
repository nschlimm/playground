package com.schlimm.forkjoindip;

import java.util.concurrent.ForkJoinTask;

/**
 * Interface to be implemented to enhance {@link ForkJoinTask} and enable them to participate in the dip-Framework.
 * 
 * @author Niklas Schlimm
 * 
 * @param <A>
 *            The type of the {@link ForkJoinTask} that implements the {@link ForkAndJoinTaskPrototype} interface.
 */
public interface ForkAndJoinTaskPrototype<A> {

	/**
	 * Creates a copy of the original {@link ForkJoinTask} -> create new sub task
	 * 
	 * @param input
	 *            the input that is to be calculated by the subtask
	 * @return the output of the sub task
	 */
	A prototype(DecomposableInput<?> input);

	/**
	 * Sub classes can decide which {@link ForkAndJoinProcessor} to use for distrubuting subtasks accross multiple
	 * threads.
	 * 
	 * @return the created {@link ForkAndJoinProcessor}
	 */
	ForkAndJoinProcessor<? extends A> createForkAndJoinProcessor();

}
