/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
* Imposes an ordering on Strings that is <b>inconsistent with equals</b>; see {@link #compare compare} for details.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class CaseInsensitiveComparator implements Comparator<String>, Serializable {	// see the file compareImplementation.txt for more discussion
	
	// -------------------- constants --------------------
	
	private static final long serialVersionUID = 1;
	
	// -------------------- compare --------------------
	
	/**
	* Converts s1 and s2 to lower case and then returns <code>s1.{@link String#compareTo compareTo}(s2)</code>.
	* So, the result is consistent with {@link String#equalsIgnoreCase equalsIgnoreCase}
	* but is <b>inconsistent with {@link String#equals equals}</b>.
	* <p>
	* @throws IllegalArgumentException if s1 or s2 is null
	*/
	public int compare(String s1, String s2) throws IllegalArgumentException {
		Check.arg().notNull(s1);
		Check.arg().notNull(s2);
		
		return s1.toLowerCase().compareTo( s2.toLowerCase() );
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_compare() {
			SortedSet<String> set = new TreeSet<String>( new CaseInsensitiveComparator() );
			set.add("aa");
			set.add("aA");
			set.add("Aa");
			set.add("AA");
			Assert.assertEquals(1, set.size());
			Assert.assertTrue( set.contains("aa") );
			Assert.assertTrue( set.contains("aA") );
			Assert.assertTrue( set.contains("Aa") );
			Assert.assertTrue( set.contains("AA") );
		}
		
	}
	
}
