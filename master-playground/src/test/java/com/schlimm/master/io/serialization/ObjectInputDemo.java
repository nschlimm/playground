package com.schlimm.master.io.serialization;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;

public class ObjectInputDemo {
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
		ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(new FileInputStream("object.out")));
		Object obj;
		while ((obj = oin.readObject()) != null) {
			System.out.println(obj);
		}
	}
}
