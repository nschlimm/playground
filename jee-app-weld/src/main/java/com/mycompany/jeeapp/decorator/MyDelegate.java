package com.mycompany.jeeapp.decorator;

public class MyDelegate implements MyServiceInterface{

	@Override
	public MyServiceInterface getDelegate() {
		return this;
	}

	@Override
	public String getHelloWorld() {
		return "Hello";
	}

}
