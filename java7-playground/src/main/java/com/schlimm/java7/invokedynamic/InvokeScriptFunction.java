package com.schlimm.java7.invokedynamic;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import javax.script.*;

import sun.org.mozilla.javascript.internal.NativeObject;

public class InvokeScriptFunction {
	static void test() throws Throwable {
	    // THE FOLLOWING LINE IS PSEUDOCODE FOR A JVM INSTRUCTION
	}
	private static void printArgs(Object... args) {
	  System.out.println(java.util.Arrays.deepToString(args));
	}
	private static MethodHandle printArgs;

	private static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name, MethodType type) {
	  // ignore caller and name, but match the type:
	  return new ConstantCallSite(printArgs.asType(type));
	}

    public static void main(String[] args) throws Throwable {
  	  MethodHandles.Lookup lookup = MethodHandles.lookup();
  	  Class thisClass = lookup.lookupClass();  // (who am I?)
  		try {
  			printArgs = lookup.findStatic(thisClass,
  			      "printArgs", MethodType.methodType(void.class, Object[].class));
  		} catch (NoSuchMethodException | IllegalAccessException e) {
  			// TODO Auto-generated catch block
  			e.printStackTrace();
  		}
  		
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");

        // JavaScript code in a String
        String script = "function mySillyObject() { this.sayHello = hello; }" +
        		"function hello(name) { print('Hello, ' + name); }" +
        		"function init() {" +
        		"return new mySillyObject();};";
        // evaluate script
        engine.eval(script);
        engine.createBindings();

        // javax.script.Invocable is an optional interface.
        // Check whether your script engine implements or not!
        // Note that the JavaScript engine implements Invocable interface.
        Invocable inv = (Invocable) engine;

        // invoke the global function named "hello"
        NativeObject object = (NativeObject)inv.invokeFunction("init" );
        MethodHandle getTarget, invoker, result;
        getTarget = MethodHandles.publicLookup().bind(object, "sayHello", MethodType.methodType(MethodHandle.class));
//        invoker = MethodHandles.exactInvoker(this.type());
//        result = MethodHandles.foldArguments(invoker, getTarget);
        
        MethodHandle handle = lookup.bind(object, "sayHello", MethodType.methodType(void.class));
        handle.invokeExact();
        
    }
}