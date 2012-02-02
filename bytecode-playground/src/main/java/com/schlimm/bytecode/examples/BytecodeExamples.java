package com.schlimm.bytecode.examples;

@SuppressWarnings("unused")
public class BytecodeExamples {
	
	public void localVariableDeclarationSimpleTypes() {
		int in = 100;
		boolean bo = true;
		byte by = 0x000A;
		short sh = 123;
		long lo = 123l;
		float fl = 123.123f;
		double dou = 123.123d;
		char ch = 'c';
	}

	public void localVariableDeclarationComplexTypes() {
		Integer integer = new Integer(100);
		String string = new String("Hello");
		StringBuffer buffer = new StringBuffer("Hello");
		Object object = new Object();
	}
	
	public void localVariableDeclarationArrays() {
		String[] string = new String[2];
		string[0] = "Hello";
		string[1] = "World!";
	}
	
	public void virtualMethodCall() {
		"Hello".length();
	}
	
}
