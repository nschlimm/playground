package com.schlimm.bytecode.invokedynamic.linkageclasses;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings({ "unused", "rawtypes" })
public class SingleParameterDynamicLinkageExample {
	
	private static MethodHandle printArgs;

	private static void printArgs(int par) {
		System.out.println("The passed int parameter value is: " + par);
	}

	public static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		Class thisClass = lookup.lookupClass(); // (who am I?)
		printArgs = lookup.findStatic(thisClass, "printArgs", MethodType.methodType(void.class, int.class));
		return new ConstantCallSite(printArgs.asType(type));
	}

}
