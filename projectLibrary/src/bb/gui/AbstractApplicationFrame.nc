/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/



THIS CODE WAS STARTED A LONG TIME AGO, AND WAS NEVER FINISHED.

I think I was trying to create a base class for guis that had a lot of common functionality in it, so that you would not need to reimplement it all the time...



/*
Programmer notes:

--Preferences; see
	jdcNewsletter_2003_07_15.txt
*/

package bb.gui;

import bb.util.Check;
//import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
*/
public abstract class AbstractApplicationFrame extends JFrame {
	
	// -------------------- application init constants --------------------
	
// +++ these constants should actually be gotten from Preferences
// -- the application should remember its last state by overwriting that file

// +++ make these instance vars too, and remove word "initial"
	
	protected static final int INITIAL_HORIZONTAL_OFFSET = 0;
	protected static final int INITIAL_VERTICAL_OFFSET = 0;
	protected static final int INITIAL_WIDTH = 800;
	protected static final int INITIAL_HEIGHT = 700;
	
	// -------------------- misc constants --------------------
	
	protected static final String APPLICATION_TITLE = "Color Idea Generator";
	
	protected static final int DEFAULT_VERTICAL_SEPARATION = 20;
	
	// -------------------- instance variables --------------------
	
	// -------------------- main --------------------
	
	// concrete application subclass should define
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public AbstractApplicationFrame() throws IllegalStateException {
		Check.state().edt();
		
		setTitle( APPLICATION_TITLE );
		setLocation(INITIAL_HORIZONTAL_OFFSET, INITIAL_VERTICAL_OFFSET);
		setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
//		setJMenuBar( buildProgramMenuBar() );
		
			// this WindowListener makes clicking on the window close button the same as choosing the Exit menu item:
		addWindowListener( new WindowAdapter() {
			public void windowClosing(WindowEvent e) { AbstractApplicationFrame.this.exit(); }
		} );
		
		rebuildGui();
		setVisible(true);

// +++ need to add shutdown hook too; one use would be to record some of the settings before exit
	}
	
	// -------------------- buildXXX gui methods --------------------
	
	/**
	* Removes everything from the content pane,
	* then adds the result of a call to {@link #buildGui buildGui},
	* then calls {@link #refreshDisplay refreshDisplay}.
	*/
	protected void rebuildGui() {
		getContentPane().removeAll();
		getContentPane().add( buildGui() );
		refreshDisplay();
	}
	
	/**
	* Concrete subclass must implement.
* Must return the root component of the gui's content.
	*/
	protected abstract JComponent buildGui();
	
	/**
	* Concrete subclass must implement.
* Must return the root component of the gui's content.
	*/
protected abstract JMenu buildMenu();
// or is it JMenuBar
	
	// -------------------- misc gui methods --------------------
	
	protected void refreshDisplay() {
		this.validate();
		this.repaint();
	}
	
	// -------------------- misc methods --------------------
	
	protected void exit() {
		dispose();
		System.exit(0);
	}
	
}
