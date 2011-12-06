/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import java.util.Calendar;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

/**
* Stores information which defines date constraints,
* and offers the {@link #accepts accepts} method to test a given Date.
* <p>
* Supports these types of constraints:
* <ol>
*  <li>absolute date bounds ({@link #dateMin} and {@link #dateMax})</li>
*  <li>day of week bounds ({@link #dayOfWeekMin} and {@link #dayOfWeekMax})</li>
*  <li>time of day bounds ({@link #timeOfDayMin} and {@link #timeOfDayMax})</li>
* </ol>
* All these bounds are always inclusive, as explained further in {@link #accepts accepts}.
* <p>
* Examples of how to construct DateConstraint instances:
* <ol>
*  <li>accepts every Date: <code>new DateConstraint(null, null, -1, -1, -1, -1)</code></li>
*  <li>
*		accepts every weekday whose time of day is 9:30 am thru 4 pm:
*		<code>new DateConstraint(null, null, {@link Calendar#MONDAY}, {@link Calendar#FRIDAY}, 9*{@link TimeLength#hour} + 30*{@link TimeLength#minute}, 16*{@link TimeLength#hour})</code>
*  </li>
* </ol>
* <p>
* This class is multithread safe: it is <a href="http://www.ibm.com/developerworks/java/library/j-jtp02183.html">immutable</a>.
* In particular, it is always <a href="http://www.ibm.com/developerworks/java/library/j-jtp0618.html">properly constructed</a>,
* all of its fields are final,
* and none of their state can be changed after construction.
* See p. 53 of <a href="http://www.javaconcurrencyinpractice.com">Java Concurrency In Practice</a> for more discussion.
* <p>
* @author Brent Boyer
*/
public class DateConstraint {
	
	// -------------------- instance fields --------------------
	
	/**
	* Specifies the minimum Date value.
	* If equals null, then there is no Date lower bound.
	* Is a Date2 instance for that class's partial immutability (e.g. so this field's accessor need not return a copy).
	* <p>
	* Contract: may be null.
	* Otherwise, must occur on or before {@link #dateMax} if dateMax is also non-null.
	*/
	private final Date2 dateMin;
	
	/**
	* Specifies the maximum Date value.
	* If equals null, then there is no Date upper bound.
	* Is a Date2 instance for that class's partial immutability (e.g. so this field's accessor need not return a copy).
	* <p>
	* Contract: may be null.
	* Otherwise, must occur on or after {@link #dateMin} if dateMin is also non-null.
	*/
	private final Date2 dateMax;
	
	/**
	* Specifies the minimum day of week value.
	* If equals -1, then there is no day of week lower bound.
	* <p>
	* Contract: may be -1.
	* Otherwise, must (i) be in the range [{@link Calendar#SUNDAY}, {@link Calendar#SATURDAY}] = [1, 7]
	* and (ii) must be <= {@link #dayOfWeekMax} if dayOfWeekMax is also != -1.
	*/
	private final int dayOfWeekMin;
	
// +++ the constraints on dayOfWeekMin/dayOfWeekMax mean that CANNOT specify dow ranges like friday-monday,
// which some people might want to do;
// the above is equivalent to friday-saturday OR sunday-monday,
// is there anyway to easily accomodate it?

// +++ there is a similar problem with timeOfDayMin/timeOfDayMax...
	
	/**
	* Specifies the maximum day of week value.
	* If equals -1, then there is no day of week upper bound.
	* <p>
	* Contract: may be -1.
	* Otherwise, must (i) be in the range [{@link Calendar#SUNDAY}, {@link Calendar#SATURDAY}] = [1, 7]
	* and (ii) must be >= {@link #dayOfWeekMin} if dayOfWeekMin is also != -1.
	*/
	private final int dayOfWeekMax;
	
