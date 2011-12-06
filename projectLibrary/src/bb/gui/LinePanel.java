/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ BUG?  if execute UnitTest.test_serialization below, the gui looks fine,
but if try resizing the window, the expandable items like the JTextAreas do not increase to fill up the window if make it larger
	--then there must be some information that is not being serialized

--for a tutorial on GroupLayout, see
	http://java.sun.com/docs/books/tutorial/uiswing/layout/group.html
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotActiveException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import javax.swing.Box;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

/**
* A Swing container that resembles the AWT container {@link Box}:
* it always lays out its Components in a single line (that can be either horizontal or vertical),
* and it does not allow the LayoutManager to be changed from what it was constructed with.
* <p>
* Besides being an AWT widget, Box is not as easy to use as it could be.
* In particular, it usually requires the user to add a {@link Border} to it to effect spacing with its parent Container.
* Likewise, it requires the user to add explicit "glue", "strut" or "rigid areas" Components inside it
* to effect spacing and sizing among its contents.
* <p>
* In contrast, this class is always configured to automatically insert gaps between
* Components that touch the edge of the parent Container, as well as gaps between Components inside it.
* Consequently, usage is trivial: an instance is created via one of the factory methods,
* and then you simply add those Components you really care about to it.
* For example, here is how you can create a Container that holds a horizontal row of buttons:
* <pre><code>
	JButton jbutton1 = new JButton("jbutton1");
	JButton jbutton2 = new JButton("jbutton2");
		// then do whatever configuration is need on those buttons, like add ActionListeners
	
		// can easily create a Container to hold all those buttons as follows:
	LinePanel linePanel = LinePanel.makeHorizontal();
	linePanel.add( jbutton1 );
	linePanel.add( jbutton2 );
* </code></pre>
* <p>
* <b>Some layout issues have been seen with this class.</b>
* <p>
* The first issue is that have seen instances get shrunk much more than seems reasonable.
* This class currently uses GroupLayout for its LayoutManager,
* and this issue seems to only happen when this class is used in a GUI with other Containers that have different LayoutManagers.
* It appears to be caused by older LayoutManagers or Components doing screwy things with preferred and maximum sizes.
* The cure is to always use this class, or at least always use GroupLayout (or some other modern LayoutManager?) in other Containers.
* <p>
* The second issue that have seen is sometimes a JTextArea embedded inside a JScrollPane will stretch far more than it ought to
* (e.g. to the point where the scroll bars are outside the window).
* This only seems to happen when the text gets really large (in either rows or columns).
* <i>This is a bug with GroupLayout (see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6898855">this bug report</a>
* as well as my JScrollPaneDemo class in my singleShotPrograms project).</i>
* One cure is to limit the preferred size of the JScrollPane with code like this:
* <pre><code>
	private JComponent buildTextArea(String text) {
		JTextArea jtextArea = new JTextArea();
		//jtextArea.setXXX(...);	// do whatever configuration is needed
		
		JScrollPane jscrollPane = new JScrollPane(jtextArea);
		jscrollPane.setPreferredSize( SwingUtil.screenSize() );	// CRITICAL: if do not call this, have seen the JScrollPane get made WAY bigger than the window, which is bad, since cannot see the scroll bars plus other components get pushed off screen
		return jscrollPane;
	}
* </code></pre>
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class LinePanel extends JPanel {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/**
	* Contract: is never null.
	* <p>
	* @serial
	*/
	private final Axis axis;
	
	/** Contract: is never null. */
	private transient GroupLayout groupLayout;	// CRITICAL: MUST be transient, since GroupLayout is not serializable
	
	/** Contract: is never null. */
	private transient GroupLayout.Group groupParallel;	// CRITICAL: MUST be transient, since GroupLayout is not serializable
	
	/** Contract: is never null. */
	private transient GroupLayout.Group groupSequential;	// CRITICAL: MUST be transient, since GroupLayout is not serializable
	
	// -------------------- factory makeXXX methods and constructors --------------------
	
	/**
	* Returns a new LinePanel with a horizontal {@link GroupLayout} layout manager and a double buffer.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static LinePanel makeHorizontal() throws IllegalStateException {
		// edt checked by LinePanel below
		
		return new LinePanel(Axis.horizontal, true);
	}
	
	/**
	* Returns a new LinePanel with a vertical {@link GroupLayout} layout manager and a double buffer.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static LinePanel makeVertical() throws IllegalStateException {
		// edt checked by LinePanel below
		
		return new LinePanel(Axis.vertical, true);
	}
	
	/**
	* Constructs a new LinePanel with the specified {@link Axis} and buffering strategy.
	* <p>
	* @param axis specifes the direction that components will be laid out along
	* @param isDoubleBuffered a boolean, true for double-buffering, which uses additional memory space to achieve fast, flicker-free updates
	* @throws IllegalArgumentException if axis is null or is not a recognized value
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public LinePanel(Axis axis, boolean isDoubleBuffered) throws IllegalArgumentException, IllegalStateException {
		super(isDoubleBuffered);
		
		Check.arg().notNull(axis);
		Check.state().edt();
		
		this.axis = axis;
		initLayout();	// CRITICAL: call this only after have assigned axis
	}
	
	// -------------------- initLayout --------------------
	
	private void initLayout() throws IllegalArgumentException {
		groupLayout = new GroupLayout2(this);	// CRITICAL: must use GroupLayout2 instead of GroupLayout in order for serialization to work
		groupLayout.setAutoCreateGaps(true);	// automatically add gaps between components
		groupLayout.setAutoCreateContainerGaps(true);	// automatically create gaps between components that touch the edge of the container and the container.
		super.setLayout(groupLayout);	// CRITICAL: call super.setLayout, because override setLayout below to always crash
		
		groupParallel = groupLayout.createParallelGroup();
		groupSequential = groupLayout.createSequentialGroup();
		
		switch (axis) {
			case horizontal:
				groupLayout.setHorizontalGroup( groupSequential );
				groupLayout.setVerticalGroup( groupParallel );
				break;
			case vertical:
				groupLayout.setHorizontalGroup( groupParallel );	// Note: simply switch the parallel and sequential groups compared to makeHorizontal
				groupLayout.setVerticalGroup( groupSequential );
				break;
			default:
				throw new IllegalArgumentException("axis = " + axis + " is an unrecognized value");
		}
	}
	
	// -------------------- serialization --------------------
	
	/**
	* Customizes deserialization.
	* <p>
	* Background: the sole issue is that Sun screwed up and failed to make {@link GroupLayout} implement {@link Serializable}.
	* This class's GroupLayout-related fields are all transient, so no problem here.
	* Unfortunately, our superlass, JPanel, retains a reference to the layout manager and we have no control over its serialization.
	* <p>
	* The solution is that we use a {@link GroupLayout2} for {@link #groupLayout}, since it is serializable in some sense.
	* Well, GroupLayout2 does not actually write or read any data during serialization, but at least it does not throw any Exceptions.
	* This enables serialization of this class to proceed without modification (which is why there is no implementation of writeObject).
	* <p>
	* To deserialize here, this method first calls {@link ObjectInputStream#defaultReadObject},
	* which restores all of this class's non-transient state as well as its superclass's (including all the components that were added).
	* Then, since it has its {@link #axis} field available, it only need call {@link #initLayout initLayout} to recreate the GroupLayout-related fields.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws ClassNotFoundException if the class of a serialized object could not be found
    * @throws IOException if an I/O problem occurs
	*/
	private void readObject(ObjectInputStream ois) throws IllegalStateException, ClassNotFoundException, IOException, NotActiveException  {
		Check.state().edt();
		
		ois.defaultReadObject();
		initLayout();
	}
	
	// No need--see readObject javadocs above
	//private void writeObject(ObjectOutputStream oos) throws IllegalStateException, IOException {
	
	/*
	Note: I initially tried adding writeObject to this class, and having it first call setLayout(null) to remove the GroupLayout reference in its superclass.
	Unfortunately, that failed because JPanel's serialization stuff is called BEFORE this class's writeObject is executed,
	and so the layout nulling is worthless.
	(See the private ObjectOutputStream.writeSerialData(Object obj, ObjectStreamClass desc) method in its source file,
	whose javadoc states "Writes instance data for each serializable class of given object, from superclass to subclass.")
	
	Next, before I had GroupLayout2 available, I tried having THIS class implement Externalizable.
	That failed because there is just so much state in the superclass that need to automatically get hold of, it was too painful to do it manually.
	
	I AM ACTUALLY AMAZED THAT THE PRESENT CODE ABOVE WORKS, because when the components from the superclass (JPanel) are deserialized they do not go thru addImpl below.
	It must be the constraints arg that is passed to addImpl which has enough information that when deserialization occurs, all added components go back where they should.
	
	See also
		http://java.sun.com/j2se/1.5.0/docs/guide/serialization/examples/externsuper/sources.html
		http://java.sun.com/j2se/1.5.0/docs/guide/serialization/examples/nonexternsuper/index3.html
	albeit these examples do nothing to help here.
	*/
	
	// -------------------- addImpl --------------------
	
	// I THINK that this is the only add-related method that need to override, since the Container.addImpl javadocs state: "This is the method to override if a program needs to track every add request to a container as all other add methods defer to this one."
	protected void addImpl(Component comp, Object constraints, int index) {
		super.addImpl(comp, constraints, index);	// call this as per the recommendation in the Container.addImpl javadocs
		
		groupParallel.addComponent(comp);
		groupSequential.addComponent(comp);
	}
	
	// -------------------- setLayout --------------------
	
	/**
	* Overrides the superclass version to forbid changing the LayoutManager after construction.
	* <p>
	* @throws IllegalStateException if construction has finished ()
	*/
	@Override public void setLayout(LayoutManager mgr) throws IllegalStateException {
		if ( (groupLayout != null) && (groupLayout != mgr) ) throw new IllegalStateException("LinePanel forbids changing its LayoutManager from what it was constructed with");
	}
	// Note that one reason why cannot change the LayoutManager is because this will probably break addImpl above (the groups would no longer valid)
	
	// -------------------- Axis (static inner enum) --------------------
	
	/**
	* Enum of the possible orientations of the axis that components will be laid out along.
	* <p>
	* This enum is multithread safe: it is stateless (except for the enumeration of values, which are immutable).
	* <p>
	* Like all java enums, this enum is Comparable and Serializable.
	* <p>
	* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/language/enums.html">Enum documentation</a>
	*/
	public static enum Axis {
		/** Value for a horizontal orientation. */
		horizontal,
		
		/** Value for a vertical orientation. */
		vertical;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				buildGui();
//				test_serialization();
				
				return null;
			} } );
		}
		
		static JFrame buildGui() {	// package-protected access so that other test classes can use
			return new Displayer( buildContents(), UnitTest.class.getSimpleName(), UnitTest.class.getName() );
		}
		
		private static JComponent buildContents() {
			LinePanel linePanel = LinePanel.makeVertical();
			linePanel.add( buildLabels() );
			linePanel.add( buildTextComponents() );
			linePanel.add( buildButtons() );
			return linePanel;
		}
		
		private static JComponent buildLabels() {
			LinePanel linePanel = LinePanel.makeVertical();
			
			JLabel jlabel1 = new JLabel("jlabel1");
			JLabel jlabel2 = new JLabel("jlabel2");
			JLabel jlabel3 = new JLabel("jlabel3");
			
				// if uncomment the lines below, can see that alignment is not honored by GroupLayout:
			//jlabel1.setAlignmentX(0.0f);
			//jlabel2.setAlignmentX(0.5f);
			//jlabel3.setAlignmentX(1.0f);
			
			linePanel.add( jlabel1 );
			linePanel.add( jlabel2 );
			linePanel.add( jlabel3 );
			
			return linePanel;
		}
		
		private static JComponent buildTextComponents() {
			LinePanel linePanel = LinePanel.makeVertical();
			linePanel.add( buildTextField() );
			linePanel.add( buildTextArea("jtextArea1") );
			linePanel.add( buildSplitPane() );
			return linePanel;
		}
		
		private static JTextField buildTextField() {
			JTextField jtextField = new JTextField("jtextField");
			ComponentUtil.setHeightMaxToPref(jtextField);	// CRITICAL: if do not call this, the JTextField annoyingly gets stretched in the vertical dimension, which makes it deceitfully look like a JTextArea when it is not
			return jtextField;
		}
		
		private static JComponent buildTextArea(String name) {
			JTextArea jtextArea = new JTextArea(name);
			
			JScrollPane jscrollPane = new JScrollPane();
			jscrollPane.getViewport().setView( jtextArea );
jscrollPane.setPreferredSize( Toolkit.getDefaultToolkit().getScreenSize() );
// +++ need to this because of a bug: see singleShotPrograms/.../JScrollPaneDemo or this bug report: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6898855
			return jscrollPane;
		}
		
		private static JComponent buildSplitPane() {
			JSplitPane jsplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildTextArea("jtextArea2A"), buildTextArea("jtextArea2B"));
			jsplitPane.setOneTouchExpandable(true);
			jsplitPane.setResizeWeight(0.5);	// 0.5 makes the divider be in the middle by default (user, of course, can manually adjust to whatever they want
// +++ would be nice to have a mouse listener, such that if the user double clicks on the divider, it goes back to its default size.
// This listener would need to call jsplitPane.resetToPreferredSizes().
			return jsplitPane;
		}
		
		private static JComponent buildButtons() {
			LinePanel linePanel = LinePanel.makeHorizontal();
			
			JButton jbutton1 = new JButton("jbutton1");
			JButton jbutton2 = new JButton("jbutton2");
			JButton jbutton3 = new JButton("jbutton3");
			
			linePanel.add( jbutton1 );
			linePanel.add( jbutton2 );
			linePanel.add( jbutton3 );
			
			return linePanel;
		}
		
		private static void test_serialization() throws Exception {
			JFrame frame1 = buildGui();
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream( baos );
			oos.writeObject( frame1 );
			
			frame1.dispose();
			
			ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
			JFrame frame2 = (JFrame) ois.readObject();
			frame2.setTitle( frame1.getTitle() + " (THIS IS THE DESERIALIZED JFrame--if see it, then serialization works!)" );
			frame2.setVisible(true);
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
