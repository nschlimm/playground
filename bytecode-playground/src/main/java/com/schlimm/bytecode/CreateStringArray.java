package com.schlimm.bytecode;

import org.objectweb.asm.util.ASMifier;

public class CreateStringArray {
	
	public static void createArray() {
		String[] strings = new String[2];
		strings[0] = "Hello";
		strings[1] = "World!";
	}
	
	public static void main(String[] args) throws Exception {
		ASMifier.main(new String[]{"com.schlimm.bytecode.CreateStringArray"});
	}

}
