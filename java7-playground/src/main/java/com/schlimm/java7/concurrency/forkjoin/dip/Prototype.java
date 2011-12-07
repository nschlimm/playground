package com.schlimm.java7.concurrency.forkjoin.dip;

public interface Prototype<A, B> {
	
	A prototype(B input);

}
