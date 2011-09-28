package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.User2;


/**
 * Shared objects example - see the hashcodes
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo5 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		String login = "nschlimm";
		oout.writeObject(new User2(login, 7, "testpwd"));
		List<User2> list = new ArrayList<User2>();
		list.add(new User2(login, 8, "testpwd"));
		list.add(new User2("robert", 8, "testpwd"));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
