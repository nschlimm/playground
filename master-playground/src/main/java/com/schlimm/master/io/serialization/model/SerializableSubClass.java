package com.schlimm.master.io.serialization.model;

import java.io.Serializable;

/**
 * Non-seriazable superclasss
 * 
 * @author Niklas Schlimm
 *
 */
public class SerializableSubClass extends NonSerializableSuperClass implements Serializable {

	private final int i;

	public SerializableSubClass(String s, int i) {
		super(s);
		this.i = i;
	}

	@Override
	public String toString() {
		return getS() + ", i=" + 1;
	}
}
