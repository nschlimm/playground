package com.schlimm.java7.concurrency.forkjoin.dip;

import java.util.List;

public interface DecomposableInput {
	
	boolean computeDirectly();
	List<DecomposableInput> disassemble();

}
