/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Window;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.swing.GroupLayout;
import javax.swing.JLabel;

/**
* Provides static utility methods that deal with <code>Component</code>s.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class ComponentUtil {
	
	// -------------------- xxxComponentTree --------------------
	
	/**
	* Drills depth first down the Component tree rooted at component, returning a Set of all the Components that were encountered.
	* <p>
	* The result is based off of an {@link IdentityHashMap}, <b>so reference-equality semantics are used instead of equals</b>.
	* Thus, a Component is always added to the result, unless that exact same reference has already been encountered in the tree.
	* (It is possible in theory, if unlikely in practice, that the Component tree contains a given Component more than once.
	* For example, a JLabel instance could be added to more than one JPanel.)
	* <p>
	* Altho each Component is added to the result in the order that it is encountered during the search,
	* the iteration order of the result is essentially random as per the behavior of IdentityHashMap.
	* <p>
	* Contract: the result is never null, and always contains at least component itself.
	*/
	public static Set<Component> getComponentTree(Component component) throws IllegalArgumentException, IllegalStateException {
		// component and edt checked by appendComponentTree below
		
		Set<Component> set = Collections.newSetFromMap( new IdentityHashMap<Component,Boolean>() );
		appendComponentTree(component, set);
		return set;
	}
	
	private static void appendComponentTree(Component component, Set<Component> set) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(component);
		Check.arg().notNull(set);
		Check.state().edt();
		
		set.add(component);
		
		if (component instanceof Container) {
			Container parent = (Container) component;
			for (Component child : parent.getComponents()) {
				appendComponentTree(child, set);
			}
		}
	}
	
	/**
	* Drills depth first down the Component tree rooted at component.
	* At each Component node in that tree, paints the edges of that rectangle which describes the location and size of the node.
	* <p>
	* Motivation: this method can be invaluable in debugging complex form layouts,
	* as it can show what components (especially normally invisible ones like Panels or other Containers) are taking up a lot of space.
	* <p>
	* To see how this method can be used, suppose that a {@link Displayer} instance is being used to show the GUI.
	* Then the user could subclass Displayer like below to override its paint method to additionally call this method:
	* <pre><code>
		public class Displayer2 extends Displayer {
			
			private static final long serialVersionUID = 0;
			
			public Displayer2(JComponent jcomponent, String title, String prefsName) throws IllegalArgumentException, IllegalStateException {
				super(jcomponent, title, prefsName);
			}
			
			&#64;Override public void paint(Graphics g) throws IllegalArgumentException, IllegalStateException {
				super.paint(g);
				ComponentUtil.paintComponentTree(this, g);	// CRITICAL: do this last, since do not want other painting events to overpaint the Component outlines done here
			}
			
		}
	* </code></pre>
	* The code above will ensure that the bounds of every Component in the GUI is drawn, because it gets the Component tree from a top level window.
	* <p>
	* A top level window need not be used: a lower level Component in the tree could also be supplied to this method.
	* In this case, the outlines of only those Components inside the supplied one will be drawn.
	* Unfortunately, there could be subsequent painting events which overpaint these outlines.
	* For that reason, using the top level window, as in the code above, may work best.
	*/
	public static void paintComponentTree(Component component, Graphics g) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(g);
		// component and edt checked by getComponentTree below
		
		boolean isWindow = component instanceof Window;	// CRITICAL: need to detect if component is a top level window, because if it is, its location is in terms of screen coordinates, which is unlike all the other coordinate systems (including that for g) which are defined relative to something within the window
		int x = isWindow ? 0 : component.getX();
		int y = isWindow ? 0 : component.getY();
		
		g.drawRect( x, y, component.getWidth(), component.getHeight() );
		
		if (component instanceof Container) {
			Container parent = (Container) component;
			for (Component child : parent.getComponents()) {
				Graphics g2 = null;
				try {
					g2 = g.create(x, y, g.getClipBounds().width, g.getClipBounds().height);	// CRITICAL: have to change the coordinate origin to be where child expects it to be; also must create a new Graphics instance (as opposed to modifying g) because otherwise subsequent loops do not see the original g (which is what they need) but will see the effects of previous loops (which is very bad)
					paintComponentTree(child, g2);
				}
				finally {
					if (g2 != null) g2.dispose();	// CRITICAL; see Graphics.dispose javadocs, where they recommend manually calling this as soon as are done using it if it was obtained by a call to create like we did here
				}
			}
		}
	}
	
	// -------------------- getParentWindow --------------------
	
	/**
	* Returns component itself if it is a Window,
	* or the first (should only be one?) parent Window which contains component if it is inside a Window,
	* or null if it is not inside a Window.
	* <p>
	* @throws IllegalArgumentException if component == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static Window getParentWindow(Component component) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(component);
		Check.state().edt();
		
		for ( ; component != null; component = component.getParent()) {
			if (component instanceof Window) return (Window) component;
		}
		return null;
	}
	
	// -------------------- label --------------------
	
	/**
	* "Labels" component.
	* In particular, returns a new {@link LinePanel} which contains a single row of children:
	* a new JLabel with labelText on the left, followed by component on the right.
	* <p>
	* Motivation: this method was originally written make the labelling of JTextFields, a typical requirement in GUI forms, be very convenient.
	* <p>
	* <b>Warning:</b> this method is unsuitable for complex forms, because these typically need inter-row alignment among the labels to visually look right.
	* For situations like these, more complex techniques must be used (e.g. {@link GroupLayout}, <a href="https://tablelayout.dev.java.net/">TableLayout</a>).
	* <p>
	* @throws IllegalArgumentException if labelText is blank; component == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static LinePanel label(String labelText, Component component) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notBlank(labelText);
		Check.arg().notNull(component);
		Check.state().edt();
		
		LinePanel linePanel = LinePanel.makeHorizontal();
		linePanel.add( new JLabel(labelText) );
		linePanel.add( component );
		return linePanel;
	}
	
	// -------------------- setHeightMaxToPref --------------------
	
	/**
	* Sets just the maximum height of component to its preferred height; the maximum width is left unchanged.
	* <p>
	* A great example of where this method is needed is JTextField: for some strange reason, the designer of this widget did not make its height rigid (e.g. like a JLabel).
	* Consequently, when placed into certain forms and with certain layout managers, JTextFields can be vertically stretched.
	* This is bad because it deceitfully makes them look like a JTextArea, and also causes them to eat up valuable free space that might be more usefully devoted
	* to another component that could actually use the space (e.g. a JTextArea).
	* <p>
	* @throws IllegalArgumentException if component == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static void setHeightMaxToPref(Component component) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(component);
		Check.state().edt();
		
		Dimension pref = component.getPreferredSize();
		Dimension max = component.getMaximumSize();
		component.setMaximumSize( new Dimension( max.width, pref.height) );
	}
	
	/**
	* Sets just the maximum width of component to its preferred height; the maximum height is left unchanged.
	* <p>
	* @throws IllegalArgumentException if component == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static void setWidthMaxToPref(Component component) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(component);
		Check.state().edt();
		
		Dimension pref = component.getPreferredSize();
		Dimension max = component.getMaximumSize();
		component.setMaximumSize( new Dimension( pref.width, max.height) );
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ComponentUtil() {}
	
}
