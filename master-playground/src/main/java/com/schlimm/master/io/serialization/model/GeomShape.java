package com.schlimm.master.io.serialization.model;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;


/**
 * Dealing with different object versions
 * Storing new version in the same structure then the old version
 * 
 * @author Niklas Schlimm
 *
 */
public class GeomShape implements Serializable {

	/**
	 * version id
	 */
	private static final long serialVersionUID = 1500954070183700280L;

	private static final ObjectStreamField[] serialPersistentFields = {
		new ObjectStreamField("point", Point.class),
		new ObjectStreamField("dimension", Dimension.class)
	};
	
	private Rectangle rectangle;

	public GeomShape(Rectangle rectangle) {
		super();
		this.rectangle = rectangle;
	}
	
	@Override
	public String toString() {
		return "Geomshape: " + rectangle;
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		ObjectOutputStream.PutField fields = oos.putFields();
		fields.put("point", rectangle.getLocation());
		fields.put("dimension", rectangle.getSize());
		oos.writeFields();
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ObjectInputStream.GetField fields = ois.readFields();
		Point point = (Point) fields.get("point", null);
		Dimension dimension = (Dimension) fields.get("dimension", null);
		rectangle = new Rectangle(point, dimension);
	}
}
