package com.schlimm.java7.concurrency.forkjoin.dip;

public interface ComposableResult {

	ComposableResult assemble(ComposableResult result);
	
}
