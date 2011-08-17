package com.mycompany.springapp.scope;

import org.springframework.stereotype.Component;

@Component
public class MySingletonScopedService {
	private String name ="Test";

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
