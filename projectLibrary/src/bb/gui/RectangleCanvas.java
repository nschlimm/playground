/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ subclassing Canvas makes this a AWT widget; need to have it subclass JPanel or something to be make it be Swing...
	--see how I did this in my GraphPriceVolumeAbstract class

+++ possibly related to the above: I am seeing it cause problems in
	websiteTools\src\bb\color\ColorControl
because it can grow in size as the window is increased, but then it fails to shrink back when the window is made smaller
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.JComponent;

/**
* Subclass of <code>Canvas</code> which simply draws a rectangular shape.
* <p>
* Its width, height, and foreground color are all accessible and settable via the existing methods of Component.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class RectangleCanvas extends Canvas {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* Simply passes each arg to the corresponding set method.
	* <p>
	* @throws IllegalArgumentException if any of the set methods objects
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public RectangleCanvas(int width, int height, Color foreground) throws IllegalArgumentException, IllegalStateException {
		Check.state().edt();
		
		setSize(width, height);
		setForeground(foreground);
	}
		
	// -------------------- paint --------------------
	
	/**
	* Overrides to do custom painting (here, draws a rectangle).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void paint(Graphics g) throws IllegalStateException {
		Check.state().edt();
		
		g.fillRect(0, 0, getWidth(), getHeight());
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				new Displayer( buildContents(), UnitTest.class.getSimpleName(), UnitTest.class.getName() );
			} } );
		}
		
		private static JComponent buildContents() {
			LinePanel linePanel = LinePanel.makeVertical();
			linePanel.add( new RectangleCanvas(50, 10, Color.RED) );
			linePanel.add( new RectangleCanvas(50, 10, Color.GREEN) );
			linePanel.add( new RectangleCanvas(50, 10, Color.BLUE) );
			return linePanel;
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
