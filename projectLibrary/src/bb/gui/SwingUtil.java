/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ methods that need to add to this class:
	--a defect of Window.pack is that it does not take the title's width into account (it only considers the window's contents)
		--this means that long titles could be citoff, which is annoying
		--see if can find a way to set the window size that takes all items into account
*/

package bb.gui;

import bb.util.Check;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/**
* General static utility methods that are useful for Swing programming.
* <p>
* <p>
* Like typical Java GUI code, most of this class's methods are not multithread safe:
* they expect to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in all public methods unless noted otherwise
* (e.g. the <code>invokeXXX</code> methods may be safely called by any thread).
* <p>
* @author Brent Boyer
*/
public final class SwingUtil {
	
	// -------------------- invokeNow, invokeNowIfEdt --------------------
	
	/**
	* Synchronously executes task on {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* (i.e. this method does return until task has finished running).
	* <p>
	* If the calling thread is EventQueue's dispatch thread, then this method directly executes task.
	* Otherwise, task is submitted to {@link EventQueue#invokeAndWait EventQueue.invokeAndWait}, and this method returns when invokeAndWait returns.
	* <p>
	* Thus, any thread may call this method (unlike EventQueue.invokeAndWait, which cannot be called by EventQueue's dispatch thread).
	* <p>
	* @throws IllegalArgumentException if task == null
	* @throws InterruptedException if calling thread is interrupted while waiting for the dispatch thread to finish excecuting task.run
	* @throws InvocationTargetException if an exception is thrown by task.run
	*/
	public static void invokeNow(Runnable task) throws IllegalArgumentException, InterruptedException, InvocationTargetException {
		Check.arg().notNull(task);
		
		if (EventQueue.isDispatchThread()) {
			task.run();
		}
		else {
			EventQueue.invokeAndWait( task );
		}
	}
	
// +++ any use for the new SwingWorker class introduced in 1.6?
// http://javaboutique.internet.com/tutorials/swingworker/
	
	/**
	* Ensures that at some point task is executed on {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
	* <p>
	* If the calling thread is EventQueue's dispatch thread, then this method directly executes task.
	* Otherwise, task is submitted to {@link EventQueue#invokeLater EventQueue.invokeLater}, and then this method immediately returns.
	* <p>
	* So, in general, task is asynchronously executed.
	* However, in the special case that the calling thread is already the dispatch thread, it is synchronously executed.
	* <p>
	* Any thread may call this method.
	* <p>
	* @throws IllegalArgumentException if task == null
	*/
	public static void invokeNowIfEdt(Runnable task) throws IllegalArgumentException {
		Check.arg().notNull(task);
		
		if (EventQueue.isDispatchThread()) {
			task.run();
		}
		else {
			EventQueue.invokeLater( task );
		}
	}
	
	// -------------------- maximizeWindow --------------------
	
	/**
	* Sets the location and size of window so as to maximize its area.
	* <p>
	* The fundamental upper bound is the {@link Toolkit#getScreenSize size of the screen}.
	* On systems with multiple displays, <i>the size of the primary display is used</i>.
	* <p>
	* A secondary constraint is the presence of permanent desktop items like the <a href="http://en.wikipedia.org/wiki/Taskbar">Windows Taskbar</a>.
	* This method assumes that you do not want the window to cover these items, so their presence makes the effective screen size smaller.
	* <p>
	* @throws IllegalArgumentException if window is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws HeadlessException if GraphicsEnvironment.isHeadless() returns true
	*/
	public static void maximizeWindow(Window window) throws IllegalArgumentException, IllegalStateException, HeadlessException {
		Check.arg().notNull(window);
		Check.state().edt();
		
		//window.setExtendedState( Frame.MAXIMIZED_BOTH );	// would love to simply call this, unfortunately, it only works for Frames, and it does not takes insets into account...
		
		Toolkit kit = Toolkit.getDefaultToolkit();
		Dimension screenSize = kit.getScreenSize();	// see also: GraphicsConfiguration.getBounds() and GraphicsConfiguration.getMaximumWindowBounds()
		
		GraphicsConfiguration gc = window.getGraphicsConfiguration();
		Insets insets = kit.getScreenInsets(gc);
		
		int windowX = insets.left;
		int windowY = insets.top;
		window.setLocation(windowX, windowY);
		
		int screenWidthFree = screenSize.width - (insets.left + insets.right);
		int screenHeightFree = screenSize.height - (insets.top + insets.bottom);
		window.setSize(screenWidthFree, screenHeightFree);
	}
// +++ my SlideShow.buildFrame method has a more sophisticated version of this method which uses Preferences if defined, else falls back on the code above; need to put that code somewhere in bbLib...
	
