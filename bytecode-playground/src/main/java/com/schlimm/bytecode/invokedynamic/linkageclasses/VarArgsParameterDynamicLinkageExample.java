package com.schlimm.bytecode.invokedynamic.linkageclasses;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings({ "unused", "rawtypes" })
public class VarArgsParameterDynamicLinkageExample {

	private static MethodHandle printArgs;
	
	private static void printArgs(Object... par) {
		for (int i = 0; i < par.length; i++) {
			System.out.println(par[i]);
		}
	}

	public static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		Class thisClass = lookup.lookupClass();
		printArgs = lookup.findStatic(thisClass, "printArgs", MethodType.methodType(void.class, Object[].class));
		return new ConstantCallSite(printArgs.asType(type));
	}

}
