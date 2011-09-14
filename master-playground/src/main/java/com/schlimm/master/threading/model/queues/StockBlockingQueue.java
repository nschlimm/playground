package com.schlimm.master.threading.model.queues;

import java.util.concurrent.BlockingQueue;

public abstract class StockBlockingQueue<E> implements QueuingStock<E> {
	
	protected BlockingQueue<E> stock = null;
	
	public void add(E quantity) throws InterruptedException {
		stock.put(quantity);
	}
	
	public void reduce(E quantity) throws InterruptedException {
		stock.take();
	}

	public long getUnits() {
		return stock.size();
	}

	@Override
	public void tryAdd(E quantity) throws InterruptedException {
		stock.offer(quantity);
	}

	@Override
	public void tryReduce(E quantity) throws InterruptedException {
		stock.poll();
	}

}
