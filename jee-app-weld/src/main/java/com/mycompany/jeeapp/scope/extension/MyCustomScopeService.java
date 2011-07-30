package com.mycompany.jeeapp.scope.extension;

@CustomScoped
public class MyCustomScopeService {

	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}
	
}
