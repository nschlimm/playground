/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import bb.util.ThrowableUtil;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

/**
* Subclass of {@link JDialog} which lets the user input some highly sensitive piece of text (e.g. a password).
* <p>
* The text is entered in a {@link TextFieldSecure} instance inside the dialog.
* This class allows optional header text above the TextFieldSecure (e.g. for instructions, a password hint, etc),
* as well as requires prompt text on the left to label the TextFieldSecure.
* It automatically puts a label on the right which specifies the maximum number of chars that may be entered.
* <p>
* The only public member is the static {@link #getInputSecure getInputSecure} method, which is all that normal users will use.
* For subclassing purposes, the other members of this class have protected access.
* <p>
* Like typical Java GUI code, this class's GUI parts (the constructor and all instance methods) are not multithread safe:
* they expect to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every such public method.
* Note, however, that the chief public API method, {@link #getInputSecure getInputSecure}, may be safely called by any thread.
* <p>
* @author Brent Boyer
*/
public class DialogInputSecure extends JDialog {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/**
	* A {@link TextFieldSecure} instance that is used by this class to input highly sensitive text.
	* <p>
	* @serial
	*/
	private final TextFieldSecure textFieldSecure;
	
	// -------------------- getInputSecure --------------------
	
	/**
	* Reads and returns some sensitive piece of information (e.g. a password) from a new DialogInputSecure instance.
	* The result is a char[], not a String, so that it can be zeroed out after use.
	* <p>
	* See the {@link #DialogInputSecure constructor} for documentation of all the params and throws.
	* <p>
	* <i>Note: any thread may safely call this method</i>.
	* <p>
	* @throws InterruptedException if the calling thread is interrupted will waiting inside this method
	* @throws InvocationTargetException if a problem happens while reading user input;
	* this Exception will wrap some underlying one (e.g. an IllegalArgumentException if one of the args of this method is invalid)
	*/
	public static final char[] getInputSecure(Frame parent, String title, boolean modal, String header, String prompt, int numberCharsMax) throws InterruptedException, InvocationTargetException {
		DialogTask dialogTask = new DialogTask(parent, title, modal, header, prompt, numberCharsMax);
		SwingUtil.invokeNow(dialogTask);
		return dialogTask.getUserInput();
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Constructs a new DialogInputSecure instance.
	* <p>
	* @param parent the parent Frame that this JDialog will be attached to; may be null
	* @param title the dialog's title; may be null
	* @param modal specifies whether or not this JDialog is a modal dialog
	* @param header optional text (e.g. instructions, a password hint) displayed above the TextFieldSecure;
	* this text will be put into a JLabel, so it may be html formatted; may be null
	* @param prompt mandatory text displayed next to the TextFieldSecure;
	* this text will be put into a JLabel, so it may be html formatted; must be non-blank
	* @param numberCharsMax the maximum number of chars that may be typed in the TextFieldSecure
	* as well as the number of columns in the TextFieldSecure (this affects its displayed width); must be > 0
	* @throws IllegalArgumentException if prompt is blank; numberCharsMax <= 0
	*/
	protected DialogInputSecure(Frame parent, String title, boolean modal, String header, String prompt, int numberCharsMax) throws IllegalArgumentException {
		super(parent, title, modal);
		
		Check.arg().notBlank(prompt);
		Check.arg().positive(numberCharsMax);
		
		textFieldSecure = new TextFieldSecure(numberCharsMax);
		
		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		getContentPane().add( buildGui(header, prompt, numberCharsMax) );
		pack();
		setLocationRelativeTo(parent);	// MUST do this call after have properly sized the dialog with pack
		setVisible(true);
	}
	
	// -------------------- buildXXX --------------------
	
	protected JComponent buildGui(String header, String prompt, int numberCharsMax) {
		LinePanel linePanel = LinePanel.makeVertical();
		if (header != null) {
			linePanel.add( new JLabel(header) );
		}
		linePanel.add( buildSecureEntry(prompt, numberCharsMax) );
		linePanel.add( buildButtons() );
		return linePanel;
	}
	
	protected JComponent buildSecureEntry(String prompt, int numberCharsMax) {
		LinePanel linePanel = LinePanel.makeHorizontal();
		linePanel.add( new JLabel(prompt) );
		linePanel.add( textFieldSecure );
		linePanel.add( new JLabel("(" + numberCharsMax + " chars max)") );
		return linePanel;
	}
	
	protected JComponent buildButtons() {
		LinePanel linePanel = LinePanel.makeHorizontal();
		
		JButton helpButton = new JButton("Help");
		helpButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				String helpText =
					"This dialog allows you to enter text (e.g. a password) in a very secure manner." + "\n" +
					"\n" +
					"For top security, when you type in this dialog's text field, no visible characters are echoed." + "\n" +
					"Unlike some other interfaces, which at least echo some generic character like an asterisk ('*')," + "\n" +
					"this dialog echoes nothing, since that would give away the length of the text." + "\n" +
					"Furthermore, there is no caret (vertical line indicating text position) in the text field either," + "\n" +
					"since that too might give away the length of the text." + "\n" +
					"\n" +
					"Because of the above, it is important to give the user some visual cue when the text field has keyboard focus." + "\n" +
					"(Keyboard focus means that if you type on the keyboard, the text is entered in the component" + "\n" +
					" which has the focus, as opposed to some other text component which might be present in the interface.)" + "\n" +
					"This dialog indicates when its text field has keyboard focus by setting the field's background color to white," + "\n" +
					"and it indicates when it lacks keyboard focus by setting its background color to light red." + "\n" +
					"\n" +
					"If you type in the text field and then click on the OK button" + "\n" +
					"(or type the enter/return key on some platforms), then this dialog disappears" + "\n" +
					"but the text that you typed in (except for any possible enter/return key)" + "\n" +
					"is retained (and should be read by some other part of the program)." + "\n" +
					"\n" +
					"Else, if you type in the text field and then click on the Cancel button," + "\n" +
					"then this dialog disappears and the text that you typed in is lost.";
				JOptionPane.showMessageDialog(DialogInputSecure.this, helpText, "DialogInputSecure Help", JOptionPane.INFORMATION_MESSAGE);
			}
		} );
		linePanel.add( helpButton );
		
		JButton okButton = new JButton("OK");
		okButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				dispose();
			}
		} );
		this.getRootPane().setDefaultButton(okButton);
		linePanel.add( okButton );
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				textFieldSecure.zeroOutInput();
				textFieldSecure.setText("");	// need to call this as well, since the above does not affect the position indices or anything
				dispose();
			}
		} );
		linePanel.add( cancelButton );
		
		return linePanel;
	}
	
	// -------------------- DialogTask (static inner class) --------------------
	
	/**
	* Bridges the requirement of {@link #getInputSecure getInputSecure} that any thread can call that method
	* with the requirement of the constructor that only EventQueue's dispatch thread can call it.
	*/
	private static class DialogTask implements Runnable {
		
		private final Frame parent;
		private final String title;
		private final boolean modal;
		private final String header;
		private final String prompt;
		private final int numberCharsMax;
		private char[] userInput;
		
		private DialogTask(Frame parent, String title, boolean modal, String header, String prompt, int numberCharsMax) {
			this.parent = parent;
			this.title = title;
			this.modal = modal;
			this.header = header;
			this.prompt = prompt;
			this.numberCharsMax = numberCharsMax;
		}
		
		public synchronized void run() {
			Check.state().edt();
			
			DialogInputSecure dialog = null;
			try {
				dialog = new DialogInputSecure(parent, title, modal, header, prompt, numberCharsMax);
				userInput = dialog.textFieldSecure.getInput();
			}
			// Note: no need to worry about any Exceptions, since the call to SwingUtil.invoke in getInputSecure above will throw a InvocationTargetException if any occur
			finally {
				if (dialog != null) dialog.textFieldSecure.zeroOutInput();
			}
		}
		
		public synchronized char[] getUserInput() throws IllegalStateException {
			Check.state().notNull(userInput);
			return userInput;
		}
		
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
			Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				test_getInputSecure();
				test_serialization();	// this method used to fail due to a bug that sun fixed in 1.5_02: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6190713
				
				return null;
			} } );
		}
		
		private static void test_getInputSecure() throws Exception {
			String instructions =
				"<html>" +
					"<p>" +
						"Type <i>anything</i> that you wish in the text field below." + "<br>" +
						"When you hit the OK button, this dialog will disappear" + "<br>" +
						"and the text that you typed will be printed to System.out." +
					"</p>" +
				"</html>";
			char[] chars = DialogInputSecure.getInputSecure(null, "UnitTest Of DialogInputSecure.getInputSecure", true, instructions, "Text input:", 5);
			String inputText = new String( chars );
			System.out.println("The text that was input to the DialogInputSecure is:");
			System.out.println(inputText);
		}
		
