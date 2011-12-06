/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ as of jdk 1.6, this class should be replaced by Collections.newSetFromMap???  or maybe that would be suboptimal performance???
*/

package bb.util;


import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;


/**
* Set implementation backed by a concurrent hash table (a {@link ConcurrentHashMap} instance).
* This class is to be used precisely in those situations where {@link CopyOnWriteArraySet} has issues:
* <ol>
*  <li>the set size is large</li>
*  <li>mutative operations are as common as read-only operations</li>
*  <li>Iterators must support the mutative remove operation</li>
* </ol>
* <p>
* Because it uses a backing <tt>ConcurrentHashMap</tt> instance, this class does <i>not</i> permit the <tt>null</tt> element.
* <p>
* This class offers constant time performance for the basic operations
* ({@link #add add}, {@link #remove remove}, {@link #contains contains} and {@link #size size}),
* assuming that the hash function disperses the elements properly among the buckets.
* <p>
* Iterating over this set requires time proportional to the sum of
* the <tt>ConcurrentHashSet</tt> instance's size (the number of elements)
* plus the "capacity" of the backing <tt>ConcurrentHashMap</tt> instance (the number of buckets).
* Thus, it's very important not to set the initial capacity too high (or the load factor too low)
* if iteration performance is important.
* Note that this class makes no guarantees as to the iteration order of the set;
* in particular, it does not guarantee that the order will remain constant over time.
* <p>
* This class is thread safe,
* and offers the same full concurrency of retrievals, adjustable expected concurrency for updates,
* and iterator behavior as <tt>ConcurrentHashMap</tt>.
* <p>
* @author Brent Boyer
* @param <E> Type of element stored in the set
*/
public class ConcurrentHashSet<E> extends AbstractSet<E> implements Serializable {


	// -------------------- constants --------------------


	static final long serialVersionUID = 1L;
// +++ probably need to change this
	
	
	/** Dummy value to associate with an Object in the backing Map. */
	private static final Object present = new Object();


	// -------------------- instance fields --------------------

	
	/** @serial */
	private final ConcurrentHashMap<E, Object> map;


	// -------------------- constructors --------------------


	/**
	* Constructs a new empty set.
	* The backing <tt>ConcurrentHashMap</tt> instance has the default initial capacity (16),
	* load factor (0.75f), and concurrencyLevel (16).
	*/
	public ConcurrentHashSet() {
		map = new ConcurrentHashMap<E, Object>();
	}
	
	
	/**
	* Constructs a new empty set.
	* The backing <tt>ConcurrentHashMap</tt> instance has the specified initial capacity,
	* along with the default load factor (0.75f) and concurrencyLevel (16).
	* <p>
	* @param initialCapacity the initial capacity of the hash table
	* @throws IllegalArgumentException if initialCapacity < 0
	*/
	public ConcurrentHashSet(int initialCapacity) throws IllegalArgumentException {
		map = new ConcurrentHashMap<E,Object>(initialCapacity);
	}

	
	/**
	* Constructs a new, empty set.
	* The backing <tt>ConcurrentHashMap</tt> instance has the specified initial capacity, load factor, and concurrency level.
	* <p>
	* @param initialCapacity the initial capacity of the hash map
	* @param loadFactor the load factor of the hash map
	* @param concurrencyLevel the concurrency level of the hash map
	* @throws IllegalArgumentException if initialCapacity < 0; loadFactor <= 0; concurrencyLevel <= 0
	*/
	public ConcurrentHashSet(int initialCapacity, float loadFactor, int concurrencyLevel) throws IllegalArgumentException {
		map = new ConcurrentHashMap<E, Object>(initialCapacity, loadFactor, concurrencyLevel);
	}
	
	
	/**
	* Constructs a new set containing the elements in the specified collection.
	* The backing <tt>ConcurrentHashMap</tt> instance has an initial capacity
	* sufficient to contain the elements in the specified collection or 16, if that is greater.
	* It also has the default load factor (0.75f) and concurrencyLevel (16).
	* <p>
	* @param c the collection whose elements are to be placed into this set
	* @throws IllegalArgumentException if c == null
	*/
	public ConcurrentHashSet(Collection<? extends E> c) throws IllegalArgumentException {
		Check.arg().notNull(c);
		
		map = new ConcurrentHashMap<E, Object>( (int) Math.max((c.size()/0.75f) + 1, 16) );
		addAll(c);
	}
	

/*
+++ this was copied from HashSet; only introduce if likewise add a ConcurrentLinkedHashSet class

	**
	* Constructs a new, empty linked hash set.
	* (This package private constructor is only used by ConcurrentLinkedHashSet.)
	* The backing ConcurrentHashMap instance is a LinkedHashMap with the specified initial
	* capacity and the specified load factor.
	* <p>
	* @param initialCapacity the initial capacity of the hash map.
	* @param loadFactor the load factor of the hash map.
	* @param dummy ignored (distinguishes this constructor from other int, float constructor.)
	* @throws IllegalArgumentException if the initial capacity is less than zero, or if the load factor is nonpositive.
	*
	ConcurrentHashSet(int initialCapacity, float loadFactor, boolean dummy) {
		map = new LinkedHashMap<E,Object>(initialCapacity, loadFactor);
	}
*/

	// -------------------- Set api --------------------
	
	
	public int size() { return map.size(); }
	
	
	public boolean isEmpty() { return map.isEmpty(); }
	
	
	public boolean contains(Object o) { return map.containsKey(o); }
	
	
	public boolean add(E o) { return (map.put(o, present) == null); }
	
	
	public boolean remove(Object o) { return (map.remove(o) == present); }
	
	
	public void clear() { map.clear(); }
	
	
	/**
	* Returns an {@link Iterator} over the elements in this set.
	* The elements are returned in no particular order.
	* The result supports {@link Iterator#remove Iterator.remove}.
	* <p>
	* @return an Iterator over the elements in this set
	*/
	public Iterator<E> iterator() {
		return map.keySet().iterator();
	}
	
	
	// Note: all other methods of the Set api are adequately handled by our superclass, AbstractSet
	

	// -------------------- clone --------------------

	
// +++ unimplemented because ConcurrentHashMap does not implement Cloneable


	// -------------------- serialization methods: writeObject, readObject --------------------
	
	
	// No need: the only state in this class is the ConcurrentHashMap field, and ConcurrentHashMap is serializable

	
}