	/**
	* Specifies the minimum time of day value (where the time is measured in milliseconds).
	* If equals -1, then there is no time of day lower bound.
	* <p>
	* Contract: may be -1.
	* Otherwise, must (i) be in the range [0, {@link TimeLength#dayMax}]
	* and (ii) must be <= {@link #timeOfDayMax} if timeOfDayMax is also != -1.
	*/
	private final long timeOfDayMin;
	
	/**
	* Specifies the maximum time of day value (where the time is measured in milliseconds).
	* If equals -1, then there is no time of day upper bound.
	* <p>
	* Contract: may be -1.
	* Otherwise, must (i) be in the range [0, {@link TimeLength#dayMax}]
	* and (ii) must be >= {@link #timeOfDayMin} if timeOfDayMin is also != -1.
	*/
	private final long timeOfDayMax;
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if any of the params violate the corresponding field's contract
	*/
	public DateConstraint(Date dateMin, Date dateMax, int dayOfWeekMin, int dayOfWeekMax, long timeOfDayMin, long timeOfDayMax) throws IllegalArgumentException {
		if ((dateMin != null) && (dateMax != null) && (dateMin.getTime() >= dateMax.getTime())) throw new IllegalArgumentException("dateMin = " + DateUtil.getTimeStamp(dateMin) + " occurs at or after dateMax = " + DateUtil.getTimeStamp(dateMax));
		
		if (dayOfWeekMin < -1) throw new IllegalArgumentException("dayOfWeekMin = " + dayOfWeekMin + " < -1");
		if (dayOfWeekMin == 0) throw new IllegalArgumentException("dayOfWeekMin == 0");
		if (dayOfWeekMin > 7) throw new IllegalArgumentException("dayOfWeekMin = " + dayOfWeekMin + " > 7");
		if (dayOfWeekMax < -1) throw new IllegalArgumentException("dayOfWeekMax = " + dayOfWeekMax + " < -1");
		if (dayOfWeekMax == 0) throw new IllegalArgumentException("dayOfWeekMax == 0");
		if (dayOfWeekMax > 7) throw new IllegalArgumentException("dayOfWeekMax = " + dayOfWeekMax + " > 7");
		if ((dayOfWeekMin != -1) && (dayOfWeekMax != -1) && (dayOfWeekMin > dayOfWeekMax)) throw new IllegalArgumentException("dayOfWeekMin = " + dayOfWeekMin + " > dayOfWeekMax = " + dayOfWeekMax);
		
		if (timeOfDayMin < -1) throw new IllegalArgumentException("timeOfDayMin = " + timeOfDayMin + " < -1");
		if (timeOfDayMin > TimeLength.dayMax) throw new IllegalArgumentException("timeOfDayMin = " + timeOfDayMin + " > TimeLength.dayMax = " + TimeLength.dayMax);
		if (timeOfDayMax < -1) throw new IllegalArgumentException("timeOfDayMax = " + timeOfDayMax + " < -1");
		if (timeOfDayMax > TimeLength.dayMax) throw new IllegalArgumentException("timeOfDayMax = " + timeOfDayMax + " > TimeLength.dayMax = " + TimeLength.dayMax);
		if ((timeOfDayMin != -1) && (timeOfDayMax != -1) && (timeOfDayMin > timeOfDayMax)) throw new IllegalArgumentException("timeOfDayMin = " + timeOfDayMin + " > timeOfDayMax = " + timeOfDayMax);
		
		this.dateMin = (dateMin != null) ? new Date2(dateMin) : null;
		this.dateMax = (dateMax != null) ? new Date2(dateMax) : null;
		this.dayOfWeekMin = dayOfWeekMin;
		this.dayOfWeekMax = dayOfWeekMax;
		this.timeOfDayMin = timeOfDayMin;
		this.timeOfDayMax = timeOfDayMax;
	}
	
	// -------------------- accessors --------------------
	
	/** Returns the {@link #dateMin} field. */
	public Date2 getDateMin() { return dateMin; }
	
	/** Returns the {@link #dateMax} field. */
	public Date2 getDateMax() { return dateMax; }
	
