package com.mycompany.springapp.scope;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("thread")
public class MyThreadScopedService {
	private String name ="Test";

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
