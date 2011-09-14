package com.schlimm.master.threading.model;

public interface Stock {

	void add(long quantity) throws InterruptedException;	
	void reduce(long quantity) throws InterruptedException;
	long getUnits() throws InterruptedException;
	Stock prototype(int initial);

}
