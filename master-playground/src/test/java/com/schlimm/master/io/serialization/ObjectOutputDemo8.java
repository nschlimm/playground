package com.schlimm.master.io.serialization;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;


/**
 * Create out of memory error
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo8 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream("object.out"));
		while (true) {
			oout.writeObject(new byte[10240]);
		}
	}
}
