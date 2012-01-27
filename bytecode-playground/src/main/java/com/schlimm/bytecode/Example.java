package com.schlimm.bytecode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

class Example {
	public static CallSite mybsm(MethodHandles.Lookup callerClass, String dynMethodName, MethodType dynMethodType)
			throws Throwable {
		MethodHandle mh = callerClass.findStatic(Example.class, "IntegerOps.adder",
				MethodType.methodType(Integer.class, Integer.class, Integer.class));
		if (!dynMethodType.equals(mh.type())) {
			mh = mh.asType(dynMethodType);
		}
		return new ConstantCallSite(mh);
	}
}
