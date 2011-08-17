package com.mycompany.springapp.springcdi.interceptor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Qualifier("impl1")
@ReturnValueAdopted
public class Simple_MyServiceInterface_Impl implements Simple_MyServiceInterface {

	public String sayHello() {
		return "Hello";
	}

	public String sayHello(String what) {
		return what;
	}

	public String sayGoodBye() {
		return "Good bye";
	}

}
