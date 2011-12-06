package com.schlimm.java7.coin;

import java.lang.reflect.InvocationTargetException;

public class HandleExceptionsJava6Example {
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) {
		Class string;
		try {
			string = Class.forName("java.lang.String");
			string.getMethod("length").invoke("test");
		} catch (ClassNotFoundException e) {
			// do something
		} catch (IllegalAccessException e) {
			// do something
		} catch (IllegalArgumentException e) {
			// do something
		} catch (InvocationTargetException e) {
			// do something
		} catch (NoSuchMethodException e) {
			// do something
		} catch (SecurityException e) {
			// do something
		}
	}
}
