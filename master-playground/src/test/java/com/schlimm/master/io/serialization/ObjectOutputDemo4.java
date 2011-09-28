package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.SerializableSubClass;


/**
 * Nonserialiyed superclass does not get serialiyed
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo4 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new SerializableSubClass("test", 10));
		List<SerializableSubClass> list = new ArrayList<SerializableSubClass>();
		list.add(new SerializableSubClass("supi", 11));
		list.add(new SerializableSubClass("nerd", 12));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
