package com.mycompany.jeeapp.scope;

import javax.enterprise.context.RequestScoped;

@RequestScoped
public class MyRequestService {

	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}
	
}
