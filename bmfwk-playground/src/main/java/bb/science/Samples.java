/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--the current implementation uses a single double[], which is resized as necessary, to hold all the values
	--thus, this class is to doubles what ArrayList is to Objects
	--an alternative implementation would be to have an internal List of double[]s:
		--whenever exceed the capacity of the current array, simply allocate a new one, and add to that until it fills up, etc
		--this would avoid the expense of copying elements that the current implementation faces whenever it needs to resize to a new, larger, array due to an element being added past the current capacity
		--another possible advantage is that could store extremely large amountss of data:
			--recall that an inherent limitation with Java for both arrays and Collections is that they can only hold at most Integer.MAX_VALUE values
			--for discussions of the array size restrictions in Java, see
				"...this means, specifically, that the type of a dimension expression must not be long"
				http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.10

				http://forum.java.sun.com/thread.jspa?forumID=31&threadID=598705
			--as far as Collections go, note that Collection.size returns an int so here too Integer.MAX_VALUE is an absolute upper bound

+++ future evolution of this class:
	--Samples should be an interface instead
	--the implementation below is meant for speed: it has unsynchronized methods, data is unsorted but stored as added
	--but other subclasses could make different guarantees, such as: thread safe, sorted data, resettable, etc
	--since it might be nice to subclass this class, more members should have protected access
	--should I expose random access of elements like ArrayList does, or maintain the current requirement that the user call values if they need that?
*/

package bb.science;

import bb.util.Check;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
* Simply stores double values via the {@link #add add} method.
* Typically, these are samples from some sort of measurement.
* <p>
* No restriction is placed on the values stored except this: add only accepts normal (non-NaN and non-infinite) doubles.
* This restriction means that the result returned by {@link #values values}
* may be safely supplied to other classes (e.g. the statistical routines inside {@link Math2}).
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
* @see StatsOverTime
*/
public class Samples {
	
	// -------------------- constants --------------------
	
	/** Specifies the default value for the initial size of the values buffer. */
	private static final int sizeInitial_default = 1024;
	
	private static final int integerMaxDiv2 = Integer.MAX_VALUE / 2;
	
	// -------------------- fields --------------------
	
	/** Buffer which holds the data values. */
	private double[] values = null;
	
	/** Stores the number of data values recorded by this instance. Hence, n - 1 is the last valid index of {@link #values}. */
	private int n = 0;
	
	// -------------------- constructors --------------------
	
	/** Constructor which allocates an initial buffer with size {@link #sizeInitial_default}. */
	public Samples() {
		this(sizeInitial_default);
	}
	
	/**
	* Constructor which allocates an initial buffer with size specified by sizeInitial.
	* <p>
	* @throws IllegalArgumentException if sizeInitial <= 0
	*/
	public Samples(int sizeInitial) throws IllegalArgumentException {
		Check.arg().positive(sizeInitial);
		
		values = new double[sizeInitial];
	}
	
	// -------------------- add and helper methods --------------------
	
	/**
	* Adds d to the internal array which stores all the values.
	* This array will be resized if necessary in order to hold d.
	* <p>
	* @throws IllegalArgumentException if d is not {@link Check#normal normal};
	* @throws IllegalStateException if the buffer's length = {@link Integer#MAX_VALUE}
	*/
	public void add(double d) throws IllegalArgumentException, IllegalStateException {
		Check.arg().normal(d);
		
		if (n == values.length) increaseBuffer();
		values[n++] = d;
	}
	
	/**
	* Increases the capacity of the values buffer.
	* The current implementation doubles the capacity (within an upper bound of {@link Integer#MAX_VALUE}).
	* <p>
	* @throws IllegalStateException if the buffer's length = {@link Integer#MAX_VALUE}
	*/
	private void increaseBuffer() throws IllegalStateException {
		if (values.length == Integer.MAX_VALUE) throw new IllegalStateException("cannot resize the buffer, since its length already = Integer.MAX_VALUE = " + Integer.MAX_VALUE + " (Java restricts arrays to int addressing)");
		
		int lengthNext = (values.length < integerMaxDiv2) ? 2 * values.length : Integer.MAX_VALUE;
		values = Arrays.copyOf(values, lengthNext);
	}
	
	// -------------------- accessors and mutators --------------------
	
	/** Returns the number of values that have been added to this instance. */
	public int size() { return n; }
	
	/**
	* Returns a new array which holds all the values that have been added to this instance.
	* <p>
	* The implementation here returns an array which has the values in the order that they were added.
	* <p>
	* Contract: the result is never null, but will be zero-length if no values have ever been added.
	*/
	public double[] values() {
		return Arrays.copyOf(values, n);
	}
	
/*
	** Clears all previously added values from this instance. *
	public void reset() {
		n = 0;
	}
Have decided for now that it is a bad idea to have this method: user should simply create a new instance instead.
+++ see Programmer notes at beginning of class for other alternatives.

If ever have a subclass which has this method, then need to modify the javadocs of many of the above methods to read like this:
	* Returns a new array which holds all the values that are currently being stored by this instance.
	* This is the values added since the last call to {@link #reset reset}, if it has ever been called,
	* or since construction, if reset has never been called.
*/
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_add() throws Exception {
			int n = 100;
			Samples samples = new Samples(n);
			for (int i = 1; i <= n; i++) {
				samples.add( i );
			}
			double[] values = samples.values();
			double sum = 0.0;
			for (double d : values) sum += d;
			Assert.assertEquals(5050, sum, 0);	// the sum of the integers [1, 100] is 5050 by Gauss's famous algorithm http://en.wikipedia.org/wiki/Carl_Friedrich_Gauss#Early_years_.281777.E2.80.931798.29
		}
		
	}
	
}
