package com.schlimm.master.io.serialization.model;

import java.io.Serializable;

public class User implements Serializable {
	
	private final String name;
	private final String login;
	// transient fields not serialized
	private transient final String pwd;
	
	public User(String name, String login, String pwd) {
		super();
		this.name = name;
		this.login = login;
		this.pwd = pwd;
	}

	@Override
	public String toString() {
		return name + ", login=" + login + ", pwd=" + pwd;
	}
}
