/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--see also this RFE:
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6591333
*/

package bb.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

/**
* The original version of this class did Locale and TimeZone checking code for another project.
* The code that remains merely prints out diagnostic information.
* Am keeping this around in case some other use for it is ever found...
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class LocaleTimeZoneUtil {
	
	// -------------------- main and helper methods --------------------
	
	/**
	* Performs the default Locale and TimeZone checks.
	* When finished, an appropriate sound is played and the JVM is explicitly shut down
	* with an exit code appropriate to the termination (0 if normal, 1 otherwise).
	*/
	public static void main(final String[] args) {
		Execute.thenExitIfEntryPoint( new Runnable() { public void run() {
			Check.arg().empty(args);
			
			printDefaults();
			printAvailable();
		} } );
	}
	
	private static void printDefaults() {
		System.out.println();
		System.out.println("JVM defaults:");
		System.out.println("\t" + "Locale.getDefault() = " + Locale.getDefault().getDisplayName());
		System.out.println("\t" + "TimeZone.getDefault() = " + TimeZone.getDefault().getDisplayName());
	}
	
	private static void printAvailable() {
		System.out.println();
		System.out.println("Available Locales:");
		SortedSet<Locale> locales = new TreeSet<Locale>( new LocaleComparator() );
		locales.addAll( Arrays.asList( Locale.getAvailableLocales() ) );
		for (Locale locale : locales) {
			System.out.println("\t" + locale.getDisplayName());
		}
		
		System.out.println("Available TimeZones:");
		SortedSet<String> timeZones = new TreeSet<String>( Arrays.asList( TimeZone.getAvailableIDs() ) );
		for (String timeZoneID : timeZones) {
			System.out.println("\t" + timeZoneID);
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private LocaleTimeZoneUtil() {}
	
	// -------------------- LocaleComparator (static inner class) --------------------
	
	/**
	* Imposes an ordering on Locales that is <i>consistent with equals</i>; see {@link #compare compare} for details.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
	*/
	public static class LocaleComparator implements Comparator<Locale>, Serializable {	// see the file compareImplementation.txt for more discussion
		
		private static final long serialVersionUID = 1;
		
		/**
		* Tries to order l1 and l2 by their names:
		* returns <code>l1.{@link Locale#getDisplayName()}.compareTo( l2.getDisplayName() )</code> if that result is != 0.
		* <p>
		* Else returns 0 if <code>l1.{@link Locale#equals equals}(l2)</code> is true.
		* This is the only circumstance in which 0 will ever be returned, thus,
		* <i>this Comparator is consistent with equals</i> (see {@link Comparator} for more discussion).
		* <p>
		* Else throws an IllegalStateException.
		* <p>
		* @throws IllegalArgumentException if l1 or l2 is null
		* @throws IllegalStateException if run out of criteria to order l1 and l2
		*/
		public int compare(Locale l1, Locale l2) throws IllegalArgumentException, IllegalStateException {
			Check.arg().notNull(l1);
			Check.arg().notNull(l2);
			
			int nameComparison = l1.getDisplayName().compareTo( l2.getDisplayName() );
			if (nameComparison != 0) return nameComparison;
			
			if (l1.equals(l2)) return 0;
			
			throw new IllegalStateException("ran out of criteria to order l1 = " + l1 + " and l2 = " + l2);
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None: main tests it
	
}


