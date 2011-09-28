package com.schlimm.master.io.serialization.model;

import java.io.ObjectStreamField;
import java.io.Serializable;

public class User2 implements Serializable {
	
	private final String login;
	private final int level;
	// transient fields not serialized
	private transient final String pwd;
	
	private static final ObjectStreamField[] serialPersistentFields = { 
		new ObjectStreamField("login", String.class),
		new ObjectStreamField("level", int.class)
	};
	
	public User2(String login, int level, String pwd) {
		super();
		this.login = login;
		this.level = level;
		this.pwd = pwd;
	}

	@Override
	public String toString() {
		return "this@" +System.identityHashCode(this)+ ", login@"+System.identityHashCode(login)+"=" + login + ", level@"+System.identityHashCode(level)+"=" + level + ", pwd@"+System.identityHashCode(pwd)+"=" + pwd;
	}
}
