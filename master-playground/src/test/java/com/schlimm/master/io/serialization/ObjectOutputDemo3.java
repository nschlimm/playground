package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.User;


/**
 * Transient fields do not get serialized
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo3 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new User("Niklas Schlimm", "nschlimm", "testpwd"));
		List<User> list = new ArrayList<User>();
		list.add(new User("Frank Rahn", "frahn", "superpwd"));
		list.add(new User("Frank Hinkel", "fhinkel", "nerdpwd"));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