	// -------------------- centerLineInScrollPane --------------------
	
	/**
	* Centers the line containing the caret inside a scroll pane.
	* <p>
	* @param textComponent the JTextComponent whose line is to be centered
	* @throws IllegalArgumentException if textComponent is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread};
	* textComponent has not been added to a JScrollPane
	* @throws BadLocationException if an internal error happens when setting the viewport's position
	*/
	public static void centerLineInScrollPane(JTextComponent textComponent) throws IllegalArgumentException, IllegalStateException, BadLocationException {
		Check.arg().notNull(textComponent);
		Check.state().edt();
		
		JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, textComponent);
		if (viewport == null) throw new IllegalStateException("textComponent has not been added to a JScrollPane (it has no ancestor JViewport)");
		
		Rectangle r = textComponent.modelToView( textComponent.getCaretPosition() );
		int extentHeight = viewport.getExtentSize().height;
		int viewHeight = viewport.getViewSize().height;
		int y = Math.max(0, r.y - (extentHeight / 2));
		y = Math.min(y, viewHeight - extentHeight);
		viewport.setViewPosition(new Point(0, y));
	}
	// To understand the code above, see:
	//	http://forums.sun.com/thread.jspa?threadID=5358397&tstart=0
	//	http://tips4java.wordpress.com/2009/01/04/center-line-in-scroll-pane/
/*
old code that did not work for some reason?

					// set jscrollPane's view port to be centered around the line:
				Rectangle caretRectangle = textPane.modelToView(caretPos);
				Rectangle viewRectangle = new Rectangle(
					0, caretRectangle.y - (jscrollPane.getHeight() - caretRectangle.height) / 2,
					jscrollPane.getWidth(), jscrollPane.getHeight()
				);
				textPane.scrollRectToVisible(viewRectangle);

*/
	
