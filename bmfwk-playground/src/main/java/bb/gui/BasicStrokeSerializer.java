/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.BasicStroke;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.util.concurrent.Callable;

/**
* The sole purpose of this class is to aid classes which have a BasicStroke field that needs to get serialized.
* <p>
* <i>This class would be unnecessary of Sun were to merely make BasicStroke implement Serializable.</i>
* I filed a bug report with Sun about this; it has been assigned an internal review ID of: 383603

<!--
+++ someone else has filed a bug report already:
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4305099
note that in the comments, it looks like someone else has a class like this one...
-->

* <p>
* This class's instance part multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* However, the static {@link #write write} and {@link #read read} methods restrict the calling thread to
* {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} because they deal with a {@link BasicStroke} instance.
* <p>
* @author Brent Boyer
*/
public class BasicStrokeSerializer implements Serializable {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/** @serial */
	private final float width;
	
	/** @serial */
	private final int cap;
	
	/** @serial */
	private final int join;
	
	/** @serial */
	private final float miterlimit;
	
	/** @serial */
	private final float[] dash;
	
	/** @serial */
	private final float dash_phase;
	
	// -------------------- write, read --------------------
	
	/**
	* Constructs a new {@link #BasicStrokeSerializer} instance from basicStroke
	* and serializes it to objectOutputStream.
	* <p>
	* @throws IllegalArgumentException if basicStroke == null; objectOutputStream == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws InvalidClassException if something is wrong with a class used by serialization
	* @throws NotSerializableException if some object to be serialized does not implement the java.io.Serializable interface
	* @throws IOException if any is thrown by the underlying OutputStream
	*/
	public static void write(BasicStroke basicStroke, ObjectOutputStream objectOutputStream) throws IllegalArgumentException, IllegalStateException, InvalidClassException, NotSerializableException, IOException {
		Check.arg().notNull(basicStroke);
		Check.arg().notNull(objectOutputStream);
		Check.state().edt();
		
		BasicStrokeSerializer serializer = new BasicStrokeSerializer(basicStroke);
		objectOutputStream.writeObject(serializer);
	}
	
	/**
	* Deserializes a BasicStrokeSerializer object from objectInputStream,
	* {@link #convert converts} it to a new BasicStroke instance, and returns it.
	* <p>
	* @throws IllegalArgumentException if objectInputStream == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws ClassNotFoundException if a class of a serialized object cannot be found
	* @throws InvalidClassException if something is wrong with a class used by serialization
	* @throws StreamCorruptedException if control information in the stream is inconsistent
	* @throws OptionalDataException if primitive data was found in the stream instead of objects
	* @throws IOException if any is thrown by the underlying InputStream
	* @throws ClassCastException if the next Object on objectInputStream is not a BasicStrokeSerializer instance
	*/
	public static BasicStroke read(ObjectInputStream objectInputStream) throws IllegalArgumentException, IllegalStateException, ClassNotFoundException , InvalidClassException, StreamCorruptedException, OptionalDataException, IOException, ClassCastException {
		Check.arg().notNull(objectInputStream);
		Check.state().edt();
		
		BasicStrokeSerializer serializer = (BasicStrokeSerializer) objectInputStream.readObject();
		return serializer.convert();
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Creates a new BasicStrokeSerializer instance.
	* <p>
	* @throws IllegalArgumentException if basicStroke == null
	*/
	public BasicStrokeSerializer(BasicStroke basicStroke) throws IllegalArgumentException {
		Check.arg().notNull(basicStroke);
		
		width = basicStroke.getLineWidth();
		cap = basicStroke.getEndCap();
		join = basicStroke.getLineJoin();
		miterlimit = basicStroke.getMiterLimit();
		dash = basicStroke.getDashArray();
		dash_phase = basicStroke.getDashPhase();
	}
	
	// -------------------- convert --------------------
	
	/** Converts this instance into a new BasicStroke instance and returns it. */
	public BasicStroke convert() {
		return new BasicStroke(width, cap, join, miterlimit, dash, dash_phase);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Tests the parent class.
		* <p>
		* If this method is this Java process's entry point (i.e. first <code>main</code> method),
		* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
		* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
		* Otherwise, this method returns and leaves the JVM running.
		*/
		public static void main(final String[] args) {
			Execute.usingEdt( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				BasicStroke strokeOriginal = new BasicStroke(1.0f);
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream( baos );
				write(strokeOriginal, oos);
				ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
				BasicStroke strokeDeserialized = read(ois);
				
				if (strokeOriginal.equals(strokeDeserialized)) System.out.println("Good: write/read correctly produced an equals instance");
				else throw new Exception("ERROR: write/read failed to produce an equals instance");
				
				return null;
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
