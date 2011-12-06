/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*

Programmer notes:

+++ if revise MemoryMeasurer as discussed in that class to use MemoryMXBean,
then this class becomes obsolete in favor of the JDK class MemoryUsage?

*/

package bb.util;

import java.text.DecimalFormat;
import java.text.ParsePosition;
import org.junit.Assert;
import org.junit.Test;

/**
* Class which records data describing the memory state of a JVM.
* <p>
* This class is multithread safe: it is mostly immutable (both its immediate state, as well as the deep state of its fields).
* The sole exception is the {@link #integerFormat} field, which is guarded by synchronization.
* <p>
* @author Brent Boyer
*/
public class MemoryState {
	
	// -------------------- constants --------------------
	
	/** Default value for the separator arg of {@link #toStringHeader(String)}/{@link #toString(String, boolean)}. */
	public static final String separatorDefault = ", ";
	
	private static final DecimalFormat integerFormat = new DecimalFormat("#,##0");	// is same pattern as what is returned by ((DecimalFormat) NumberFormat.getIntegerInstance()).toPattern() (at least in my US locale)
	
	// -------------------- fields --------------------
	
	/**
	* Amount of memory (within the total allocated) that is used.
	* <p>
	* Contract: is >= 0, and is always equal to {@link #total} - {@link #free}
	*/
	private final long used;
	
	/**
	* Amount of memory (within the total allocated) that is free.
	* This definition of free memory is the same used by {@link Runtime#freeMemory}.
	* <p>
	* Contract: is >= 0, and is always equal to {@link #total} - {@link #used}
	*/
	private final long free;
	
	/**
	* Amount of memory (within the max allocatable) that could be allocated.
	* <p>
	* Contract: is >= 0, and is always equal to {@link #max} - {@link #used}
	*/
	private final long available;
	
	/**
	* Total amount of memory currently used by the JVM.
	* This definition of total memory is the same used by {@link Runtime#totalMemory}.
	* <p>
	* Contract: is >= 1, and is always equal to {@link #used} + {@link #free}
	*/
	private final long total;
	
	/**
	* Maximum amount of memory that could be used by the JVM.
	* This definition of max memory is the same used by {@link Runtime#maxMemory}.
	* <p>
	* Contract: is >= 1, and is always equal to {@link #used} + {@link #available}
	*/
	private final long max;
	
	// -------------------- format --------------------
	
	/**
	* Returns {@link #integerFormat}.{@link DecimalFormat#format format}(value).
	*/
	private static String format(long value) {
		synchronized(integerFormat) {
			return integerFormat.format(value);
		}
	}
	
	// -------------------- parse --------------------
	
