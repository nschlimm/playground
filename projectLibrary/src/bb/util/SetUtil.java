/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods for Sets.
* <p>
* Every method comes in two versions.
* Tne first type takes SortedSet args, and preserves the sortedness in the result, which is always a NavigableSet instance.
* The second type takes arbitrary Sets.
* <p>
* Every method returns a new Set instance, and never modifies any of its args.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class SetUtil {
	
	// -------------------- set operations: union, intersection, difference --------------------
	
	// see also: http://docs.roxen.com/pike/7.0/tutorial/expressions/set_operations.xml
	
	/**
	* Returns a new NavigableSet instance that is the union of set1 and set2 (i.e. every element that is in either set1 or set2).
	* <p>
	* The args set1 and set2 are two SortedSets that share the same Comparable element type.
	* The result is a NavigableSet of the same element type.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	@SuppressWarnings("unchecked")
	public static <T extends Comparable> NavigableSet<T> union(SortedSet<T> set1, SortedSet<T> set2) throws IllegalArgumentException {
		return (NavigableSet) union( set1, set2, new TreeSet<T>() );
	}
	
	/**
	* Returns a new Set instance that is the union of set1 and set2 (i.e. every element that is in either set1 or set2).
	* <p>
	* The args set1 and set2 may be any type of Set class.
	* The element type of the result is the most specific common superclass of set1 and set2.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	public static <T> Set<T> union(Set<? extends T> set1, Set<? extends T> set2) throws IllegalArgumentException {
		return union( set1, set2, new HashSet<T>() );
	}
	
	private static <T> Set<T> union(Set<? extends T> set1, Set<? extends T> set2, Set<T> target) throws IllegalArgumentException {
		Check.arg().notNull(set1);
		Check.arg().notNull(set2);
		Check.arg().notNull(target);
		
		target.addAll( set1 );
		target.addAll( set2 );
		
		return target;
	}
	
	/**
	* Returns a new NavigableSet instance that is the intersection of set1 and set2 (i.e. every element that is in both set1 and set2).
	* <p>
	* The args set1 and set2 are two SortedSets that share the same Comparable element type.
	* The result is a NavigableSet of the same element type.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	@SuppressWarnings("unchecked")
	public static <T extends Comparable> NavigableSet<T> intersection(SortedSet<T> set1, SortedSet<T> set2) throws IllegalArgumentException {
		return (NavigableSet) intersection( set1, set2, new TreeSet<T>() );
	}
	
	/**
	* Returns a new Set instance that is the intersection of set1 and set2 (i.e. every element that is in both set1 and set2).
	* <p>
	* The args set1 and set2 may be any type of Set class.
	* The element type of the result is the most specific common superclass of set1 and set2.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	public static <T> Set<T> intersection(Set<? extends T> set1, Set<? extends T> set2) throws IllegalArgumentException {
		return intersection( set1, set2, new HashSet<T>() );
	}
	
	private static <T> Set<T> intersection(Set<? extends T> set1, Set<? extends T> set2, Set<T> target) throws IllegalArgumentException {
		Check.arg().notNull(set1);
		Check.arg().notNull(set2);
		Check.arg().notNull(target);
		
		target.addAll( set1 );
		target.retainAll( set2 );
		
		return target;
	}
	
	/**
	* Returns a new NavigableSet instance that is the difference of set1 and set2 (i.e. every element of set1 that is not in set2).
	* <p>
	* The args set1 and set2 are two SortedSets that share the same Comparable element type.
	* The result is a NavigableSet of the same element type.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	@SuppressWarnings("unchecked")
	public static <T extends Comparable> NavigableSet<T> difference(SortedSet<T> set1, SortedSet<T> set2) throws IllegalArgumentException {
		return (NavigableSet) difference( set1, set2, new TreeSet<T>() );
	}
	
	/**
	* Returns a new Set instance that is the difference of set1 and set2 (i.e. every element of set1 that is not in set2).
	* <p>
	* The args set1 and set2 may be any type of Set class.
	* The element type of the result is the most specific common superclass of set1 and set2.
	* <p>
	* @throws IllegalArgumentException if set1 == null; set2 == null
	*/
	public static <T> Set<T> difference(Set<? extends T> set1, Set<? extends T> set2) throws IllegalArgumentException {
		return difference( set1, set2, new HashSet<T>() );
	}
	
	private static <T> Set<T> difference(Set<? extends T> set1, Set<? extends T> set2, Set<T> target) throws IllegalArgumentException {
		Check.arg().notNull(set1);
		Check.arg().notNull(set2);
		Check.arg().notNull(target);
		
		target.addAll( set1 );
		target.removeAll( set2 );
		
		return target;
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private SetUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_setOperations_sorted() {
			SortedSet<String> set1 = new TreeSet<String>( Arrays.asList("a", "b") );
			SortedSet<String> set2 = new TreeSet<String>( Arrays.asList("b", "c") );
			
			NavigableSet<String> unionExpected = new TreeSet<String>( Arrays.asList("a", "b", "c") );
			Assert.assertEquals( unionExpected, union(set1, set2) );
			
			NavigableSet<String> intersectionExpected = new TreeSet<String>( Arrays.asList("b") );
			Assert.assertEquals( intersectionExpected, intersection(set1, set2) );
			
			NavigableSet<String> differenceExpected = new TreeSet<String>( Arrays.asList("a") );
			Assert.assertEquals( differenceExpected, difference(set1, set2) );
		}
		
		@Test public void test_setOperations_unsorted() {
			Object obj1Only = new Object();
			String objShared = "objShared";
			String obj2Only = "obj2Only";
			Set<Object> set1 = new HashSet<Object>( Arrays.asList( obj1Only, objShared ) );
			Set<String> set2 = new HashSet<String>( Arrays.asList( obj2Only, objShared ) );
			
			Set<Object> unionExpected = new HashSet<Object>( Arrays.asList(obj1Only, obj2Only, objShared) );
			Assert.assertEquals( unionExpected, union(set1, set2) );
			
			Set<Object> intersectionExpected = new HashSet<Object>( Arrays.asList(objShared) );
			Assert.assertEquals( intersectionExpected, intersection(set1, set2) );
			
			Set<Object> differenceExpected = new HashSet<Object>( Arrays.asList(obj1Only) );
			Assert.assertEquals( differenceExpected, difference(set1, set2) );
		}
		
	}
	
}
