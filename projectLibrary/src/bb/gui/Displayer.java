/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--an alternative to this class which is xml driven is the JDNC project:
	https://jdnc.dev.java.net/
*/

package bb.gui;

import bb.util.Check;
import bb.util.Execute;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;

/**
* JFrame subclass that initially only contains the JComponent supplied to its constructor.
* <p>
* The initial motivation for this class was to enable simple GUIs to be built very easily during development
* by implementing a typical set of JFrame operations.
* For example, this class is extensively used by this package's test code.
* <p>
* As a convenience, this class uses the JDK Preferences api to store the location and size whenever either changes.
* This stored location is used to initialize the location and size of subsequent instances.
* This location and size tracking is done on a per-name basis (see the prefsName constructor arg),
* which allows different users of this class to have their own customizations.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class Displayer extends JFrame {
	
	// -------------------- Preferences constants --------------------
	
	private static final String frameX_key = "frameX";
	private static final String frameY_key = "frameY";
	private static final String frameWidth_key = "frameWidth";
	private static final String frameHeight_key = "frameHeight";
	
	// -------------------- misc constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- instance fields --------------------
	
	/** @serial */
	private final String prefsName;
	
	// -------------------- show --------------------
	
	/**
	* Constructs a new Displayer instance from the args.
	* <p>
	* This method may be safely called by any thread because
	* the work is asynchronously done by a task submitted to EventQueue's dispatch thread.
	* <i>Consequently, this method usually returns before the new Displayer instance is visible.</i>
	* <p>
	* @throws IllegalArgumentException if jcomponent == null; title is {@link Check#notBlank blank}
	*/
/*
DECIDED AGAINST THIS METHOD, because it implies that jcomponent was created in a non EDT thread which is wrong

	public static void show(final JComponent jcomponent, final String title, final String prefsName) throws IllegalArgumentException {
		Check.arg().notNull(jcomponent);
		Check.arg().notBlank(title);
		
		EventQueue.invokeLater( new Runnable() {
			public void run() {
				new Displayer(jcomponent, title, prefsName);
			}
		} );
	}
*/
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* Performs all kinds of GUI initialization, such as:
	* adds jcomponent to its content pane, uses title to label itself, then it makes itself visible.
	* The initial location and size are the last values set by a Display instance that used prefsName,
	* if that information is available, otherwise this instance is maximized.
	* <p>
	* @throws IllegalArgumentException if jcomponent == null; title is {@link Check#notBlank blank}; prefsName is blank
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public Displayer(JComponent jcomponent, String title, String prefsName) throws IllegalArgumentException, IllegalStateException {
		super();
		
		Check.arg().notNull(jcomponent);
		Check.arg().notBlank(title);
		Check.arg().notBlank(prefsName);
		Check.state().edt();
		
		this.prefsName = prefsName;	// CRITICAL: do this first so that the getPreferences method can be used by subsequent code
		
		addComponentListener(
			new ComponentAdapter() {
				public void componentMoved(ComponentEvent e) {
					getPreferences().putInt(frameX_key, getX());
					getPreferences().putInt(frameY_key, getY());
				}
				public void componentResized(ComponentEvent e) {
					getPreferences().putInt(frameWidth_key, getWidth());
					getPreferences().putInt(frameHeight_key, getHeight());
				}
			}
		);
		
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
			// get the specifications of what a maximized window would be:
				// see also: GraphicsConfiguration.getBounds() and GraphicsConfiguration.getMaximumWindowBounds()
		Toolkit kit = Toolkit.getDefaultToolkit();
		Dimension screenSize = kit.getScreenSize();
		GraphicsConfiguration gc = getGraphicsConfiguration();
		Insets insets = kit.getScreenInsets(gc);
			// get the location from user's last setting, if available, else default to the maximized window:
		int frameX = getPreferences().getInt(frameX_key, insets.left);
		int frameY = getPreferences().getInt(frameY_key, insets.top);
		setLocation(frameX, frameY);
			// get the height and width from user's last setting, if available, else default to the maximized window:
		int screenWidthFree = screenSize.width - (insets.left + insets.right);
		int screenHeightFree = screenSize.height - (insets.top + insets.bottom);
		int frameWidth = getPreferences().getInt(frameWidth_key, screenWidthFree);
		int frameHeight = getPreferences().getInt(frameHeight_key, screenHeightFree);
		setSize(frameWidth, frameHeight);
// +++ Note: this code is being used in some other projects (e.g. SlideShow); need to put somewhere in bbLib...
		
		setTitle(title);
		
		getContentPane().add( jcomponent );
		getContentPane().setBackground( new Color(225, 225, 225) );
		//getContentPane().setLayout(???);	// default seems to be OK when add just jcomponent, furthermore, the user of this class can always change this
		
		setVisible(true);
	}
	
	// -------------------- getPreferences --------------------
	
	private Preferences getPreferences() {
		return Preferences.userRoot().node(prefsName);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.thenContinue( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				EventQueue.invokeLater( new Runnable() {
					public void run() {
						JComponent jcomponent = new JLabel("TRY MOVING THIS WINDOW IN THE NEXT 5 SECONDS!");
						String title = "Preferences Test";
						String prefsName = UnitTest.class.getName();
						Displayer displayer = new Displayer( jcomponent, title, prefsName );
							// ensure that it starts off minimally sized:
						displayer.pack();
							// and now increase it to half the screen size if possible (makes it MUCH easier for the user to see it):
						Dimension dimension = SwingUtil.fractionOfScreenSize(0.5);
						int widthNew = Math.max( displayer.getWidth(), dimension.width );
						int heightNew = Math.max( displayer.getHeight(), dimension.height );
						displayer.setSize(widthNew, heightNew);
					}
				} );
				
				Thread.sleep(5*1000);
				
				EventQueue.invokeLater( new Runnable() {
					public void run() {
						JComponent jcomponent = new JLabel(
							"<html>" +
								"<p>" +
									"If you moved the first window within 5 seconds," + "<br>" +
									"this one should be created in its last location" + "<br>" +
									"(and will then need to move this to see the first one again)" + "<br>" +
								"</p>" +
							"</html>"
						);
						String title = "Preferences Test";
						String prefsName = UnitTest.class.getName();	// CRITICAL: must use the same value as above
						new Displayer( jcomponent, title, prefsName );
					}
				} );
				
				return null;
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
