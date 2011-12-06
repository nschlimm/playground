/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package d;

import bb.gui.LinePanel;
import bb.util.Check;
import bb.util.Execute;
import bb.util.ObjectState;
import bb.util.ThrowableUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
* This class provides static methods which <i>send</i> output to a <i>GUI</i>.
* <p>
* <i>This class was designed to require as little typing as possible to use</i>.
* In particular, all public names (package, class, and method) are lower case and very short.
* So, you can avoid import statements and easily invoke all methods using their fully qualified form.
* Here is a typical use:
* <pre><code>
*    StringBuilder sb = new StringBuilder();
*    d.g.s(sb);
* </code></pre>
* <p>
* Mnemonic: the package name d stands for debug; the class name g stands for GUI; all the methods named s stand for send.
* <p>
* Like typical Java GUI code, this class's gui parts (the {@link ObjectStateDisplay} inner class) are not multithread safe:
* they expect to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* Note, however, that the public API <code>s</code> methods may be safely called by any thread.
* <p>
* @author Brent Boyer
*/
public final class g {
	
	// -------------------- s --------------------
	
	/** Simply calls <code>s(null, obj, true)</code>. */
	public static void s(Object obj) throws RuntimeException {
		s(null, obj, true);
	}
	
	/** Simply calls <code>s(null, obj, pauseExecution)</code>. */
	public static void s(Object obj, boolean pauseExecution) throws RuntimeException {
		s(null, obj, pauseExecution);
	}
	
	/** Simply calls <code>s(label, obj, true)</code>. */
	public static void s(String label, Object obj) throws RuntimeException {
		s(label, obj, true);
	}
	
	/**
	* Displays obj's state in a new window.
	* <p>
	* Any thread may call this method with any combination of params except this one situation:
	* if {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} calls this method,
	* then the pauseExecution param must be false (if it were true, then the GUI would freeze).
	* <p>
	* @param label text labelling the new window
	* @param obj the object whose state is displayed; there are no constraints on it (e.g. may be null)
	* @param pauseExecution if true, then executes synchronously
	* (i.e. the calling Thread waits until the user either closes the window or clicks on the "Continue Execution" button before returning from this method);
	* otherwise executes asynchornously (i.e. exits almost immediately, regardless of user input)
	* @throws RuntimeException (or some subclass) if any error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void s(String label, Object obj, boolean pauseExecution) throws RuntimeException {
		try {
			if (pauseExecution && EventQueue.isDispatchThread()) {
				throw new IllegalStateException("pauseExecution == true, but the calling thread is the Event Dispatch Thread, which is an illegal combination (would freeze the gui)");
			}
			
			ObjectStateDisplay display = new ObjectStateDisplay(label, obj, pauseExecution);
			EventQueue.invokeLater(display);
			if (pauseExecution) display.waitOnUser();
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private g() {}
	
	// -------------------- ObjectStateDisplay (inner class) --------------------
	
// +++ THIS CLASS IS VERY PRELIMINARY, AND NEEDS A LOT OF WORK...

	/**
* ...
	* <p>
	* Like typical Java GUI code, this class is not multithread safe:
	* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
	* This threading limitation is checked in every public method.
	
+++ actually, the above statement is false:
	--the constructor, which can be called by any thread, seems to rely on the fact that its params get turned into final fields for their thread safety
	--then this class relies on synchronozed for the run/waitOnUser interaction to safely work
Need to rethink this totally...
	
	*/
	private static final class ObjectStateDisplay implements Runnable {
		
		private static final long serialVersionUID = 1;
		
		/** Specifies how many spaces a tab char will expand to when display Strings. */
		private static final int tabSize = 4;
		
		/**
		* The next ObjectStateDisplay instance's {@link #instanceId} field.
		* <p>
		* Contract: is initialized to 0, and if this field has that value, it means that no instances have been created.
		*/
		private static final AtomicLong instanceIdNext = new AtomicLong();
		
		/** Every time any ObjectStateDisplay instance is moved, this field stores its latest x position. */
		private static int latestX = 50;
		/** Every time any ObjectStateDisplay instance is moved, this field stores its latest y position. */
		private static int latestY = 50;
// +++ use the new Preferences api to persist this stuff...
		
		/** Records this instance's Id. */
		private final long instanceId = instanceIdNext.incrementAndGet();
		
		private final String label;
		private final ObjectState objectState;
		private final boolean pauseExecution;
		
		private boolean shouldWait = true;
		/** Condition predicate for this instance's condition queue (i.e. the wait/notifyAll calls below; see "Java Concurrency in Practice" by Goetz et al p. 296ff, especially p. 299). */
		private final Object waitObject = new Object();
		
		private JFrame frame;
// +++ or use a Displayer; this will do the prefs stuff above for free too...
		private JButton continueButton;
		private JButton closeButton;
		
		private ObjectStateDisplay(String label, Object obj, boolean pauseExecution) throws IllegalStateException {
			this.label = (label != null) ? " -- " + label : "";
			this.objectState = new ObjectState(obj);
			this.pauseExecution = pauseExecution;
		}
		
