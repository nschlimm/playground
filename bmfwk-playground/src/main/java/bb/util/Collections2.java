/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

// WARNING: THE COPYRIGHT NOTICE ABOVE ONLY APPLIES TO THE Collections2 CLASS BELOW THAT I WROTE.
// BEWARE THAT THE Unmodifiables CLASS AT THE END OF THIS FILE CONTAINS CODE THAT WAS COPIED FROM SUN'S SOURCE CODE, AND I AM NOT SURE WHAT THE LEGAL STATUS OF THAT IS.

package bb.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.RandomAccess;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
* Adds functionality that is missing from {@link Collections}.
* <i>There is no reason for this class other than oversight on Sun's part: if I could convince Sun to include this stuff in their Collections class then could eliminate this.</i>
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class Collections2 {
	
	// -------------------- unmodifiableNavigableSet --------------------
	
	/**
	* Returns an unmodifiable view of navigableSet.
	* This method allows modules to safely provide users with direct "read-only" access of internal NavigableSets.
	* Query operations on the result "read through" to navigableSet, and attempts to modify the result,
	* whether direct or via its iterator, result in an <code>UnsupportedOperationException</code>.
	* <p>
	* The returned collection will be {@link Serializable} if navigableSet is Serializable.
	* <p>
	* @param navigableSet the NavigableSet for which an unmodifiable view is to be returned
	* @return an unmodifiable view of navigableSet
	*/
	public static <E> NavigableSet<E> unmodifiableNavigableSet(NavigableSet<E> navigableSet) {
		return new UnmodifiableNavigableSet<E>(navigableSet);
	}
	
// +++ need to add: checkedNavigableSet, checkedNavigableMap, unmodifiableNavigableMap, synchronizedNavigableSet, synchronizedNavigableMap...
// OR is there a better way to generate all these permutations?
	
	/**
	* @serial include
	*/
	static class UnmodifiableNavigableSet<E> extends Unmodifiables.UnmodifiableSortedSet<E> implements NavigableSet<E>, Serializable {
		
		private static final long serialVersionUID = -3241754943941475848L;	// determined this value by executing the following (BEFORE this field was defined in this class): serialver  -classpath ../class  bb.util.Collections2$UnmodifiableNavigableSet
		
		/** @serial */
		private final NavigableSet<E> ns;
		
		UnmodifiableNavigableSet(NavigableSet<E> ns) {
			super(ns);
			this.ns = ns;
		}
		
			// arrived at the methods below simply by examining the javadocs for NavigableSet and seeing what were all the methods that were defined just in that class (should work, assuming that our UnmodifiableXXX class hierarchy correctly wraps all other methods)
		
		public E ceiling(E e) { return ns.ceiling(e); }
		
		public Iterator<E> descendingIterator() {
			return new Iterator<E>() {
				Iterator<? extends E> i = ns.descendingIterator();
				
				public boolean hasNext() { return i.hasNext(); }
				public E next() { return i.next(); }
				public void remove() { throw new UnsupportedOperationException(); }
			};
		}
		
		public NavigableSet<E> descendingSet() {
			return new UnmodifiableNavigableSet<E>( ns.descendingSet() );
		}
		
		public E floor(E e) { return ns.floor(e); }
		
		//public SortedSet<E> headSet(E toElement)	NO NEED: already done by our superclass, UnmodifiableSortedSet
		
		public NavigableSet<E> headSet(E toElement, boolean inclusive) {
			return new UnmodifiableNavigableSet<E>( ns.headSet(toElement, inclusive) );
		}
		
		public E higher(E e) { return ns.higher(e); }
		
		//public Iterator<E> iterator()	NO NEED: already done by our ancestorclass, UnmodifiableCollection
		
		public E lower(E e) { return ns.lower(e); }
		
		public E pollFirst() { throw new UnsupportedOperationException(); }
		
		public E pollLast() { throw new UnsupportedOperationException(); }
		
		public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new UnmodifiableNavigableSet<E>( ns.subSet(fromElement, fromInclusive, toElement, toInclusive) );
		}
		
		//public SortedSet<E> subSet(E fromElement, E toElement)	NO NEED: already done by our superclass, UnmodifiableSortedSet
		
		//publicSortedSet<E> tailSet(E fromElement)	NO NEED: already done by our superclass, UnmodifiableSortedSet
		
		public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
			return new UnmodifiableNavigableSet<E>( ns.tailSet(fromElement, inclusive) );
		}
		
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private Collections2() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_unmodifiableNavigableSet_pass() {
			NavigableSet<String> set1 = new TreeSet<String>( Arrays.asList("a", "b", "y", "z") );
			NavigableSet<String> set2 = unmodifiableNavigableSet(set1);
			
			Assert.assertEquals( set1, set2 );
			
			Assert.assertEquals( "y", set2.ceiling("n") );
			
			List<String> list = new ArrayList<String>( set2.size() );
			for (Iterator<String> iter = set2.descendingIterator(); iter.hasNext(); ) {
				list.add(iter.next());
			}
			Assert.assertEquals( Arrays.asList("z", "y", "b", "a"), list );
			
			Assert.assertEquals( set1.descendingSet(), set2.descendingSet() );
			
			Assert.assertEquals( "b", set2.floor("n") );
			
			Set<String> set = new HashSet<String>( Arrays.asList("a") );
			Assert.assertEquals( set, set2.headSet("b") );
			
			set = new HashSet<String>( Arrays.asList("a", "b") );
			Assert.assertEquals( set, set2.headSet("b", true) );
			
			Assert.assertEquals( "z", set2.higher("y") );
			
			list = new ArrayList<String>( set2.size() );
			for (Iterator<String> iter = set2.iterator(); iter.hasNext(); ) {
				list.add(iter.next());
			}
			Assert.assertEquals( Arrays.asList("a", "b", "y", "z"), list );
			
			Assert.assertEquals( "a", set2.lower("b") );
			
			set = new HashSet<String>( Arrays.asList("b", "y") );
			Assert.assertEquals( set, set2.subSet("a", false, "y", true) );
			
			set = new HashSet<String>( Arrays.asList("a", "b") );
			Assert.assertEquals( set, set2.subSet("a", "y") );
			
			set = new HashSet<String>( Arrays.asList("y", "z") );
			Assert.assertEquals( set, set2.tailSet("y") );
			
			set = new HashSet<String>( Arrays.asList("z") );
			Assert.assertEquals( set, set2.tailSet("y", false) );
		}
		
		/** Main purpose of this method is to prove that can add Date2 instances to a NavigableSet<Date>. */
		@Test public void test_unmodifiableNavigableSet_pass2() {
			NavigableSet<Date> set1 = new TreeSet<Date>();
			set1.add( new Date2(0) );
			NavigableSet<Date> set2 = unmodifiableNavigableSet(set1);
			Assert.assertEquals( set1, set2 );
		}
		
		@Test(expected=UnsupportedOperationException.class) public void test_unmodifiableNavigableSet_fail() {
			NavigableSet<String> set1 = new TreeSet<String>( Arrays.asList("a", "b", "y", "z") );
			NavigableSet<String> set2 = unmodifiableNavigableSet(set1);
			set2.clear();
		}
		
	}
	
}


