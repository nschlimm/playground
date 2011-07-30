package com.mycompany.jeeapp.decorator;

import javax.decorator.Decorator;
import javax.decorator.Delegate;
import javax.inject.Inject;

@Decorator
public class MyDecorator2 implements MyServiceInterface{

	@Inject @Delegate
	private MyServiceInterface delegate;
	
	@Override
	public MyServiceInterface getDelegate() {
		return delegate;
	}

	@Override
	public String getHelloWorld() {
		return delegate.getHelloWorld();
	}
	

}
