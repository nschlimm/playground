package com.schlimm.master.io.serialization.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Setting final fields
 * 
 * @author Niklas Schlimm
 *
 */
public class SerializableSubClass3 extends NonSerializableSuperClass implements Serializable {

	private final int i;

	public SerializableSubClass3(String s, int i) {
		super(s);
		this.i = i;
	}

	@Override
	public String toString() {
		return getS() + ", i=" + 1;
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
		oos.writeObject(getS());
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		setS((String)ois.readObject());
	}
}
