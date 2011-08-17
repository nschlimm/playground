package com.mycompany.springapp.springcdi;

import javax.decorator.Decorator;
import javax.decorator.Delegate;

@Decorator
public class MyServiceSecurityDecorator implements MyService {

	@Delegate
	private MyService delegate;
	
	public String sayHello() {
		return delegate.sayHello() + "security";
	}

}
