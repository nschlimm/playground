package com.mycompany.jeeapp.decorator;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

@Decorator
public class MyDecorator implements MyServiceInterface{
	
	@Inject @Delegate
	private MyServiceInterface serviceDelegate;

	@Override
	public MyServiceInterface getDelegate() {
		return serviceDelegate;
	}

	@Override
	public String getHelloWorld() {
		return serviceDelegate.getHelloWorld();
	}

}
