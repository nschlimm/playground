package com.mycompany.jeeapp.scope;

import javax.inject.Singleton;

@Singleton
public class MySingletonService {

	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}
	
}
