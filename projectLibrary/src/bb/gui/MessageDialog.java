/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

/**
* Subclass of Dialog which displays a title & message(s), as well as an "OK" button.
* <p>
* This class was written because AWT does not have an equivalent to {@link javax.swing.JOptionPane}.
* <p>
* Like typical Java GUI code, this class's GUI parts (the constructor and all instance methods) are not multithread safe:
* they expect to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* Note, however, that the chief public API method, <code>display</code>, may be safely called by any thread.
* <p>
* @author Brent Boyer
*/
public class MessageDialog extends Dialog {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- display --------------------
	
	/**
	* Creates and displays a new MessageDialog instance.
	* <p>
	* The modal param specifies two different things.
	* First, it controls the modality of the dialog that is displayed..
	* Second, it determines the execution mode.
	* In particular, if modal is true, then this method executes synchronously (i.e. it blocks until the user has dismissed the dialog).
	* Otherwise, it executes asynchronously (i.e. it returns immediately after displaying the dialog).
<!--
+++ should i have 2 params that specify each effect separately?
-->
	* <p>
	* This method may be called by any thread because it internally submits all of its work to {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
	* <p>
	* @throws InterruptedException if the calling thread is interrupted will waiting inside this method
	* @throws InvocationTargetException if a problem happens while creating and displaying the dialog;
	* this Exception will wrap the underlying Throwable (e.g. an IllegalArgumentException if one of the args of this method is invalid)
	*/
	public static void display(final Window owner, final String title, final Dialog.ModalityType type, final String... messages) throws InterruptedException, InvocationTargetException {
		Runnable displayer = new Runnable() {
			public void run() {
				new MessageDialog(owner, title, type, messages);
			}
		};
		switch (type) {
			case APPLICATION_MODAL:
			case DOCUMENT_MODAL:
			case TOOLKIT_MODAL:
				SwingUtil.invokeNow(displayer);
				break;
			case MODELESS:
				EventQueue.invokeLater(displayer);
				break;
			default:
				throw new IllegalStateException("type = " + type + " is unknown");
		}
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Calls <code>super(owner, title, modal)</code>, then adds each message in the array as a separate Label
	* and an "OK" Button on the bottom, then shows this instance.
	* <p>
	* @throws IllegalArgumentException if messages is null or empty
	*/
	private MessageDialog(Window owner, String title, Dialog.ModalityType type, String... messages) throws IllegalArgumentException {
		super(owner, title, type);
		
		Check.arg().notEmpty(messages);
		
		buildContents(messages);
		pack();
		setVisible(true);
	}
	
	// -------------------- buildXXX --------------------
	
	private void buildContents(String... messages) {
		add( buildMessageLines(messages), BorderLayout.NORTH );
		add( buildButtons(), BorderLayout.SOUTH );
	}
	
	private Component buildMessageLines(String... messages) {
		Panel panel = new Panel();
		panel.setLayout( new GridLayout(messages.length, 1) );
		for (String message : messages) {
			panel.add( new Label(message) );
		}
		return panel;
	}
	
	private Component buildButtons() {
		Button b = new Button("OK");
		b.addActionListener(
			new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					MessageDialog.this.dispose();
				}
			}
		);
		//return b;
			// Buttons get stretched by many layouts, so embed inside a flexible panel instead:
		Panel panel = new Panel();
		panel.add(b);
		return panel;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.thenContinue( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				test_synchronousMode();
				test_asynchronousMode();	// this method used to fail due to a bug that sun fixed in 1.5_02: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6190713
				
				return null;
			} } );
		}
		
		private static void test_synchronousMode() throws Exception {
			display(null, "test_synchronousMode", Dialog.ModalityType.DOCUMENT_MODAL, "This test will execute in synchronous mode.", "In other words, the currently executing method will not return until this dialog is dismissed");
			System.out.println("You should only see this line on the console AFTER the test_synchronousMode dialog is dismissed");
		}
		
		private static void test_asynchronousMode() throws Exception {
			display(null, "test_asynchronousMode", Dialog.ModalityType.MODELESS, "This test will execute in asynchronous mode; you can dismiss this dialog whenever you want");
			System.out.println("You should see this line on the console as soon as the test_asynchronousMode dialog is displayed");
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
