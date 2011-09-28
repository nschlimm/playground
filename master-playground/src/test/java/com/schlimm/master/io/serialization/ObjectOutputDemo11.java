package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.SerializableSubClass3;


/**
 * Setting final fields
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo11 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new SerializableSubClass3("test", 10));
		List<SerializableSubClass3> list = new ArrayList<SerializableSubClass3>();
		list.add(new SerializableSubClass3("supi", 11));
		list.add(new SerializableSubClass3("nerd", 12));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