/*
++++++++++++++++++++++++++++++++++++++++++++++++++

SUN SOURCE CODE: EVERYTHING BELOW THIS POINT WAS COPIED -almost- VERBATIM out of the JDK 1.6.0.15 version of java.util.Collections on 2009-08-04.

In order to get it to compile without errors and warnings, however, some lines needed to be added/changed.

Every such line has a comment at the end that starts with "// +++" and states the modification.

++++++++++++++++++++++++++++++++++++++++++++++++++
*/


class Unmodifiables {
	
    /**
     * @serial include
     */
    static class UnmodifiableCollection<E> implements Collection<E>, Serializable {
	// use serialVersionUID from JDK 1.2.2 for interoperability
	private static final long serialVersionUID = 1820017752578914078L;

	/** @serial */	// +++  added this line to suppress javadoc warning
	final Collection<? extends E> c;

	UnmodifiableCollection(Collection<? extends E> c) {
            if (c==null)
                throw new NullPointerException();
            this.c = c;
        }

	public int size() 		    {return c.size();}
	public boolean isEmpty() 	    {return c.isEmpty();}
	public boolean contains(Object o)   {return c.contains(o);}
	public Object[] toArray()           {return c.toArray();}
	public <T> T[] toArray(T[] a)       {return c.toArray(a);}
        public String toString()            {return c.toString();}

	public Iterator<E> iterator() {
	    return new Iterator<E>() {
		Iterator<? extends E> i = c.iterator();

		public boolean hasNext() {return i.hasNext();}
		public E next() 	 {return i.next();}
		public void remove() {
		    throw new UnsupportedOperationException();
                }
	    };
        }

