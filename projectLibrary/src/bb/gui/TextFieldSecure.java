/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;
import java.io.NotActiveException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.swing.JPasswordField;
import javax.swing.text.DefaultCaret;
import javax.swing.text.GapContent;
import javax.swing.text.JTextComponent;

/**
* Subclass of {@link JPasswordField} which fixes defects in its superclass to offer superior security.
* <p>
* For top security, it is critical that:
* <ol>
*  <li>
*		the text entered in the text field (or even its length) never gets revealed.
*		This class achieves this by setting the echo char to an ordinary whitespace char
*		and the caret to a {@link CaretSecure} instance.
*  </li>
*  <li>
*		a char[] be used as the underlying text storage medium (as opposed to, say, a String)
*		and that the char[] be zeroed out when use is over.
*		This class uses the {@link DocumentSecure} and {@link ContentSecure} classes to achieve this.
*  </li>
*  <li>
*		serialization never writes out the sensitive text, where it would be exposed to the world.
*		This class uses the {@link ContentSecure} classes to prevent this.
*  </li>
* </ol>
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class TextFieldSecure extends JPasswordField {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- constructor --------------------
	
	/**
	* Constructs a new TextFieldSecure instance.
	* <p>
	* @param numberCharsMax the maximum number of chars that may be typed in this instance
	* as well as its number of columns (this affects its displayed width); must be > 0
	* @throws IllegalArgumentException if numberCharsMax <= 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public TextFieldSecure(int numberCharsMax) throws IllegalArgumentException, IllegalStateException {
		// numberCharsMax checked by the DocumentSecure constructor below
		Check.state().edt();
		
		setColumns(numberCharsMax);
		setEchoChar(' ');
		setCaret( new CaretSecure() );
		addFocusListener( CaretSecure.makeFocusListener(this) );
		setDocument( new DocumentSecure(numberCharsMax) );
	}
	
	// -------------------- accessors and mutators --------------------
	
	/**
	* Returns the Document used by this instance, which is always a {@link DocumentSecure} instance.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public DocumentSecure getDocumentSecure() throws IllegalStateException {
		Check.state().edt();
		
		return (DocumentSecure) getDocument();
	}
	
	/**
	* Returns the maximum number of chars that may be typed in this instance.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public int getNumberCharsMax() throws IllegalStateException {
		// edt checked by getDocumentSecure
		
		return getDocumentSecure().getNumberCharsMax();
	}
	
	/**
	* Returns a char[] of the chars currently in this instance. Is just a renamed version of {@link #getPassword getPassword}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public char[] getInput() throws IllegalStateException {
		Check.state().edt();
		
		return getPassword();
	}
	
	/**
	* Calls <code>{@link #getDocumentSecure getDocumentSecure}.{@link DocumentSecure#zeroOutContent zeroOutContent()}</code>.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void zeroOutInput() throws IllegalStateException {
		// edt checked by getDocumentSecure
		
		getDocumentSecure().zeroOutContent();
	}
	
	// -------------------- serialization --------------------
	
	/**
	* Customizes deserialization.
	* This method manually sets the caret field to a CaretSecure instance, since Sun wrongly declared that field transient.
* (If you look in the source code of JTextComponent, there is the comment "This should be serializable",
* so confirm in a later release of the JVM whether or not you need this method--may be able to eliminate.)
	* <p>
	* @throws ClassNotFoundException if the class of a serialized object could not be found
    * @throws IOException if an I/O problem occurs
    * @throws NotActiveException if the stream is not currently reading objects
	*/
	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException, NotActiveException  {
		ois.defaultReadObject();
		setCaret( new CaretSecure() );
	}
	
	// -------------------- DocumentSecure (static inner class) --------------------
	
	/**
	* Subclass of {@link DocumentLimitedLength} which is designed for high security.
	* This class always uses a {@link ContentSecure} instance to hold its data, for the reasons described there.
	* It also offers a {@link #zeroOutContent zeroOutContent} method which hooks into the corresponding ContentSecure method.
	*/
	private static class DocumentSecure extends DocumentLimitedLength {
		
		private static final long serialVersionUID = 1;
		
		/** Constructor. */
		private DocumentSecure(int numberCharsMax) {
			super( new ContentSecure(numberCharsMax), numberCharsMax );
		}
		
		private ContentSecure getContentSecure() { return (ContentSecure) getContent(); }
		
		/** Calls <code>{@link #getContentSecure getContentSecure}.{@link ContentSecure#zeroOutContent zeroOutContent()}</code>. */
		private void zeroOutContent() { getContentSecure().zeroOutContent(); }
		
	}
	
	// -------------------- ContentSecure (static inner class) --------------------
	
	/**
	* Subclass of {@link GapContent} which is designed for high security.
	* This class subclasses GapContent, since GapContent uses a single underlying char[] for its storage
	* which {@link #zeroOutContent can be zeroed out} when use is done.
	* This class also takes care to {@link #writeObject never write out the sensitive text content} during serialization.
	*/
	private static class ContentSecure extends GapContent {
		
		private static final long serialVersionUID = 1;
		
		/**
		* Multiple calls can be made to the {@link #allocateArray allocateArray} method.
		* And this is true even tho intelligence was put into the length initially allocated by the constructor below.
		* For instance, the user could attempt to paste a big string of text into the enclosing TextFieldSecure,
		* which will cause a large reallocation request.
		* In order to be sure that all buffers that were ever allocated all get zeroed out at some point,
		* the {@link #zeroOutContent zeroOutContent} method needs to access them all, so this field stores them.
		* <p>
		* <i>Note: would not need to do this if Sun would simply have made the resize method of GapVector protected
		* so that we could override it and zero out each old array just before it is discarded...</i>
		* <p>
		* <b>Warning:</b> storing all the buffers like this makes this class unsuited for handling large size documents
		* because of all the wasted memory.
		*/
		private transient java.util.List<char[]> buffers;	// has to be lazy initialized inside allocateArray because of
		
		/** Constructor. */
		private ContentSecure(int lengthInitial) {
			super( lengthInitial + 3 );	// add 3 to give room for the implied break, the gap, plus 1 extra overtyped char
		}
		
		/**
		* First calls {@link GapContent#finalize super.finalize},
		* then calls {@link #zeroOutContent zeroOutContent} (to guarantee that the content is zeroed out before garbage collection).
		*/
		protected void finalize() throws Throwable {
			super.finalize();
			zeroOutContent();
		}
		
		/** Returns the superclass result, but before returning it, stores it inside {@link #buffers}. */
		protected Object allocateArray(int length) {
			char[] arrayNew = (char[]) super.allocateArray(length);
			if (buffers == null) buffers = new java.util.ArrayList<char[]>();
			buffers.add(arrayNew);
			return arrayNew;
		}
		
		/**
		* Writes zeroes to the underlying char[] which holds this instance's text contents.
		* (Actually, this method zeroes out every buffer that was ever created by the {@link #allocateArray allocateArray} method,
		* not just the current one.)
		*/
		private void zeroOutContent() {
			for (char[] buffer: buffers) {
				for (int i = 0; i < buffer.length; i++) {
					buffer[i] = '0';
				}
			}
		}
		
		/**
		* The default serialization behavior would write out the complete current state of this instance,
		* <i>including the highly sensitive underlying char[] that stores this instance's text content</i>.
		* This constitutes a major security breach.
		* <p>
		* To prevent this catastrophy, this method ensures that only a zeroed out char[] is ever written.
		* The underlying char[] is first copied to a local variable, then zeroed out,
		* then serialization is performed, and then it is restored from the local copy before method return.
		* <p>
		* So, users of this class need to beware that the serialized object will lose all the text state.
		*/
		private void writeObject(ObjectOutputStream oos) throws IOException {
				// copy the underlying char[] to a local variable and zero out the underlying char[] as well:
			char[] chars = (char[]) getArray();
			char[] charsTemp = new char[ chars.length ];
			for (int i = 0; i < chars.length; i++) {
				charsTemp[i] = chars[i];
				chars[i] = '0';
			}
			
				// do normal serialization (which will write the zeroed out array):
			oos.defaultWriteObject();
			
				// restore the underlying char[] to a local variable and zero out the local variable as well:
			for (int i = 0; i < chars.length; i++) {
				chars[i] = charsTemp[i];
				charsTemp[i] = '0';
			}
		}
		
	}
	
	// -------------------- CaretSecure (static inner class) --------------------
	
	/**
	* Subclass of {@link DefaultCaret} which is designed for high security.
	* This class has a {@link #paint permanently invisible caret}, so it never indicates the length of text being entered.
	*/
	private static class CaretSecure extends DefaultCaret {
		
		private static final long serialVersionUID = 1;
		
		/** Constructor. */
		private CaretSecure() {
			//setUpdatePolicy( DefaultCaret.NEVER_UPDATE );	// do NOT do this: it will cause all the text that is typed to be entered in reverse order (since the caret would then always be at the beginning of the JPasswordField, that is where new chars are inserted)
		}
		
		/**
		* Draws nothing, effectively making the caret invisible.
		* <p>
		* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
		*/
		public void paint(Graphics g) throws IllegalStateException {
			Check.state().edt();
		}
		
		/**
		* Because this instance's caret is invisible, it is important to give the user some visual cue
		* when the JTextComponent it is part of has keyboard focus.
		* This method creates a FocusListener which
		* indicates when textComponent has keyboard focus by setting its background color to white,
		* and setting its background color to light red when it lacks keyboard focus.
		* <p>
		* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
		*/
		private static FocusListener makeFocusListener(final JTextComponent textComponent) throws IllegalStateException {
			Check.state().edt();
			
			return new FocusListener() {
				public void focusGained(FocusEvent fe) { textComponent.setBackground( Color.WHITE ); }
				public void focusLost(FocusEvent fe) { textComponent.setBackground( new Color(255, 200, 200) ); }	// is a light red
			};
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None: is tested by DialogInputSecure
	
}
