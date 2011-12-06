package com.schlimm.java7.coin;

import java.lang.reflect.InvocationTargetException;

public class HandleExceptionsJava7ExampleMultiCatch {

	public static void main(String[] args) {
		try {
			Class<?> string = Class.forName("java.lang.String");
			string.getMethod("length").invoke("test");
		} catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			// do something!!!
		}
	}
}