	/** Returns the {@link #dayOfWeekMin} field. */
	public int getDayOfWeekMin() { return dayOfWeekMin; }
	
	/** Returns the {@link #dayOfWeekMax} field. */
	public int getDayOfWeekMax() { return dayOfWeekMax; }
	
	/** Returns the {@link #timeOfDayMin} field. */
	public long getTimeOfDayMin() { return timeOfDayMin; }
	
	/** Returns the {@link #timeOfDayMax} field. */
	public long getTimeOfDayMax() { return timeOfDayMax; }
	
	// -------------------- accepts --------------------
	
	/**
	* Returns true if date passes all of the constraints specified by this instance, false otherwise.
	* <p>
	* The tests are:
	* <ol>
	*  <li>if dateMin != null, then returns false if date.getTime() < dateMin.getTime()</li>
	*  <li>if dateMax != null, then returns false if date.getTime() > dateMax.getTime()</li>
	*  <li>if dayOfWeekMin != -1, then returns false if date's dayOfWeek < dayOfWeekMin</li>
	*  <li>if dayOfWeekMax != -1, then returns false if date's dayOfWeek > dayOfWeekMax</li>
	*  <li>if timeOfDayMin != -1, then returns false if date's timeOfDay < timeOfDayMin</li>
	*  <li>if timeOfDayMax != -1, then returns false if date's timeOfDay > timeOfDayMax</li>
	* </ol>
	* This method only returns true if all of these tests are passed.
	* <p>
	* Note that the logic above means that all of the bounds (both min and max) act as inclusive bounds
	* (i.e. let f be some field of date; then f is accepted if min <= f <= max, so that the acceptance range is the closed interval [min, max]).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public boolean accepts(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		if ((dateMin != null) && (date.getTime() < dateMin.getTime())) return false;
		if ((dateMax != null) && (date.getTime() > dateMax.getTime())) return false;
		
		if ((dayOfWeekMin != -1) || (dayOfWeekMax != -1)) {
			int dayOfWeek = DateUtil.getDayOfWeek(date);	// for performance, do NOT immediately calculate this unless it is really needed
			if ((dayOfWeekMin != -1) && (dayOfWeek < dayOfWeekMin)) return false;
			if ((dayOfWeekMax != -1) && (dayOfWeek > dayOfWeekMax)) return false;
		}
		
		if ((timeOfDayMin != -1) || (timeOfDayMax != -1)) {
			long timeOfDay = DateUtil.getTimeOfDay(date);	// for performance, do NOT immediately calculate this unless it is really needed
			if ((timeOfDayMin != -1) && (timeOfDay < timeOfDayMin)) return false;
			if ((timeOfDayMax != -1) && (timeOfDay > timeOfDayMax)) return false;
		}
		
		return true;
	}
	
	// -------------------- equals, hashCode, toString --------------------
	
	/**
	* Immediately returns true if <code>this == obj</code>,
	* or false if <code>obj == null</code> or <code>!this.getClass().equals( obj.getClass() )</code>.
	* Otherwise, determines equality based on whether or not obj (which now must be a DateConstraint instance)
	* has every field equals to that of this instance.
	* <p>
	* The class equals criteria mentioned above is required because there exist subclasses of this class
	* which contain new "aspects" (state that affects equality), so these subclasses must override this method.
	* For more discussion, see the essay stored in the file equalsImplementation.txt
	*/
	@Override public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (!this.getClass().equals( obj.getClass() )) return false;
		
