package com.schlimm.master.io.serialization;

import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.schlimm.master.io.serialization.model.GeomShape;


/**
 * Dealing with different object versions
 * 
 * @author Niklas Schlimm
 *
 */
public class ObjectOutputDemo12 {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("object.out")));
		oout.writeObject(new GeomShape(new Rectangle(10, 10)));
		List<GeomShape> list = new ArrayList<GeomShape>();
		list.add(new GeomShape(new Rectangle(11,11)));
		list.add(new GeomShape(new Rectangle(12,12)));
		oout.writeObject(list);
		oout.writeObject(null);
		oout.close();
	}
}
