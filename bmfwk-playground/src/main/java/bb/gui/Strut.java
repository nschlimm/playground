/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;

/**
* This class is a Component that is always a {@link #strutLength specified length}
* along a {@link #axis specified direction}.
* Unless it is explicitly changed by the programmer
* (via the {@link #setAxis setAxis} and {@link #setStrutLength setStrutLength} methods),
* the strut's orientation and length will always be kept fixed.
* In particular, attempts to change an instance's length (e.g. via the {@link #reshape reshape} method) will be rebuffed.
* <p>
* The strut's behavior in the direction perpendicular to the orientation axis is more flexible.
* First, its extent (max, preferred, and min) along this direction may be specified (either in a constructor or changed later).
* Secondly, this extent may be changed via the {@link #reshape reshape} method.
* <p>
* This Component is useful in adding space between other visible Components.
* The sole purpose of this class is to offer an AWT alternative to
* the strut methods available in Swing's {@link javax.swing.Box Box} class.
* <p>
* This class was designed to work with Java 1.1 and forward.
<!--
* This class was designed to work with the oldest JVMs (even 1.0), so it uses some deprecated APIs.

Have commented out the above, as well as several methods below, because do not want to generate compiler warnings.
-->
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class Strut extends Component {
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	/** Specifies the x-axis (i.e. horizontal dimension). */
	public static final int xAxis = 0;	// is same as BoxLayout.X_AXIS (from looking at its source code); we do not use it, however, since we want to use this class if swing is absent
	
	/** Specifies the y-axis (i.e. vertical dimension). */
	public static final int yAxis = 1;	// is same as BoxLayout.Y_AXIS (from looking at its source code); we do not use it, however, since we want to use this class if swing is absent
	
	// -------------------- instance fields --------------------
	
	/**
	* Stores the orientation axis of this strut.
	* @serial
	*/
	protected int axis;
	
	/**
	* Stores the length of this strut along the {@link #axis orientation axis}.
	* @serial
	*/
	protected int strutLength;
	
	/**
	* Stores the maximum transverse (i.e. perpendicular direction) extent of this strut.
	* @serial
	*/
	protected int maxTransverse;
	
	/**
	* Stores the preferred transverse (i.e. perpendicular direction) extent of this strut.
	* @serial
	*/
	protected int preferredTransverse;
	
	/**
	* Stores the minimum transverse (i.e. perpendicular direction) extent of this strut.
	* @serial
	*/
	protected int minTransverse;
	
	// -------------------- constructors --------------------
	
	/** Simply calls <code>this(axis, strutLength, 0, null)</code>. */
	public Strut(int axis, int strutLength) throws IllegalArgumentException {
		this(axis, strutLength, 0, null);
	}
	
	/** Simply calls <code>this(axis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color)</code>. */
	public Strut(int axis, int strutLength, int preferredTransverse, Color color) throws IllegalArgumentException {
		this(axis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color);
	}
	
	/**
	* Constructs a new Strut instance with the specified
	* strut orientation axis, length, max-preferred-minimum transverse sizes, and color.
	* <p>
	* @param axis the orientation axis of this strut (i.e. the direction in which it will be held rigid)
	* @param strutLength the length of the strut along the orientation axis
	* @param maxTransverse the maximum transverse (i.e. perpendicular direction) extent of this strut
	* @param preferredTransverse the preferred transverse (i.e. perpendicular direction) extent of this strut
	* @param minTransverse the minimum transverse (i.e. perpendicular direction) extent of this strut
	* @param color the foreground Color of this strut; if null its parent's foreground Color will be used
	* @throws IllegalArgumentException if axis is neither {@link #xAxis} nor {@link #yAxis};
	* strutLength, maxTransverse, preferredTransverse, or minTransverse are < 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Strut(int axis, int strutLength, int maxTransverse, int preferredTransverse, int minTransverse, Color color) throws IllegalArgumentException, IllegalStateException {
		Check.state().edt();
		
		setAxis(axis);
		setStrutLength(strutLength);
		setMaxTransverse(maxTransverse);
		setPreferredTransverse(preferredTransverse);
		setMinTransverse(minTransverse);
		setForeground(color);
	}
	
	// -------------------- factory methods --------------------
	
	/**
	* Reurns a new horizontal (x-axis oriented) Strut instance with the specified length, preferred transverse size, and color.
	* <p>
	* @return <code>new Strut(xAxis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color)</code>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static Strut inX(int strutLength, int preferredTransverse, Color color) throws IllegalStateException {
		Check.state().edt();
		
		return new Strut(xAxis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color);
	}
	
	/**
	* Reurns a new horizontal (x-axis oriented) Strut instance with the specified length.
	* <p>
	* @return <code>inX(strutLength, 0, null)</code>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static Strut inX(int strutLength) throws IllegalStateException {
		Check.state().edt();
		
		return inX(strutLength, 0, null);
	}
	
	/**
	* Reurns a new vertical (y-axis oriented) Strut instance with the specified length, preferred transverse size, and color.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @return <code>new Strut(yAxis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color)</code>
	*/
	public static Strut inY(int strutLength, int preferredTransverse, Color color) throws IllegalStateException {
		Check.state().edt();
		
		return new Strut(yAxis, strutLength, Integer.MAX_VALUE, preferredTransverse, 0, color);
	}
	
	/**
	* Reurns a new vertical (y-axis oriented) Strut instance with the specified length.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @return <code>inY(strutLength, 0, null)</code>
	*/
	public static Strut inY(int strutLength) throws IllegalStateException {
		Check.state().edt();
		
		return inY(strutLength, 0, null);
	}
	
	// -------------------- accessors & mutators --------------------
	
	/**
	* Mutator for the axis field.
	* <p>
	* @throws IllegalArgumentException if axis is neither xAxis nor yAxis
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setAxis(int axis) throws IllegalArgumentException, IllegalStateException {
		if ((axis != xAxis) && (axis != yAxis)) throw new IllegalArgumentException("arg axis = " + axis + " is neither xAxis nor yAxis");
		Check.state().edt();
		
		this.axis = axis;
	}
	
	/**
	* Mutator for the strutLength field.
	* <p>
	* @throws IllegalArgumentException if strutLength is < 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setStrutLength(int strutLength) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNegative(strutLength);
		Check.state().edt();
		
		this.strutLength = strutLength;
	}
	
	/**
	* Mutator for the maxTransverse field.
	* <p>
	* @throws IllegalArgumentException if maxTransverse is < 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setMaxTransverse(int maxTransverse) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNegative(maxTransverse);
		Check.state().edt();
		
		this.maxTransverse = maxTransverse;
	}
	
	/**
	* Mutator for the preferredTransverse field.
	* <p>
	* @throws IllegalArgumentException if preferredTransverse is < 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setPreferredTransverse(int preferredTransverse) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNegative(preferredTransverse);
		Check.state().edt();
		
		this.preferredTransverse = preferredTransverse;
	}
	
	/**
	* Mutator for the minTransverse field.
	* <p>
	* @throws IllegalArgumentException if minTransverse is < 0
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setMinTransverse(int minTransverse) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNegative(minTransverse);
		Check.state().edt();
		
		this.minTransverse = minTransverse;
	}
	
	// -------------------- size methods --------------------
	
	/**
	* Returns <code>new Dimension(strutLength, maxTransverse)</code> (if axis == xAxis)
	* or <code>new Dimension(maxTransverse, strutLength)</code> (if axis == yAxis).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Dimension getMaximumSize() throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				return new Dimension(strutLength, maxTransverse);
			case yAxis:
				return new Dimension(maxTransverse, strutLength);
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
	
/*
	**
	* Returns <code>new Dimension(preferredTransverse, strutLength)</code>.
	* (Looking at the source code for Component, this method seems to be the fundamental method for preferred size.
	* It is called by getPreferredSize.)
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public Dimension preferredSize() throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				return new Dimension(strutLength, preferredTransverse);
			case yAxis:
				return new Dimension(preferredTransverse, strutLength);
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
*/
	/**
	* Returns <code>new Dimension(preferredTransverse, strutLength)</code>.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Dimension getPreferredSize() throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				return new Dimension(strutLength, preferredTransverse);
			case yAxis:
				return new Dimension(preferredTransverse, strutLength);
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
	
/*
	**
	* Returns <code>new Dimension(strutLength, minTransverse)</code> (if axis == xAxis)
	* or <code>new Dimension(minTransverse, strutLength)</code> (if axis == yAxis).
	* (Looking at the source code for Component, this method seems to be the fundamental method for minimum size.
	* It is called by getMinimumSize.)
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public Dimension minimumSize() throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				return new Dimension(strutLength, minTransverse);
			case yAxis:
				return new Dimension(minTransverse, strutLength);
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
*/
	/**
	* Returns <code>new Dimension(strutLength, minTransverse)</code> (if axis == xAxis)
	* or <code>new Dimension(minTransverse, strutLength)</code> (if axis == yAxis).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Dimension getMinimumSize() throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				return new Dimension(strutLength, minTransverse);
			case yAxis:
				return new Dimension(minTransverse, strutLength);
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
	
	// -------------------- isLightweight --------------------
	
	/**
	* Returns true: this Component is peerless.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public boolean isLightweight() throws IllegalStateException {
		Check.state().edt();
		
		return true;
	}
	
	// -------------------- paint --------------------
	
	/**
	* Since this is a lightweight (has no peer) gui object that is a direct subclass of Component,
	* this method must be implemented.
	* The code here merely fills in this Component's rectangular (length & width) dimensions with its foreground Color.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @see <a href="http://java.sun.com/products/jfc/tsc/articles/painting/index.html">Painting in AWT and Swing</a>
	*/
	public void paint(Graphics g) throws IllegalStateException {
		Check.state().edt();
		
		Dimension size = getSize();
		g.fillRect(0, 0, size.width, size.height);
	}
	
	// -------------------- reshape --------------------
	
