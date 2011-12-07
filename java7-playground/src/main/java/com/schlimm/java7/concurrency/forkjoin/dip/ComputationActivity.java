package com.schlimm.java7.concurrency.forkjoin.dip;

public interface ComputationActivity {
	
	ComposableResult<?> compute(DecomposableInput<?> task);

}
