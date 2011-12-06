/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;
		
import bb.util.Check;
import java.awt.EventQueue;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
* This class provides static utility methods that deal with MouseEvents.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class MouseUtil {
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private MouseUtil() {}
	
	/**
	* Determines whether the <code>MouseEvent</code> represents a click of the leftmost mouse button (buttion #1).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static boolean isLeftMouseButtonClick(MouseEvent me) throws IllegalStateException {
		Check.state().edt();
		
		return ((me.getModifiers() & InputEvent.BUTTON1_MASK) != 0);
	}
	
	/**
	* Determines whether the <code>MouseEvent</code> represents a click of the middle mouse button (buttion #2).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static boolean isMiddleMouseButtonClick(MouseEvent me) throws IllegalStateException {
		Check.state().edt();
		
		return ((me.getModifiers() & InputEvent.BUTTON2_MASK) != 0);
	}
	
	/**
	* Determines whether the <code>MouseEvent</code> represents a click of the rightmost mouse button (buttion #3).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static boolean isRightMouseButtonClick(MouseEvent me) throws IllegalStateException {
		Check.state().edt();
		
		return ((me.getModifiers() & InputEvent.BUTTON3_MASK) != 0);
	}
	
	/**
	* Determines whether the <code>MouseEvent</code> represents a single click.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static boolean isSingleClick(MouseEvent me) throws IllegalStateException {
		Check.state().edt();
		
		return (me.getClickCount() == 1);
	}
	
	/**
	* Determines whether the <code>MouseEvent</code> represents a double click.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static boolean isDoubleClick(MouseEvent me) throws IllegalStateException {
		Check.state().edt();
		
		return (me.getClickCount() == 2);
	}
	
}
