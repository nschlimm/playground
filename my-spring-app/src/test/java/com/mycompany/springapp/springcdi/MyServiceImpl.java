package com.mycompany.springapp.springcdi;

import org.springframework.stereotype.Component;

@Component
public class MyServiceImpl implements MyService {

	public String sayHello() {
		return "Hello";
	}

}
