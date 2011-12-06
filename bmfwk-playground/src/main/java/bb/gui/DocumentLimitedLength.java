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
import java.util.concurrent.Callable;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.GapContent;
import javax.swing.text.PlainDocument;

/**
* Subclass of {@link PlainDocument} which limits the number of chars that it will contain.
* <p>
* One use of this class is to serve as the model of a JTextComponent like JTextField
* when the text needs to be constrained to a maximum length.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @see <a href="http://forum.java.sun.com/thread.jspa?forumID=57&threadID=404834">Java forum posting #1</a>
* @see <a href="http://forum.java.sun.com/thread.jspa?forumID=57&threadID=340706">Java forum posting #2</a>
* @author Brent Boyer
*/
public class DocumentLimitedLength extends PlainDocument {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- fields --------------------
	
	/**
	* Maximum number of chars that this instance will hold.
	* <p>
	* Contract: is > 0.
	* <p>
	* @serial
	*/
	private final int numberCharsMax;
	
	// -------------------- constructor --------------------
	
	/**
	* Constructs a new DocumentLimitedLength instance.
	* <p>
	* @param numberCharsMax specifies the maximum number of chars that this instance will hold; must be > 0
	* @throws IllegalArgumentException if numberCharsMax <= 0
	*/
	public DocumentLimitedLength(int numberCharsMax) throws IllegalArgumentException {
		this( new GapContent(), numberCharsMax );
	}
	
	/**
	* Constructs a new DocumentLimitedLength instance.
	* <p>
	* @param content a {@link javax.swing.AbstractDocument.Content} instance that contains initial text content
	* @param numberCharsMax specifies the maximum number of chars that this instance will hold; must be > 0
	* @throws IllegalArgumentException if content == null; numberCharsMax <= 0; content.length() > numberCharsMax
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public DocumentLimitedLength(AbstractDocument.Content content, int numberCharsMax) throws IllegalArgumentException, IllegalStateException {
		super(content);
		
		Check.arg().notNull(content);
		Check.arg().positive(numberCharsMax);
		if (content.length() > numberCharsMax) throw new IllegalArgumentException("content.length() = " + content.length() + " > numberCharsMax = " + numberCharsMax);
		Check.state().edt();
		
		this.numberCharsMax = numberCharsMax;
	}
	
	// -------------------- accessors and mutators --------------------
	
	/**
	* Returns the maximum number of chars that may be typed in this instance.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public int getNumberCharsMax() throws IllegalStateException {
		Check.state().edt();
		
		return numberCharsMax;
	}
	
	/**
	* Returns all the text contained in this instance.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws RuntimeException (or some subclass) if any error occurs; this may merely wrap some other underlying Throwable
	*/
	public String getText() throws IllegalStateException, RuntimeException {
		Check.state().edt();
		
		try {
			return getText(0, getLength());
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);	// this line should never get called, but need the catch block to suppress the compiler from forcing a throws BadLocationException declaration
		}
	}
// +++ why does Document lack this method?  must have been an oversight by Sun...
	
	// -------------------- Document methods --------------------
	
	/**
	* First calls <code>super.insertString(offset, s, attributeSet)</code>.
	* Then checks to see if the new length exceeds {@link #numberCharsMax}, and if it does,
	* sounds a beep and then truncates the final chars so that the length ends up equaling numberCharsMax.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws BadLocationException if offset is not a valid position within the document
	*/
	public void insertString(int offset, String s, AttributeSet attributeSet) throws IllegalStateException, BadLocationException {
		// all args checked by the call to super.insertString below
		Check.state().edt();
		
		super.insertString(offset, s, attributeSet);
		
		if (getLength() > numberCharsMax) {
			UIManager.getLookAndFeel().provideErrorFeedback(null);
			remove(numberCharsMax, getLength() - numberCharsMax);
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
			Execute.usingEdt( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				DocumentLimitedLength doc = new DocumentLimitedLength(10);
				doc.insertString(0, "abcdefghij", null);	// add first 10 chars of the alphabet
				System.out.println("doc after step #1: " + doc.getText());
				doc.insertString(5, "123", null);	// add first 3 natural numbers starting at index 5
				System.out.println("doc after step #2: " + doc.getText());
				System.out.println("did insertString work as expected: " + doc.getText().equals("abcde123fg") );
				
				return null;
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
