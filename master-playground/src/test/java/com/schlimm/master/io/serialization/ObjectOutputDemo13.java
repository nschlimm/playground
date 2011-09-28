package com.schlimm.master.io.serialization;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Customize what objects are really written and read (writeReplace() und readResolve())
 * 
 * @author Niklas Schlimm
 *
 */
@SuppressWarnings("serial")
public class ObjectOutputDemo13 implements Serializable {

	private static String[] endings = {"opoulos", "ides"};
	
	public class GreekSerializedForm implements Serializable {
		
		private String name;

		public GreekSerializedForm(String name) {
			super();
			for (int i = 0; i < endings.length; i++) {
				String ending = endings[i];
				if (name.endsWith(ending)) {
					name = name.substring(0,name.length() - ending.length()) + i;
				}
			}
			this.name = name;
		}
		
		private Object readResolve() throws ObjectStreamException {
			String longName = name;
			for (int i = 0; i < endings.length; i++) {
				String ending = endings[i];
				if (longName.endsWith(Integer.toString(i))) {
					longName = longName.substring(0, longName.length() - 1) + ending;
				}
			}
			return new Greek(longName);
		}
		
		

	}

	public class Greek  implements Serializable {
		private final String name;

		public Greek(String name) {
			super();
			this.name = name;
		}
		@Override
		public String toString() {
			return name;
		}
		private Object writeReplace() throws ObjectStreamException {
			return new GreekSerializedForm(name);
		}
		
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream("object.out"));
		oout.writeObject(new ObjectOutputDemo13().new Greek("Apostolopoulos"));
		oout.writeObject(new ObjectOutputDemo13().new Greek("Kytides"));
		oout.writeObject(null);
		oout.close();
	}
	
}
