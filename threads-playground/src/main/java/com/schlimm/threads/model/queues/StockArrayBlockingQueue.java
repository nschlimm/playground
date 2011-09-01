package com.schlimm.threads.model.queues;

import java.util.concurrent.ArrayBlockingQueue;

public class StockArrayBlockingQueue<E> extends StockBlockingQueue<E> {

	public StockArrayBlockingQueue(int capacity) {
		stock = new ArrayBlockingQueue<E>(capacity);
	}
	
	@Override
	public QueuingStock<E> prototype(int capacity) {
		return new StockArrayBlockingQueue<E>(capacity);
	}

}
