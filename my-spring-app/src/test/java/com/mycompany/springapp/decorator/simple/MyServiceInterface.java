package com.mycompany.springapp.decorator.simple;

public interface MyServiceInterface {

	public void setDelegateClass(MyDelegate delegate);
	public MyDelegate getDelegateClass();
	public void setDelegateInterface(MyServiceInterface serviceDelegate);
	public MyServiceInterface getDelegateInterface();
}