/*
Seemed to need this hack method when used older LayoutManagers, and couldnot get JTextAreas embedded insude JScrollPanes to work as desired.
Now that I use my LinePanel class for stuff, the GroupLayout that it interbally uses seems to work flawlessly.
So, have obsoleted these methods for now...

	**
	* Simply calls <code>{@link #optimizeJScrollPanePreferredSize(JScrollPane, Dimension) optimizeJScrollPanePreferredSize}(scrollPane, null)</code>.
	* <p>
	* @throws IllegalArgumentException if scrollPane == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public static void optimizeJScrollPanePreferredSize(JScrollPane jscrollPane) throws IllegalArgumentException, IllegalStateException {
		optimizeJScrollPanePreferredSize(jscrollPane, null);
	}
	
	**
	* Sets the preferred size of the supplied JScrollPane to an optimal value.
	* <p>
	* This method solves a serious display issue with JScrollPane.
	* A problem with typical uses of JScrollPane that wrap, say, a JTextArea is that if
	* one scroll bar has to appear, it will force the appearance of the other one even tho the other is not necessary.
	* To see why this can happen, suppose that the vertical scroll bar appears.
	* Then it will eat some of the horizontal space that the view Component would otherwise have had for itself.
	* To compensate, the JScrollPane will then bring up the horizontal bar.
	* This is visually annoying as well as useless because there is little or nothing for the horizontal bar to scroll over.
	* <p>
	* This method intelligently adjusts the JScrollPane's preferred size to account for
	* the dimensions that will be taken up by its scroll bars and Insets as well as its view.
	* This may allow either scroll bar (horizontal or vertical) to appear without necessarily bringing up the other.
	* Of course, if the view component itself is too large in both dimensions, there is nothing that can be done.
	* <p>
	* The limit param, if non-null, may be further used to set upperbounds on the width and height
	* used for jscrollPane's preferred size.
	* A common choice to supply for limit is the result of calling {@link #fractionOfScreenSize fractionOfScreenSize}
	* <p>
	* @param jscrollPane the JScrollPane whose preferred size is to be optimized; must not be null
	* @param limit a Dimension object that sets upperbounds on the width and height used for jscrollPane's preferred size; may be null
	* @throws IllegalArgumentException if jscrollPane == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public static void optimizeJScrollPanePreferredSize(JScrollPane jscrollPane, Dimension limit) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(jscrollPane);
		Check.state().edt();
		
		Dimension viewPreferredSize = jscrollPane.getViewport().getView().getPreferredSize();
		
		int width =
			viewPreferredSize.width +
			jscrollPane.getVerticalScrollBar().getPreferredSize().width +
			jscrollPane.getInsets().left +
			jscrollPane.getInsets().right;
			
		int height =
			viewPreferredSize.height +
			jscrollPane.getHorizontalScrollBar().getPreferredSize().height +
			jscrollPane.getInsets().top +
			jscrollPane.getInsets().bottom;
			
		// Note: the Viewport itself never contributes anything to the layout (e.g. no Border or Insets)
		
		if (limit != null) {
			width = Math.min( width, limit.width );
			height = Math.min( height, limit.height );
		}
		
		jscrollPane.setPreferredSize( new Dimension( width, height) );
	}
*/
	
	// -------------------- screenSize, fractionOfScreenSize --------------------
	
	/**
	* Returns the screen's size.
	* On systems with multiple displays, <i>just the primary display is used</i>.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws HeadlessException if GraphicsEnvironment.isHeadless() returns true
	*/
	public static Dimension screenSize() throws HeadlessException, IllegalStateException {
		Check.state().edt();
		
		return Toolkit.getDefaultToolkit().getScreenSize();
	}
	
	/**
	* Returns <code>{@link #fractionOfScreenSize(double, double) fractionOfScreenSize}(fraction, fraction)</code>.
	* <p>
	* @throws IllegalArgumentException if fraction is {@link Check#normalNotNegative abnormal or negative}
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws HeadlessException if GraphicsEnvironment.isHeadless() returns true
	*/
	public static Dimension fractionOfScreenSize(double fraction) throws IllegalArgumentException, IllegalStateException, HeadlessException {
		return fractionOfScreenSize(fraction, fraction);
	}
	
	/**
	* First gets the screen's size by calling <code>{@link #screenSize screenSize}</code>.
	* Then returns a new Dimension instance
	* whose width is the screen's width multiplied by fractionX
	* and whose height is the screen's height multiplied by fractionY.
	* <p>
	* @throws IllegalArgumentException if fractionX or fractionY are {@link Check#normalNotNegative abnormal or negative}
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws HeadlessException if GraphicsEnvironment.isHeadless() returns true
	*/
	public static Dimension fractionOfScreenSize(double fractionX, double fractionY) throws IllegalArgumentException, IllegalStateException, HeadlessException {
		Check.arg().normalNotNegative(fractionX);
		Check.arg().normalNotNegative(fractionY);
		// edt checked by screenSize below
		
		Dimension ss = screenSize();
		return new Dimension(
			(int) Math.round( fractionX * ss.width ),
			(int) Math.round( fractionY * ss.height )
		);
	}
	
	// -------------------- textSize --------------------
	
	/**
	* Returns the size of the text as it will appear
	* on the {@link GraphicsDevice#getDefaultScreenDevice default screen device},
	* in its {@link GraphicsConfiguration#getDefaultConfiguration default configuration} .
	* <p>
	* @throws IllegalArgumentException if s is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
/*
Wrote this method when I thought that the only way to get a FontMetrics instance was from a Graphics2D.
Then, I found out from this article
	http://www.exampledepot.com/egs/java.awt/TextDim.html
that JComponent has a getFontMetrics accessor too, which sorta eliminated my immediate need for this method, see
	SearchAndReplace2.Authorizer.getHeightFraction


	public static Dimension textSize(String s) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(s);
		Check.state().edt();
		
	    Graphics2D graphics = null;
	    try {
			GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice device = environment.getDefaultScreenDevice();
			GraphicsConfiguration configuration = device.getDefaultConfiguration();
BufferedImage image = configuration.createCompatibleImage(10, 10);
// +++ does the dimensions above matter?
			Graphics2D graphics = image.createGraphics();
			return graphics.getFontMetrics().getStringBounds(s, graphics);
		}
		finally {
			if (graphics != null) graphics.dispose();
		}
	}
*/
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private SwingUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			bb.util.Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				test_maximizeWindow();
			} } );
		}
		
		private static void test_maximizeWindow() {
			JFrame jframe = new JFrame("test_maximizeWindow");
			jframe.getContentPane().add( new JLabel("Should see this JFrame be maximally sized for the free space available on the primary display") );
			jframe.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
			jframe.setVisible(true);
			
			maximizeWindow(jframe);
			//jframe.setExtendedState( Frame.MAXIMIZED_BOTH );	// uncomment this line instead of the above if need proof that it does not work (does not take insets into account)
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
