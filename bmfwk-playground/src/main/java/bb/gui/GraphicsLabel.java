/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ should allow text rotatation (e.g. orient it vertically)?
	--that way, for certain graph labels like on the x-axis, they are less likely to run into each other
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.LineMetrics;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
* Represents a text label in some graphical context (e.g. the label for a grid line).
* <p>
* This text label always has some associated "anchor point": a {@link Point2D} instance which defines a location in space.
* The text is located somewhere around the anchor point (e.g. below it, above it, etc;
* see the various factory methods of this class for some of the common possibilities).
* <p>
* In addition to the "anchor point", there is also always an associated "reference point",
* which is the term used by {@link FontMetrics} for the point which marks the start of the text's baseline.
* <p>
* <b>Warning:</b> the dimension's of each instance are only valid for the particular Graphics2D context it was constructed with.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class GraphicsLabel {
// +++ should this extend JComponent or JPanel?
	
	private final String text;
	private final Point2D anchorPoint;
	private final Point2D referencePoint;
	private final double ascent;
	private final double descent;
	private final double leading;
	private final double width;
	
	// -------------------- getWidth, getHeight --------------------
	
	/**
	* Returns the width of the rectangle which bounds text.
	* <p>
	* @throws IllegalArgumentException if text == null; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static double getWidth(String text, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().notNull(g2);
		Check.state().edt();
		
		return g2.getFontMetrics().getStringBounds(text, g2).getWidth();
	}
	
	/**
	* Returns the height of the rectangle which bounds text.
	* <p>
	* @throws IllegalArgumentException if text == null; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static double getHeight(String text, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().notNull(g2);
		Check.state().edt();
		
		return g2.getFontMetrics().getStringBounds(text, g2).getHeight();
	}
	
	// -------------------- makeXXX --------------------
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance which is centered on anchorPoint.
	* Specificly, the rectangle which bounds text has its center at anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeCenter(String text, Point2D anchorPoint, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		// all args and state checked by make below
		
		return make(text, anchorPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance whose center is offset from anchorPoint.
	* Specificly, the rectangle which bounds text has its center located at a vector displacement of (dx, dy) from anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* dx or dy are not {@link Check#normal normal}; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeOffsetCenter(String text, Point2D anchorPoint, double dx, double dy, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normal(dx);
		Check.arg().normal(dy);
		// all other args and state checked by other methods below
		
		Point2D centerPoint = new Point2D.Double(anchorPoint.getX() + dx, anchorPoint.getY() + dy);
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance whose corner is offset from anchorPoint.
	* Specificly, the rectangle which bounds text has its relevant corner located at a vector displacement of (dx, dy) from anchorPoint.
	* The corner used is the
	* top left if dx >= 0 and dy >= 0;
	* the bottom left if dx >= 0 and dy < 0;
	* the top right if dx < 0 and dy >= 0;
	* the bottom right if dx < 0 and dy < 0.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* dx or dy are not {@link Check#normal normal}; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeOffsetCorner(String text, Point2D anchorPoint, double dx, double dy, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normal(dx);
		Check.arg().normal(dy);
		// all other args and state checked by other methods below
		
		double width = getWidth(text, g2);
		double height = getHeight(text, g2);
		
		double x;
		double y;
		if (dx >= 0) {	// corner is on the left edge of the bounding rectangle
			if (dy >= 0) {	// corner is the top left of the bounding rectangle
				x = anchorPoint.getX() + dx + (width / 2.0);
				y = anchorPoint.getY() + dy + (height / 2.0);
			}
			else {	// corner is the bottom left of the bounding rectangle
				x = anchorPoint.getX() + dx + (width / 2.0);
				y = anchorPoint.getY() + dy - (height / 2.0);
			}
		}
		else {	// corner is on the right edge of the bounding rectangle
			if (dy >= 0) {	// corner is the top right of the bounding rectangle
				x = anchorPoint.getX() + dx - (width / 2.0);
				y = anchorPoint.getY() + dy + (height / 2.0);
			}
			else {	// corner is the bottom right of the bounding rectangle
				x = anchorPoint.getX() + dx - (width / 2.0);
				y = anchorPoint.getY() + dy - (height / 2.0);
			}
		}
		Point2D centerPoint = new Point2D.Double(x, y);
		
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance to the left of anchorPoint.
	* Specificly, the rectangle which bounds text
	* has its right edge a distance of margin from anchorPoint,
	* and its top and bottom edges are vertically equidistant from anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* margin is {@link Check#normalNotNegative abnormal or negative};
	* g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeLeft(String text, Point2D anchorPoint, double margin, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normalNotNegative(margin);
		// all other args and state checked by other methods below
		
		double width = getWidth(text, g2);
		Point2D centerPoint = new Point2D.Double( anchorPoint.getX() - (width / 2.0) - margin, anchorPoint.getY() );
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance to the right of anchorPoint.
	* Specificly, the rectangle which bounds text
	* has its left edge a distance of margin from anchorPoint,
	* and its top and bottom edges are vertically equidistant from anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* margin is {@link Check#normalNotNegative abnormal or negative};
	* g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeRight(String text, Point2D anchorPoint, double margin, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normalNotNegative(margin);
		// all other args and state checked by other methods below
		
		double width = getWidth(text, g2);
		Point2D centerPoint = new Point2D.Double( anchorPoint.getX() + (width / 2.0) + margin, anchorPoint.getY() );
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance to the right of labelExisting.
	* Specificly, both instances have the same baseline,
	* and the left edge of this new instance is a distance of margin from the right edge of labelExisting.
	* <p>
	* Note: the anchorPoint of the result is labelExisting's anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; labelExisting == null;
	* margin is {@link Check#normalNotNegative abnormal or negative};
	* g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeRight(String text, GraphicsLabel labelExisting, double margin, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().notNull(labelExisting);
		Check.arg().normalNotNegative(margin);
		Check.arg().notNull(g2);
		// all other args and state checked by other methods below
		
		FontMetrics fontMetrics = g2.getFontMetrics();
		LineMetrics lineMetrics = fontMetrics.getLineMetrics(text, g2);
		
		double ascent = lineMetrics.getAscent();
		double descent = lineMetrics.getDescent();
		double leading = lineMetrics.getLeading();
		double width = fontMetrics.getStringBounds(text, g2).getWidth();
		
		Point2D refPointOld = labelExisting.getReferencePoint();
		double x = refPointOld.getX() + labelExisting.getWidth() + margin;
		double y = refPointOld.getY();
		Point2D refPointNew = new Point2D.Double(x, y);
		
		return new GraphicsLabel(text, labelExisting.getAnchorPoint(), refPointNew, ascent, descent, leading, width);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance which is above anchorPoint.
	* Specificly, the rectangle which bounds text
	* has its bottom edge a distance of margin from anchorPoint,
	* and its left and right edges are horizontally equidistant from anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* margin is {@link Check#normalNotNegative abnormal or negative};
	* g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeAbove(String text, Point2D anchorPoint, double margin, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normalNotNegative(margin);
		// all other args and state checked by other methods below
		
		double height = getHeight(text, g2);
		Point2D centerPoint = new Point2D.Double( anchorPoint.getX(), anchorPoint.getY() - (height / 2.0) - margin );
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Convenience factory method which returns a new GraphicsLabel instance which is below anchorPoint.
	* Specificly, the rectangle which bounds text
	* has its top edge a distance of margin from anchorPoint,
	* and its left and right edges are horizontally equidistant from anchorPoint.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null;
	* margin is {@link Check#normalNotNegative abnormal or negative};
	* g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static GraphicsLabel makeBelow(String text, Point2D anchorPoint, double margin, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(anchorPoint);
		Check.arg().normalNotNegative(margin);
		// all other args and state checked by other methods below
		
		double height = getHeight(text, g2);
		Point2D centerPoint = new Point2D.Double( anchorPoint.getX(), anchorPoint.getY() + (height / 2.0) + margin );
		return make(text, centerPoint, anchorPoint, g2);
	}
	
	/**
	* Fundamental factory method, called by many of the others.
	* Returns a new GraphicsLabel instance which is centered on centerPoint.
	* Specificly, the rectangle which bounds text has its center at centerPoint.
	* The location of anchorPoint is arbitrary, but it is the Point2D used as the anchorPoint of the result.
	* <p>
	* @throws IllegalArgumentException if text == null; centerPoint == null; anchorPoint == null; g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	private static GraphicsLabel make(String text, Point2D centerPoint, Point2D anchorPoint, Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().notNull(centerPoint);
		Check.arg().notNull(anchorPoint);
		Check.arg().notNull(g2);
		Check.state().edt();
		
		FontMetrics fontMetrics = g2.getFontMetrics();
		LineMetrics lineMetrics = fontMetrics.getLineMetrics(text, g2);
		
		double ascent = lineMetrics.getAscent();
		double descent = lineMetrics.getDescent();
		double leading = lineMetrics.getLeading();
		double width = fontMetrics.getStringBounds(text, g2).getWidth();
		
		double x = centerPoint.getX() - (width / 2.0);
		double y = centerPoint.getY() + ((ascent - descent) / 2.0);	// NOTE: ascent - descent IS CORRECT (do NOT use +)
		Point2D referencePoint = new Point2D.Double(x, y);
		
		return new GraphicsLabel(text, anchorPoint, referencePoint, ascent, descent, leading, width);
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if text == null; anchorPoint == null; referencePoint == null;
	* any double arg is {@link Check#normalNotNegative abnormal or negative}
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public GraphicsLabel(String text, Point2D anchorPoint, Point2D referencePoint, double ascent, double descent, double leading, double width) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().notNull(anchorPoint);
		Check.arg().notNull(referencePoint);
		Check.arg().normalNotNegative(ascent);
		Check.arg().normalNotNegative(descent);
		Check.arg().normalNotNegative(leading);
		Check.arg().normalNotNegative(width);
		Check.state().edt();
		
		this.text = text;
		this.anchorPoint = anchorPoint;
		this.referencePoint = referencePoint;
		this.ascent = ascent;
		this.descent = descent;
		this.leading = leading;
		this.width = width;
	}
	
	// -------------------- shift --------------------
	
	/**
	* Returns <code>{@link #shift(String, double, double) shift}({@link #getText}, dx, dy)</code>.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public GraphicsLabel shift(double dx, double dy) throws IllegalArgumentException, IllegalStateException {
		Check.state().edt();
		
		return shift(getText(), dx, dy);
	}
	
	/**
	* Returns a new GraphicsLabel which is the same as this instance except that
	* the {@link #text} field is changed to the text param
	* and the {@link #referencePoint} field is shifted by the deltas.
	* Specifically, the new x is the old x + dx, and the new y is the old y + dy.
	* <p>
	* @throws IllegalArgumentException if text == null; dx or dy are not {@link Check#normal normal}
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public GraphicsLabel shift(String text, double dx, double dy) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(text);
		Check.arg().normal(dx);
		Check.arg().normal(dy);
		Check.state().edt();
		
		Point2D referencePointNew = new Point2D.Double( getReferencePoint().getX() + dx, getReferencePoint().getY() + dy );
		return new GraphicsLabel(text, getAnchorPoint(), referencePointNew, getAscent(), getDescent(), getLeading(), getWidth());
	}
	
	// -------------------- accessors --------------------
	
	/**
	* Returns the text for this instance.
	* Result is never null (but may be anything else, including an empty string or all whitespace).
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public String getText() throws IllegalStateException {
		Check.state().edt();
		
		return text;
	}
	
	/**
	* Returns the "anchor point", which is an arbitrary {@link Point2D} which is associated with this instance.
	* Result is never null.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Point2D getAnchorPoint() throws IllegalStateException {
		Check.state().edt();
		
		return anchorPoint;
	}
	
	/**
	* Is the term used by {@link FontMetrics} for the point which marks the start of text's baseline.
	* Result is never null.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Point2D getReferencePoint() throws IllegalStateException {
		Check.state().edt();
		
		return referencePoint;
	}
	
	/**
	* Returns the ascent (as defined by {@link LineMetrics}) of this instance's {@link #getText text}.
	* Result is >= 0.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getAscent() throws IllegalStateException {
		Check.state().edt();
		
		return ascent;
	}
	
	/**
	* Returns the descent (as defined by {@link LineMetrics}) of this instance's {@link #getText text}.
	* Result is >= 0.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getDescent() throws IllegalStateException {
		Check.state().edt();
		
		return descent;
	}
	
	/**
	* Returns the leading (as defined by {@link LineMetrics}) of this instance's {@link #getText text}.
	* Result is >= 0.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getLeading() throws IllegalStateException {
		Check.state().edt();
		
		return leading;
	}
	
	/**
	* Returns the width of this instance's {@link #getText text}.
	* Result is >= 0.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getWidth() throws IllegalStateException {
		Check.state().edt();
		
		return width;
	}
	
	/**
	* Returns the height of this instance's {@link #getText text}.
	* Here, the height is strictly the height of this label considered by itself,
	* and is always calculated as the sum of the ascent + descent
	* (i.e. the leading, that is the space between lines, is not considered).
	* <i>This differs from the height returned by, say, {@link FontMetrics}.</i>
	* Result is >= 0.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getHeight() throws IllegalStateException {
		Check.state().edt();
		
		return ascent + descent;
	}
	
	/**
	* Returns the x-coordinate of the left edge of the rectangle which bounds this instance's {@link #getText text}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getLeft() throws IllegalStateException {
		Check.state().edt();
		
		return getReferencePoint().getX();
	}
	
	/**
	* Returns the x-coordinate of the right edge of the rectangle which bounds this instance's {@link #getText text}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getRight() throws IllegalStateException {
		Check.state().edt();
		
		return getReferencePoint().getX() + getWidth();
	}
	
	/**
	* Returns the y-coordinate of the top edge of the rectangle which bounds this instance's {@link #getText text}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getTop() throws IllegalStateException {
		Check.state().edt();
		
		return getReferencePoint().getY() - getAscent();
	}
	
	/**
	* Returns the y-coordinate of the bottom edge of the rectangle which bounds this instance's {@link #getText text}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getBottom() throws IllegalStateException {
		Check.state().edt();
		
		return getReferencePoint().getY() + getDescent();
	}
	
	/**
	* Returns the rectangle which bounds this instance's {@link #getText text}.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Rectangle2D.Double getBounds() throws IllegalStateException {
		Check.state().edt();
		
		return new Rectangle2D.Double( getLeft(), getTop(), getWidth(), getHeight() );
	}
	
	/**
	* Returns the maximum distance which this instance's {@link #getText text} vertically extends
	* above the {@link #getAnchorPoint anchor point}.
	* By "above", it is meant as the user sees the screen (recall that the screen y-coordinate system, however, runs from top to bottom).
	* Result will be negative if this instance's text never rises above the anchor point.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getExtentAboveAnchorPoint() throws IllegalStateException {
		Check.state().edt();
		
		return getAnchorPoint().getY() - (getReferencePoint().getY() - getAscent());
	}
	
	/**
	* Returns the maximum distance which this instance's {@link #getText text} vertically extends
	* below the {@link #getAnchorPoint anchor point}.
	* By "below", it is meant as the user sees the screen (recall that the screen y-coordinate system, however, runs from top to bottom).
	* Result will be negative if this instance's text never falls below the anchor point.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getExtentBelowAnchorPoint() throws IllegalStateException {
		Check.state().edt();
		
		return (getReferencePoint().getY() + getDescent()) - getAnchorPoint().getY();
	}
	
	/**
	* Returns the maximum distance which this instance's {@link #getText text} horizontally extends
	* to the left of the {@link #getAnchorPoint anchor point}.
	* By "left", it is meant as the user sees the screen.
	* Result will be negative if this instance's text never goes left of the anchor point.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getExtentToLeftOfAnchorPoint() throws IllegalStateException {
		Check.state().edt();
		
		return getAnchorPoint().getX() - getReferencePoint().getX();
	}
	
	/**
	* Returns the maximum distance which this instance's {@link #getText text} horizontally extends
	* to the right of the {@link #getAnchorPoint anchor point}.
	* By "right", it is meant as the user sees the screen.
	* Result will be negative if this instance's text never goes right of the anchor point.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public double getExtentToRightOfAnchorPoint() throws IllegalStateException {
		Check.state().edt();
		
		return (getReferencePoint().getX() + getWidth()) - getAnchorPoint().getX();
	}
	
	// -------------------- paintXXX --------------------
	
	/**
	* Paints this instance's text onto g2.
	* <p>
	* @throws IllegalArgumentException if g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void paint(Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(g2);
		Check.state().edt();
		
		float x = (float) getReferencePoint().getX();
		float y = (float) getReferencePoint().getY();
		g2.drawString(text, x, y);
	}
	
	/**
	* Paints the baseline for this instance's text onto g2.
	* <p>
	* @throws IllegalArgumentException if g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void paintBaseline(Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(g2);
		Check.state().edt();
		
		g2.draw(
			new Line2D.Double(
				getReferencePoint().getX(),
				getReferencePoint().getY(),
				getReferencePoint().getX() + getWidth(),
				getReferencePoint().getY()
			)
		);
	}
	
	/**
	* Paints the rectangle which bounds this instance's text onto g2.
	* <p>
	* @throws IllegalArgumentException if g2 == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public void paintBounds(Graphics2D g2) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(g2);
		Check.state().edt();
		
		g2.draw( getBounds() );
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				new Displayer( new Sheet(), UnitTest.class.getSimpleName(), UnitTest.class.getName() );
			} } );
		}
		
		private static class Sheet extends javax.swing.JPanel {
			
			private static final long serialVersionUID = 1;
			
			private Sheet() {
				setSize(600, 500);
				setPreferredSize( new Dimension(600, 500) );
			}
			
			public void paint(Graphics g) {
				Graphics2D g2 = (Graphics2D) g;
				
				Point2D origin = new Point2D.Double(250, 250);
				Point2D top = new Point2D.Double(origin.getX(), origin.getY() - 100);
				Point2D bottom = new Point2D.Double(origin.getX(), origin.getY() + 100);
				Point2D left = new Point2D.Double(origin.getX() - 100, origin.getY());
				Point2D right = new Point2D.Double(origin.getX() + 100, origin.getY());
				
				g2.draw( new Line2D.Double(top, bottom) );
				g2.draw( new Line2D.Double(left, right) );
				
				GraphicsLabel labelOrigin = GraphicsLabel.makeCenter("origin", origin, g2);
				labelOrigin.paint(g2);
				labelOrigin.paintBounds(g2);
				System.out.println();
				System.out.println( "labelOrigin.getExtentToRightOfAnchorPoint() = " + labelOrigin.getExtentToRightOfAnchorPoint() );
				System.out.println( "labelOrigin.getWidth() = " + labelOrigin.getWidth() );
				
				GraphicsLabel labelXAxis = GraphicsLabel.makeOffsetCenter("x-axis", origin, 50, 0, g2);
				labelXAxis.paint(g2);
				System.out.println();
				System.out.println( "labelXAxis.getExtentToRightOfAnchorPoint() = " + labelXAxis.getExtentToRightOfAnchorPoint() );
				System.out.println( "labelXAxis.getWidth() = " + labelXAxis.getWidth() );
				
				GraphicsLabel labelYAxis = GraphicsLabel.makeOffsetCenter("y-axis", origin, 0, -50, g2);
				labelYAxis.paint(g2);
				System.out.println();
				System.out.println( "labelYAxis.getExtentAboveAnchorPoint() = " + labelYAxis.getExtentAboveAnchorPoint() );
				System.out.println( "labelYAxis.getHeight() = " + labelYAxis.getHeight() );
				
				GraphicsLabel labelFirstQuadrant = GraphicsLabel.makeOffsetCorner("firstQuadrant", origin, 25, -25, g2);
				labelFirstQuadrant.paint(g2);
				GraphicsLabel labelSecondQuadrant = GraphicsLabel.makeOffsetCorner("secondQuadrant", origin, -25, -25, g2);
				labelSecondQuadrant.paint(g2);
				GraphicsLabel labelThirdQuadrant = GraphicsLabel.makeOffsetCorner("thirdQuadrant", origin, -25, 25, g2);
				labelThirdQuadrant.paint(g2);
				GraphicsLabel labelFourthQuadrant = GraphicsLabel.makeOffsetCorner("fourthQuadrant", origin, 25, 25, g2);
				labelFourthQuadrant.paint(g2);
				
				GraphicsLabel labelLeft = GraphicsLabel.makeLeft("labelLeft", left, 10, g2);
				labelLeft.paint(g2);
				System.out.println();
				System.out.println( "labelLeft.getExtentToLeftOfAnchorPoint() = " + labelLeft.getExtentToLeftOfAnchorPoint() );
				System.out.println( "labelLeft.getWidth() = " + labelLeft.getWidth() );
				
				GraphicsLabel labelRight = GraphicsLabel.makeRight("labelRight", right, 10, g2);
				labelRight.paint(g2);
				System.out.println();
				System.out.println( "labelRight.getExtentToRightOfAnchorPoint() = " + labelRight.getExtentToRightOfAnchorPoint() );
				System.out.println( "labelRight.getWidth() = " + labelRight.getWidth() );
				
				GraphicsLabel labelRightOfRight = GraphicsLabel.makeRight("labelRightOfRight", labelRight, 20, g2);
				labelRightOfRight.paint(g2);
				labelRightOfRight.paintBaseline(g2);
				
				GraphicsLabel labelAbove = GraphicsLabel.makeAbove("labelAbove", top, 10, g2);
				labelAbove.paint(g2);
				System.out.println();
				System.out.println( "labelAbove.getExtentAboveAnchorPoint() = " + labelAbove.getExtentAboveAnchorPoint() );
				System.out.println( "labelAbove.getHeight() = " + labelAbove.getHeight() );
				
				GraphicsLabel labelAboveToRight = labelAbove.shift("labelAboveToRight", 70, 0);
				labelAboveToRight.paint(g2);
				
				GraphicsLabel labelBelow = GraphicsLabel.makeBelow("labelBelow", bottom, 10, g2);
				labelBelow.paint(g2);
				System.out.println();
				System.out.println( "labelBelow.getExtentBelowAnchorPoint() = " + labelBelow.getExtentBelowAnchorPoint() );
				System.out.println( "labelBelow.getHeight() = " + labelBelow.getHeight() );
				
				GraphicsLabel labelNoChars = GraphicsLabel.makeBelow("", bottom, 10, g2);
				labelNoChars.paint(g2);
				System.out.println();
				System.out.println( "labelNoChars.getWidth() = " + labelNoChars.getWidth() );
				System.out.println( "labelNoChars.getHeight() = " + labelNoChars.getHeight() );
			}
			
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}


// The code below illustrates how to rotate text.

// See also:
// http://forum.java.sun.com/thread.jspa?threadID=431941&messageID=1968632
// http://www.esus.com/javaindex/j2se/jdk1.2/javaawt/awtgraphics/2dgraphics/Java2Dapi/Java2Dapi.html

/*
http://javaalmanac.com/egs/java.awt/RotateText.html?l=rel
// Draw string rotated clockwise 90 degrees
	AffineTransform at = new AffineTransform();
	at.setToRotation(Math.PI/2.0);
	g2d.setTransform(at);
	g2d.drawString("aString", x, y);

	// Draw string rotated counter-clockwise 90 degrees
	at = new AffineTransform();
	at.setToRotation(-Math.PI/2.0);
	g2d.setTransform(at);
	g2d.drawString("aString", x, y);
*/

/*
http://www.kickjava.com/?http://www.kickjava.com/424.htm

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;

class myComponent extends JComponent {
	private static final int N = 300;

	public Dimension getPreferredSize() {
	return new Dimension(N, N);
	}

	public void paint(Graphics g) {
	Graphics2D g2d = (Graphics2D)g;

	AffineTransform aft = AffineTransform.
	  getRotateInstance(Math.PI, N / 2, N / 2);
	g2d.setTransform(aft);

	Font f = new Font("monospaced", Font.BOLD, 24);
	g2d.setFont(f);
	String s = "testing";
	g2d.drawString(s, 100, 100);
	FontMetrics fm = getFontMetrics(f);
	int h = fm.getHeight();
	int w = fm.stringWidth(s);
	g2d.drawLine(100, 100 + h, 100 + w, 100 + h);
	}
}

public class graph2d {
	public static void main(String args[]) {
	JFrame f = new JFrame("testing");
	f.addWindowListener(new WindowAdapter() {
	  public void windowClosing(WindowEvent e) {
		System.exit(0);
	  }
	});

	JPanel p = new JPanel();
	p.add(new myComponent());
	f.getContentPane().add(p);

	f.pack();
	f.setVisible(true);
	}
}

*/
