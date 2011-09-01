package com.schlimm.threads.model.queues;

public interface QueuingStock<E> {

	void add(E quantity) throws InterruptedException;	
	void reduce(E quantity) throws InterruptedException;
	void tryAdd(E quantity) throws InterruptedException;	
	void tryReduce(E quantity) throws InterruptedException;
	long getUnits() throws InterruptedException;
	QueuingStock<E> prototype(int initial);

}
