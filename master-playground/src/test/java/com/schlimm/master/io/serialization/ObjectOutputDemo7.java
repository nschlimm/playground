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
 * Shared and unshared objects - see the hashcodes
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo7 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		String login = "nschlimm";
		User3 user = new User3(login, 7, "testpwd");
		oout.writeObject(user);
		List<User3> list = new ArrayList<User3>();
		list.add(user); // same object, same nschlimm login String
		list.add(new User3(login, 8, "testpwdi")); // new object, and new nschlimm login String
		oout.writeObject(list);
		oout.writeUnshared(user); // new object id and new nschlimm login String => complete new
		oout.writeObject(null);
		oout.close();
	}
}
