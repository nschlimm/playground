/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ java 1.5 introduced the TimeUnit class; should this class subclass it or somehow use it?
*/

package bb.util;

/**
* Provides fields and methods related to lengths of some common periods of time.
* <p>
* Every field or method parameter that is a time value is a long, and has the same units of milliseconds,
* making them conform with the usual type and units used for time in Java
* (e.g. {@link java.util.Date}, {@link System#currentTimeMillis System.currentTimeMillis}, etc).
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class TimeLength {
	
	// -------------------- constants --------------------
	
	/** Length of 1 second <i>in milliseconds</i>. */
	public static final long second = 1000L;
	
	/** Length of 1 minute <i>in milliseconds</i>. */
	public static final long minute = 60L * second;
	
	/** Length of 1 hour <i>in milliseconds</i>. */
	public static final long hour = 60L * minute;
	
	/**
	* Length of a standard day (i.e. 24 hours) <i>in milliseconds</i>.
	* <p>
	* On a standard day, nothing unusual (time zone change, leap second) happens.
	* <p>
	* @see #dayTzChNeg
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayLeapNeg
	* @see #dayMin
	* @see #dayMax
	*/
	public static final long day = 24L * hour;
	
	/**
	* Length of a positive time zone change day <i>in milliseconds</i>.
	* Such days are always 1 hour shorter than a {@link #day standard day}.
	* <p>
	* Here, "positive time zone change day" means that the time of day was set forward by 1 hour at some point (usually at 1 or 2am).
	* For such a day, the {@link DateUtil#getAmountTimeZoneChange DateUtil.getAmountTimeZoneChange} method returns 1.
	* <p>
	* <i>Note: this class assumes that on a time zone change day, nothing else unusual (e.g. a leap second) happens.</i>
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayLeapNeg
	* @see #dayMin
	* @see #dayMax
	*/
	public static final long dayTzChPos = day - hour;
	
	/**
	* Length of a negative time zone change day <i>in milliseconds</i>.
	* Such days are always 1 hour longer than a {@link #day standard day}.
	* <p>
	* Here, "negative time zone change day" means that the time of day was set back by 1 hour at some point (usually at 1 or 2am).
	* For such a day, the {@link DateUtil#getAmountTimeZoneChange DateUtil.getAmountTimeZoneChange} method returns -1.
	* <p>
	* <i>Note: this class assumes that on a time zone change day, nothing else unusual (e.g. a leap second) happens.</i>
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayLeapNeg
	* @see #dayMin
	* @see #dayMax
	*/
	public static final long dayTzChNeg = day + hour;
	
	/**
	* Length of a positive leap second day <i>in milliseconds</i>.
	* Such days are always 1 second longer than a {@link #day standard day}.
	* <p>
	* Here, "positive leap second day" means that the last minute of the day has 1 extra second
	* (its second count goes to 60 instead of the usual value of 59).
	* For such a day, the {@link DateUtil#getLeapSecond DateUtil.getLeapSecond} method returns +1
	* (assuming that the underlying platform supports leap seconds).
	* <p>
	* <b>Note:</b> leap seconds only occur on December 31 or June 30 of some years;
	* see {@link java.util.Date} or {@link DateUtil} for more discussion.
	* <p>
	* <i>Note: this class assumes that on a leap second day, nothing else unusual (e.g. a time zone change) happens.</i>
	* This is currently true of the USA and (most of?) Europe, but may possibly not be true for some locality or in the future.
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayTzChNeg
	* @see #dayLeapNeg
	* @see #dayMin
	* @see #dayMax
	*/
	public static final long dayLeapPos = day + second;
	
	/**
	* Length of a negative leap second day <i>in milliseconds</i>.
	* Such days are always 1 second shorter than a {@link #day standard day}.
	* <p>
	* Here, "negative leap second day" means that the last minute of the day has 1 less second
	* (its second count goes to 58 instead of the usual value of 59).
	* For such a day, the {@link DateUtil#getLeapSecond DateUtil.getLeapSecond} method returns -1
	* (assuming that the underlying platform supports leap seconds).
	* <p>
	* <b>Note:</b> leap seconds only occur on December 31 or June 30 of some years;
	* see {@link java.util.Date} or {@link DateUtil} for more discussion.
	* <i>As of 2005/3/23, there has never been a negative leap second day.</i>
	* <p>
	* <i>Note: this class assumes that on a leap second day, nothing else unusual (e.g. a time zone change) happens.</i>
	* This is currently true of the USA and (most of?) Europe, but may possibly not be true for some locality or in the future.
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayMin
	* @see #dayMax
	*/
	public static final long dayLeapNeg = day - second;
	
	/**
	* Minimum length of a day <i>in milliseconds</i>.
	* The minimum length day occurs when a positive time zone change happens,
	* so this value is simply equal to {@link #dayTzChNeg}.
	* <p>
	* This variable was introduced solely to aid code readability where it is the minimum length of a day that is important,
	* as opposed to any time zone change.
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayLeapNeg
	* @see #dayMax
	*/
	public static final long dayMin = dayTzChNeg;
	
	/**
	* Maximum length of a day <i>in milliseconds</i>.
	* The maximum length day occurs when a negative time zone change happens,
	* so this value is simply equal to {@link #dayTzChNeg}.
	* <p>
	* This variable was introduced solely to aid code readability where it is the maximum length of a day that is important,
	* as opposed to any time zone change.
	* <p>
	* @see #day
	* @see #dayTzChNeg
	* @see #dayTzChNeg
	* @see #dayLeapPos
	* @see #dayLeapNeg
	* @see #dayMin
	*/
	public static final long dayMax = dayTzChNeg;
	
	/** Length of a standard week (i.e. exactly 7 {@link #day}s) <i>in milliseconds</i>. */
	public static final long week = 7L * day;
	
	/**
	* Minimum length of a week <i>in milliseconds</i>.
	* The minimum length week occurs when a positive time zone change happens during the week,
	* so this value is simply equal to <code>(6L *{@link #day}) + {@link #dayTzChPos}</code>.
	*/
	public static final long weekMin = (6L * day) + dayTzChPos;
	
	/**
	* Maximum length of a week <i>in milliseconds</i>.
	* The maximum length week occurs when a negative time zone change happens during the week,
	* so this value is simply equal to <code>(6L *{@link #day}) + {@link #dayTzChNeg}</code>.
	*/
	public static final long weekMax = (6L * day) + dayTzChNeg;
	
	/**
	* Length of a standard month (i.e. exactly 30 {@link #day}s) <i>in milliseconds</i>.
	* <p>
	* <b>Warning:</b> the only actual months which have this standard length are April, June, September, November;
	* all the other months, which are the majority, are "non-standard" and have different lengths.
	*/
	public static final long month = 30L * day;
// +++ revisit the choice above: maybe 31 IS the better choice;
// do a web search and see if there is any consensus, as well as what joda time does...
	
	/**
	* Length of a standard year (i.e. exactly 365 {@link #day}s) <i>in milliseconds</i>.
	* <p>
	* <b>Warning:</b> leap years have 366 days, and even standard (365 day) years can have leap seconds added.
	*/
	public static final long year = 365L * day;
	
	// -------------------- timeDescription --------------------
	
	public static String timeDescription(long time) {
		if (time % day == 0) {
			return (time / day) + " days";
		}
		else if (time % hour == 0) {
			return (time / hour) + " hours";
		}
		else if (time % minute == 0) {
			return (time / minute) + " minutes";
		}
		else if (time % second == 0) {
			return (time / second) + " seconds";
		}
		else {
			return time + " milliseconds";
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private TimeLength() {}
	
}