// +++ what am hoping to confirm in this method is that the deserialized dialog contains no text initially
// (i.e. that the serialization features promised in TextFieldSecure/ContentSecure work as described)
// and that the deserialized dialog otherwise works as before (that it has an invisible caret etc)
// UNFORTUNATELY, WHEN I LAST RAN THIS METHOD ON 2009-03-06, IT SEEMS THAT THE DESERIALIZED VERSION STILL HAS THE DATA INSIDE.
		private static void test_serialization() throws Exception {
			EventQueue.invokeAndWait( new Runnable() {
				public void run() {
					try {
						DialogInputSecure dialog = new DialogInputSecure(null, "UnitTest Of DialogInputSecure serialization", true, null, "Text input:", 5);
						String text = new String( dialog.textFieldSecure.getPassword() );
						System.out.println("text entered in the unserialized dialog: " + text);
						dialog.dispose();
						
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						ObjectOutputStream oos = new ObjectOutputStream( baos );
						oos.writeObject( dialog );
						ObjectInputStream ois = new ObjectInputStream( new ByteArrayInputStream( baos.toByteArray() ) );
						DialogInputSecure dialogDeserialized = (DialogInputSecure) ois.readObject();
						dialogDeserialized.setTitle("This is the deserialized dialog");
						dialogDeserialized.setVisible(true);
						String textDeserialized = new String( dialogDeserialized.textFieldSecure.getPassword() );
						System.out.println("text present inside the deserialized dialog: " + textDeserialized);
						dialogDeserialized.dispose();
					}
					catch (Throwable t) {
						throw ThrowableUtil.toRuntimeException(t);
					}
				}
			} );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
