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
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
* Subclass of JDialog which is used to notify the user of a caught Throwable.
* <p>
* Normally, to display an error to the user, one might simply use code like
* <pre><code>
	String message =
		"Program encountered an error." + "\n" +
		"Type: " + t.getClass().getName() + "\n" +
		"Message: " + t.getMessage() + "\n";
	JOptionPane.showMessageDialog(frame, message, "SlideShow Error", JOptionPane.ERROR_MESSAGE);
* </code></pre>
* <p>
* As an alternative, using this class offers the following additional functionality:
* <ol>
*  <li>the dialog may/may not be modal</li>
*  <li>the user may show/hide the Throwable's stacktrace</li>
*  <li>the user may chose to exit the JVM</li>
* </ol>
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
*/
public class ThrowableDialog extends JDialog {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/**
	* The Frame or Dialog that owns this instance; may be null.
	* @serial
	*/
	private final Component owner;
	
	/** @serial */
	private String message;
	
	/** @serial */
	private Throwable throwable;
	
	/** @serial */
	private boolean showOnlyBasicInfo = true;
	
	// -------------------- constructors --------------------
	
	/**
	* Constructs a new ThrowableDialog instance.
	* <p>
	* @param owner the owner Dialog that this JDialog will be attached to; may be null
	* @param title the dialog's title
	* @param modal specifies whether or not this JDialog is a modal dialog
	* @param throwable the Throwable to report
	* <p>
	* @throws IllegalArgumentException if message is blank; throwable == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public ThrowableDialog(Dialog owner, String title, boolean modal, String message, Throwable throwable) throws IllegalArgumentException, IllegalStateException {
		super(owner, title, modal);
		Check.state().edt();
		
		this.owner = owner;
		init(title, modal, message, throwable);
	}
	
	/**
	* Constructs a new ThrowableDialog instance.
	* <p>
	* @param owner the owner Frame that this JDialog will be attached to; may be null
	* @param title the dialog's title
	* @param modal specifies whether or not this JDialog is a modal dialog
	* @param throwable the Throwable to report
	* <p>
	* @throws IllegalArgumentException if message is blank; throwable == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public ThrowableDialog(Frame owner, String title, boolean modal, String message, Throwable throwable) throws IllegalArgumentException, IllegalStateException {
		super(owner, title, modal);
		Check.state().edt();
		
		this.owner = owner;
		init(title, modal, message, throwable);
	}
	
	/**
	* Initialization routine called by the 2 public constructors which contains code common to them.
	* This method was written because both those constructors must do a call to super as their first line,
	* so they cannot do a call to this.
	* <p>
	* @throws IllegalArgumentException if message is blank; throwable == null
	*/
	protected void init(String title, boolean modal, String message, Throwable throwable) throws IllegalArgumentException {
		playSound();
		setMessage(message);
		setThrowable(throwable);
		refresh();
	}
	
	// -------------------- accessors and mutators --------------------
	
	/**
	* Accessor for the message field.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public String getMessage() throws IllegalStateException {
		Check.state().edt();
		
		return message;
	}
	
	/**
	* Mutator for the message field.
	* <p>
	* @throws IllegalArgumentException if message is blank
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setMessage(String message) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notBlank(message);
		Check.state().edt();
		
		this.message = message;
	}
	
	/**
	* Accessor for the throwable field.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Throwable getThrowable() throws IllegalStateException {
		Check.state().edt();
		
		return throwable;
	}
	
	/**
	* Mutator for the throwable field.
	* <p>
	* @throws IllegalArgumentException if throwable == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setThrowable(Throwable throwable) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(throwable);
		Check.state().edt();
		
		this.throwable = throwable;
	}
	
	// -------------------- build methods --------------------
	
	protected void refresh() {
		getContentPane().removeAll();
		getContentPane().add( buildGui() );
		pack();
		setLocationRelativeTo(owner);	// MUST do this call after have properly sized the dialog with pack
		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		setVisible(true);
	}
	
	protected JComponent buildGui() {
		LinePanel linePanel = LinePanel.makeVertical();
		linePanel.add( buildInfo() );
		linePanel.add( buildButtons() );
		return linePanel;
	}
	
	protected JComponent buildInfo() {
		LinePanel linePanel = LinePanel.makeVertical();
		
		linePanel.add( new JLabel(getMessage()) );
		
		if (showOnlyBasicInfo) {
			linePanel.add( new JLabel("Type:  " + getThrowable().getClass().getName()) );
			linePanel.add( new JLabel("Message:  " + getThrowable().getMessage()) );
		}
		else {
			JTextArea jtextArea = new JTextArea();
			jtextArea.setEditable(false);
			jtextArea.setLineWrap(false);
			jtextArea.setTabSize(4);
			jtextArea.setText( ThrowableUtil.toString(getThrowable()) );	// must do this last -- in particular, must call setTabSize before this
			linePanel.add( new JScrollPane(jtextArea) );
		}
		
		return linePanel;
	}
	
	protected JComponent buildButtons() {
		LinePanel linePanel = LinePanel.makeHorizontal();
		
		JButton dismissButton = new JButton("Dismiss");
		dismissButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				ThrowableDialog.this.dispose();
			}
		} );
		this.getRootPane().setDefaultButton(dismissButton);
		linePanel.add( dismissButton );
		
		JButton detailsButton = new JButton( (showOnlyBasicInfo ? "Details" : "Hide details") );
		detailsButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				showOnlyBasicInfo = !showOnlyBasicInfo;
				refresh();
			}
		} );
		linePanel.add( detailsButton );
		
		JButton exitButton = new JButton("Exit Program");
		exitButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				programExit();
			}
		} );
		linePanel.add( exitButton );
		
		return linePanel;
	}
	
	// -------------------- misc methods --------------------
	
	/**
	* This implementation calls <code>{@link Sounds#playErrorMinor()}</code>.
	* Subclasses may override to play a different sound, or nothign at all.
	*/
	protected void playSound() {
		Sounds.playErrorMinor(false);
	}
	
	/**
	* This implementation calls <code>{@link System#exit System.exit}(1)</code>.
	* Subclasses may override to call a program specific shutdown sequence instead.
	*/
	protected void programExit() {
		System.exit(1);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				new TestDialog();
			} } );
		}
		
		private static class TestDialog extends ThrowableDialog {
			
			private static final long serialVersionUID = 1;
			
			private static final String message =
				"<html>" +
					"ThrowableDialog.UnitTest encountered an error.<br>" +
					"<i>This was deliberately programmed:</i> can reasonably click any button below." +
				"</html>";
				
			private TestDialog() {
				super((Frame) null, "ThrowableDialog.UnitTest", true, message, new RuntimeException("purposely generated RuntimeException..."));
			}
			
			protected void programExit() {
				System.err.println("Below is a StackTrace of the Throwable that was caught:");
				getThrowable().printStackTrace(System.err);
				System.exit(1);
			}
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
