/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

--I thought about dropping synchronization for multithread support and going to a write lock for the add method
and read locks for the others which in theory would permit much greater concurrency.
I decided NOT to do this after reading the javadocs on java.util.concurrent.locks.ReadWriteLock:
because I expect the add method to be called the most in typical applications,
and the remaining query methods only a few times at the end of some process,
there will not be any gain (indeed there may be a performance loss due to the added complexity of RW locks).

Another problem is that when you use synchronized, you force the latest versions of the data to be loaded
but it is not clear at all that using an RW lock will guarantee you the same thing.

Note that if it were not for the atomicity required inside the add method
(wherein you need to see if obj has been mapped to a count or not),
then the concurrent properties of the AtomicLongs used for the counts
would allow you to use a ConcurrentHashMap
and a lock which allows arbitrary concurrent threads to call add
and arbitrary concurrent threads to call the other methods
(but add or any of those other methods could not simultaneously be called).

*/


package bb.util;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;


/**
* Class to which arbitrary objects may be {@link #add added},
* and statistical inquiries may be made about
* (e.g. {@link #getSet getSet}, {@link #getMostCommon getMostCommon}, {@link #getFraction getFraction},
* {@link #getCountTotal getCountTotal}, {@link #getCount getCount}).
* <p>
* This class is multithread safe: all public methods are synchronized.
* <p>
* @author Brent Boyer
* @param <E> Type of element stored
*/
public class ObjectCounter<E> {
	
	
	// -------------------- instance fields --------------------
	
	
	private final Map<E, AtomicLong> objectToCount = new HashMap<E, AtomicLong>();	// Note: in the current design, we are using an AtomicLong instead of a Long not for any concurrency benefits but because the long value contained inside can be mutated without creating a new instance, which should cut down on unnecessary object creation and garbage collection; but see the Programmer notes at the start of this file for more discussion


	private long countTotal = 0;
	
	
	// -------------------- constructor --------------------
	
	
	public ObjectCounter() {}
	
	
	// -------------------- public api --------------------
	
	
	/**
	* Places obj into an internal Map field and increments its occurrence count.
	* <p>
	* Using a Map means that the set of Objects added via this method which are considered equivalent
	* (and thus share a common count) is determined on the basis of their equals method.
	* <p>
	* @param obj an arbitrary Object (of this instance's parameterized type E) or null
	* @throws IllegalStateException if countTotal == Long.MAX_VALUE
	*/
	public synchronized void add(E obj) throws IllegalStateException {
		if (countTotal == Long.MAX_VALUE) throw new IllegalStateException("cannot add anymore objects because countTotal == Long.MAX_VALUE = " + Long.MAX_VALUE);
		
		AtomicLong count = objectToCount.get(obj);
		if (count == null) {
			count = new AtomicLong(0);
			objectToCount.put( obj, count );
		}
		count.incrementAndGet();
		++countTotal;
	}
	
	
	/**
	* Returns the set of all objects that have been added to this instance.
	* <p>
	* Contract: the result may be zero-length, but is never null.
	*/
	public synchronized Set<E> getSet() throws IllegalStateException {
		return objectToCount.keySet();
	}
	
	
	/**
	* Returns that object (of this instance's parameterized type E) which is the most common
	* (i.e. has been added to this instance the most times, and thus has the largest count).
	* <p>
	* @throws IllegalStateException if zero objects have been added to this instance
	*/
	public synchronized E getMostCommon() throws IllegalStateException {
		if (countTotal == 0) throw new IllegalStateException("Impossible to execute mostCommon because zero Objects have been added to this instance");

		E mostCommon = null;
		long countMax = 0;
		for (E obj : objectToCount.keySet()) {
			long count = objectToCount.get(obj).longValue();
			if (count > countMax) {
				mostCommon = obj;
				countMax = count;
			}
		}
		return mostCommon;
	}
	
	
	/**
	* Returns the fraction of the total population of objects stored by this instance which equal obj
	* (i.e. {@link #getCount getCount}(obj) divided by {@link #getCountTotal getCountTotal}).
	* <p>
	* @throws IllegalStateException if zero objects have been added to this instance
	*/
	public synchronized double getFraction(E obj) throws IllegalStateException {
		if (countTotal == 0) throw new IllegalStateException("Impossible to execute getFraction because zero Objects have been added to this instance");

		return getCount(obj) / ((double) countTotal);
	}
	
	
	/** Returns the total count (i.e. the total number of objects that have been added to this instance). */
	public synchronized long getCountTotal() { return countTotal; }
	
	
	/** Returns obj's count (i.e. the number of objects that have been added to this instance which equal obj). */
	public synchronized long getCount(E obj) {
		AtomicLong count = objectToCount.get(obj);
		if (count == null) return 0;
		return count.longValue();
	}


	// -------------------- UnitTest (static inner class) --------------------


	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_takesOnlyStrings() throws Exception {
			ObjectCounter<String> counter = new ObjectCounter<String>();
			counter.add( "String1" );
			counter.add( "String2" );
			counter.add( new String("String2") );	// so "String2" is in 2 times
			counter.add( "String3" );
			counter.add( new String("String3") );
			counter.add( new String("String3") );	// so "String3" is in 3 times
			Assert.assertEquals( "String3", counter.getMostCommon() );
			SortedSet<String> objectSet = new TreeSet<String>( counter.getSet() );
			Assert.assertEquals( "String1", objectSet.first() );
		}
		
		@Test public void test_takesArbitraryObjects() throws Exception {
			ObjectCounter<Object> counter = new ObjectCounter<Object>();
			counter.add( null );
			counter.add( "Object that is a String" );
			counter.add( new String("Object that is a String") );	// force this one to be a separate instance but that is equivalent
			counter.add( new Integer(3) );
			counter.add( new Integer(3) );
			counter.add( new Integer(3) );
			Assert.assertTrue( counter.getCountTotal() == 6 );
			Assert.assertTrue( counter.getCount(null) == 1 );
			Assert.assertTrue( counter.getFraction(null) == 1.0 / 6.0 );
			Assert.assertTrue( counter.getCount("Object that is a String") == 2 );
			Assert.assertTrue( counter.getFraction("Object that is a String") == 2.0 / 6.0 );
			Assert.assertTrue( counter.getCount( new Integer(3) ) == 3 );
			Assert.assertTrue( counter.getFraction( new Integer(3) ) == 3.0 / 6.0 );
			Assert.assertEquals( new Integer(3), counter.getMostCommon() );
		}

	}
	
	
}
