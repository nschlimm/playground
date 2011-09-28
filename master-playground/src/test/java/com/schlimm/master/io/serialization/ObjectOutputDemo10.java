package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.SerializableSubClass2;


/**
 * Serializing non-serializable superclass
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo10 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new SerializableSubClass2("test", 10));
		List<SerializableSubClass2> list = new ArrayList<SerializableSubClass2>();
		list.add(new SerializableSubClass2("supi", 11));
		list.add(new SerializableSubClass2("nerd", 12));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
