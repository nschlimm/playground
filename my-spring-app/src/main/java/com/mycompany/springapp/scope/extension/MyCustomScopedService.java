package com.mycompany.springapp.scope.extension;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("custom")
public class MyCustomScopedService {
	
	private String name ="Test";

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

}
