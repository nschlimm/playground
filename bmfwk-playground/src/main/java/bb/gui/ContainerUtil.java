/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Insets;

/**
* Provides static utility methods that deal with Containers.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public final class ContainerUtil {
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
    private ContainerUtil() {}
    
	/**
	* Returns the width which is available for child Components (i.e. takes container's Insets into account).
	* <p>
	* @throws IllegalArgumentException if container == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @see javax.swing.JFrame
	*/
	public static int getAvailableWidth(Container container) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(container);
		Check.state().edt();
		
		Insets insets = container.getInsets();
		return container.getSize().width - (insets.left + insets.right);
	}
	
	/**
	* Returns the height which is available for child Components (i.e. takes container's Insets into account).
	* <p>
	* @throws IllegalArgumentException if container == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @see javax.swing.JFrame
	*/
	public static int getAvailableHeight(Container container) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(container);
		Check.state().edt();
		
		Insets insets = container.getInsets();
		return container.getSize().height - (insets.top + insets.bottom);
	}
	
}
