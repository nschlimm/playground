/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ have filed a bug report with Sun to make GroupLayout implement Serializable:
	Your report has been assigned an internal review ID of 1639981, which is NOT visible on the Sun Developer Network (SDN).
If Sun ever fixes GroupLayout, then this class should die...
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.Container;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import javax.swing.GroupLayout;
import javax.swing.JPanel;

/**
* Subclass of {@link GroupLayout} that adds no new functionality except for implementing {@link Externalizable},
* which is needed because its superclass does not implement {@link Serializable}.
* <p>
* This class implements Externalizable with, essentially, empty methods which read and write no data.
* The sole purpose of these methods is to preempt calls to its superclass during serialization/deserialization,
* since those calls will fail.
* <p>
* Correct usage of these class in serialization/deserialization is tricky and usually requires some other class
* to customise deserialization (e.g. to manually restore an equivalent GroupLayout instance).
* See the source code of {@link LinePanel} for one example.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class GroupLayout2 extends GroupLayout implements Externalizable {
	
	// -------------------- constants --------------------
	
	/**
	* The sole purpose of this method is to provide a host value for the {@link #GroupLayout2() public no-arg constructor}.
	* It is never used for any other purpose.
	*/
	private static final Container hostDummy = new JPanel();
	
	private static final long serialVersionUID = 1;
	
	// -------------------- constructors --------------------
	
	/**
	* Calls <code>{@link #GroupLayout2(Container) this}({@link #hostDummy})</code>.
	* <p>
	* <i>This constructor is needed solely because {@link Externalizable} requires a public no-arg constructor.</i>
	* Users should never use it.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public GroupLayout2() throws IllegalStateException {
		// edt checked by this below
		
		this(hostDummy);
	}
	
	/**
	* Calls <code>{@link GroupLayout#GroupLayout(Container) super}(host)</code>.
	* <p>
	* @param host the Container the GroupLayout2 is the LayoutManager for
	* @throws IllegalArgumentException if host is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public GroupLayout2(Container host) throws IllegalArgumentException, IllegalStateException {
		super(host);
		
		Check.state().edt();
	}
	
	// -------------------- Externalizable methods --------------------
	
	/**
	* Customizes deserialization.
	* <p>
	* This method does nothing beyond checking the calling thread.
	* Its sole purpose is to keep its unserializable superclass from getting called.
	* <p>
	* Note that the {@link #GroupLayout2() public no-arg constructor} will have been called
	* prior to the deserialization process calling this method.
	* Therefore, after this method returns, this instance has {@link #hostDummy} as its host forever,
	* regardless of what value it had before serialization;
	* it will always be a worthless instance.
	* So, users of this class must manually restore a clean new instance when deserializing.
	* <p>
	* @serialData none: no data is read
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void readExternal(ObjectInput oi) throws IllegalStateException  {
		Check.state().edt();
	}
	
	/**
	* Customizes serialization.
	* <p>
	* This method does nothing beyond checking the calling thread.
	* Its sole purpose is to keep its unserializable superclass from getting called.
	* <p>
	* @serialData none: no data is written
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void writeExternal(ObjectOutput oo) throws IllegalStateException {
		Check.state().edt();
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				test_serialization();
				
				return null;
			} } );
		}
		
		// sole purpose is to show that throws no Exception; see LinePanel.UnitTest for proof that it works in the context of an entire GUI
		private static void test_serialization() throws Exception {
			GroupLayout2 groupLayout2 = new GroupLayout2();
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream( baos );
			oos.writeObject( groupLayout2 );
			
			ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
			groupLayout2 = (GroupLayout2) ois.readObject();
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