/*
	**
	* Overrides superclass implementation to enforce the fixed strut length requirement:
	* if axis == xAxis, then the width arg is replaced by strutLength in a call to super.reshape;
	* else if axis == yAxis, then the height arg is replaced by strutLength in a call to super.reshape.
	* (Looking at the source code for Component, this method seems to be the fundamental method for shape control.
	* It is eventually called by all the setSize, resize, setBounds methods.)
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public void reshape(int x, int y, int width, int height) throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				super.reshape(x, y, strutLength, height);
				break;
			case yAxis:
				super.reshape(x, y, width, strutLength);
				break;
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
*/
	/**
	* Overrides superclass implementation to enforce the fixed strut length requirement:
	* if axis == xAxis, then the width arg is replaced by strutLength in a call to super.reshape;
	* else if axis == yAxis, then the height arg is replaced by strutLength in a call to super.reshape.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void setBounds(int x, int y, int width, int height) throws IllegalStateException {
		Check.state().edt();
		
		switch (axis) {
			case xAxis:
				super.setBounds(x, y, strutLength, height);
				break;
			case yAxis:
				super.setBounds(x, y, width, strutLength);
				break;
			default:
				throw new IllegalStateException("the field axis = " + axis + " has a value that is illegal; it should never have been assigned this value");
		}
	}
	
}
