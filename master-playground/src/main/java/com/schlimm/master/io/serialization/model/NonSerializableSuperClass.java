package com.schlimm.master.io.serialization.model;

public class NonSerializableSuperClass {

	private String s;

	public NonSerializableSuperClass(String s) {
		super();
		this.s = s;
	}

	public NonSerializableSuperClass() {
		this("s-uninitialized");
	}

	public String getS() {
		return s;
	}

	public void setS(String s) {
		this.s = s;
	}
	
}
