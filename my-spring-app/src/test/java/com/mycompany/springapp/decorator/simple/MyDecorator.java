package com.mycompany.springapp.decorator.simple;

import javax.decorator.Decorator;
import javax.decorator.Delegate;

@Decorator
public class MyDecorator implements MyServiceInterface {
	
	@Delegate
	private MyDelegate delegateClass;
	
	@Delegate 
	private MyServiceInterface delegateInterface;

	public void setDelegateClass(MyDelegate delegate) {
		this.delegateClass = delegate;
	}

	public MyDelegate getDelegateClass() {
		return delegateClass;
	}

	public void setDelegateInterface(MyServiceInterface serviceDelegate) {
		this.delegateInterface = serviceDelegate;
	}

	public MyServiceInterface getDelegateInterface() {
		System.out.println("In Decorator 1");
		delegateInterface.getDelegateClass();
		return delegateInterface;
	}

}
