/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer Notes:

+++ this class is obsoleted now by better equivalent in other collections libraries from google and apache?

--this class was written quick and dirty: should make it an interface instead,
and have this class be a non-synchronized implementation,
and then make a wrapper (or subclass) synchronized version


--this class needs an iterator method?
	--one problem: cannot do something simple like return
		map.keySet()
	or
		map.keySet().iterator()
	since those results are backed by the underlying Map, and so can change it
	(which is a great way to introduce inconsistency)


--initally thought of using 2 different Maps for the 2 different map directions (e.g. a-->b and b-->a).
The problem with this approach is that, when presented with a general object,
you then have to test each Map for the mapping, since you have no idea which Map will contain it.

*/


package bb.util;


import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;


/**
* An object that provides a way to associate pairs of Objects.
* <p>
* Ordinary Maps provide a one-way association: key-->value.
* This class provides a stronger two-way association: object1<-->object2.
* In other words, there is no distinction between keys and values:
* once an association has been established between 2 Objects, either one may be used as a key to retrieve the other.
* A side effect of this strong association is that a given Object may only be associated with at most
* one other Object at a given time in a given BidirectionalMap instance.
* (In contrast, with ordinary Maps, keys must be unique but values need not be.)
* <p>
* This class offers the obvious methods:
* {@link #put put}, {@link #contains contains}, {@link #get get}, {@link #remove remove},
* {@link #removeIfPresent removeIfPresent}.
* <p>
* The capacity of the internal data structures of this class will always grow (up to memory limits)
* to accomodate any number of associations.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class BidirectionalMap {


	// -------------------- constants --------------------
	
	
	protected static final int capacityInitial_default = 16;
	protected static final float loadFactor_default = 0.75f;


	// -------------------- instance fields --------------------


	private final Map<Object,Object> map;


	// -------------------- constructor --------------------


	/**
	* Constructs a new BidirectionalMap instance with the specified initial capacity and load factor.
	* <p>
	* <i>The initialCapacity and loadFactor arguments are strictly for performance tuning</i>.
	* For instance, if a good estimate exists for the number of associations,
	* then specifying it via initialCapacity may lead to fewer resize operations in the internal data structures.
	* Similarly, the loadFactor parameter specifies the trade off between wasted storage space versus
	* potential resize operations in the internal data structures.
	* These parameters have the same meaning as in the {@link HashMap} class.
	* <p>
	* @param initialCapacity the initial number of associations that this instance will have the capacity for
	* @param loadFactor a measure of how full the internal data storage structures are allowed to get before their capacity is automatically increased
	*/
	public BidirectionalMap(int initialCapacity, float loadFactor) {
		map = new HashMap<Object,Object>( 2*initialCapacity, loadFactor );	// factor of 2 is because we will put in 2 maps for each association
	}


	/** Constructs a new BidirectionalMap instance; simply calls <code>this(initialCapacity, loadFactor_default)</code>. */
	public BidirectionalMap(int initialCapacity) {
		this(initialCapacity, loadFactor_default);
	}


	/** Constructs a new BidirectionalMap instance; simply calls <code>this(capacityInitial_default)</code>. */
	public BidirectionalMap() {
		this(capacityInitial_default);
	}


	// -------------------- public API methods: put, contains, get, remove --------------------


	/**
	* Establishes an association (i.e. a bidirectional map) between object1 and object2.
	* Any previous association involving object1 is first removed, and likewise with object2.
	* Either arg may be null.
	* The order of the args is irrelevant for establishing the bidirectional map.
	*/
	public void put(Object object1, Object object2) throws IllegalArgumentException {
		removeIfPresent(object1);
		removeIfPresent(object2);

		map.put(object1, object2);	// map object1-->object2
		map.put(object2, object1);	// map object2-->object1
	}


	/**
	* Determines whether or not this instance contains object
	* (i.e. whether or not object is currently associated with anything).
	*/
	public boolean contains(Object object) {
		return map.containsKey(object);
	}


	/**
	* Returns the partner Object that has been associated with object.
	* <p>
	* @throws IllegalArgumentException if object is not currently associated with anything
	*/
	public Object get(Object object) throws IllegalArgumentException {
		if (!contains(object)) throw new IllegalArgumentException("arg object is not currently associated with anything");

		Object associatedObject = map.get(object);	// use the map object-->associatedObject
		assert isReverseMapped(object, associatedObject) : "The internal state of this BidirectionalMap has become inconsistent (there is a pair for which it either fails to have a map in the reverse direction, or else this second map points back to a different Object)";

		return associatedObject;
	}


	/**
	* Completely unmaps object from its partner.
	* <p>
	* Note: the disassociation is done in both directions (object-->partner and partner-->object)
	* so that there is no need (indeed, it is an error) to subsequently call remove(partner).
	* <p>
	* @return the partner that used to be associated with object
	* @throws IllegalArgumentException if object is not currently associated with anything
	*/
	public Object remove(Object object) throws IllegalArgumentException {
		if (!contains(object)) throw new IllegalArgumentException("arg object is not currently associated with anything");

		Object associatedObject = map.remove(object);	// unmap object-->associatedObject
		assert isReverseMapped(object, associatedObject) : "The internal state of this BidirectionalMap has become inconsistent (there is a pair for which it either fails to have a map in the reverse direction, or else this second map points back to a different Object)";
		map.remove(associatedObject);	// unmap associatedObject-->(WHAT SHOULD BE)object

		return associatedObject;
	}


	/**
	* Completely unmaps object from its partner, if an association exists.
	* Else, if object has no association, this method does nothing.
	* <p>
	* Note: the disassociation, if performed, is done in both directions (object-->partner and partner-->object)
	* so that there is no need to subsequently call removeIfPresent(partner).
	*/
	public void removeIfPresent(Object object) {
		if (!contains(object)) return;

		Object associatedObject = map.remove(object);	// unmap object-->associatedObject
		assert isReverseMapped(object, associatedObject) : "The internal state of this BidirectionalMap has become inconsistent (there is a pair for which it either fails to have a map in the reverse direction, or else this second map points back to a different Object)";
		map.remove(associatedObject);	// unmap associatedObject-->(WHAT SHOULD BE)object
	}


	// -------------------- helper methods: removeIfPresent, isReverseMapped --------------------


	/**
	* Determines whether or not the reverse map of keysPartner-->key exists in this BidirectionalMap instance.
	* The purpose of this method is to enable consistency checks on the internal state of this BidirectionalMap.
	*/
	protected boolean isReverseMapped(Object key, Object keysPartner) {
		if (map.containsKey(keysPartner)) {
			Object shouldBeKey = map.get(keysPartner);
			return (shouldBeKey == key);
		}
		else
			return false;
	}


	// -------------------- UnitTest (static inner class) --------------------


	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_put_get_remove() {
			BidirectionalMap bm = new BidirectionalMap();
			Object obj1 = new Object();
			Object obj2 = new Object();
			bm.put(obj1, obj2);
			
			Object shouldbe_obj2 = bm.get(obj1);
			Assert.assertSame(obj2, shouldbe_obj2);
			
			Object shouldbe_obj1 = bm.remove(obj2);
			Assert.assertSame(obj1, shouldbe_obj1);
		}
		
	}


}
