/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.io.DirUtil;
import bb.io.FileUtil;
import bb.util.Check;
import bb.util.Execute;
import bb.util.logging.LogUtil;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
* Class which supports taking screen shots of the entire desktop, AWT Components, or Swing JComponents.
* This functionality is implemented in a series of <code>take</code> methods, each of which returns a BufferedImage.
* This class also offers convenience <code>write</code> methods for storing BufferedImages to files.
* <p>
* The images taken by this class should be the precise images seen on the screen.
* <b>However, the images written to files may deviate from the originals.</b>
* One obvious cause is limitations of the chosen file format (especially with lossy formats like jpeg).
* A subtle issue can occur, however, even when using lossless formats like png:
* if the file is subsequently opened by another application,
* that application may rescale the image, which can often cause visible artifacts.
* <blockquote>
*	To see this last problem on Windows XP,
*	call {@link #take()} which returns an image of the entire desktop and write it to a file,
*	and then open the file with XP's default graphics file viewer ("Windows Picture And Fax Viewer").
*	This program shrinks the desktop image in order to fit it inside the program's window,
*	and rescaling artifacts are readily seen, especially if the desktop image has any kind of text in it.
*	If "Windows Picture And Fax Viewer" instead cropped the image or had a scroll pane, then this should not happen.
* </blockquote>
* <p>
* Acknowledgement: this class was inspired by the program
* <a href="http://www.discoverteenergy.com/files/ScreenImage.java">ScreenImage</a>.
* Differences from the above program:
* <ol>
*  <li>this class uses {@link BufferedImage#TYPE_INT_ARGB} instead of {@link BufferedImage#TYPE_INT_RGB} in order to preserve alpha</li>
*  <li>this class's {@link #formatNameDefault default image file format} is PNG instead of JPEG</li>
*  <li>this class's <code>take</code> methods simply take snapshots and never have the side effect of writing image files</li>
*  <li>this class added a version of <code>take</code> which can get a snapshot of a region of a Component</li>
*  <li>
*		when taking a snapshot of a region of a Component or JComponent,
*		the Rectangle that specifies the region always has coordinates relative to the origin of the item
*  </li>
* </ol>
* See also:
* <a href="http://forum.java.sun.com/thread.jspa?forumID=57&threadID=597936">forum discussion #1 on screen shots</a>
* <a href="http://forum.java.sun.com/thread.jspa?forumID=256&threadID=529933">forum discussion #2 on screen shots</a>
* <a href="http://forum.java.sun.com/thread.jspa?forumID=57&threadID=622393">forum discussion #3 on screen shots</a>.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public final class ScreenShot {
	
	// -------------------- constants --------------------
	
	/**
	* Defines the image type for the BufferedImages that will create when taking snapshots.
	* The current value is {@link BufferedImage#TYPE_INT_ARGB}, which was chosen because
	* <ol>
	*  <li>the 'A' in its name means that it preserves any alpha in the image (cannot use the "non-A" types)</li>
	*  <li>the "_INT" types are the fastest types (the "BYTE" types are slower)
	* </ol>
	* @see <a href="http://forum.java.sun.com/thread.jspa?threadID=709109&tstart=0">this forum posting</a>
	*/
	private static final int imageType = BufferedImage.TYPE_INT_ARGB;
	
	/**
	* Default value for the graphics file format that will be written by this class.
	* The current value is "png" because the PNG format is by far the best lossless format currently available.
	* Furthermore, java cannot write to GIF anyways (only read).
	* <p>
	* @see <a href="http://www.w3.org/TR/PNG/">Portable Network Graphics (PNG) Specification (Second Edition)</a>
	* @see <a href="http://www.w3.org/QA/Tips/png-gif">GIF or PNG</a>
	* @see <a href="http://www.libpng.org/pub/png/">PNG Home Site</a>
	*/
	public static final String formatNameDefault = "png";
	
	// -------------------- take --------------------
	
	// desktop versions:
	
	/**
	* Takes a screen shot of the entire desktop.
	* <p>
	* Any thread may call this method.
	* <p>
	* @return a BufferedImage representing the entire screen
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws AWTException if the platform configuration does not allow low-level input control. This exception is always thrown when GraphicsEnvironment.isHeadless() returns true
	* @throws SecurityException if createRobot permission is not granted
	*/
	public static BufferedImage take() throws AWTException, SecurityException, IllegalStateException {
		// edt checked by take below
		
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		Rectangle region = new Rectangle(0, 0, d.width, d.height);
		return take(region);
	}
	
	/**
	* Takes a screen shot of the specified region of the desktop.
	* <p>
	* Any thread may call this method.
	* <p>
	* @param region the Rectangle within the screen that will be captured
	* @return a BufferedImage representing the specified region within the screen
	* @throws IllegalArgumentException if region == null; region's width and height are not greater than zero
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws AWTException if the platform configuration does not allow low-level input control. This exception is always thrown when GraphicsEnvironment.isHeadless() returns true
	* @throws SecurityException if createRobot permission is not granted
	*/
	public static BufferedImage take(Rectangle region) throws IllegalArgumentException, IllegalStateException, AWTException, SecurityException {
		Check.arg().notNull(region);
		Check.state().edt();
		
		return new Robot().createScreenCapture( region );	// altho not currently mentioned in its javadocs, if you look at its source code, the Robot class is synchronized so it must be multithread safe, which is why any thread should be able to call this method
	}
	
	// AWT Component versions:
	
	/**
	* Takes a screen shot of that part of the desktop whose area is where component lies.
	* Any other gui elements in this area, including ones which may lie on top of component,
	* will be included, since the result always reflects the current desktop view.
	* <p>
	* Only {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} may call this method.
	* <p>
	* @param component AWT Component to take a screen shot of
	* @return a BufferedImage representing component
	* @throws IllegalArgumentException if component == null; component's width and height are not greater than zero
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws AWTException if the platform configuration does not allow low-level input control. This exception is always thrown when GraphicsEnvironment.isHeadless() returns true
	* @throws SecurityException if createRobot permission is not granted
	*/
	public static BufferedImage take(Component component) throws IllegalArgumentException, IllegalStateException, AWTException, SecurityException {
		Check.arg().notNull(component);
		// edt checked by take below
		
		Rectangle region = component.getBounds();
		region.x = 0;	// CRITICAL: this and the next line are what make region relative to component
		region.y = 0;
		return take(component, region);
	}
	
	/**
	* Takes a screen shot of that part of the desktop whose area is the region relative to where component lies.
	* Any other gui elements in this area, including ones which may lie on top of component,
	* will be included, since the result always reflects the current desktop view.
	* <p>
	* Only {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} may call this method.
	* <p>
	* @param component AWT Component to take a screen shot of
	* @param region the Rectangle <i>relative to</i> component that will be captured
	* @return a BufferedImage representing component
	* @throws IllegalArgumentException if component == null; component's width and height are not greater than zero; region == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws AWTException if the platform configuration does not allow low-level input control. This exception is always thrown when GraphicsEnvironment.isHeadless() returns true
	* @throws SecurityException if createRobot permission is not granted
	*/
	public static BufferedImage take(Component component, Rectangle region) throws IllegalArgumentException, IllegalStateException, AWTException, SecurityException {
		Check.arg().notNull(component);
		Check.arg().notNull(region);
		Check.state().edt();
		
		Point p = new Point(0, 0);
		SwingUtilities.convertPointToScreen(p, component);
		region.x += p.x;
		region.y += p.y;
		return take(region);
	}
	
	// Swing JComponent versions:
	
	/**
	* Takes a screen shot of <i>just</i> jcomponent
	* (no other gui elements will be present in the result).
	* <p>
	* Only {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} may call this method.
	* <p>
	* @param jcomponent Swing JComponent to take a screen shot of
	* @return a BufferedImage representing jcomponent
	* @throws IllegalArgumentException if jcomponent == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static BufferedImage take(JComponent jcomponent) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(jcomponent);
		// edt checked by take below
		
		Dimension d = jcomponent.getSize();
		Rectangle region = new Rectangle(0, 0, d.width, d.height);
		return take(jcomponent, region);
	}
	
	/**
	* Takes a screen shot of <i>just</i> the specified region of jcomponent
	* (no other gui elements will be present in the result).
	* <p>
	* Only {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} may call this method.
	* <p>
	* @param jcomponent Swing JComponent to take a screen shot of
	* @param region the Rectangle <i>relative to</i> jcomponent that will be captured
	* @return a BufferedImage representing the region within jcomponent
	* @throws IllegalArgumentException if jcomponent == null; region == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static BufferedImage take(JComponent jcomponent, Rectangle region) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(jcomponent);
		Check.arg().notNull(region);
		Check.state().edt();
		
		boolean opaquenessOriginal = jcomponent.isOpaque();
		Graphics2D g2d = null;
		try {
			jcomponent.setOpaque( true );
			BufferedImage image = new BufferedImage(region.width, region.height, imageType);
			g2d = image.createGraphics();
			g2d.translate(-region.x, -region.y) ;	// CRITICAL: this and the next line are what make region relative to component
			g2d.setClip( region );
			jcomponent.paint( g2d );
			return image;
		}
		finally {
			jcomponent.setOpaque( opaquenessOriginal );
			if (g2d != null) g2d.dispose();
		}
	}
	
	// -------------------- write --------------------
	
	/**
	* Calls <code>{@link #write(BufferedImage, File) write}(image, new File(filePath))</code>.
	* <p>
	* @param image the BufferedImage to be written
	* @param filePath path of the File that will write image to
	* @throws IllegalArgumentException if image == null; filePath is blank
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws IOException if an I/O problem occurs
	*/
	public static void write(BufferedImage image, String filePath) throws IllegalArgumentException, IllegalStateException, IOException {
		Check.arg().notNull(image);
		Check.arg().notBlank(filePath);
		// edt checked by write below
		
		write(image, new File(filePath));
	}
	
	/**
	* Calls <code>{@link #write(BufferedImage, String, File) write}(image, {@link #getGraphicsFormat getGraphicsFormat}(file), file)</code>.
	* <p>
	* @param image the BufferedImage to be written
	* @param file the File that will write image to
	* @throws IllegalArgumentException if image or file is null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws IOException if an I/O problem occurs
	*/
	public static void write(BufferedImage image, File file) throws IllegalArgumentException, IllegalStateException, IOException {
		Check.arg().notNull(image);
		// edt checked by write below
		
		write(image, getGraphicsFormat(file), file);
	}
	
	/**
	* Writes image to file in the format specified by formatName.
	* <p>
	* <b>Warning: a side effect</b> of this method is that it will create any needed parent directories of file if they are not already existing.
	* <p>
	* @param image the BufferedImage to be written
	* @param formatName the graphics file format (e.g. "pnj", "jpeg", etc);
	* must be in the same set of values supported by the formatName arg of {@link ImageIO#write(RenderedImage, String, File)}
	* @param file the File that will write image to
	* @throws IllegalArgumentException if image == null; type is blank; file == null
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	* @throws IOException if an I/O problem occurs
	*/
	public static void write(BufferedImage image, String formatName, File file) throws IllegalArgumentException, IllegalStateException, IOException {
		Check.arg().notNull(image);
		Check.arg().notBlank(formatName);
		Check.arg().notNull(file);
		Check.state().edt();
		
		File parent = file.getParentFile();
		if (parent!= null) DirUtil.ensureExists(parent);	// this is CRUCIAL: ImageIO.write below will crash if the parent directory is not existing!
		
		ImageIO.write(image, formatName, file);
	}
	
	// -------------------- getGraphicsFormat --------------------
	
	/**
	* Returns the name of the graphics format stored in file.
	* <p>
	* This is assumed to be file's extension, if it exists.
	* Otherwise, if file has no extension, then {@link #formatNameDefault} is returned.
	* <p>
	* Any thread may call this method.
	* <p>
	* @param file the File whose graphics format needs to be determined
	* @throws IllegalArgumentException if file == null
	*/
	private static String getGraphicsFormat(File file) throws IllegalArgumentException {
		Check.arg().notNull(file);
		
		String format = FileUtil.getExtension(file);
		if (format.length() == 0) format = formatNameDefault;
		return format;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ScreenShot() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(final String[] args) {
			Execute.usingEdt( new Runnable() { public void run() {
				Check.arg().empty(args);
				
				Gui gui = new Gui();
				new Timer(1000, gui.getTimerActionListener()).start();
			} } );
		}
		
		private static class Gui {
		
			private Frame frame;
			private TextField textField;
			private JFrame jframe;
			private JLabel jlabel;
			private JPanel jpanel;
			
			private int count = 0;
						
			private Gui() {
				System.out.println("Creating a Frame with AWT widgets inside...");
				frame = new Frame("ScreenShot.UnitTest.main Frame");
				textField = new TextField();
				textField.setText( "Waiting for the screen shot process to automatically start..." );
				frame.add(textField);
				frame.pack();
				frame.setLocationRelativeTo(null);	// null will center it in the middle of the screen
				frame.setVisible(true);
				
				System.out.println("Creating a JFrame with Swing widgets inside...");
				jlabel = new JLabel(
					"<html>" +
						"To be, or not to be: that is the question:" + "<br>" +
						"Whether 'tis nobler in the mind to suffer" + "<br>" +
						"The slings and arrows of outrageous fortune," + "<br>" +
						"Or to take arms against a sea of troubles," + "<br>" +
						"And by opposing end them?" + "<br>" +
						"To die: to sleep; No more;" + "<br>" +
						"and by a sleep to say we end" + "<br>" +
						"The heart-ache and the thousand natural shocks" + "<br>" +
						"That flesh is heir to," + "<br>" +
						"'tis a consummation Devoutly to be wish'd." + "<br>" +
					"</html>"
				);
				jpanel = new JPanel();
				jpanel.setBorder( BorderFactory.createEmptyBorder(20, 20, 20, 20) );
				jpanel.add(jlabel);
				jframe = new JFrame("ScreenShot.UnitTest.main JFrame");
				jframe.getContentPane().add(jpanel);
				jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				jframe.pack();
				Point p = frame.getLocation();
				p.translate(0, frame.getSize().height  + 10);
				jframe.setLocation(p);
				jframe.setVisible(true);
			}
			
			private ActionListener getTimerActionListener() {
				return new ActionListener() {
					public void actionPerformed(ActionEvent evt) {
						try {
							switch (count++) {
								case 0:
									displayMessage("Taking a screen shot of the entire desktop...");
									ScreenShot.write( ScreenShot.take(), LogUtil.makeLogFile("desktop.png") );
									break;
								case 1:
									displayMessage("Taking a screen shot of the central rectangle of the desktop...");
									Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
									Rectangle region = getCenteredRectangle(d);
									ScreenShot.write( ScreenShot.take(region), LogUtil.makeLogFile("desktopCenteredRectangle.png") );
									break;
								case 2:
									displayMessage("Taking a screen shot of the TextField...");
									ScreenShot.write( ScreenShot.take(textField), LogUtil.makeLogFile("textField.png") );
									break;
								case 3:
									displayMessage("Taking a screen shot of the central rectangle of the TextField...");
									d = textField.getSize();
									region = getCenteredRectangle(d);
									ScreenShot.write( ScreenShot.take(textField, region), LogUtil.makeLogFile("textFieldCenteredRectangle.png") );
									break;
								case 4:
									displayMessage("Taking a screen shot of the JLabel...");
									ScreenShot.write( ScreenShot.take(jlabel), LogUtil.makeLogFile("jlabel.png") );
									break;
								case 5:
									displayMessage("Taking a screen shot of the central rectangle of the JLabel...");
									d = jpanel.getSize();
									region = getCenteredRectangle(d);
									ScreenShot.write( ScreenShot.take(jpanel, region), LogUtil.makeLogFile("jpanelCenteredRectangle.png") );
									break;
								default:
									frame.dispose();
									jframe.dispose();	// CRITICAL: need to dispose of BOTH windows, since according to the JFrame.setDefaultCloseOperation javadoc: "When the last displayable window within the Java virtual machine (VM) is disposed of, the VM may terminate"
									break;
							}
						}
						catch (Throwable t) {
							t.printStackTrace(System.err);
						}
					}
					
					private void displayMessage(String text) {
						System.out.println(text);
						textField.setText(text);
						frame.pack();
						frame.invalidate();
					}
					
					private Rectangle getCenteredRectangle(Dimension d) {
						int x = d.width / 4;
						int y = d.height / 4;
						int width = d.width / 2;
						int height = d.height / 2;
						return new Rectangle(x, y, width, height);
					}
				};
			}
			
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
