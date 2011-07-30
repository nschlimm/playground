package com.mycompany.jeeapp.scope;

import java.io.Serializable;

import javax.enterprise.context.SessionScoped;

@SessionScoped
public class MySessionService implements Serializable {

	/**
	 * Unique ID.
	 */
	private static final long serialVersionUID = 1L;
	
	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}
	
}
