package com.schlimm.bytecode.invokedynamic.linkageclasses;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings({ "unused", "rawtypes" })
public class SimpleDynamicLinkageExample {
	
	private static MethodHandle sayHello;

	private static void sayHello() {
		System.out.println("There we go!");
	}

	public static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
		MethodHandles.Lookup lookup = MethodHandles.lookup();
		Class thisClass = lookup.lookupClass(); // (who am I?)
		sayHello = lookup.findStatic(thisClass, "sayHello", MethodType.methodType(void.class));
		return new ConstantCallSite(sayHello.asType(type));
	}

}