	public boolean add(E e){
	    throw new UnsupportedOperationException();
        }
	public boolean remove(Object o) {
	    throw new UnsupportedOperationException();
        }

	public boolean containsAll(Collection<?> coll) {
	    return c.containsAll(coll);
        }
	public boolean addAll(Collection<? extends E> coll) {
	    throw new UnsupportedOperationException();
        }
	public boolean removeAll(Collection<?> coll) {
	    throw new UnsupportedOperationException();
        }
	public boolean retainAll(Collection<?> coll) {
	    throw new UnsupportedOperationException();
        }
	public void clear() {
	    throw new UnsupportedOperationException();
        }
    }

    /**
     * @serial include
     */
    static class UnmodifiableSet<E> extends UnmodifiableCollection<E>
    				 implements Set<E>, Serializable {
	private static final long serialVersionUID = -9215047833775013803L;

	UnmodifiableSet(Set<? extends E> s)	{super(s);}
	public boolean equals(Object o) {return o == this || c.equals(o);}
	public int hashCode() 		{return c.hashCode();}
    }

    /**
     * @serial include
     */
    static class UnmodifiableSortedSet<E>
	                     extends UnmodifiableSet<E>
	                     implements SortedSet<E>, Serializable {
	private static final long serialVersionUID = -4929149591599911165L;
		/** @serial */	// +++  added this line to suppress javadoc warning
        private final SortedSet<E> ss;

	UnmodifiableSortedSet(SortedSet<E> s) {super(s); ss = s;}

        public Comparator<? super E> comparator() {return ss.comparator();}

        public SortedSet<E> subSet(E fromElement, E toElement) {
            return new UnmodifiableSortedSet<E>(ss.subSet(fromElement,toElement));
        }
        public SortedSet<E> headSet(E toElement) {
            return new UnmodifiableSortedSet<E>(ss.headSet(toElement));
        }
        public SortedSet<E> tailSet(E fromElement) {
            return new UnmodifiableSortedSet<E>(ss.tailSet(fromElement));
        }

        public E first() 	           {return ss.first();}
        public E last()  	           {return ss.last();}
    }

    /**
     * @serial include
     */
    static class UnmodifiableList<E> extends UnmodifiableCollection<E>
    				  implements List<E> {
        static final long serialVersionUID = -283967356065247728L;
	/** @serial */	// +++  added this line to suppress javadoc warning
	final List<? extends E> list;

	UnmodifiableList(List<? extends E> list) {
	    super(list);
	    this.list = list;
	}

	public boolean equals(Object o) {return o == this || list.equals(o);}
	public int hashCode() 		{return list.hashCode();}

	public E get(int index) {return list.get(index);}
	public E set(int index, E element) {
	    throw new UnsupportedOperationException();
        }
	public void add(int index, E element) {
	    throw new UnsupportedOperationException();
        }
	public E remove(int index) {
	    throw new UnsupportedOperationException();
        }
	public int indexOf(Object o)            {return list.indexOf(o);}
	public int lastIndexOf(Object o)        {return list.lastIndexOf(o);}
	public boolean addAll(int index, Collection<? extends E> c) {
	    throw new UnsupportedOperationException();
        }
	public ListIterator<E> listIterator() 	{return listIterator(0);}

	public ListIterator<E> listIterator(final int index) {
	    return new ListIterator<E>() {
		ListIterator<? extends E> i = list.listIterator(index);

		public boolean hasNext()     {return i.hasNext();}
		public E next()		     {return i.next();}
		public boolean hasPrevious() {return i.hasPrevious();}
		public E previous()	     {return i.previous();}
		public int nextIndex()       {return i.nextIndex();}
		public int previousIndex()   {return i.previousIndex();}

		public void remove() {
		    throw new UnsupportedOperationException();
                }
		public void set(E e) {
		    throw new UnsupportedOperationException();
                }
		public void add(E e) {
		    throw new UnsupportedOperationException();
                }
	    };
	}

	public List<E> subList(int fromIndex, int toIndex) {
            return new UnmodifiableList<E>(list.subList(fromIndex, toIndex));
        }

        /**
         * UnmodifiableRandomAccessList instances are serialized as
         * UnmodifiableList instances to allow them to be deserialized
         * in pre-1.4 JREs (which do not have UnmodifiableRandomAccessList).
         * This method inverts the transformation.  As a beneficial
         * side-effect, it also grafts the RandomAccess marker onto
         * UnmodifiableList instances that were serialized in pre-1.4 JREs.
         *
         * Note: Unfortunately, UnmodifiableRandomAccessList instances
         * serialized in 1.4.1 and deserialized in 1.4 will become
         * UnmodifiableList instances, as this method was missing in 1.4.
         */
        private Object readResolve() {
            return (list instanceof RandomAccess
		    ? new UnmodifiableRandomAccessList<E>(list)
		    : this);
        }
    }

