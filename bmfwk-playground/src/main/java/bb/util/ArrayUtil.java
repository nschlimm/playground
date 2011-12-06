/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import java.util.Random;
import org.junit.Test;

/**
* Provides static utility methods that deal with arrays.
* (All these methods should have been put in java's <code>Arrays</code> class...)
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class ArrayUtil {
	
	// -------------------- concatenate --------------------
	
	/**
	* This method concatenates two arrays into one.
	* <p>
	* The concatenation is done is according to the following rules:
	* <ol>
	*  <li>if <code>a1 == null</code>, then <code>a2</code> is returned (which could be null as well)</li>
	*  <li>else if <code>a2 == null</code>, then <code>a1</code> is returned</li>
	*  <li>else if <code>a1.length == 0</code>, then <code>a2</code> is returned (which could be zero length as well)</li>
	*  <li>else if <code>a2.length == 0</code>, then <code>a1</code> is returned</li>
	*  <li>
	*		else a new array is allocated
	*		and the elements from <code>a1</code> and then <code>a2</code>
	*		are copied in order onto it before it is returned;
	*		<i>note: the new array is created with a1's type, so a2 had better be compatible</i>
	*  </li>
	* </ol>
	*/
	@SuppressWarnings("unchecked")
	public static <T> T[] concatenate(T[] a1, T[] a2) {
			// corner case 1: at least one arg is null
		if (a1 == null) return a2;
		if (a2 == null) return a1;
			
			// corner case 2: at least one arg is zero-length
		if (a1.length == 0) return a2;
		if (a2.length == 0) return a1;
			
			// normal case 3: both arrays are non-null AND have at least 1 element
		//T[] union = new T[ a1.length + a2.length ];
			// the above does not work; this a major problem with java generics:
			//	http://weblogs.java.net/blog/dwalend/archive/2005/01/did_i_miss_gene.html
			//	http://forum.java.sun.com/thread.jspa?threadID=530823&tstart=75
			//	http://forum.java.sun.com/thread.jspa?forumID=316&threadID=457033
			//	http://forum.java.sun.com/thread.jspa?forumID=316&threadID=564355
			//	http://www.angelikalanger.com/Articles/Papers/JavaGenerics/ArraysInJavaGenerics.htm
			// so fall back on this (and hope that a2 is compatible...):
		T[] union = (T[]) java.lang.reflect.Array.newInstance(
			a1.getClass().getComponentType(),
			a1.length + a2.length
		);
		System.arraycopy(a1, 0, union, 0, a1.length);
		System.arraycopy(a2, 0, union, a1.length, a2.length);
		return union;
	}
	
	// -------------------- shuffle --------------------
	
	/**
	* Simply calls <code>{@link #shuffle(Object[], Random) shuffle}(a, new Random())</code>.
	* <p>
	* @throws IllegalArgumentException if a == null
	*/
	public static <T> void shuffle(T[] a) throws IllegalArgumentException {
		shuffle(a, new Random());
	}
	
	/**
	* Shuffles the elements of a in a random fashion.
	* <p>
	* @throws IllegalArgumentException if a == null; random == null
	*/
	public static <T> void shuffle(T[] a, Random random) throws IllegalArgumentException {
		Check.arg().notNull(a);
		Check.arg().notNull(random);
		
		if (a.length < 2) return;
		
		for (int i = a.length; i > 1; i--) {	// this code is identical to the shuffle algorithm used inside Collections.shuffle
			swap(a, i - 1, random.nextInt(i));
		}
	}
	
	/**
	* Shuffles the elements of a in a random fashion.
	* <p>
	* @throws IllegalArgumentException if a == null; random == null
	*/
	public static void shuffle(int[] a, Random random) throws IllegalArgumentException {
		Check.arg().notNull(a);
		Check.arg().notNull(random);
		
		if (a.length < 2) return;
		
		for (int i = a.length; i > 1; i--) {	// this code is identical to the shuffle algorithm used inside Collections.shuffle
			swap(a, i - 1, random.nextInt(i));
		}
	}
	
	// -------------------- swap --------------------
	
	/**
	* Swaps elements i and j of a.
	* <p>
	* @throws IllegalArgumentException if a == null
	* @throws ArrayIndexOutOfBoundsException if i or j is an illegal index value for a
	*/
	public static <T> void swap(T[] a, int i, int j) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
		Check.arg().notNull(a);
		
		T tmp = a[i];
		a[i] = a[j];
		a[j] = tmp;
	}
	
	/**
	* Swaps elements i and j of a.
	* <p>
	* @throws IllegalArgumentException if a == null
	* @throws ArrayIndexOutOfBoundsException if i or j is an illegal index value for a
	*/
	public static void swap(int[] a, int i, int j) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
		Check.arg().notNull(a);
		
		int tmp = a[i];
		a[i] = a[j];
		a[j] = tmp;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ArrayUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_concatenate() {
			String[] a1 = new String[] {"e0", "e1"};
			System.out.println( "a1: " + StringUtil.toString(a1, ", ") );
			String[] a2 = new String[] {"E2", "E3"};
			System.out.println( "a2: " + StringUtil.toString(a2, ", ") );
			String[] concat = concatenate(a1, a2);
			System.out.println( "concatenate of a1 & a2: " + StringUtil.toString(concat, ", ") );
		}
		
		@Test public void test_shuffle() {
			String[] a = new String[] {"e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9"};
			System.out.println( "a: " + StringUtil.toString(a, ", ") );
			shuffle(a);
			System.out.println( "shuffle #1 of a: " + StringUtil.toString(a, ", ") );
			shuffle(a);
			System.out.println( "shuffle #2 of a: " + StringUtil.toString(a, ", ") );
			shuffle(a);
			System.out.println( "shuffle #3 of a: " + StringUtil.toString(a, ", ") );
		}
		
		@Test public void test_swap() {
			String[] a = new String[] {"e0", "e1"};
			System.err.println( "a: " + StringUtil.toString(a, ", ") );
			swap(a, 0, 1);
			System.err.println( "swap (0, 1) of a: " + StringUtil.toString(a, ", ") );
		}
		
	}
	
}
