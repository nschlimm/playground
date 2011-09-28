package com.schlimm.master.io.serialization.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Serializing non-serializable superclass
 * 
 * @author Niklas Schlimm
 *
 */
public class SerializableSubClass2 extends NonSerializableSuperClass implements Serializable {

	private int i;

	public SerializableSubClass2(String s, int i) {
		super(s);
		this.i = i;
	}

	@Override
	public String toString() {
		return getS() + ", i=" + 1;
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.writeInt(i);
		oos.writeObject(getS());
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		i = ois.readInt();
		setS((String)ois.readObject());
	}
}
