package com.mycompany.jeeapp.scope;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyApplicationService {

	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}
	
}
