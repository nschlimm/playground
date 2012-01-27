package com.schlimm.bytecode;

import org.objectweb.asm.util.ASMifier;

public class CreatingASMGeneratorCode {

	public static void main(String[] args) throws Exception {
		ASMifier.main(new String[]{"com.schlimm.bytecode.HelloWorld"});
	}
}
