package com.schlimm.master.io.serialization;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.Alien;

public class ObjectOutputDemo2 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new Alien("Mars", 341242L));
		List<Alien> list = new ArrayList<Alien>();
		list.add(new Alien("Venus", 1237232));
		list.add(new Alien("Venus", 1237233));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