    /**
     * @serial include
     */
    static class UnmodifiableRandomAccessList<E> extends UnmodifiableList<E>
                                              implements RandomAccess
    {
        UnmodifiableRandomAccessList(List<? extends E> list) {
            super(list);
        }

	public List<E> subList(int fromIndex, int toIndex) {
            return new UnmodifiableRandomAccessList<E>(
                list.subList(fromIndex, toIndex));
        }

        private static final long serialVersionUID = -2542308836966382001L;

        /**
         * Allows instances to be deserialized in pre-1.4 JREs (which do
         * not have UnmodifiableRandomAccessList).  UnmodifiableList has
         * a readResolve method that inverts this transformation upon
         * deserialization.
         */
        private Object writeReplace() {
            return new UnmodifiableList<E>(list);
        }
    }

    /**
     * @serial include
     */
    private static class UnmodifiableMap<K,V> implements Map<K,V>, Serializable {
	// use serialVersionUID from JDK 1.2.2 for interoperability
	private static final long serialVersionUID = -1034234728574286014L;

	/** @serial */	// +++  added this line to suppress javadoc warning
	private final Map<? extends K, ? extends V> m;

	UnmodifiableMap(Map<? extends K, ? extends V> m) {
            if (m==null)
                throw new NullPointerException();
            this.m = m;
        }

	public int size() 		         {return m.size();}
	public boolean isEmpty() 	         {return m.isEmpty();}
	public boolean containsKey(Object key)   {return m.containsKey(key);}
	public boolean containsValue(Object val) {return m.containsValue(val);}
	public V get(Object key) 	         {return m.get(key);}

	public V put(K key, V value) {
	    throw new UnsupportedOperationException();
        }
	public V remove(Object key) {
	    throw new UnsupportedOperationException();
        }
	public void putAll(Map<? extends K, ? extends V> m) {
	    throw new UnsupportedOperationException();
        }
	public void clear() {
	    throw new UnsupportedOperationException();
        }

	private transient Set<K> keySet = null;
	private transient Set<Map.Entry<K,V>> entrySet = null;
	private transient Collection<V> values = null;

	public Set<K> keySet() {
	    if (keySet==null)
		keySet = Collections.unmodifiableSet(m.keySet());	// +++ had to add Collections. before unmodifiableSet
	    return keySet;
	}

	public Set<Map.Entry<K,V>> entrySet() {
	    if (entrySet==null)
		entrySet = new UnmodifiableEntrySet<K,V>(m.entrySet());
	    return entrySet;
	}

	public Collection<V> values() {
	    if (values==null)
		values = Collections.unmodifiableCollection(m.values());	// +++ had to add Collections. before unmodifiableCollection
	    return values;
	}

	public boolean equals(Object o) {return o == this || m.equals(o);}
	public int hashCode()           {return m.hashCode();}
        public String toString()        {return m.toString();}

