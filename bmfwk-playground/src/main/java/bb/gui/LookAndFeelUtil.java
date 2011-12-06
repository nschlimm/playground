/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer Notes:

+++ look at all the 3rd party L & Fs, such as at:
	https://substance.dev.java.net/
	http://www.taranfx.com/blog/best-java-swing-look-and-feel-themes-professional-casual-top-10
	http://www.javootoo.com/
	http://compiere.org/looks/
Figure out how to add them so that the code below will recognize them (there has to be a Swing way to register L & F's ?)
	--I believe the installLookAndFeel method will work?
*/

package bb.gui;

import bb.util.Check;
import java.awt.EventQueue;
import java.util.HashMap;
import java.util.Map;
import javax.swing.UIManager;

/**
* This class implements some constants and utilty methods for Swing's Look and Feel functionality.
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @author Brent Boyer
*/
public class LookAndFeelUtil {
	
	// -------------------- constants --------------------
	
// +++ the arrays below are almost certainly a bad idea -- see notes above
// +++ and should not be exposing mutable fields like these anyways
	
	/** This array lists all the Look and Feel names that are available on the <i>current</i> system. */
	static final String[] lookAndFeels;
	
	/** This array lists all the Look and Feel fully qualified classnames that are available on the <i>current</i> system. */
	private static final String[] lookAndFeelClassnames;
	
	/** This Map correlates lookAndFeels to lookAndFeelClassnames. */
	private static final Map<String,String> nameToClassname = new HashMap<String,String>();
	
	// -------------------- static initializer --------------------
	
// +++ the functionality below should be put in a method, and redone each time is called?
	
	/**
	* Initializes some of the constants of this class.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	static {
		Check.state().edt();
		
		UIManager.LookAndFeelInfo[] lafInfos = UIManager.getInstalledLookAndFeels();
// +++ how come when I run this under 1.5, I do not see the new Ocean theme?
// http://www.google.com/search?hl=en&q=java+ocean+theme+swing&btnG=Google+Search
// http://www-128.ibm.com/developerworks/java/library/j-tiger10194/
		
		lookAndFeels = new String[lafInfos.length];
		lookAndFeelClassnames = new String[lafInfos.length];
		
		for (int i = 0; i < lafInfos.length; i++) {
			lookAndFeels[i] = lafInfos[i].getName();
			lookAndFeelClassnames[i] = lafInfos[i].getClassName();
			nameToClassname.put( lookAndFeels[i], lookAndFeelClassnames[i] );
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private LookAndFeelUtil() {}
	
	// -------------------- utility methods --------------------
	
	/**
	* Returns the Look And Feel class name that corresponds to the supplied short name.
	* <p>
	* @throws IllegalArgumentException if name is blank, or does not correspond to any known class name
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public static String getClassName(String name) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notBlank(name);
		Check.state().edt();
		
		if (nameToClassname.containsKey(name)) return nameToClassname.get(name);
		throw new IllegalArgumentException("name = " + name + " does not map to any classname");
	}
	
}


/*
 the code below is an old implementation; may need to reconsider it as the code above did not seem to pick up 3rd party look and feels
(perhaps because i failed to register them?):

	// -------------------- constants --------------------
	
	**
	* This array simply lists all the Look and Feels that have been known to be made on any system.
	* Should add more names to this list as they get made in the future.
	*
	public static final String[] POSSIBLE_LOOK_AND_FEELS =
		{"Mac", "Metal", "Motif", "Windows"};
	
	** This array lists all the Look and Feels that are available on the <i>current</i> system. *
	public static final String[] lookAndFeels;
	
	** Package prefix for the Metal Look and Feel. *
	public static final String METAL_PACKAGE_PREFIX = "javax.swing.plaf.";
	
	** Package prefix for the Pluggable (i.e. non-Metal) Look and Feels. *
	public static final String PLAF_PACKAGE_PREFIX = "com.sun.java.swing.plaf.";
	
	// -------------------- static initializer --------------------
	
	static {
		ArrayList validNames = new ArrayList(POSSIBLE_LOOK_AND_FEELS.length);
		
		for (String laf : POSSIBLE_LOOK_AND_FEELS) {
			try {
				Class.forName( getClassName( laf ) );
				validNames.add( laf );
			}
			catch (Throwable t) {}
		}
		
		lookAndFeels = new String[validNames.size()];
		validNames.toArray(lookAndFeels);
		Arrays.sort(lookAndFeels);			// MUST ensure that this array is sorted before use (required by the isValid method)
	}
	
	// -------------------- utility methods --------------------
	
	**
	* Determines if the supplied name is a member of the <code>lookAndFeels</code> array.
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public static boolean isValid(String name) throws IllegalStateException {
		Check.state().edt();
		
		return (Arrays.binarySearch(lookAndFeels, name) >= 0);
	}
	
	**
	* Returns the Java class name corresponding to the supplied Look and Feel name.
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*
	public static String getClassName(String name) throws IllegalStateException {
		Check.state().edt();
		
		String packagePrefix = (name.equals("Metal")) ? METAL_PACKAGE_PREFIX : PLAF_PACKAGE_PREFIX;

		return	packagePrefix + name.toLowerCase() + "." + name + "LookAndFeel";
	}
*/