		/**
* ...
		* <p>
		* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
		*/
		public void run() throws IllegalStateException {
			Check.state().edt();
			
			frame = new JFrame();
			
			frame.setTitle( makeTitle() );
			synchronized (ObjectStateDisplay.class) {
				frame.setLocation(latestX, latestY);
			}
			//setSize(600, 500);	// obsolete -- call pack below (will do an optimal setting)
			frame.getContentPane().setLayout( new FlowLayout() );
			frame.getContentPane().setBackground( new Color(225, 225, 225) );
			
			frame.addComponentListener( new ComponentAdapter() {
				public void componentMoved(ComponentEvent ce) {
					Component c = ce.getComponent();
					synchronized (ObjectStateDisplay.class) {
						ObjectStateDisplay.latestX = c.getX();
						ObjectStateDisplay.latestY = c.getY();
					}
				}
			} );
			
			frame.addWindowListener( new WindowAdapter() {
				public void windowClosing(WindowEvent we) { ObjectStateDisplay.this.handleWindowClose(); }
			} );
			
			frame.getContentPane().add( buildGui() );
			frame.pack();
			
			frame.setVisible(true);	// this call should always be last (e.g. http://developer.java.sun.com/developer/bugParade/bugs/4103289.html)
			
			if (pauseExecution) continueButton.requestFocus();
			else onContinueExecution();	// programmatically click continueButton
		}
		
		private String makeTitle() {
			return "ObjectStateDisplay #" + instanceId + label;
		}
		
		private JComponent buildGui() {
			LinePanel linePanel = LinePanel.makeVertical();
			
			linePanel.add( new JLabel("Current Thread: " + Thread.currentThread().toString()) );
			
			if (objectState.getObject() == null) {
				linePanel.add( new JLabel(objectState.getType() ) );	// note that getType works even if the underlying Object is null
			}
			else {
				linePanel.add( new JLabel("Hashcode: " + objectState.getHashCode() ) );
				linePanel.add( buildFieldsDisplay() );
			}
			
			linePanel.add( buildButtons() );
			
			return linePanel;
		}
		
		private JComponent buildFieldsDisplay() {
/*
+++ do something better than the below in the future
	--have general objects be links (buttons?) so that can click on them and bring up their states, etc
	--use a JTable, with one row per item?
		--issues with JTable:
			--seems to be column centric
			--i do NOT want column labels; can i avoid them by using some of the constructors?
			--i do NOT want every cell in the right column to be rendered the same way
				--this can actually be coded around; custom renderers are supported..
			--altho i probably DO want every cell in the left column to be a JLabel that is italic text, top centered & right justified
		--should allow the user to edit some of the table's values, like the field data?  that might be cool...
*/
			JTextArea jtextArea = new JTextArea();
			jtextArea.setEditable(false);
			jtextArea.setLineWrap(false);
			jtextArea.setTabSize(tabSize);
			jtextArea.append( objectState.toStringLabeled() );
			return new JScrollPane(jtextArea);
		}
		
		private JComponent buildButtons() {
			LinePanel linePanel = LinePanel.makeHorizontal();
			
			continueButton = new JButton("Continue Execution");
			continueButton.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					onContinueExecution();
				}
			} );
			frame.getRootPane().setDefaultButton(continueButton);
			linePanel.add(continueButton);
			
			closeButton = new JButton("Close");
			closeButton.addActionListener( new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					handleWindowClose();
				}
			} );
			linePanel.add(closeButton);
			
			return linePanel;
		}
		
		/** This method calls onContinueExecution(), and then calls dispose(). */
		private void handleWindowClose() {
			onContinueExecution();
			frame.dispose();
		}
		
		/**
		* This method always disables continueButton and sets its label to indicate that execution has proceeded.
		* Then it makes the close button be the default button.
		* Finally, if waitObject is not null, it calls waitObject.notifyAll() and then
		* dereferences waitObject (assigns it to null) so that the notification code never executes twice.
		* This method is multithread safe.
		*/
		private void onContinueExecution() {
			continueButton.setEnabled(false);
			continueButton.setText("Execution has proceeded");
			
			frame.getRootPane().setDefaultButton(closeButton);
			closeButton.requestFocus();
			
			synchronized (waitObject) {
				shouldWait = false;
				waitObject.notifyAll();
			}
		}
		
		private void waitOnUser() throws IllegalStateException, InterruptedException {
			if (EventQueue.isDispatchThread()) throw new IllegalStateException("the calling thread is the Event Dispatch Thread, which is not allowed to call this method (will freeze the gui)");
			
			synchronized (waitObject) {
				while (shouldWait) {
					waitObject.wait();
				}
			}
		}
		
	}
/*

Programmer Notes:

--how should arrays be handled?  autoexpand all the entries?

-- could add Field metadata (e.g. a string of all the Modifiers for the field);
this would be most useful if do the above gui thing, whereby you would have to click
on a button to bring up all the meta data; to do it by default in the getState method is too much

-- may also be interested in the fields from superclasses too?
(i.e. need a method called getCompleteState)
In this case, need an algorithm that recursively goes up the inheritance tree to find fields

*/
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static final class UnitTest {
		
		public static void main(final String[] args) {
			Execute.thenContinue( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				StringBuilder sb = new StringBuilder();
				sb.append("This is another...");
				d.g.s( "Some test StringBuilder", sb, true );
				d.g.s( "Some test Object", new Object(), true );
				d.g.s( null, null, false );
			} } );
		}
		
		/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
		private UnitTest() {}
		
	}
	
}