	/**
	* Parse a new MemoryState instance from the data in s.
	* <p>
	* <b>Warning:</b> separator will be directly supplied as the regex argument to String.split,
	* so you must ensure that it is a valid regex and will split the tokens of s as expected.
	* In particular, if s has field labels inside it, you will need to write a complicated regex
	* to handle that, as opposed to a simple regex like ", " or "\\t" for bare comma and tab delimted data.
	* <p>
	* @throws IllegalArgumentException if s is blank; separator == null;
	* s does not obey the expected format for a legitimate MemoryState representation
	*/
	public static MemoryState parse(String s, String separator) throws IllegalArgumentException {
		Check.arg().notBlank(s);
		Check.arg().notNull(separator);
		
		String[] tokens = s.split(separator, -1);
		Check.arg().hasSize(tokens, 5);
		
		synchronized(integerFormat) {
			ParsePosition pp = new ParsePosition(0);
			Number n = integerFormat.parse(tokens[0], pp);
			Check.arg().notNull(n);
			long used = n.longValue();
			
			pp.setIndex(0);
			n = integerFormat.parse(tokens[1], pp);
			Check.arg().notNull(n);
			long free = n.longValue();
			
			pp.setIndex(0);
			n = integerFormat.parse(tokens[2], pp);
			Check.arg().notNull(n);
			long available = n.longValue();
			
			pp.setIndex(0);
			n = integerFormat.parse(tokens[3], pp);
			Check.arg().notNull(n);
			long total = n.longValue();
			
			pp.setIndex(0);
			n = integerFormat.parse(tokens[4], pp);
			Check.arg().notNull(n);
			long max = n.longValue();
			
			MemoryState ms = new MemoryState(free, total, max);
			if (ms.used != used) throw new IllegalArgumentException("s = " + s + " has a used token which is inconsistent with its free, total, max values");
			if (ms.available != available) throw new IllegalArgumentException("s = " + s + " has an available token which is inconsistent with its free, total, max values");
			return ms;
		}
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if free < 0; total < 1; max < 1
	*/
	public MemoryState(long free, long total, long max) throws IllegalArgumentException {
		Check.arg().notNegative(free);
		Check.arg().positive(total);
		Check.arg().positive(max);
		
		this.free = free;
		this.total = total;
		this.max = max;
		
		this.used = total - free;
		this.available = max - used;
	}
	
	// -------------------- accessors --------------------
	
	/** Returns the value of the {@link #used} field. */
	public long getUsed() { return used; }
	
	/**
	* Returns the ratio of the {@link #used} field to the {@link #max} field.
	* In other words, this method returns the relative amount of memory being used.
	*/
	public double getUsedRatio() {
		return ((double) used) / ((double) max);
	}
	
	/** Returns the value of the {@link #free} field. */
	public long getFree() { return free; }
	
	/** Returns the value of the {@link #available} field. */
	public long getAvailable() { return available; }
	
	/**
	* Returns the ratio of the {@link #available} field to the {@link #max} field.
	* In other words, this method returns the relative amount of memory that still could be allocated
	* given the memory that is already being used.
	*/
	public double getAvailableRatio() {
		return ((double) available) / ((double) max);
	}
	
	/** Returns the value of the {@link #total} field. */
	public long getTotal() { return total; }
	
	/** Returns the value of the {@link #max} field. */
	public long getMax() { return max; }
	
	// -------------------- equals, hashCode, toStringXXX --------------------
	
	/**
	* Determines equality based on whether or not obj is a MemoryState instance
	* whose every field equals that of this instance.
	*/
	@Override public final boolean equals(Object obj) {	// for why is final, see the essay stored in the file equalsImplementation.txt
		if (this == obj) return true;
		if (!(obj instanceof MemoryState)) return false;
		
		MemoryState other = (MemoryState) obj;
		return	// Note: only need compare the 3 fundamental fields, because the other 2 are derived from them:
			(this.free == other.free) &&
			(this.total == other.total) &&
			(this.max == other.max);
	}
	
	/** Returns a value based on all of the fields. */
	@Override public final int hashCode() {	// for why is final, see the essay stored in the file equalsImplementation.txt
		return	// Note: only need use the 3 fundamental fields, because the other 2 are derived from them:
			HashUtil.hash(free) ^
			HashUtil.hash(total) ^
			HashUtil.hash(max);
	}
	
	/** Returns <code>{@link #toStringHeader(String) toStringHeader}({@link #separatorDefault})</code>. */
	public static String toStringHeader() {
		return toStringHeader(separatorDefault);
	}
	
	/**
	* Returns a description of the data returned by {@link #toString(String, boolean)}.
	* <p>
	* @throws IllegalArgumentException if separator == null or separator.length() == 0
	*/
	public static String toStringHeader(String separator) throws IllegalArgumentException {
		Check.arg().notNull(separator);
		if (separator.length() == 0) throw new IllegalArgumentException("separator.length() == 0");
		
		return "used (bytes)" + separator + "free (bytes)" + separator + "available (bytes)" + separator + "total (bytes)" + separator + "max (bytes)";
	}
	
	/** Returns <code>{@link #toString(String, boolean) toString}({@link #separatorDefault}, true)</code>. */
	@Override public String toString() {
		return toString(separatorDefault, true);
	}
	
	/**
	* Returns a String which describes this instance.
	* <p>
	* @param separator String value to place between the different memory types in order to separate them
	* @param includeLabels specifies whether or not to label the different memory types
	* @throws IllegalArgumentException if separator == null or separator.length() == 0
	*/
	public String toString(String separator, boolean includeLabels) throws IllegalArgumentException {
		Check.arg().notNull(separator);
		if (separator.length() == 0) throw new IllegalArgumentException("separator.length() == 0");
		
		if (includeLabels) {
			return "used = " + format(used) + separator + "free = " + format(free) + separator + "available = " + format(available) + separator + "total = " + format(total) + separator + "max = " + format(max);
		}
		else {
			return format(used) + separator + format(free) + separator + format(available) + separator + format(total) + separator + format(max);
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_parse() throws Exception {
			MemoryState ms1 = MemoryMeasurer.perform();
			String s = ms1.toString("\t", false);
			MemoryState ms2 = MemoryState.parse(s, "\t");
			Assert.assertEquals("MemoryState.toString and MemoryState.parse are inconsistent", ms1, ms2);
		}
		
	}
	
}
