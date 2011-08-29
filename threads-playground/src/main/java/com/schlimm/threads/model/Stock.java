package com.schlimm.threads.model;

public interface Stock {

	public long add(long quantity);	
	public long reduce(long quantity);
	public long getUnits();
	public String getCase();

}
