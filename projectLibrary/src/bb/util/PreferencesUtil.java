/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;

import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

/**
* Provides static utility methods for dealing with {@link Preferences}.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class PreferencesUtil {
	
	// -------------------- main --------------------
	
	/** Executes {@link #resetPreferences resetPreferences}. */
	public static void main(final String[] args) {
		Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
			Check.arg().empty(args);
			
			resetPreferences();
			
			return null;
		} } );
	}
	
	// -------------------- resetPreferences --------------------
	
	/**
	* Removes all Preferences nodes except for the system and user root nodes.
	* If the Preferences implementation supports stored defaults, this method will expose them.
	*/
	public static void resetPreferences() throws Exception {
		resetPreferences( Preferences.userRoot() );
		resetPreferences( Preferences.systemRoot() );
	}
	
	/** Since cannot remove a root node, this method instead removes all of root's children. */
	private static void resetPreferences(Preferences root) throws Exception {
		if (!root.name().equals("")) throw new IllegalArgumentException("root is not a root node, its name = " + root.name());
		
		for (String child : root.childrenNames()) {
			root.node(child).removeNode();
		}
		root.flush();	// this persists the removal
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private PreferencesUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// will need to add code when/if add more functionality above...
	
}