		DateConstraint other = (DateConstraint) obj;
		return
			equalDates(this.dateMin, other.dateMin) &&
			equalDates(this.dateMax, other.dateMax) &&
			(this.dayOfWeekMin == other.dayOfWeekMin) &&
			(this.dayOfWeekMax == other.dayOfWeekMax) &&
			(this.timeOfDayMin == other.timeOfDayMin) &&
			(this.timeOfDayMax == other.timeOfDayMax);
	}
	
	private boolean equalDates(Date date1, Date date2) {
		if (date1 == null) return (date2 == null);
		else return date1.equals(date2);
	}
	
	/** Returns a value based on all of the fields. */
	@Override public final int hashCode() {	// for why is final, see the essay stored in the file equalsImplementation.txt
		return
			((dateMin != null) ? dateMin.hashCode() : 0) ^
			((dateMax != null) ? dateMax.hashCode() : 0) ^
			HashUtil.hash(dayOfWeekMin) ^
			HashUtil.hash(dayOfWeekMax) ^
			HashUtil.hash(timeOfDayMin) ^
			HashUtil.hash(timeOfDayMin);
	}
	
	/** Returns <code>{@link #toString(String) toString}(", ")</code>. */
	@Override public String toString() {
		return toString(", ");
	}
	
	/**
	* Returns a String representation of this instance.
	* Each field is labeled, and separator appears between each field.
	* <p>
	* @throws IllegalArgumentException if separator == null
	*/
	public String toString(String separator) throws IllegalArgumentException {
		Check.arg().notNull(separator);
		
		return
			"dateMin = " + ((dateMin != null) ? DateUtil.getTimeStampConcise(dateMin) : "null") + separator +
			"dateMax = " + ((dateMax != null) ? DateUtil.getTimeStampConcise(dateMax) : "null") + separator +
			"dayOfWeekMin = " + ((dayOfWeekMin != -1) ? DateUtil.getDayOfWeekName(dayOfWeekMin) : "-1") + separator +
			"dayOfWeekMax = " + ((dayOfWeekMax != -1) ? DateUtil.getDayOfWeekName(dayOfWeekMax) : "-1") + separator +
			"timeOfDayMin = " + ((timeOfDayMin != -1) ? DateUtil.getTimeOfDayStampConcise(timeOfDayMin) : "-1") + separator +
			"timeOfDayMax = " + ((timeOfDayMax != -1) ? DateUtil.getTimeOfDayStampConcise(timeOfDayMax) : "-1");
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_accepts() throws Exception {
			Date date2005_01_22 = DateUtil.parseDayStamp("2005-01-22");	// is a saturday
			Date date2005_02_02 = DateUtil.parseDayStamp("2005-02-02");	// is a wednesday
			
			Date dateInside = new Date( date2005_01_22.getTime() + (12 * TimeLength.hour) );
			Date dateOutside = new Date( date2005_02_02.getTime() + (14 * TimeLength.hour) );
			
			Date dayMin = DateUtil.parseDayStamp("2005-01-15");	// can get away with short day form and no time zone, because dateInside and dateOutside are far enough away
			Date dayMax = DateUtil.parseDayStamp("2005-01-31");
			DateConstraint dayConstraint = new DateConstraint(dayMin, dayMax, -1, -1, -1, -1);
			Assert.assertTrue( dayConstraint.accepts(dateInside) );
			Assert.assertFalse( dayConstraint.accepts(dateOutside) );
			
			int dayOfWeekMin = Calendar.SATURDAY;
			int dayOfWeekMax = Calendar.SATURDAY;
			DateConstraint dayOfWeekConstraint = new DateConstraint(null, null, dayOfWeekMin, dayOfWeekMax, -1, -1);
			Assert.assertTrue( dayOfWeekConstraint.accepts(dateInside) );
			Assert.assertFalse( dayOfWeekConstraint.accepts(dateOutside) );
			
			long timeOfDayMin = 11 * TimeLength.hour;
			long timeOfDayMax = 13 * TimeLength.hour;
			DateConstraint timeOfDayConstraint = new DateConstraint(null, null, -1, -1, timeOfDayMin, timeOfDayMax);
			Assert.assertTrue( timeOfDayConstraint.accepts(dateInside) );
			Assert.assertFalse( timeOfDayConstraint.accepts(dateOutside) );
		}
		
	}
	
}

