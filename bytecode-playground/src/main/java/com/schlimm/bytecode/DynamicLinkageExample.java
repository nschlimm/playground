package com.schlimm.bytecode;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings({ "unused", "rawtypes" })
public class DynamicLinkageExample {

	static void test() throws Throwable {
		// THE FOLLOWING LINE IS PSEUDOCODE FOR A JVM INSTRUCTION
	}

	private static void printArgs() {
		System.out.println("Das sinn wa!!");
	}

	private static MethodHandle printArgs;

	public static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
		System.out.println("Entered call site bootstrap ....");
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		Class thisClass = lookup.lookupClass(); // (who am I?)
		printArgs = lookup.findStatic(thisClass, "printArgs", MethodType.methodType(void.class));
		return new ConstantCallSite(printArgs.asType(type));
	}

}
