package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.User3;


/**
 * Unshared login String object - see the hashcodes
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo6 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		String login = "nschlimm";
		oout.writeObject(new User3(login, 7, "testpwd"));
		List<User3> list = new ArrayList<User3>();
		list.add(new User3(login, 8, "testpwd"));
		list.add(new User3("robert", 8, "testpwd"));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