        /**
         * We need this class in addition to UnmodifiableSet as
         * Map.Entries themselves permit modification of the backing Map
         * via their setValue operation.  This class is subtle: there are
         * many possible attacks that must be thwarted.
         *
         * @serial include
         */
        static class UnmodifiableEntrySet<K,V>
	    extends UnmodifiableSet<Map.Entry<K,V>> {
	    private static final long serialVersionUID = 7854390611657943733L;

            @SuppressWarnings("unchecked")	// +++ added this line to suppress compiler warning
            UnmodifiableEntrySet(Set<? extends Map.Entry<? extends K, ? extends V>> s) {
                super((Set)s);
            }
            public Iterator<Map.Entry<K,V>> iterator() {
                return new Iterator<Map.Entry<K,V>>() {
		    Iterator<? extends Map.Entry<? extends K, ? extends V>> i = c.iterator();

                    public boolean hasNext() {
                        return i.hasNext();
                    }
		    public Map.Entry<K,V> next() {
			return new UnmodifiableEntry<K,V>(i.next());
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @SuppressWarnings("unchecked")	// +++ added this line to suppress compiler warning
            public Object[] toArray() {
                Object[] a = c.toArray();
                for (int i=0; i<a.length; i++)
                    a[i] = new UnmodifiableEntry<K,V>((Map.Entry<K,V>)a[i]);
                return a;
            }

            @SuppressWarnings("unchecked")	// +++ added this line to suppress compiler warning
            public <T> T[] toArray(T[] a) {
                // We don't pass a to c.toArray, to avoid window of
                // vulnerability wherein an unscrupulous multithreaded client
                // could get his hands on raw (unwrapped) Entries from c.
		Object[] arr = c.toArray(a.length==0 ? a : Arrays.copyOf(a, 0));

                for (int i=0; i<arr.length; i++)
                    arr[i] = new UnmodifiableEntry<K,V>((Map.Entry<K,V>)arr[i]);

                if (arr.length > a.length)
                    return (T[])arr;

                System.arraycopy(arr, 0, a, 0, arr.length);
                if (a.length > arr.length)
                    a[arr.length] = null;
                return a;
            }

            /**
             * This method is overridden to protect the backing set against
             * an object with a nefarious equals function that senses
             * that the equality-candidate is Map.Entry and calls its
             * setValue method.
             */
            @SuppressWarnings("unchecked")	// +++ added this line to suppress compiler warning
            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                return c.contains(new UnmodifiableEntry<K,V>((Map.Entry<K,V>) o));
            }

            /**
             * The next two methods are overridden to protect against
             * an unscrupulous List whose contains(Object o) method senses
             * when o is a Map.Entry, and calls o.setValue.
             */
            public boolean containsAll(Collection<?> coll) {
                Iterator<?> e = coll.iterator();
                while (e.hasNext())
                    if (!contains(e.next())) // Invokes safe contains() above
                        return false;
                return true;
            }
            public boolean equals(Object o) {
                if (o == this)
                    return true;

                if (!(o instanceof Set))
                    return false;
                Set s = (Set) o;
                if (s.size() != c.size())
                    return false;
                return containsAll(s); // Invokes safe containsAll() above
            }

            /**
             * This "wrapper class" serves two purposes: it prevents
             * the client from modifying the backing Map, by short-circuiting
             * the setValue method, and it protects the backing Map against
             * an ill-behaved Map.Entry that attempts to modify another
             * Map Entry when asked to perform an equality check.
             */
            private static class UnmodifiableEntry<K,V> implements Map.Entry<K,V> {
                private Map.Entry<? extends K, ? extends V> e;

                UnmodifiableEntry(Map.Entry<? extends K, ? extends V> e) {this.e = e;}

                public K getKey()	  {return e.getKey();}
                public V getValue()  {return e.getValue();}
                public V setValue(V value) {
                    throw new UnsupportedOperationException();
                }
                public int hashCode()	  {return e.hashCode();}
                public boolean equals(Object o) {
                    if (!(o instanceof Map.Entry))
                        return false;
                    Map.Entry t = (Map.Entry)o;
                    return eq(e.getKey(),   t.getKey()) &&
                           eq(e.getValue(), t.getValue());
                }
                public String toString()  {return e.toString();}
            }
        }
    }

    /**
     * @serial include
     */
    static class UnmodifiableSortedMap<K,V>
	  extends UnmodifiableMap<K,V>
	  implements SortedMap<K,V>, Serializable {
	private static final long serialVersionUID = -8806743815996713206L;

		/** @serial */	// +++  added this line to suppress javadoc warning
        private final SortedMap<K, ? extends V> sm;

	UnmodifiableSortedMap(SortedMap<K, ? extends V> m) {super(m); sm = m;}

        public Comparator<? super K> comparator() {return sm.comparator();}

        public SortedMap<K,V> subMap(K fromKey, K toKey) {
            return new UnmodifiableSortedMap<K,V>(sm.subMap(fromKey, toKey));
        }
        public SortedMap<K,V> headMap(K toKey) {
            return new UnmodifiableSortedMap<K,V>(sm.headMap(toKey));
        }
        public SortedMap<K,V> tailMap(K fromKey) {
            return new UnmodifiableSortedMap<K,V>(sm.tailMap(fromKey));
        }

        public K firstKey()           {return sm.firstKey();}
        public K lastKey()            {return sm.lastKey();}
    }

    /**
     * Returns true if the specified arguments are equal, or both null.
     */
    private static boolean eq(Object o1, Object o2) {
        return (o1==null ? o2==null : o1.equals(o2));
    }
	
}
