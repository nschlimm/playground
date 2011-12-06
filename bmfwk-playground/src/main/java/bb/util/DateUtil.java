/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer's notes:

+++ leap seconds are so problematic that maybe they should be dropped from this class, which should assume that only proleptic UT1 time standards will be supported?
	--this class would then need to detect if the platform's time standard supported leap seconds and bomb in that case...
	--NOTE: "GregorianCalendar implements proleptic Gregorian and Julian calendars"
		so the standard JDK Calendar implementation CANNOT support leap seconds as that contradicts the proleptic (perfectly extrapolatable) nature

+++ this project claims to replace the JDK's Date and Calendar classes:
	http://joda-time.sourceforge.net/
and jsr-310
	https://jsr-310.dev.java.net/
aims to include the above code in the JDK.
KEEP AN EYE ON IT and write a version of this class for it when it comes out.
*/

package bb.util;

import bb.science.Math2;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods relating to Dates.

* <h4>isExactXXX versus isSameXXX</h4>

* Several methods in this class are named like isExactXXX (e.g. {@link #isExactDay isExactDay}),
* while other methods in this class are named like isSameXXX (e.g. {@link #isSameDayOfYear isSameDayOfYear})
* These methods have slightly different semantics.
* The isExactXXX methods are named for events that can only happen at one block of time.
* For example, given a particular day (i.e. one that has a precise start time),
* there is no other day out of every possible day that could be the exact same day
* (since it would have a different start time).
* In contrast, the isSameXXX methods are named for events that can only happen at multiple blocks of time. 
* For instance, a particular day of the year (e.g. the 3rd day) occurs every single year.

* <h4>Calendar used</h4>

* Every method in this class that uses a {@link Calendar} instance to do its work
* ultimately gets that Calendar by a call to {@link Calendar#getInstance Calendar.getInstance}.
* <p>
* One consequence is that the JVM's default time zone and locale are used to define all date related defaults (e.g. what day of the week is the start of the week).
* These defaults can be changed in several ways.
* One way is programmatically, by calling {@link Locale#setDefault Locale.setDefault} and {@link TimeZone#setDefault TimeZone.setDefault}.
* Probably a better approach, altho it seems to be undocumented by Sun, is to specify these defaults via the system properties
* <code>user.region</code> and <code>user.timezone</code>.

* <h4>Caching</h4>

* Because most calls to the Calendar class are very slow,
* many methods in this class do not always directly use a {@link Calendar} instance.
* Instead, they automatically uses extensive caching to achieve high performance.
* <p>
* The only downside to caching is the memory used, so this class offers functionality to prevent memory exhaustion:
* <ol>
*  <li>
*		for caching of calendar field codes, which uses instances of the {@link DateInfo} inner class,
*		see {@link #setDateInfoCacheSizeMax setDateInfoCacheSizeMax}
*  </li>
*  <li>
*		for caching of String <--> Date mappings, see the
*		{@link #dayOfYearCache}, {@link #timeOfDayStampCache}, {@link #timeOfDayStampForFileCache},
*		{@link #timeStampCache}, and {@link #timeStampForFileCache} fields.
*		Since each is an instance of {@link DateStringCache}, it will have these methods available:
* 		{@link DateStringCache#setSizeMax setSizeMax} and {@link DateStringCache#clear clear}.
*		To conveniently suppress all these caches, call {@link #suppressDateStringCaches suppressDateStringCaches}.
*  </li>
* </ol>

* <h4>Date text pattern</h4>

* Unless noted otherwise, every date's text pattern follows the
* <a href="http://en.wikipedia.org/wiki/ISO_8601">ISO 8601 specification</a>
* as closely as possible.
* This specification was chosen because its top down (most to least significant) information order
* is the only rational choice, unlike the common (but defective) American and European conventions.
* It has <a href="http://www.cl.cam.ac.uk/~mgk25/iso-time.html">many advantages</a>.
* For example, lexicographic order equals chronological order:
* String representations of Dates formatted by this spec,
* if sorted using {@link String#compareTo String.compareTo}, will be in ascending time order as well.
* This has all sorts of pleasant side effects, such as time stamps in file names cause the files to be sorted in time order.
* For more discussions, see
* <a href="http://www.hermetic.ch/cal_stud/formats.htm">link1</a>
* and <a href="http://www.probabilityof.com/ISO8601.shtml">link2</a>.

* <h4>Time zone changes and leap seconds</h4>

* <p>
* This class only makes these simplifying assumptions:
* <ol>
*  <li>a given day never has both a time zone change as well as a leap second: it can have at most one such event</li>
*  <li>
*		there is at most 1 leap second that can occcur on a given day.
*		This differs from the ISO C date and time conventions used by {@link Date}, which allow up to 2 leap seconds to occur on a given day.
*		A consequence is that the seconds field of a date processed by this class may reach 60 on a positive leap second day,
*		but will never reach 61 as allowed by ISO C.
*  </li>
* </ol>
* See {@link #getDayLength getDayLength} for more discussion.
* <p>
* Otherwise, this class generally makes no simplifying assumptions about time zone changes and leap seconds
* (e.g. that a day is always exactly 24 hours long)
* unless explicitly stated otherwise (e.g. {@link #get24HoursLater get24HoursLater}).
* Instead, the algorithms typically use a Calendar instance (or the cached result thereof) to perform their work,
* so they should always operate correctly.
* <p>
* Concerning leap seconds, the javadocs for {@link Date} have a brief discussion; below are more references:
* <blockquote>
*	<a href="http://tf.nist.gov/pubs/bulletin/leapsecond.htm">Leap second and UT1-UTC information </a>
* </blockquote>
* <blockquote>
*	In order to keep the cumulative difference in UT1-UTC less than 0.9 seconds,
*	a leap second is added to the atomic time to decrease the difference between the two.
*	This leap second can be either positive or negative depending on the Earth's rotation.
*	Since the first leap second in 1972, all leap seconds have been positive
*	and there were 22 leap seconds in the 27 years to January, 1999.
*	This pattern reflects the general slowing trend of the Earth due to tidal braking.<br>
*	<a href="http://tycho.usno.navy.mil/leapsec.html">http://tycho.usno.navy.mil/leapsec.html</a>
* </blockquote>
* <blockquote>
*	UTC is kept always within one second of GMT by the insertion of extra seconds as necessary (positive leap seconds).
*	It could happen that seconds would need to be removed (negative leap seconds),
*	however all leap seconds so far have been positive.<br>
*	<a href="http://www.npl.co.uk/time/leap_second.html">http://www.npl.co.uk/time/leap_second.html</a>
* </blockquote>
* <blockquote>
*	Leap seconds add up to roughly an extra day every 115,000 years.<br>
*	<a href="http://mindprod.com/jgloss/leapyear.html">http://mindprod.com/jgloss/leapyear.html</a>
* </blockquote>
* <blockquote>
*	UTC might be redefined without Leap Seconds...<br>
*	<a href="http://www.ucolick.org/~sla/leapsecs/">http://www.ucolick.org/~sla/leapsecs/</a>
* </blockquote>
* <blockquote>
*	The Future of Leap Seconds...<br>
*	<a href="http://en.wikipedia.org/wiki/Coordinated_Universal_Time#Future">http://en.wikipedia.org/wiki/Coordinated_Universal_Time#Future</a><br>
*	<a href="http://www.ucolick.org/~sla/leapsecs/onlinebib.html">http://www.ucolick.org/~sla/leapsecs/onlinebib.html</a>
* </blockquote>
* <blockquote>
*	UTC has a complicated history, and leap seconds as presently known in UTC only were introduced into the standard in 1972<br>
*	<a href="http://www.ucolick.org/~sla/leapsecs/timescales.html">http://www.ucolick.org/~sla/leapsecs/timescales.html</a><br>
*	(the section on "Coordinated Universal Time -- UTC" is near the end of this page)
* </blockquote>
* <blockquote>
*	Calendar reform:<br>
*	<a href="http://slashdot.org/article.pl?sid=04/12/21/1519235&tid=99">http://slashdot.org/article.pl?sid=04/12/21/1519235&tid=99</a>
* </blockquote>
* <blockquote>
*	Leap seconds may be eliminated:<br>
*	<a href="http://science.slashdot.org/article.pl?sid=07/11/20/0356214">http://science.slashdot.org/article.pl?sid=07/11/20/0356214</a><br>
*	<a href="http://www.timeanddate.com/time/leap-seconds-future.html">http://www.timeanddate.com/time/leap-seconds-future.html</a>
* </blockquote>
* <p>
* <b>Warning:</b> it appears that leap seconds are not currently supported in Calendar on any platform,
* which if true means that this class too does not support them either.
* See {@link UnitTest#test_calendarLeapSecondBehavior UnitTest.test_calendarLeapSecondBehavior}
* or this <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4272347">bug report</a>.

* <h4>Threads</h4>

* <p>
* This class is multithread safe.
* In order order to achieve high performance, the simple approach of using synchronized methods has been avoided
* and other more complicated techniques are used instead, for instance:
* <ol>
*  <li>
*		each thread has its own Calendar and various {@link DateFormat} instances stored in {@link ThreadLocal}s
*  </li>
*  <li>
*		the caching methods do not synchronize unless the relevant cache needs to be modified,
*		and the different caches each use their own synchronization lock
*  </li>
* </ol>
* <p>
* @author Brent Boyer
*/
public final class DateUtil {
	
	// -------------------- SimpleDateFormat constants --------------------
	
	/**
	* A <i>time pattern</i> String for the {@link SimpleDateFormat} class
	* which exactly specifies a day of the year.
	* <p>
	* For reasons stated in the class javadocs, this format follows the human-readable version
	* of the ISO 8601 specification for day of the year dates (±YYYY-MM-DD).
	* <i>But it must be used with a {@link IsoDateFormat} instance</i> to get the era tokens right.
	* <p>
	* No explicit time zone is given.
	* Other users of this field (e.g. {@link #timeStampPattern}) explicitly state the time zone elsewhere in their format.
	* If left unspecified, then the local time zone has to be assumed.
	* <p>
	* This format may be used in a file name; see {@link #timeOfDayForFilePattern}.
	*/
	private static final String dayOfYearPattern = "Gyyyy-MM-dd";
	
	/**
	* A <i>time pattern</i> String for the {@link SimpleDateFormat} class
	* which exactly specifies the time of the day with as much precision (millseconds) as the JDK allows.
	* <p>
	* For reasons stated in the class javadocs, this format follows the human-readable version
	* of the ISO 8601 specification for the time of the day (HH:MM:SS.SSS).
	* Here, HH is a 24-hour clock (i.e. takes on the values 00 thru 23, which eliminates the need to specify am/pm)
	* and SSS is the milliseconds.
	* <p>
	* No explicit time zone is given.
	* Other users of this field (e.g. {@link #timeStampPattern}) explicitly state the time zone elsewhere in their format.
	* If left unspecified, then the local time zone has to be assumed.
	* <p>
	* Because this format uses the colon (':') char as a field separator,
	* it is not suitable for use in a file name time stamp; see {@link #timeOfDayForFilePattern}.
	*/
	private static final String timeOfDayPattern = "HH:mm:ss.SSS";
	
	/**
	* Serves the same purpose as {@link #timeOfDayPattern} except that it should produce legal file names.
	* <p>
	* To achieve this, the ISO 8601 specified colon (':') field separator char is replaced by a dash ('-') char.
	* <i>This means that this format is not strictly ISO 8601 compliant.</i>
	* <p>
	* Concerning what consitutes a legal file name across many operating systems,
	* note that the ISO 9660 Level 1 format is probably the most universal file name format, but it is hopelessly restrictive.
	* ISO 9660 Level 2 merely adds extra file name chars (30 instead of 8), so it too is insufficient.
	* So, the choice was made to restrict this constant to a format that should work on the major operating systems; see
	* <ul>
	*  <li><a href="http://www.jpsoft.com/help/index.htm?filenames.htm">http://www.jpsoft.com/help/index.htm?filenames.htm</a></li>
	*  <li><a href="http://scilnet.fortlewis.edu/tech/NT-Server/File_Names.htm">http://scilnet.fortlewis.edu/tech/NT-Server/File_Names.htm</a></li>
	*  <li><a href="http://www.comptechdoc.org/os/windows/ntwsguide/ntwsfiles.html">http://www.comptechdoc.org/os/windows/ntwsguide/ntwsfiles.html</a></li>
	*  <li><a href="http://www.npac.syr.edu/users/bernhold/web-cdroms.html">http://www.npac.syr.edu/users/bernhold/web-cdroms.html</a></li>
	* </ul>
	*/
	private static final String timeOfDayForFilePattern = "HH-mm-ss.SSS";
	
	/**
	* A <i>time pattern</i> String for the {@link SimpleDateFormat} class
	* which exactly specifies a moment in time with as much precision (millseconds) as the JDK allows.
	* <p>
	* For reasons stated in the class javadocs, this format follows the human-readable version
	* of the ISO 8601 specification for dates and times (±YYYY-MM-DDTHH:MM:SS.SSSZ)
	* In other words, this is a concatenation of @link #dayOfYearPattern}, the letter 'T',
	* {@link #timeOfDayPattern}, and the explicit RFC 822 time zone used;
	* see further notes in those constants.
	* <i>But it must be used with a {@link IsoDateFormat} instance</i> to get the era tokens right.
	* <p>
	* Because this format uses the colon (':') char as a field separator via timeOfDayPattern,
	* it is not suitable for use in a file name time stamp; see {@link #timeStampForFilePattern}.
	*/
	private static final String timeStampPattern = dayOfYearPattern + "'T'" + timeOfDayPattern + "Z";	// CRITICAL: you really must specify the time zone (Z); if do not, then my test code below fails due to a bug in SimplDateFormat; (the first problem is found at t = 25678800000L <--> 1970-10-25 01:00:00:000); see my SdfBug.java class and the bug report filed with Sun on 2007/7/28
	
	/**
	* Serves the same purpose as {@link #timeStampPattern} except that it should produce legal file names.
	* <p>
	* This is a concatenation of @link #dayOfYearPattern}, the letter 'T',
	* {@link #timeOfDayForFilePattern}, and the explicit RFC 822 time zone used;
	* see further notes in those constants.
	* <i>This means that this format is not strictly ISO 8601 compliant.</i>
	*/
	private static final String timeStampForFilePattern = dayOfYearPattern + "'T'" + timeOfDayForFilePattern + "Z";
	
	// -------------------- Calendar fields and methods --------------------
	
	private static final ThreadLocal<Calendar> calendarPerThread = new ThreadLocal<Calendar>() {
		protected synchronized Calendar initialValue() { return Calendar.getInstance(); }
	};
	
	private static Calendar getCalendar() { return calendarPerThread.get(); }
	
	// -------------------- fields for getXXXStamp/parseXXXStamp caching --------------------
	
	/** The cache used by {@link #getDayStamp getDayStamp} and {@link #parseDayStamp parseDayStamp}. */
	public static final DateStringCache dayOfYearCache = new DateStringCache( dayOfYearPattern, true );
	
	/** The cache used by {@link #getDayStamp getDayStamp} and {@link #parseDayStamp parseDayStamp}. */
	public static final DateStringCache timeOfDayStampCache = new DateStringCache( timeOfDayPattern );
	
	/** The cache used by {@link #getDayStamp getDayStamp} and {@link #parseDayStamp parseDayStamp}. */
	public static final DateStringCache timeOfDayStampForFileCache = new DateStringCache( timeOfDayForFilePattern );
	
	/** The cache used by {@link #getTimeStamp getTimeStamp} and {@link #parseTimeStamp parseTimeStamp}. */
	public static final DateStringCache timeStampCache = new DateStringCache( timeStampPattern, true );
	
	/** The cache used by {@link #getTimeStampForFile getTimeStampForFile} and {@link #parseTimeStampForFile parseTimeStampForFile}. */
	public static final DateStringCache timeStampForFileCache = new DateStringCache( timeStampForFilePattern, true );
	
	// -------------------- clearDateStringCaches, suppressDateStringCaches --------------------
	
	/**
	* Calls {@link DateStringCache#clear clear} on all the {@link DateStringCache} fields of this class:
	* {@link #dayOfYearCache}, {@link #timeOfDayStampCache}, {@link #timeOfDayStampForFileCache},
	* {@link #timeStampCache}, {@link #timeStampForFileCache}.
	*/
	public static void clearDateStringCaches() {
		dayOfYearCache.clear();
		timeOfDayStampCache.clear();
		timeOfDayStampForFileCache.clear();
		timeStampCache.clear();
		timeStampForFileCache.clear();
	}
	
	/**
	* Calls {@link DateStringCache#setSizeMax setSizeMax}(0) on all the {@link DateStringCache} fields of this class:
	* {@link #dayOfYearCache}, {@link #timeOfDayStampCache}, {@link #timeOfDayStampForFileCache},
	* {@link #timeStampCache}, {@link #timeStampForFileCache}.
	*/
	public static void suppressDateStringCaches() {
		dayOfYearCache.setSizeMax(0);
		timeOfDayStampCache.setSizeMax(0);
		timeOfDayStampForFileCache.setSizeMax(0);
		timeStampCache.setSizeMax(0);
		timeStampForFileCache.setSizeMax(0);
	}
	
	// -------------------- getCacheIssues and helper fields and methods --------------------
	
	/** Default value for the prefix param of {@link #getCacheIssues(String, String) getCacheIssues(prefix, separator)}. */
	private static final String prefix_default = null;
	
	/** Default value for the separator param of {@link #getCacheIssues(String, String) getCacheIssues(prefix, separator)}. */
	private static final String separator_default = "**********" + "\n";
	
	/** Returns {@link #getCacheIssues(String) getCacheIssues}({@link #prefix_default}). */
	public static String getCacheIssues() {
		return getCacheIssues(prefix_default);
	}
	
	/** Returns {@link #getCacheIssues(String, String) getCacheIssues}(prefix, {@link #separator_default}). */
	public static String getCacheIssues(String prefix) {
		return getCacheIssues(prefix, separator_default);
	}
	
	/**
	* Returns a String description of any cache issues
	* (e.g. warnings of cache misses, which indicate that caches might need to be increased).
	* <p>
	* Contract: the result is never null, <i>but is zero-length if and only if there are no issues</i>.
	* If there are issues, they are listed one per line.
	* See the prefix and separator param descriptions below for where they appear.
	* <p>
	* @param prefix used in formatting the result for better readability;
	* this value, if non-null and if there are issues, appears as the first piece of text in the result;
	* the value should end in a newline char;
	* common choices would be one or more simple newline chars ('\n') or maybe a text preamble of some sort
	* @param separator used in formatting the result for better readability;
	* this value, if non-null and if there are issues, appears in exactly two places in the result:
	* immediately before the issues as well as immediately after the issues;
	* the value should end in a newline char;
	* a common choice is a line of distinctive chars like asterisks ('*')
	*/
	public static String getCacheIssues(String prefix, String separator) {
		StringBuilder sb = new StringBuilder();
		
		String header =
			((prefix != null) ? prefix : "") +
			((separator != null) ? separator : "") +
			"The following issues were detected with the caching inside DateUtil:" + "\n";
		
		appendIssues( "DateInfoCache", getDateInfoCacheIssues(), sb, header );
		appendIssues( "dayOfYearCache", dayOfYearCache.getIssues(), sb, header );
		appendIssues( "timeOfDayStampCache", timeOfDayStampCache.getIssues(), sb, header );
		appendIssues( "timeOfDayStampForFileCache", timeOfDayStampForFileCache.getIssues(), sb, header );
		appendIssues( "timeStampCache", timeStampCache.getIssues(), sb, header );
		appendIssues( "timeStampForFileCache", timeStampForFileCache.getIssues(), sb, header );
		
		if ((sb.length() > 0) && (separator != null)) sb.append(separator);
		
		return sb.toString();
	}
	
	private static void appendIssues(String label, String issues, StringBuilder sb, String header) {
		if (issues.length() > 0) {
			if (sb.length() == 0) sb.append(header);
			sb.append('\n');
			sb.append(label).append(":").append('\n');
			sb.append(issues);
		}
	}
	
	// -------------------- static initializer --------------------
	
/*
suppressing this for now, requiring the user to know to investigate this manually,
because am seeing issues if logging occurs during shutdown, such as:
	1) the wrong log formatter (the default xml one) is used on the file, even tho the correct one is specified by the log prop file
	2) the log file's .lck companion does not get deleted
I assume that all this weirdness is due to logging during shutdown not being a good idea.

	** Establishes a shutdown hook that logs the result of {@link #getCacheIssues getCacheIssues} if anything is discovered. *
	static {
		Thread cacheChecker = new Thread(
			new Runnable() {
				public void run() {
					String cacheIssues = getCacheIssues();
					if (cacheIssues.length() == 0) return;
					
					LogUtil.getLogger2().logp(Level.WARNING, "DateUtil", "<staticInitializer>", "\n" + cacheIssues);
					handleLoggerReset(cacheIssues);
					Sounds.playErrorMinor();
				}
				
				**
				* Have seen log events fail to get recorded during shutdown.
				* Reason: LogManager has its own shutdown hook which resets all of its Loggers (in particular, removes all their Handlers).
				* Need to log the information another way.
				*
				private void handleLoggerReset(String cacheIssues) {
					try {
						//if (LogUtil.getLogger2().getHandlers().length > 0) return;	// if we have at least 1 Handler left, then assume that our event got logged
							// Nope, the above has been proven in practice to be unreliable, must assume that logging failed and ensure that the output is logged by some other means:
						LogRecord record = new LogRecord(Level.WARNING, cacheIssues);
						record.setSourceClassName("DateUtil");
						record.setSourceMethodName("static initializer");
						String msg = new FormatterFull().format(record);
						System.err.println(msg);
						FileUtil.writeString( msg, LogUtil.makeLogFile("cacheIssues.txt"), false );	// always use this line: have seen the console output above fail to appear too...
					}
					catch (Throwable t) {
						t.printStackTrace(System.err);	// hmm, have seen this line too fail to generate output during shutdown...
					}
				}
			},
			"DateUtil_CacheChecker"
		);
		cacheChecker.setPriority( Thread.NORM_PRIORITY );
		Runtime.getRuntime().addShutdownHook( cacheChecker );
	}
*/
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private DateUtil() {}
	
	// -------------------- isXXX --------------------
	
// +++ the javadocs:
//	--should put the @link to the getXXX in a separate line that says that that method defines the meaning
//	--need examples of what returns true and returns false
	
	/**
	* Determines whether or not date occurs in the same {@link #getCenturyOfMillenia century of the millenia} as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameCenturyOfMillenia(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getCenturyOfMillenia(date) == getCenturyOfMillenia(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same {@link #getDecadeOfCentury decade of the century} as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameDecadeOfCentury(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getDecadeOfCentury(date) == getDecadeOfCentury(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same {@link #getYear year} as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameYear(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getYear(date) == getYear(dateReference));
	}
	
	/**
	* Determines whether or not date falls on the last day of the year.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static boolean isDayOfYearLast(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		return isExactDay(date, getYearEnd(date));
	}
	
	/**
	* Determines whether or not date occurs in the same day of year as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameDayOfYear(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getDayOfYear(date) == getDayOfYear(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same {@link #getMonth month of the year} as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameMonth(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getMonth(date) == getMonth(dateReference));
	}
	
	/**
	* Determines whether date falls on the last day of the month.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static boolean isDayOfMonthLast(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		return isExactDay(date, getMonthEnd(date));
	}
	
	/**
	* Determines whether or not date occurs in the same day of month as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameDayOfMonth(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getDayOfMonth(date) == getDayOfMonth(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the <i>exact</i> week as dateReference occurs in.
	* Here, the exact week is defined as both Dates sharing the same {@link #getWeekStart week start}.
	* Note that weeks can straddle year boundaries, so this method can return true for days in different years.
	* For example, 1998-01-01 was a Thursday.
	* If {@link Calendar#getFirstDayOfWeek Calendar.getFirstDayOfWeek} is {@link Calendar#SUNDAY SUNDAY},
	* then this method returns true when supplied with Dates occuring inside [1997-12-28, 1998-01-03].
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isExactWeek(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
			// quick test: examine their absolute time difference; can always conclude that they are not same day if over TimeLength.dayMax:
		long timeDifference = Math.abs( date.getTime() - dateReference.getTime() );
		if (timeDifference > TimeLength.weekMax) return false;
		
			// if reach here, then both dates are possibly within the same week, so must check if both have the same week start:
		return getWeekStart(date).equals( getWeekStart(dateReference) );
	}
	
	/**
	* Determines whether or not date occurs in the same week of year as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameWeekOfYear(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getWeekOfYear(date) == getWeekOfYear(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same week of month as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameWeekOfMonth(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getWeekOfMonth(date) == getWeekOfMonth(dateReference));
	}
	
	/**
	* Determines whether or not date occurs on the <i>exact</i> day as dateReference.
	* Here, the exact day is defined as both Dates sharing a) the same year b) month c) day of month.
	* This criteria is stricter than that of the isSameDayXXX methods.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isExactDay(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
			// quick test: examine their absolute time difference; can always conclude that they are not same day if over TimeLength.dayMax:
		long timeDifference = Math.abs( date.getTime() - dateReference.getTime() );
		if (timeDifference > TimeLength.dayMax) return false;
		
			// if reach here, then both dates are with 25 hours of each other, so can simply check if have the same day of week:
		return isSameDayOfWeek(date, dateReference);
	}
	
	/**
	* Determines whether or not date occurs in the same day of week as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameDayOfWeek(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getDayOfWeek(date) == getDayOfWeek(dateReference));
	}
	
	/**
	* Determines whether or not date's day of week is a "week day" (i.e. Monday thru Friday).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static boolean isWeekDay(Date date) throws IllegalArgumentException {
		// date checked by getDayOfWeek below
		
		switch (getDayOfWeek(date)) {
			case Calendar.MONDAY:
			case Calendar.TUESDAY:
			case Calendar.WEDNESDAY:
			case Calendar.THURSDAY:
			case Calendar.FRIDAY:
				return true;
			default:
				return false;
		}
	}
	
	/**
	* Determines whether or not date's day of week is a "week end" (i.e. Saturday or Sunday).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static boolean isWeekEnd(Date date) throws IllegalArgumentException {
		// date checked by getDayOfWeek below
		
		switch (getDayOfWeek(date)) {
			case Calendar.SATURDAY:
			case Calendar.SUNDAY:
				return true;
			default:
				return false;
		}
	}
	
	/**
	* Determines whether or not date occurs within the the specififed limit of days from dateReference.
	* <p>
	* This method first first determines the times of the {@link #getDayStart start of day} for both date and dateReference.
	* Then it determines the min and max of these two times (call them tMin and tMax).
	* Then it takes tMin and computes the time for a Date which occurs exactly limit days later (call it tLimit).
	* This method returns true if tMax occurs on or before tLimit.
	* Note that this method does not care which of date or dateReference occurs first, nor what time of day each represents.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null; limit < 0
	*/
	public static boolean isWithinDays(Date date, Date dateReference, int limit) throws IllegalArgumentException {
		// date, dateReference checked by calls to getDayStart below
		Check.arg().notNegative(limit);
		
		long t = getDayStart(date).getTime();
		long tReference = getDayStart(dateReference).getTime();
		long tMin = Math.min( t, tReference );
		long tMax = Math.max( t, tReference );
		long tLimit = getSameTimeOtherDay( new Date(tMin), limit ).getTime();
		return (tMax <= tLimit);
	}
	
	/**
	* Determines whether or not date is a leap day in the Gregorian Calendar (i.e. is February 29th).
	* <p>
	* @throws IllegalArgumentException if date == null
	* @throws IllegalStateException if {@link #getCalendar getCalendar} does not return a GregorianCalendar instance 
	*/
	public static boolean isLeapDay(Date date) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(date);
		
		if (!(getCalendar() instanceof GregorianCalendar)) throw new IllegalStateException("getCalendar()'s type = " + getCalendar().getClass().getName() + " is !instanceof GregorianCalendar");
		return (getMonth(date) == Calendar.FEBRUARY) && (getDayOfMonth(date) == 29);
		// Note: GregorianCalendar has an isLeapYear(int year) method that could also potentially use in an assert, but it is problematic (e.g. in how it handles BC years) 
	}
	
	/**
	* Determines whether or not date occurs in the same hour of the day as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameHourOfDay(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getHourOfDay(date) == getHourOfDay(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same minute of the hour as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameMinuteOfHour(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getMinuteOfHour(date) == getMinuteOfHour(dateReference));
	}
	
	/**
	* Determines whether or not date occurs in the same second of the minute as dateReference.
	* <p>
	* @throws IllegalArgumentException if date == null; dateReference == null
	*/
	public static boolean isSameSecondOfMinute(Date date, Date dateReference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		Check.arg().notNull(dateReference);
		
		return (getSecondOfMinute(date) == getSecondOfMinute(dateReference));
	}
	
	// -------------------- getXXX --------------------
	
// +++ javadocs need to
//	--report what range of values the result can be in
	
	/**
	* Returns the era of date.
	* <p>
	* The value that is returned is defined by the concrete subclass of {@link Calendar}
	* that is being used for the current Locale used by the JVM.
	* In most parts of the world, by default, this will be an instance of {@link GregorianCalendar},
	* so the result will be either {@link GregorianCalendar#AD} or {@link GregorianCalendar#BC}.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getEra(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.era;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.ERA);
	}
	
	/**
	* Returns the century of the millenia of date.
	* <p>
	* The value that is returned is in the range [0, 9]
	* (e.g. 2004 is the 0th century of the millenia, 2104 is the 1st century, etc).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getCenturyOfMillenia(Date date) throws IllegalArgumentException {
		// date checked by getYear below
		
		int year = getYear(date);
		int numberWithCenturyAsDigitLast = year / 100;
		int century = numberWithCenturyAsDigitLast % 10;
		return century;
	}
	
	/**
	* Returns the decade of the century of date.
	* <p>
	* The value that is returned is in the range [0, 9]
	* (e.g. 2004 is the 0th decade of the century, 2014 is the 1st decade, etc).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getDecadeOfCentury(Date date) throws IllegalArgumentException {
		// date checked by getYear below
		
		int year = getYear(date);
		int numberWithDecadeAsDigitLast = year / 10;
		int decade = numberWithDecadeAsDigitLast % 10;
		return decade;
	}
	
	/**
	* Returns the year of date.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getYear(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.year;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.YEAR);
	}
	
	/**
	* Returns that Date which represents the start of the year that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be midnight of the year's first day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getYearStart(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return new Date( info.yearStart.getTime() );	// CRITICAL: must return a new instance to preserve encapsulation, since caller may do screwy things like change the result's time
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.set( Calendar.MONTH, 0);
		calendar.set( Calendar.DAY_OF_YEAR, 1);
		calendar.set( Calendar.HOUR_OF_DAY, 0);	// CRITICAL: must use HOUR_OF_DAY and NOT HOUR, since HOUR is only 0-11 because it is coupled with am/pm, which means that if use HOUR, then the result could have a HOUR_OF_DAY equal to either 0 or 12
		calendar.set( Calendar.MINUTE, 0);
		calendar.set( Calendar.SECOND, 0);
		calendar.set( Calendar.MILLISECOND, 0);
		Date yearStart = calendar.getTime();
		assert (yearStart.compareTo(date) <= 0) : "the algorithm inside getYearStart failed for date = " + getTimeStamp(date);
		assert !isSameYear(new Date(yearStart.getTime() - 1), yearStart) : "the algorithm inside getYearStart failed for date = " + getTimeStamp(date);
		return yearStart;
	}
	
	/**
	* Returns that Date which represents the end of the year that date lies in.
	* This will be 1 millisecond before midnight of the next year's first day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getYearEnd(Date date) throws IllegalArgumentException {
		// date checked by getYearStart below
		
		Date yearStart = getYearStart(date);
		Date yearNext = getSameTimeNextYear(yearStart);
		Date yearEnd = new Date( yearNext.getTime() - 1 );
		assert (date.compareTo(yearEnd) <= 0) : "the algorithm inside getYearEnd failed for date = " + getTimeStamp(date);
		assert isSameYear(date, yearEnd) : "the algorithm inside getYearEnd failed for date = " + getTimeStamp(date);
		assert !isSameYear(yearEnd, yearNext) : "the algorithm inside getYearEnd failed for date = " + getTimeStamp(date);
		return yearEnd;
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the next year.
	* The implementation here simply returns <code>{@link #getSameTimeOtherYear getSameTimeOtherYear}(date, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeNextYear(Date date) throws IllegalArgumentException {
		return getSameTimeOtherYear(date, 1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the previous year.
	* The implementation here simply returns <code>{@link #getSameTimeOtherYear getSameTimeOtherYear}(date, -1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimePreviousYear(Date date) throws IllegalArgumentException {
		return getSameTimeOtherYear(date, -1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date (down to the millisecond),
	* but its year minus date's year equals yearDifference
	* (e.g. +1 returns next year, -1 returns previous year).
	* <p>
	* <i>Note:</i> leap second effects should be properly handled, so the difference between the result and date
	* will not necessarily be a multiple of 24 hours.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeOtherYear(Date date, int yearDifference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.add(Calendar.YEAR, yearDifference);
		Date timeOther = calendar.getTime();
		assert Math2.hasSameSign( timeOther.compareTo(date), yearDifference ) : "the algorithm inside getSameTimeOtherYear failed for date = " + getTimeStamp(date) + " and yearDifference = " + yearDifference;
		return timeOther;
	}
	
	/**
	* Returns the month of the year of date.
	* <p>
	* <b>Warning:</b> for compatibility with {@link Calendar}, the result uses the same 0 offset month basis.
	* For Locales which use a Gregorian Calendar, the result is in the range [0, 11].
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getMonth(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.month;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.MONTH);
	}
	
	/**
	* Returns that Date which represents the start of the month that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be midnight of the month's first day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getMonthStart(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return new Date( info.monthStart.getTime() );	// CRITICAL: must return a new instance to preserve encapsulation, since caller may do screwy things like change the result's time
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.set( Calendar.DAY_OF_MONTH, 1);
		calendar.set( Calendar.HOUR_OF_DAY, 0);	// CRITICAL: must use HOUR_OF_DAY and NOT HOUR, since HOUR is only 0-11 because it is coupled with am/pm, which means that if use HOUR, then the result could have a HOUR_OF_DAY equal to either 0 or 12
		calendar.set( Calendar.MINUTE, 0);
		calendar.set( Calendar.SECOND, 0);
		calendar.set( Calendar.MILLISECOND, 0);
		Date monthStart = calendar.getTime();
		assert (monthStart.compareTo(date) <= 0) : "the algorithm inside getMonthStart failed for date = " + getTimeStamp(date);
		assert !isSameMonth(new Date(monthStart.getTime() - 1), monthStart) : "the algorithm inside getMonthStart failed for date = " + getTimeStamp(date);
		return monthStart;
	}
	
	/**
	* Returns that Date which represents the end of the month that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be 1 millisecond before midnight of the next month's first day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getMonthEnd(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		Date monthStart = getMonthStart(date);
		Date monthNext = getSameTimeNextMonth(monthStart);
		Date monthEnd = new Date( monthNext.getTime() - 1 );
		assert (monthEnd.getTime() >= date.getTime()) : "the algorithm inside getMonthEnd failed for date = " + getTimeStamp(date) + "; it calculated monthEnd = " + getTimeStamp(monthEnd) + " which occurs before date";
		assert isSameMonth(date, monthEnd) : "the algorithm inside getMonthEnd (or isSameMonth) failed";
		assert !isSameMonth(new Date(monthEnd.getTime() + 1), monthEnd) : "the algorithm inside getMonthEnd (or isSameMonth) failed";
		return monthEnd;
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the next month of the year.
	* The implementation here simply returns <code>{@link #getSameTimeOtherMonth getSameTimeOtherMonth}(date, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeNextMonth(Date date) throws IllegalArgumentException {
		return getSameTimeOtherMonth(date, 1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the previous month of the year.
	* The implementation here simply returns <code>{@link #getSameTimeOtherMonth getSameTimeOtherMonth}(date, -1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimePreviousMonth(Date date) throws IllegalArgumentException {
		return getSameTimeOtherMonth(date, -1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date (down to the millisecond),
	* but its month of the year minus date's month of the year equals monthDifference
	* (e.g. +1 returns next month, -1 returns previous month).
	* <p>
	* <i>Note:</i> leap seconds are properly accounted for,
	* so the difference between the result and date will not necessarily be a multiple of 24 hours.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeOtherMonth(Date date, int monthDifference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.add(Calendar.MONTH, monthDifference);
		Date timeOther = calendar.getTime();
		if (monthDifference < 0) assert (timeOther.getTime() < date.getTime()) : "the algorithm inside getSameTimeOtherMonth failed for date = " + getTimeStamp(date) + " and monthDifference = " + monthDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs >= date";
		else if (monthDifference > 0) assert (timeOther.getTime() > date.getTime()) : "the algorithm inside getSameTimeOtherMonth failed for date = " + getTimeStamp(date) + " and monthDifference = " + monthDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs <= date";
		else assert (timeOther.getTime() == date.getTime()) : "the algorithm inside getSameTimeOtherMonth failed for date = " + getTimeStamp(date) + " and monthDifference = " + monthDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which != date";
		return timeOther;
	}
	
	/**
	* Returns the week of year.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getWeekOfYear(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.weekOfYear;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.WEEK_OF_YEAR);
	}
	
	/**
	* Returns the week of month.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getWeekOfMonth(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.weekOfMonth;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.WEEK_OF_MONTH);
	}
	
	/**
	* Returns that Date which represents the start of the week that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be midnight of the week's first day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getWeekStart(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return new Date( info.weekStart.getTime() );	// CRITICAL: must return a new instance to preserve encapsulation, since caller may do screwy things like change the result's time
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.set( Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
		calendar.set( Calendar.HOUR_OF_DAY, 0);	// CRITICAL: must use HOUR_OF_DAY and NOT HOUR, since HOUR is only 0-11 because it is coupled with am/pm, which means that if use HOUR, then the result could have a HOUR_OF_DAY equal to either 0 or 12
		calendar.set( Calendar.MINUTE, 0);
		calendar.set( Calendar.SECOND, 0);
		calendar.set( Calendar.MILLISECOND, 0);
		Date weekStart = calendar.getTime();
		assert (weekStart.compareTo(date) <= 0) : "the algorithm inside getWeekStart failed for date = " + getTimeStamp(date);
		//assert !isExactWeek(new Date(weekStart.getTime() - 1), weekStart) : "the algorithm inside getWeekStart failed for date = " + getTimeStamp(date);	// can NOT use this assert, since it leads to stack overflow from the circular method calls (isExactWeek also calls this method)
		return weekStart;
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the next week of the month.
	* The implementation here simply returns <code>{@link #getSameTimeOtherWeek getSameTimeOtherWeek}(date, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeNextWeek(Date date) throws IllegalArgumentException {
		return getSameTimeOtherWeek(date, 1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the previous week of the month.
	* The implementation here simply returns <code>{@link #getSameTimeOtherWeek getSameTimeOtherWeek}(date, -1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimePreviousWeek(Date date) throws IllegalArgumentException {
		return getSameTimeOtherWeek(date, -1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date (down to the millisecond),
	* but its week of the month minus date's week of the month equals weekDifference
	* (e.g. +1 returns next week, -1 returns previous week).
	* <p>
	* <i>Note:</i> leap seconds are properly accounted for,
	* so the difference between the result and date will not necessarily be a multiple of 7 * 24 hours.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeOtherWeek(Date date, int weekDifference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.add(Calendar.WEEK_OF_MONTH, weekDifference);
		Date timeOther = calendar.getTime();
		if (weekDifference < 0) assert (timeOther.getTime() < date.getTime()) : "the algorithm inside getSameTimeOtherWeek failed for date = " + getTimeStamp(date) + " and weekDifference = " + weekDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs >= date";
		else if (weekDifference > 0) assert (timeOther.getTime() > date.getTime()) : "the algorithm inside getSameTimeOtherWeek failed for date = " + getTimeStamp(date) + " and weekDifference = " + weekDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs <= date";
		else assert (timeOther.getTime() == date.getTime()) : "the algorithm inside getSameTimeOtherWeek failed for date = " + getTimeStamp(date) + " and weekDifference = " + weekDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which != date";
		return timeOther;
	}
	
	/**
	* Returns the day of the year of date.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getDayOfYear(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.dayOfYear;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.DAY_OF_YEAR);
	}
	
	/**
	* Returns the day of the month of date.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getDayOfMonth(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.dayOfMonth;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.DAY_OF_MONTH);
	}
	
	/**
	* Returns the day of the week of date.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getDayOfWeek(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.dayOfWeek;
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.DAY_OF_WEEK);
	}
	
	/**
	* Returns that Date which represents the start of the day that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be midnight of date's day.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getDayStart(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return new Date(info.dayStart);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.set( Calendar.HOUR_OF_DAY, 0);	// CRITICAL: must use HOUR_OF_DAY and NOT HOUR, since HOUR is only 0-11 because it is coupled with am/pm, which means that if use HOUR, then the result could have a HOUR_OF_DAY equal to either 0 or 12
		calendar.set( Calendar.MINUTE, 0);
		calendar.set( Calendar.SECOND, 0);
		calendar.set( Calendar.MILLISECOND, 0);
		Date dayStart = calendar.getTime();
		assert (dayStart.compareTo(date) <= 0) : "the algorithm inside getDayStart failed for date = " + getTimeStamp(date);
		assert !isExactDay(new Date(dayStart.getTime() - 1), dayStart) : "the algorithm inside getDayStart failed for date = " + getTimeStamp(date);
		return dayStart;
	}
	
	/**
	* Returns that Date which represents the end of the day that date lies in.
	* Because {@link Calendar} defines midnight to be the first time of day,
	* the result will be 1 millisecond before midnight of the day after date.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getDayEnd(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		Date dayStart = getDayStart(date);
		Date dayNext = getSameTimeNextDay(dayStart);
		Date dayEnd = new Date( dayNext.getTime() - 1 );
		assert (date.compareTo(dayEnd) <= 0) : "the algorithm inside getDayEnd failed for date = " + getTimeStamp(date);
		assert isExactDay(date, dayEnd) : "the algorithm inside getDayEnd failed for date = " + getTimeStamp(date);
		assert !isExactDay(dayEnd, dayNext) : "the algorithm inside getDayEnd failed for date = " + getTimeStamp(date);
		return dayEnd;
	}
	
	/**
	* Returns the time of day for date.
	* In particular, the result is the "wall clock" reading for date <i>expressed as a single long value</i>.
	* <p>
	* Here, "wall clock" means the time of day fields (i.e. hours, minutes, seconds, and milliseconds) as numbers.
	* Label these fields as HH, mm, ss, and SSS respectively.
	* One way to convert these fields into a single long is to calculate <code>(HH * TimeLength.hour) + (mm * TimeLength.minute) + (ss * TimeLength.second) + SSS</code>.
	* The result of this method is always equal to this calculation (altho this method is not necessarily implemented that way).
	* <p>
	* <p>
	* Note that the "wall clock" used is adjusted for any time zone change that happened on date for the JVM's default Locale and TimeZone.
	* The "wall clock" (unlike normal physical clocks) is also assumed to be able to represent positive leap seconds (i.e. the second field can be 60).
	* <p>
	* <b>Warning: a consequence of adjusting for time zone changes is that it is problematic to compute absolute time differences using the results of this method.</b>
	* For instance, even if date1 and date2 are known to fall on the same day,
	* the calculation <code>long timeDiff = getTimeOfDay(date1) - getTimeOfDay(date2)</code> is wrong if a time zone change occurs between date1 and date2.
	* <p>
	* This method's long result is equivalent to the String returned by {@link #getTimeOfDayStamp getTimeOfDayStamp}
	* in that either form can be perfectly converted into the other.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static long getTimeOfDay(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) {
			long tod = date.getTime() - info.dayStart;
			//if (info.leapSecond != 0) {
			//	DO NOTHING SPECIAL: by my reckoning, on either pos or neg leap second days, the difference calculated above is correct (can be turned into the expected time of day fields); the way to see this is to realize that the leap second (either pos or neg) merely shifts where the day start occurs
			//}
			if (info.timeZoneChange.occurred() && (date.getTime() >= info.timeZoneChange.time)) {
				tod += (info.timeZoneChange.amount * TimeLength.hour);
			}
			return tod;
		}
		
		long tod = date.getTime() - getDayStart(date).getTime();
		//if (getLeapSecond(date) != 0) {
		//	DO NOTHING SPECIAL: by my reckoning, on either pos or neg leap second days, the difference calculated above is correct (can be turned into the expected time of day fields); the way to see this is to realize that the leap second (either pos or neg) merely shifts where the day start occurs
		//}
		TimeZoneChange timeZoneChange = new TimeZoneChange(date);
		if (timeZoneChange.occurred() && (date.getTime() >= timeZoneChange.time)) {
			tod += (timeZoneChange.amount * TimeLength.hour);
		}
		return tod;
	}
	
	/**
	* Returns the length of date's day in milliseconds.
	* <p>
	* Contract: the result is always one of these values: {@link TimeLength#day}, {@link TimeLength#dayTzChPos}, {@link TimeLength#dayTzChNeg}, {@link TimeLength#dayLeapPos}, {@link TimeLength#dayLeapNeg}.
	* Note: this restriction means that this method assumes that at most one special event (time zone change day or leap second) can occur on a given day,
	* and that there is at most 1 leap second that can occcur on a given day (not 2, as allowed by ISO C).
	* <p>
	* @throws IllegalArgumentException if date == null
	* @throws IllegalStateException if an unexpected day length is encountered
	*/
	public static long getDayLength(Date date) throws IllegalArgumentException, IllegalStateException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.dayLength;
		
		Date dayStart = getDayStart(date);
		Date dayStartNext = getSameTimeNextDay( dayStart );
		long dayLength = dayStartNext.getTime() - dayStart.getTime();
		assert (
			(dayLength == TimeLength.day) ||
			(dayLength == TimeLength.dayTzChPos) ||
			(dayLength == TimeLength.dayTzChNeg) ||
			(dayLength == TimeLength.dayLeapPos) ||
			(dayLength == TimeLength.dayLeapNeg)
		) : "the algorithm inside getDayLength calculated dayLength = " + dayLength + " for date = " + getTimeStamp(date) + " which is an unexpected value";
		return dayLength;
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the next day.
	* The implementation here simply returns <code>{@link #getSameTimeOtherDay getSameTimeOtherDay}(date, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeNextDay(Date date) throws IllegalArgumentException {
		return getSameTimeOtherDay(date, 1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date but on the previous day.
	* The implementation here simply returns <code>{@link #getSameTimeOtherDay getSameTimeOtherDay}(date, -1)</code>.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimePreviousDay(Date date) throws IllegalArgumentException {
		return getSameTimeOtherDay(date, -1);
	}
	
	/**
	* Returns that Date which represents the same time of the day referred to by date (down to the millisecond),
	* but its day minus date's day equals dayDifference
	* (e.g. +1 returns next day, -1 returns previous day).
	* <p>
	* <i>Note:</i> leap seconds are properly accounted for,
	* so the difference between the result and date will not necessarily be a multiple of 24 hours.
	* See {@link #get24HoursLater get24HoursLater} for a method to advance the time strictly by 24 hours.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getSameTimeOtherDay(Date date, int dayDifference) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_YEAR, dayDifference);
		Date timeOther = calendar.getTime();
		if (dayDifference < 0) assert (timeOther.getTime() < date.getTime()) : "the algorithm inside getSameTimeOtherDay failed for date = " + getTimeStamp(date) + " and dayDifference = " + dayDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs >= date";
		else if (dayDifference > 0) assert (timeOther.getTime() > date.getTime()) : "the algorithm inside getSameTimeOtherDay failed for date = " + getTimeStamp(date) + " and dayDifference = " + dayDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which occurs <= date";
		else assert (timeOther.getTime() == date.getTime()) : "the algorithm inside getSameTimeOtherDay failed for date = " + getTimeStamp(date) + " and dayDifference = " + dayDifference + "; it calculated timeOther = " + getTimeStamp(timeOther) + " which != date";
		return timeOther;
	}
	
	/**
	* Returns a new Date instance that is exactly 24 hours after date.
	* <p>
	* <b>Warning:</b> leap second effects are not accounted for.
	* See {@link #getSameTimeNextDay getSameTimeNextDay} for a method which handles leap seconds.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date get24HoursLater(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		return new Date( date.getTime() + TimeLength.day );
	}
	
	/**
	* Returns the number of hours that the clock was changed by on the day that date lies in due to a time zone change.
	* The result can be +1 (a spring forward day), 0 (a normal non-time zone change day), or -1 (a fall back day).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getAmountTimeZoneChange(Date date) throws IllegalArgumentException {
		// date checked by getDateInfo below
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.timeZoneChange.amount;
		else return new TimeZoneChange(date).amount;
	}
	
	/**
	* Returns the leap second for date's day.
	* The result can be +1 (a positive leap second day), 0 (a normal non-leap second day), or -1 (a negative leap second day).
	* <p>
	* @throws IllegalArgumentException if date == null
	* @throws IllegalStateException if an unexpected state is encountered
	*/
	public static int getLeapSecond(Date date) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(date);
		
		DateInfo info = getDateInfo(date);
		if (info != null) return info.leapSecond;
		
		long dayLength = getDayLength(date);
		if ( (dayLength == TimeLength.day) || (dayLength == TimeLength.dayTzChPos) || (dayLength == TimeLength.dayTzChNeg) )
			return 0;
		else if (dayLength == TimeLength.dayLeapPos)
			return 1;
		else if (dayLength == TimeLength.dayLeapNeg)
			return -1;
		else
			throw new IllegalStateException("dayLength = " + dayLength + " is an unexpected value; this occurred for date = " + getTimeStamp(date));
		// Note: can't use a switch statement here because dayLength as well as the constants are all longs
	}
	
	/**
	* Returns the hour of the day (result is inside [0, 23]).
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getHourOfDay(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.HOUR_OF_DAY);
	}
	
	/**
	* Returns that Date which represents the start of the hour that date lies in.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static Date getHourStart(Date date) throws IllegalArgumentException {
		// date checked by getEra etc below
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		calendar.set( Calendar.MINUTE, 0);
		calendar.set( Calendar.SECOND, 0);
		calendar.set( Calendar.MILLISECOND, 0);
		Date hourStart = calendar.getTime();
		if (getAmountTimeZoneChange(date) == 0) {	// only simple way to do asserts is when there are no time zone changes
			assert (hourStart.compareTo(date) <= 0) : "the algorithm inside getHourStart failed for date = " + getTimeStamp(date);
			assert !isSameHourOfDay(new Date(hourStart.getTime() - 1), hourStart) : "the algorithm inside getHourStart failed for date = " + getTimeStamp(date);
		}
		return hourStart;
	}
	
	/**
	* Returns the minute of the hour.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getMinuteOfHour(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.MINUTE);
	}
	
	/**
	* Returns the second of the minute.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getSecondOfMinute(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.SECOND);
	}
	
	/**
	* Returns the millisecond of the second.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static int getMilliSecondOfSecond(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		Calendar calendar = getCalendar();
		calendar.setTime(date);
		return calendar.get(Calendar.MILLISECOND);
	}
	
	// -------------------- getXXXStamp --------------------
	
// +++ the javadocs:
//	--need to be written!
//	--need examples of what is returned
	
	public static String getDayStamp() { return getDayStamp( new Date() ); }
	
	/**
	* ...
<!--
make sure and discuss the -/+ era designators and when will drop the +; see IsoDateFormat.format
-->
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static String getDayStamp(Date date) throws IllegalArgumentException {
		// date checked by DateStringCache.format call below
		
		return dayOfYearCache.format( date );
	}
	
	/** Returns <code>{@link #getTimeOfDayStamp(Date) getTimeOfDayStamp}( new Date() )</code>. */
	public static String getTimeOfDayStamp() { return getTimeOfDayStamp( new Date() ); }
	
	/**
	* Returns the time of day for date.
	* In particular, the result is the "wall clock" reading for date <i>expressed as a String</i>.
	* <p>
	* Here, "wall clock" means the time of day fields (i.e. hours, minutes, seconds, and milliseconds) concatenated into a single String as per {@link #timeOfDayPattern}.
	* <p>
	* This method's String result is equivalent to the long returned by {@link #getTimeOfDay getTimeOfDay}
	* in that either form can be perfectly converted into the other.
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static String getTimeOfDayStamp(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		return timeOfDayStampCache.format( date );
	}
	
	/**
	* Is almost the same as {@link #getTimeOfDayStamp(Date) getTimeOfDayStamp(Date)} except that it takes a long arg.
	* <i>Because its arg is not an absolute moment in time, it is impossible for this method to account for time zone changes,
	* therefore, it assumes that no time zone change occurred.</i>
	* This method does, however, handle leap seconds correctly.
	* <p>
	* @param timeOfDay a long value that represents the time of day exactly like the result of {@link #getTimeOfDay getTimeOfDay}
	* @throws IllegalArgumentException if timeOfDay < 0; timeOfDay > {@link TimeLength#dayMax}
	*/
	public static String getTimeOfDayStamp(long timeOfDay) throws IllegalArgumentException {
		Check.arg().notNegative(timeOfDay);
		if (timeOfDay > TimeLength.dayMax) throw new IllegalArgumentException("timeOfDay = " + timeOfDay + " > TimeLength.dayMax = " + TimeLength.dayMax);
		
		long sum = 0;
		
		long hours = (timeOfDay - sum) / TimeLength.hour;
		assert (hours < 24) : "hours = " + hours + " >= 24";
		sum += hours * TimeLength.hour;
		
		long minutes = (timeOfDay - sum) / TimeLength.minute;
		assert (minutes < 60) : "minutes = " + minutes + " >= 60";
		sum += minutes * TimeLength.minute;
		
		long seconds = (timeOfDay - sum) / TimeLength.second;
		assert (seconds <= 60) : "seconds = " + seconds + " > 60";	// seconds CAN == 60 on a leap second day
		sum += seconds * TimeLength.second;
		
		long milliseconds = timeOfDay - sum;
		assert (milliseconds < 1000) : "milliseconds = " + milliseconds + " >= 1000";
		sum += milliseconds;
		
		assert (timeOfDay == sum) : "timeOfDay = " + timeOfDay + " != sum = " + sum;
		
		return StringUtil.toLength((int) hours, 2) + ":" + StringUtil.toLength((int) minutes, 2) + ":" + StringUtil.toLength((int) seconds, 2) + "." + StringUtil.toLength((int) milliseconds, 3);
	}
	
	/**
	* Returns a (possibly) more compact result than {@link #getTimeOfDayStamp(long) getTimeOfDayStamp} by
	* dropping the milliseconds and seconds fields if they can be implicitly understood.
	* The result, however, always contains at least the hour and minutes.
	* <p>
	* This method first calls {@link #getTimeOfDayStamp getTimeOfDayStamp} and uses that String for all subsequent processing.
	* If the millisecond field of the result is all zeroes (".000")), it is removed.
	* If and only if milliseconds were dropped, then checks the second portion, removing it too if all zeroes (":00").
	* <p>
	* Here are examples of what this method returns, in the form of long --> String:
	* <ul>
	*  <li>40953444 --> 11:22:33.444 (i.e. nothing dropped)</li>
	*  <li>40953000 --> 11:22:33 (i.e. just milliseconds dropped)</li>
	*  <li>40920000 --> 11:22 (i.e. seconds and below dropped)</li>
	* </ul>
	* <p>
	* This method is typically used to format times of day for human viewing, where convenience and brevity matter.
	* <p>
	* @param timeOfDay a long <i>(not Date)</i> value that represents the time of day as milliseconds since midnight
	* @throws IllegalArgumentException if timeOfDay < 0; timeOfDay > {@link TimeLength#dayMax}
	*/
	public static String getTimeOfDayStampConcise(long timeOfDay) throws IllegalArgumentException {
		// timeOfDay checked by getTimeOfDayStamp below
		
		String s = getTimeOfDayStamp(timeOfDay);
		
		if (s.endsWith(".000")) {	// if millisecond resolution is its smallest value
			s = s.substring( 0, s.length() - 4 );	// chop off the final ".000"
			
			if (s.endsWith(":00")) {	// if second resolution is its smallest value
				s = s.substring( 0, s.length() - 3 );	// chop off the final ":00"
			}
		}
		
		return s;
	}
	
	public static String getTimeOfDayStampForFile() { return getTimeOfDayStampForFile( new Date() ); }
	
	public static String getTimeOfDayStampForFile(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		return timeOfDayStampForFileCache.format( date );
	}
	
	public static String getTimeStamp() { return getTimeStamp( new Date() ); }
	
	/**
	* ...
<!--
make sure and discuss the -/+ era designators and when will drop the +; see IsoDateFormat.format
-->
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static String getTimeStamp(Date date) throws IllegalArgumentException {
		// date checked by DateStringCache.format call below
		
		return timeStampCache.format( date );
	}
	
	/**
	* Returns a more compact result than {@link #getTimeStamp getTimeStamp} by:
	* <ol>
	*  <li>always dropping the time zone</li>
	*  <li>dropping any low significance fields which can be implicitly understood</li>
	* </ol>
	* The result, however, always contains at least the year.
	* <p>
	* This method first calls {@link #getTimeStamp getTimeStamp} and uses that String for all subsequent processing.
	* The time zone is immediately dropped.
	* Next, if the millisecond field of the result is all zeroes (".000")), it is removed.
	* If and only if milliseconds were dropped, then checks the second portion, removing it too if all zeroes (":00").
	* Similarly, minutes and hours are examined and removed if zeroes ("00" for seconds and "T00" for hours),
	* and day of the month and month of the year are examined and removed if a one ("-01").
	* <p>
	* Here are examples of what this method returns, in the form of fullDate --> conciseDate,
	* where fullDate is what getTimeStamp returns and conciseDate what this method returns:
	* <ul>
	*  <li>+12345-12-31T23:59:59.999-0500 --> +12345-12-31T23:59:59.999 (i.e. just the time zone dropped; era retained)</li>
	*  <li>2004-12-31T23:59:59.999-0500 --> 2004-12-31T23:59:59.999 (i.e. just the time zone dropped)</li>
	*  <li>2004-12-31T23:59:59.000-0500 --> 2004-12-31T23:59:59 (i.e. milliseconds and below dropped)</li>
	*  <li>2004-12-31T23:59:00.000-0500 --> 2004-12-31T23:59 (i.e. seconds and below dropped)</li>
	*  <li>2004-12-31T23:00:00.000-0500 --> 2004-12-31T23 (i.e. minutes and below dropped)</li>
	*  <li>2004-12-31T00:00:00.000-0500 --> 2004-12-31 (i.e. hours and below dropped)</li>
	*  <li>2004-12-01T00:00:00.000-0500 --> 2004-12 (i.e. day and below dropped)</li>
	*  <li>2004-01-01T00:00:00.000-0500 --> 2004 (i.e. month and below dropped)</li>
	*  <li>-0333-01-01T00:00:00.000-0500 --> -0333 (i.e. month and below dropped; era retained)</li>
	* </ul>
	* <p>
	* This method is typically used to format dates for human viewing, where convenience and brevity matter.
	* <i>It is obviously unsuitable, however, for providing precise and unambiguous date representations</i>
	* (the lack of a time zone alone forbids that), which require getTimeStamp/getTimeStampForFile instead.
	*/
	public static String getTimeStampConcise(Date date) throws IllegalArgumentException {
		// date checked by getTimeStamp below
		
		String s = getTimeStamp(date);
		
		int indexTz = s.length() - 5;
		s = s.substring(0, indexTz);	// remove the time zone
		
		if (s.endsWith(".000")) {	// if millisecond resolution is its smallest value
			s = s.substring( 0, s.length() - 4 );	// chop off the final ".000"
			
			if (s.endsWith(":00")) {	// if second resolution is its smallest value
				s = s.substring( 0, s.length() - 3 );	// chop off the final ":00"
				
				if (s.endsWith(":00")) {	// if minute resolution is its smallest value
					s = s.substring( 0, s.length() - 3 );	// chop off the final ":00"
				
					if (s.endsWith("T00")) {	// if hour resolution is its smallest value
						s = s.substring( 0, s.length() - 3 );	// chop off the final "T00"
				
						if (s.endsWith("-01")) {	// if day resolution is its smallest value
							s = s.substring( 0, s.length() - 3 );	// chop off the final "-01"
				
							if (s.endsWith("-01")) {	// if month resolution is its smallest value
								s = s.substring( 0, s.length() - 3 );	// chop off the final "-01"
							}
						}
					}
				}
			}
		}
		
		return s;
	}
	
	public static String getTimeStampForFile() { return getTimeStampForFile( new Date() ); }
	
	/**
	* ...
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	public static String getTimeStampForFile(Date date) throws IllegalArgumentException {
		// date checked by DateStringCache.format call below
		
		return timeStampForFileCache.format( date );
	}
	
	// -------------------- parseXXX --------------------
	
// +++ the javadocs:
//	--need to be written!
//	--need examples of what is returned
	
	public static Date parseDayStamp(String string) throws IllegalArgumentException, ParseException {
		// string checked by call to DateStringCache.parse below
		
		return dayOfYearCache.parse(string);
	}
	
// +++ Note: have not added parseTimeOfDayStampXXX methods to correspond with getTimeOfDayStampXXX for now
// because am not sure if the full Date result would have any meaning
// since the most significant info (e.g. year) is impossible to determine.

// Could solve the above by returning a long which represents just the time in millis since midnight.
// But a problem remains: on time zone change days, there are time of days for which it is impossible to determine
// just the time since midnight because of the ambiguity over which time zone the time of day portion is in.
// So should only use longs if document that the result is only valid on a normal day.
	
	
	public static Date parseTimeStamp(String string) throws IllegalArgumentException, ParseException {
		// string checked by call to DateStringCache.parse below
		
		return timeStampCache.parse(string);
	}
	
// +++ Note: is it possible to add a parseTimeStampConcise method to correspond with getTimeStampConcise?
// It should work if use a lenient DateFormat, since stuff is only dropped if it is implicitly understood?
	
	public static Date parseTimeStampForFile(String string) throws IllegalArgumentException, ParseException {
		// string checked by call to DateStringCache.parse below
		
		return timeStampForFileCache.parse(string);
	}
	
	// -------------------- misc methods --------------------
	
	/**
	* Returns the day of the week name as a String which corresponds to dayOfWeek.
	* <p>
	* @param dayOfWeek an int that equals one of {@link Calendar}'s day of the week constants
	* (i.e. {@link Calendar#SUNDAY} thru {@link Calendar#SATURDAY})
	* @throws IllegalArgumentException if dayOfWeek is an invalid value
	*/
	public static String getDayOfWeekName(int dayOfWeek) throws IllegalArgumentException {
		switch(dayOfWeek) {
			case Calendar.SUNDAY: return "Sunday";
			case Calendar.MONDAY: return "Monday";
			case Calendar.TUESDAY: return "Tuesday";
			case Calendar.WEDNESDAY: return "Wednesday";
			case Calendar.THURSDAY: return "Thursday";
			case Calendar.FRIDAY: return "Friday";
			case Calendar.SATURDAY: return "Saturday";
			default: throw new IllegalArgumentException("dayOfWeek = " + dayOfWeek + " is an invalid value");
		}
	}
	
	// -------------------- DateInfo caching: fields, methods, DateInfoBin class --------------------
	
	/*
	This section is devoted to the definition of the DateInfoBin class,
	for caching of DateInfo instances in order to achieve high performance in many of the isXXX and getXXX methods
	which would otherwise need to invoke slow Calendar methods.
	
	==================================================
	
	The essential ideas behind the caching algorithm used in this section:
	1) mapping of an arbitrary time value to an appropriate part of the cache uses only a few arithmetic operations
	2) high concurrent performance is achieved by avoiding synchronization except when the cache state is changed
	(i.e. reads are cheap, writes are expensive, just as you expect)
	
	==================================================
	
	Idea 1) is achieved in the getDateInfo method by computing an -estimate- of the absolute day number
	(since the epoch) of the input time.
	This step takes just 2 arithmetic operations, a division (dividing the time by a normal day's length to estimate the day number)
	and a modulo (to map the day number into a suitable interval, namely zero thru the last valid index of dateInfoBins).
	The day number computed this way is then directly used as the index into the dateInfoBins array.
	
	Once a DateInfoBin has been selected for an input time this way, a linear search thru all the DateInfos
	which have previously been mapped to this index is performed, hopefully finding one whose day contains time
	so that it can be returned.
	If one is not found, then the DateInfoBin.onCacheMiss method will attempt to create a suitable DateInfo
	for time and store it inside it.
	
	Summary: the cache is essentially just a customized hash table, where the hash function is the day number of the input time.
	
	Note: computing day lengths as described above really is just an -estimate- since irregular day lengths
	due to time zone changes and (possibly) leap seconds ensures that not every day has 24 hours in it.
	One consequence of this is that a given day could get mapped to 3 different (but adjacent in the array) DateInfoBins.
	If this happens, it will not affect the correctness of the cache operation,
	it simply wastes memory, causing more DateInfo instances to be created then is necessary.
	
	Note: the best performance is achieved if each DateInfoBin has exactly 1 DateInfo inside it
	so that the linear search is short, just as in a hash table.
	It is for this reason that setDateInfoCacheSizeMax always resets the cache; see its javadocs.
	
	WARNING: if the time values that will be presented to getDateInfo all have estimated day numbers
	which are a constant offset from each other (e.g. day 100, 200, 300, ...),
	then there is the danger that all the times could map to the same element of dateInfoBins,
	which would cause disastrous lookup issues (i.e. performance equivalent to having to search linearly thru every DateInfo instance).
	
	To prevent this, can simply supply either a very large value of sizeMax to setDateInfoCacheSizeMax
	(if the default is not large enough),
	or a value of sizeMax which is relatively prime to the offset?
	
	Another way to prevent this would be to first hash the day number index;
	but hashes too are susceptible to this same problem when presented with the appropriate input sequences.
	
+++ see Brian Goetz's new concurrency book: he describes a clever application of Futures to build caches--any use here?
	
	==================================================
	
	Idea 2) is achieved by declaring as volatile the dateInfoBins field of this class
	as well as the dateInfos field of the DateInfoBin class.
	
	This allows concurrent threads executing the code to use the technique
	of grabbing a reference to the field and then storing it as a local variable inside the method,
	which is done
		a) to have a reference that is unchanging for the duration of the method,
		which is CRITICAL since relevant field (dateInfoBins, dateInfos)
		can be changed by another thread executing setDateInfoCacheSizeMax or onCacheMiss
		b) for performance, since volatile access is a bit slower
		(it is effectively like an atomic synchronized access)
		
	Note: this concurrency technique provides any given thread with a "snapshot"
	that may or may not be the latest version of the cache:
	it allows some threads to be using an old version of the cache
	while simultaneously another thread may be modifying a new version of the cache.
	This is simply the copy on write idiom.
	
+++ when I profiled UnitTest.benchmark_isSameDayOfWeek on 2007/7/29 using JIP,
it seemed to indicate that accessing these volatile fields is the main bottleneck in my code,
greatly slowing it down; need to think if there is another way that could do all this...

Actually, the above profiling may be misleading:
see notes in the UnitTest.benchmark_XXX methods: I now think that cpu cache effects are what is killing my time,
and this may be intrinsic given the mistmatch between cpu cache and main memory speeds...
	*/
	
	/** Object used for synchronization by the DateInfo code. */
	private static final Object lock_DateInfo = new Object();
	
	/**
	* Stores the number of DateInfo instances that are currently cached.
	* <p>
	* This value gets reset to 0 whenever {@link #setDateInfoCacheSizeMax setDateInfoCacheSizeMax} is called.
	* <p>
	* Contract: this field <i>must only be used inside a <code>synchronized (lock_DateInfo) { ... }</code> block</i>.
	* Because of this, it need not be declared as volatile.
	*/
	private static int dateInfoCacheSize = 0;
	
	/**
	* Specifies the maximum number of DateInfo instances that will be cached.
	* <p>
	* Its default value is 32 * 1024 = 32,768.
	* This was chosen because it is a multiple of 1024
	* which is close to 100 years * 365 days/year = 36,500
	* which is an estimate for how many days might be encountered in a typical application.
	* <p>
	* Contract: this field <i>must only be used inside a <code>synchronized (lock_DateInfo) { ... }</code> block</i>.
	* Because of this, it need not be declared as volatile.
	*/
	private static int dateInfoCacheSizeMax = 32 * 1024;
	
	/**
	* Stores the number of cache misses for the <i>current</i> DateInfo cache.
	* The only cache misses which contribute to this field are those due to the cache size limit being reached;
	* cache misses due to other causes (see DateInfo.onCacheMiss) are ignored.
	* <p>
	* This value gets reset to 0 whenever {@link #setDateInfoCacheSizeMax setDateInfoCacheSizeMax} is called.
	* <p>
	* Contract: this field <i>must only be used inside a <code>synchronized (lock_DateInfo) { ... }</code> block</i>.
	* Because of this, it need not be declared as volatile.
	*/
	private static long countDateInfoCacheMisses = 0;
// +++ should i also track the num of cache its here too, so can measure usefulness of this?  (similar to what was done with numberFormatHits, numberFormatMisses below)
	
	/**
	* Forms the top level of the DateInfo cache, namely, an array of {@link DateInfoBin} instances.
	* This array stores the DateInfoBins in ascending day order, modulo the array length.
	* Once a given element of this array has been assigned to a DateInfoBin instance, it is never changed.
	* <p>
	* The currennt implementation initializes this field to a DateInfoBin[] of length {@link #dateInfoCacheSizeMax}
	* so that hopefully each DateInfo will be stored in exactly 1 DateInfoBin, which leads to optimal searching
	* (just like a hash table has best performance when each element is in one bucket).
	* <p>
	* Contract: this field is always a non-null reference to a fully valid array
	* (i.e. it never has null elements), but it may be zero-length.
	* It must be declared as volatile because it is designed to be accessed outside of synchronized blocks for high performance.
	*/
	private static volatile DateInfoBin[] dateInfoBins = new DateInfoBin[ dateInfoCacheSizeMax ];
	static {
		for (int i = 0; i < dateInfoBins.length; i++) {
			dateInfoBins[i] = new DateInfoBin();
		}
	}
	
	/**
	* Returns the DateInfo instance in the cache corresponding to the <i>day</i> of date.
	* <i>Result may be null.</i>
	* <p>
	* @throws IllegalArgumentException if date == null
	*/
	private static DateInfo getDateInfo(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		DateInfoBin[] bins = dateInfoBins;	// get a local variable reference to the volatile field; see docs at start of section
		if (bins.length == 0) return null;
		
		long time = date.getTime();
		long dayEstimate = time / TimeLength.day;
		int index = (int) (dayEstimate % bins.length);	// can always safely cast to an int since bins.length is always an int
		if (index < 0) index += bins.length;	// this is caused when time < 0 due to the behavior of the % operator (see http://java.sun.com/docs/books/jls/second_edition/html/expressions.doc.html#24956); adding bins.length for this case produces a true modulo result
		
		return bins[index].findDateInfo(time, bins);
	}
	
	/**
	* Sets the {@link #dateInfoCacheSizeMax} field to sizeMax.
	* <p>
	* The choice for sizeMax is a tradeoff between memory and performance.
	* A smaller value obviously saves memory;
	* in particular, may supply sizeMax = 0 to suppress the DateInfo cache entirely.
	* On the other hand, a larger value causes better performance,
	* both because it allows more days to be cached
	* as well as because it usually results in fewer DateInfo instances per DateInfoBin,
	* which thus reduces the length of the linear search that must be performed by
	* {@link DateInfoBin#findDateInfo DateInfoBin.findDateInfo}.
	* <p>
	* Because there should ideally be no more then one DateInfo instance per DateInfoBin,
	* a side effect of this method is that it resets {@link #dateInfoBins} to a new array of length sizeMax,
	* with every element being a newly created {@link DateInfoBin} instance.
	* The {@link #dateInfoCacheSize} and {@link #countDateInfoCacheMisses}
	* fields are also reset to 0.
	* <i>So, calling this method completely clears the DateInfo cache and resets its state.</i>
	* <p>
	* @throws IllegalArgumentException if sizeMax < 0
	*/
	public static void setDateInfoCacheSizeMax(int sizeMax) throws IllegalArgumentException {
		Check.arg().notNegative(sizeMax);
		
			// CRITICAL: use a temp local variable to build up the new array...
		DateInfoBin[] binsNew = new DateInfoBin[sizeMax];
		for (int i = 0; i < binsNew.length; i++) {
			binsNew[i] = new DateInfoBin();
		}
			// ...so that the synchronized block merely does quick field assignments, minimizing the lock held time:
		synchronized (lock_DateInfo) {
			dateInfoCacheSize = 0;
			dateInfoCacheSizeMax = sizeMax;
			countDateInfoCacheMisses = 0;
			dateInfoBins = binsNew;	// Note: dateInfoBins' contract is fulfilled: at all times it remains a valid reference
		}
	}
	
	/** Contract: the result always ends with a newline. */
	private static String getDateInfoCacheIssues() {
		synchronized (lock_DateInfo) {
			StringBuilder sb = new StringBuilder();
			
			if (countDateInfoCacheMisses > 0) sb.append("countDateInfoCacheMisses = ").append(countDateInfoCacheMisses).append('\n');
			
			int numberDateInfos = 0;	// CRITICAL: do not use dateInfoCacheSize, since it could be concurrently changing
			int numberBinsWithInfos = 0;
			int numberBinsOverloaded = 0;
			int maxInfosInABin = 0;
			for (DateInfoBin bin : dateInfoBins) {
				if (bin.dateInfos != null) {
					numberDateInfos += bin.dateInfos.length;
					++numberBinsWithInfos;
					if (bin.dateInfos.length > 1) ++numberBinsOverloaded;
					if (bin.dateInfos.length > maxInfosInABin) maxInfosInABin = bin.dateInfos.length;
				}
			}
			double fractionOverloaded = ((double) numberBinsOverloaded) / numberBinsWithInfos;
			if ((maxInfosInABin > 2) || (fractionOverloaded > 0.5)) {
				sb.append("numberDateInfos = ").append(numberDateInfos).append('\n');
				sb.append("numberBinsWithInfos = ").append(numberBinsWithInfos).append('\n');
				sb.append("numberBinsOverloaded = ").append(numberBinsOverloaded).append('\n');
				sb.append("fraction of overloaded Bins (numberBinsOverloaded / numberBinsWithInfos) = ").append(fractionOverloaded).append('\n');
				
				double averageNumber = ((double) numberDateInfos) / numberBinsWithInfos;
				sb.append("average number of DateInfos per DateInfoBin (considering only Bins with DateInfos) = ").append(averageNumber).append('\n');
				
				sb.append("maxInfosInABin = ").append(maxInfosInABin).append('\n');
			}
			
			return sb.toString();
		}
	}
	
// +++ the DateInfoBin inner class below should be renamed to DateInfoCache,
// and many of the fields and methods above in this section should be moved inside,
// and only a field should be defined,
// similar to what I did with the DateStringCache in the section below...
	
	/**
	* Stores all the {@link DateInfo} instances which have a common day number key
	* (as calculated by {@link #getDateInfo getDateInfo}).
	*/
	private static final class DateInfoBin {
		
		/**
		* Recursion detection flag used inside {@link #onCacheMiss onCacheMiss}.
		* <p>
		* This field is static, instead of being instance based, merely to save memory, since there is no point
		* in having it be instance based because the onCacheMiss method must use the static lock_DateInfo field
		* so no instance-level concurrency is possible anyways.
		* <p>
		* Contract: this field <i>must only be used inside a <code>synchronized (lock_DateInfo) { ... }</code> block</i>.
		* Because of this, it need not be declared as volatile.
		*/
		private static boolean executing_onCacheMiss = false;
		
		/**
		* Forms the second level of the DateInfo cache, namely, an array of {@link DateInfo} instances.
		* This array stores the DateInfos in the order in which they were created,
		* which for an arbitrary program is essentially random order.
<!--
+++ if kept track of which DateInfos were the most popular, then an optimization would be to store the most popular ones first in the array;
but this tracking effort may not be worth it since dateInfoCacheSizeMax ought to be adjusted to cause
about one DateInfo instance to be present in each DateInfoBin (via this dateInfos field)
-->
		* <p>
		* Contract: this field may be null,
		* but if non-null then is a reference to a fully valid array (i.e. it never has null elements, nor is zero-length).
		* It must be declared as volatile because it is designed to be accessed outside of synchronized blocks for high performance.
		*/
		private volatile DateInfo[] dateInfos = null;
		
		private DateInfoBin() {}
		
		/**
		* Performs a linear search thru all the currently stored DateInfos, looking for one which is for time;
		* such an instance is returned if found, else the result of calling {@link #onCacheMiss onCacheMiss} is returned.
		*/
		private DateInfo findDateInfo(long time, DateInfoBin[] bins) {
			DateInfo[] infos = dateInfos;	// get a local variable reference to the volatile field; see docs at start of section
			if (infos == null) return onCacheMiss(time, bins, infos);
			
			for (DateInfo info : infos) {
				if (info.isFor(time)) return info;
			}
			return onCacheMiss(time, bins, infos);
		}
		
		/**
		* Handles a cache miss.
		* <p>
		* Immediately returns null if:
		* <ol>
		*  <li>
		*		the bins parameter is != the dateInfoBins field of the DateUtil class.
		*		This only happens if another thread calling setDateInfoCacheSizeMax grabbed lock_DateInfo
		*		just before the thread(s) calling onCacheMiss, which will now block until they get lock_DateInfo.
		*		In this case, immediate return is required to avoid side effects
		*		(e.g. incrementing the dateInfoCacheSize field) which would now be incorrect to do.
		*  </li>
		*  <li>
		*		the infos parameter is != the dateInfos field of this class.
		*		This only happens if one or more other threads concurrently calling onCacheMiss grabbed lock_DateInfo
		*		just before the current thread and modified the dateInfos field.
		*		In this case, immediate return is required to avoid putting redundant DateInfos into the dateInfos field
		*		(since one of the previous threads may have already stored a DateInfo suitable for time,
		*		and the code in this method does not check for this condition).
		*  </li>
		*  <li>
		*		the cache size limit (dateInfoCacheSizeMax) has been reached.
		*		In this case, the {@link #countDateInfoCacheMisses} field is incremented before return.
		*  </li>
		*  <li>
		*		the call is recursive.
		*		This is necessary because the first call to onCacheMiss
		*		will call methods like {@link #getDayStart getDayStart} as part of creating the new DateInfo instance,
		*		and these methods will in turn call getDateInfo/findDateInfo/onCacheMiss recursively,
		*		which has to be detected to avoid an infinite recursion.
		*  </li>
		*  <li>a problem creating a DateInfo suitable for time is encountered</li>
		* </ol>
		* <p>
		* Otherwise, this method creates a new DateInfo[] of length 1 greater then the previous length of the dateInfos field,
		* copies all the previous elements of dateInfos to its beginning elements,
		* adds a new DateInfo instance for time as this array's final element,
		* assigns this array as the new value of dateInfos, increments {@link #dateInfoCacheSize},
		* and returns the DateInfo instance that was created for time.
		*/
		private DateInfo onCacheMiss(long time, DateInfoBin[] bins, DateInfo[] infos) {
			synchronized (lock_DateInfo) {
				if (bins != dateInfoBins) return null;	// CRITICAL: do this test first, since if fail it do not want any side effects that they remaining code below might execute to happen like ++countDateInfoCacheMisses
				
				if (infos != dateInfos) return null;
				
				assert (dateInfoCacheSize <= dateInfoCacheSizeMax) : "dateInfoCacheSize = " + dateInfoCacheSize + " > dateInfoCacheSizeMax = " + dateInfoCacheSizeMax;
				if (dateInfoCacheSize == dateInfoCacheSizeMax) {
					if (countDateInfoCacheMisses < Long.MAX_VALUE) ++countDateInfoCacheMisses;	// CRITICAL: the if clause keeps from wrapping around
					return null;
				}
				
				if (executing_onCacheMiss) return null;	// abort recursive call
				
				try {
					executing_onCacheMiss = true;
					
					int size = (infos != null) ? infos.length + 1 : 1;	// for performance, since dateInfos is volatile, use infos as reference
					DateInfo[] infosNew = new DateInfo[size];	// CRITICAL: use a temp local variable to build up the new array...
					for (int i = 0; i < size - 1; i++) {
						infosNew[i] = infos[i];	// for performance, since dateInfos is volatile, use infos as reference
					}
					try {
						infosNew[size - 1] = new DateInfo( new Date(time) );
					}
					catch (AssertionError ae) {
							// This can legitimately happen (e.g. if time = Long.MIN_VALUE, then it is impossible to calculate the year or month start required by DateInfo)
							// NOTE: if execute this code block, then dateInfos is not reassigned to infosNew, but remains at its old value
						return null;
					}
					dateInfos = infosNew;	// ... to fulfill dateInfos' contract that at all times it remains a valid reference
					
					++dateInfoCacheSize;
					return infosNew[size - 1];	// this is the DateInfo suitable for time
				}
				finally {
					executing_onCacheMiss = false;
				}
			}
		}
		
	}
	
	// -------------------- DateInfo (static inner class) --------------------
	
	/**
	* Stores various calendar related qualities of a Date, such as its era, year, etc.
	* (Currently only stores information down to the day level
	* because the search algorithm inside {@link #getDateInfo getDateInfo} is day based.)
	* <p>
	* This class is multithread safe: it has no mutable state.
	*/
	private static final class DateInfo {
		
		private final int era;
		private final int year;
		private final Date yearStart;
		private final int month;
		private final Date monthStart;
		private final int weekOfMonth;
		private final int weekOfYear;
		private final Date weekStart;
		private final int dayOfYear;
		private final int dayOfMonth;
		private final int dayOfWeek;
		private final long dayStart;
		private final long dayLength;
		private final boolean isLeapDay;
		private final TimeZoneChange timeZoneChange;
		/** -1 if is a negative leap second day (has never happened as of 2005/3/22), 0 if no leap second, +1 if is a positive leap second day (a normal leap second). */
		private final int leapSecond;
// +++ to save memory, should some of the above fields be made into bytes/shorts/etc?
		
		private DateInfo(Date date) {
			era = getEra(date);
			year = getYear(date);
			yearStart = getYearStart(date);
			month = getMonth(date);
			monthStart = getMonthStart(date);
			weekOfMonth = getWeekOfMonth(date);
			weekOfYear = getWeekOfYear(date);
			weekStart = getWeekStart(date);
			dayOfYear = getDayOfYear(date);
			dayOfMonth = getDayOfMonth(date);
			dayOfWeek = getDayOfWeek(date);
			dayStart = getDayStart(date).getTime();
			dayLength = getDayLength(date);
			isLeapDay = isLeapDay(date);
			timeZoneChange = new TimeZoneChange(date);
			leapSecond = getLeapSecond(date);
		}
		
		@Override public String toString() {
			return
				"era = " + era + "\n" +
				"year = " + year + "\n" +
				"yearStart = " + yearStart + "\n" +
				"month = " + month + "\n" +
				"monthStart = " + monthStart + "\n" +
				"weekOfMonth = " + weekOfMonth + "\n" +
				"weekOfYear = " + weekOfYear + "\n" +
				"weekStart = " + weekStart + "\n" +
				"dayOfYear = " + dayOfYear + "\n" +
				"dayOfMonth = " + dayOfMonth + "\n" +
				"dayOfWeek = " + dayOfWeek + "\n" +
				"dayStart = " + dayStart + "\n" +
				"dayLength = " + dayLength + "\n" +
				"isLeapDay = " + isLeapDay + "\n" +
				"timeZoneChange = " + timeZoneChange + "\n" +
				"leapSecond = " + leapSecond + "\n";
		}
		
		/**
		* Returns true if this instance is suitable for time, false otherwise.
		* Here, time is the usual absolute time measurement used by Java (i.e. the number of milliseconds since the epoch),
		* and the suitability criteria is whether or not time falls inside the day defined by this instance.
		*/
		private boolean isFor(long time) {
			return (dayStart <= time) && (time < dayStart + dayLength);
		}
		
	}
	
	// -------------------- TimeZoneChange (static inner class) --------------------
	
	/**
	* Stores information about a time zone change.
	* <p>
	* Background on time zone changes:
	* <blockquote>
	*	Daylight Saving Time begins for most of the United States at 2 a.m. on the first Sunday of April.
	*	Time reverts to standard time at 2 a.m. on the last Sunday of October.
	*	In the U.S., each time zone switches at a different time.<br/>
	*	<br/>
	*	In the European Union, Summer Time begins and ends at 1 am Universal Time (Greenwich Mean Time).<br/>
	*	It starts the last Sunday in March, and ends the last Sunday in October.
	*	In the EU, all time zones change at the same moment.<br/>
	*	<br/>
	*	Taken from <a href="http://webexhibits.org/daylightsaving/b.html">http://webexhibits.org/daylightsaving/b.html</a>
	* </blockquote>
	* <p>
	* This class is multithread safe: it has no mutable state.
	*/
	private static class TimeZoneChange {
		
		/** Stores the exact absolute time when the time zone change happens; has the value Long.MIN_VALUE if there is no time zone change. */
		private final long time;
		
		/** Has the value -1 for a fall back time zone change, 0 if no time zone change, or +1 for a spring forward time zone change. */
		private final int amount;
		
		private TimeZoneChange(Date date) {
			long time = Long.MIN_VALUE;
			int amount = 0;
			
			long dayStart = DateUtil.getDayStart(date).getTime();
			for (int i = 1; i <= 23; i++) {
				Date hourDate = new Date( dayStart + (i * TimeLength.hour) );
				int hourOfDay = DateUtil.getHourOfDay(hourDate);
				if (hourOfDay != i) {
						// confirm that time zone change starts on the hour by proving that 1 ms earlier is behaving normally:
					Date datePrevious = new Date( hourDate.getTime() - 1 );
					int hourOfPrevious = DateUtil.getHourOfDay(datePrevious);
					assert (hourOfPrevious == i - 1) : "the time zone change did not occur exactly on the hour as this method assumes it should";
					
					time = hourDate.getTime();
					amount = hourOfDay - i;
					break;
				}
			}
			assert ((-1 <= amount) && (amount <= 1)) : "amount = " + amount + " is an illegal value";
			assert ((time == Long.MIN_VALUE) && (amount == 0)) || ((time != Long.MIN_VALUE) && (amount != 0)) : "time = " + time + " and amount = " + amount + " are mutually incompatible";
			
				// after every test has been passed, assign this instance's corresponding fields:
			this.time = time;
			this.amount = amount;
		}
		
		@Override public String toString() {
			return "time = " + time + ", amount = " + amount;
		}
		
		private boolean occurred() { return (amount != 0); }
		
	}
	
	// -------------------- DateStringCache (static inner class) --------------------
	
// +++ Note: this caching should actually be put in something else like SimpleDateFormat.
// Make a RFE...
	
	/**
	* Stores Date <--> String mappings (i.e. from the user's perspective, it is a bidirectional map).
	* <p>
	* If this class is used during formating/parsing (via {@link #format format}/{@link #parse parse}),
	* then a given Date/String will only be formated/parsed once.
	* Thereafter, if a request is made to format/parse that Date/String,
	* the previous result will be quickly retrieved and returned.
	* Skipping duplicate format/parse operations yields top performance, since these operations are extremely slow.
	* On the other hand, if the Date/String instances that are encountered are mostly unique,
	* then caching is ineffective and using this class wastes both CPU and memory.
	* <p>
	* The most convenient way to construct an instance of this class
	* is to use the {@link DateUtil.DateStringCache#DateUtil.DateStringCache(String) single String arg constructor}
	* which takes a date and time pattern.
	* <p>
	* Regardless of whether {@link #format format} or {@link #parse parse} is called,
	* this class will attempt to store both the Date --> String mapping as well as the String --> Date mapping.
	* A new Date --> String mapping is generated for every unique Date that is encountered.
	* However, it is possible for the String --> Date mapping to not be added because of an asymmetry that is next described.
	* <p>
	* <b>If the date and time pattern drops information, the bidirectional mapping provided by this class is not 1-1.</b>
	* For example, suppose that the pattern is the day of year pattern "yyyy-MM-dd",
	* which drops all hour and smaller information.
	* Then there are many Dates which will get mapped to the same String,
	* but that String can only be mapped back to one Date.
	* For instance, the Dates (in ISO 8601 format) 2000-01-02T03:04:00.000 and 2000-01-02T03:05:00.000
	* both get mapped to the same String "2000-01-02" if the pattern is "yyyy-MM-dd".
	* In this example, what Date should the String "2000-01-02" get mapped to?
	* We have seen that there are many possible choices (e.g. 2000-01-02T03:04:00.000 and 2000-01-02T03:05:00.000 are among the many).
	* The convention used here is to always map a String to the "fundamental" Date, which is the sole Date which String parses into.
	* In the example here, the String "2000-01-02" would get mapped to the Date corresponding to 2000-01-02T00:00:00.000.
	* <p>
	* In order to limit memory consumption, this class will only store {@link #sizeMax} String <--> Date mappings.
	* The {@link #clear clear} and {@link #setSizeMax setSizeMax} methods can be used to dynamically change the memory used.
	* <p>
	* This class is multithread safe.
<!--
+++ need a writeup of the strategy...

mention that using ConcurrentHashMap guarantees safe publication of the merely effectively immutable Date2s (see the Date2 javadocs)
-->
	*/
	public static class DateStringCache {
		
		/*
		Implementation notes:
		
		This class is currently mainly a wrapper around 2 ConcurrentHashMaps
		with a little extra logic to limit memory consumption and perform diagnostics.
		
		Concerning multithread safety, note that ConcurrentHashMap is intrinsically multithread safe.
		
		Thus, concurrent read operations can be done on it with no other protection,
		and that is what this class does to achieve top performance.
		
		However, while individual write operations on a ConcurrentHashMap are also multithread safe,
		this class encloses all such operations inside blocks synchronized on this instance
		because the associated memory and diagnostic logic need to be atomic (i.e. see consistent state).
		
		Thus, read operations are fast while write operations are slower, as is typical for a cache.
		
		Comment on encapsulation: in the code below, you will see lines like
			new Date( date.getTime() );
		The reason why must store/return a new Date instance is to guarantee encapsulation,
		since the Date class, unfortunately, is mutable.
		It would be a disaster if did not do this, since the cache's internal Date instances
		would otherwise be visible to other users, who might do screwy things like change their times.
		
+++ the long term solution is to use a different library's Date class,
if they have the intelligence to make their version immutable (e.g. joda time does);
then could avoid all that unnecessary object creation...

One short term hack is to use the Date2 class in this package.
This is a Date subclass which throws UnsupportedOperationException if attempt to mutate its state.
Sole problem: such a contract change will totally surprise users
who wrote their code expecting a generic Date class that can be mutated.
THUS, THE CODE BELOW MUST BE CHANGED TO NOT USE Date2 BEFORE IT CAN BE SHARED WITH THE GENERAL PUBLIC?
		
		Comment on optimal String memory usage: in the code below, you will see lines like
			string = StringUtil.newString(string);
		This is done to ensure minimal memory used; for details, see the StringUtil.newString javadocs as well as
		D:\software\java\proposalsQuestionsPostingsEtc\string(String)_constructor.txt
		*/
		
		/** Default value for {@link #sizeMax}. */
		private static final int sizeMax_default = 1 * 1024 * 1024;
		
		/** Description of how to turn a Date into a String.  See {@link SimpleDateFormat} for details on what this can look like. */
		private final String pattern;
		
		/** Used to format/parse Dates/Strings that lack a String/Date mapping. */
		private final ThreadLocal<DateFormat> dateFormatPerThread;
		
		/** Specifies the maximum number of elements that {@link #dateToString} and {@link #stringToDate} may hold. */
		private int sizeMax;
		
		/** Stores the Date --> String mappings. */
		private final Map<Date, String> dateToString = new ConcurrentHashMap<Date, String>();
		
		/** Stores the String --> Date mappings. */
		private final Map<String, Date> stringToDate = new ConcurrentHashMap<String, Date>();
		
		/**
		* Number of times that {@link #format format} returned a cached value.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberFormatHits = new AtomicLong();
		
		/**
		* Number of times that {@link #format format} did not return a cached value.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberFormatMisses = new AtomicLong();
		
		/**
		* Number of times that {@link #format format} failed to cache data when it called {@link #put put}.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberFormatPutFails = new AtomicLong();
		
		/**
		* Number of times that {@link #parse parse} returned a cached value.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberParseHits = new AtomicLong();
		
		/**
		* Number of times that {@link #parse parse} did not return a cached value.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberParseMisses = new AtomicLong();
		
		/**
		* Number of times that {@link #parse parse} failed to cache data when it called {@link #put put}.
		* <i>Note: the count is since the last call to {@link #setSizeMax setSizeMax}/{@link #clear clear}.</i>
		*/
		private final AtomicLong numberParsePutFails = new AtomicLong();
		
		private static ThreadLocal<DateFormat> makeDateFormatPerThread(final String pattern, final boolean useIsoDateFormat) throws IllegalArgumentException {
			Check.arg().notBlank(pattern);
			// useIsoDateFormat obviously has no constraints
			
			return new ThreadLocal<DateFormat>() {
				protected synchronized DateFormat initialValue() {
					DateFormat df = (useIsoDateFormat) ? new IsoDateFormat(pattern) : new SimpleDateFormat(pattern);
					df.setLenient(false);
					return df;
				}
			};
		}
		
		/**
		* Simply calls <code>{@link DateUtil.DateStringCache#DateUtil.DateStringCache(String, int) this}(pattern, {@link #sizeMax_default})</code>.
		* <p>
		* @throws IllegalArgumentException if pattern is blank
		*/
		public DateStringCache(String pattern) throws IllegalArgumentException {
			this(pattern, sizeMax_default);
		}
		
		/**
		* Simply calls <code>{@link DateUtil.DateStringCache#DateUtil.DateStringCache(String, int, boolean) this}(pattern, sizeMax, false)</code>.
		* <p>
		* @throws IllegalArgumentException if pattern is blank; sizeMax < 0
		*/
		public DateStringCache(String pattern, int sizeMax) throws IllegalArgumentException {
			this(pattern, sizeMax, false);
		}
		
		/**
		* Simply calls <code>{@link DateUtil.DateStringCache#DateUtil.DateStringCache(String, int, boolean) this}(pattern, {@link #sizeMax_default}, useIsoDateFormat)</code>.
		* <p>
		* @throws IllegalArgumentException if pattern is blank
		*/
		public DateStringCache(String pattern, boolean useIsoDateFormat) throws IllegalArgumentException {
			this(pattern, sizeMax_default, useIsoDateFormat);
		}
		
		/**
		* The fundamental constructor.
		* <p>
		* @param pattern a date and time pattern String that will be supplied to a {@link ThreadLocal}
		* which constructs {@link DateFormat} instances
		* @param sizeMax the value for {@link #sizeMax}
		* @param useIsoDateFormat if true, then the ThreadLocal will construct {@link IsoDateFormat} instances
		* out of pattern, else it will construct {@link SimpleDateFormat} instances
		* @throws IllegalArgumentException if pattern is blank; sizeMax < 0
		*/
		public DateStringCache(String pattern, int sizeMax, boolean useIsoDateFormat) throws IllegalArgumentException {
			// pattern checked by makeDateFormatPerThread below
			Check.arg().notNegative(sizeMax);
			// useIsoDateFormat checked by makeDateFormatPerThread below
			
			this.pattern = pattern;
			this.dateFormatPerThread = makeDateFormatPerThread(pattern, useIsoDateFormat);
			this.sizeMax = sizeMax;
		}
		
		/** Accessor for {@link #pattern}. */
		public String getPattern() { return pattern; }
		
		/** Accessor for {@link #sizeMax}. */
		public synchronized int getSizeMax() { return sizeMax; }
		
		/**
		* Mutator for {@link #sizeMax}.
		* <p>
		* The sizeMax param may be smaller in order to save memory (e.g. sizeMax = 0 suppresses the cache entirely),
		* or larger in order to get better cache coverage for higher performance.
		* <p>
		* <b>Side effect:</b> always calls {@link #clear clear}.
		* <p>
		* @throws IllegalArgumentException if sizeMax < 0
		*/
		public synchronized void setSizeMax(int sizeMax) throws IllegalArgumentException {
			Check.arg().notNegative(sizeMax);
			
			this.sizeMax = sizeMax;
			clear();
		}
		
		/**
		* Clears every Date <--> String mapping and resets all the counting fields back to 0.
		* Dereferencing the mappings helps the objects involved to be garbage collected.
		*/
		public synchronized void clear() {
			dateToString.clear();
			stringToDate.clear();
			
			numberFormatHits.set(0);
			numberFormatMisses.set(0);
			numberFormatPutFails.set(0);
			
			numberParseHits.set(0);
			numberParseMisses.set(0);
			numberParsePutFails.set(0);
		}
		
		/**
		* Returns the string which is the correct format of date.
		* <p>
		* A check if this instance already knows the format of date is first performed,
		* and is immediately returned if so.
		* <p>
		* Otherwise, date is formated and returned.
		* Before return, the date <--> string mapping is put into the cache
		* if there is available space (i.e. the cache has < {@link #sizeMax} mappings).
		* <p>
		* @throws IllegalArgumentException if date == null
		*/
		public String format(Date date) throws IllegalArgumentException {
			Check.arg().notNull(date);
			
			String string = dateToString.get(date);
			if (string == null) {
				numberFormatMisses.incrementAndGet();
				string = dateFormatPerThread.get().format(date);
				put(date, false, string, numberFormatPutFails);
			}
			else {
				numberFormatHits.incrementAndGet();
			}
			
			return string;
		}
		
		/**
		* Returns the date which is the correct parse of string.
		* <p>
		* A check if this instance already knows the parse of string is first performed,
		* and is immediately returned if so.
		* <p>
		* Otherwise, string is parsed and returned.
		* Before return, the date <--> string mapping is put into the cache
		* if there is available space (i.e. the cache has < {@link #sizeMax} mappings).
		* <p>
		* @throws IllegalArgumentException if string is blank
		* @throws ParseException if string must be parsed but is malformed
		*/
		public Date parse(String string) throws IllegalArgumentException, ParseException {
			Check.arg().notBlank(string);
			
			Date date = stringToDate.get(string);
			if (date == null) {
				numberParseMisses.incrementAndGet();
				date = dateFormatPerThread.get().parse(string);
				put(date, true, string, numberParsePutFails);
			}
			else {
				numberParseHits.incrementAndGet();
			}
//			return new Date( date.getTime() );	// CRITICAL: see Implementation notes above
return date;
		}
		
		/** Puts the date <--> mapping into the cache. */
		private synchronized void put(Date date, boolean dateWasParsed, String string, AtomicLong putFails) {
			if (dateToString.size() < sizeMax) {
//				date = new Date( date.getTime() );	// CRITICAL: see Implementation notes above
if (!(date instanceof Date2)) date = new Date2( date.getTime() );
				string = StringUtil.newString(string);	// CRITICAL: see Implementation notes above
				
				dateToString.put(date, string);	// see class javadocs: this mapping is always done
				
				if (!stringToDate.containsKey(string)) {	// see class javadocs: this mapping need only be done the first time that string is encountered
					if (!dateWasParsed) {
						try {
							date = dateFormatPerThread.get().parse(string);	// see class javadocs: this parse of string will produce the "fundamental" Date
						}
						catch (Throwable t) { throw ThrowableUtil.toRuntimeException(t); }	// have to throw a RuntimeException, else format will have to declare the checked ParseException, which would cause all the getXXXStamp methods to do likewise which is a real pain...
					}
					stringToDate.put(string, date);
				}
			}
			else {
				putFails.incrementAndGet();
			}
			assert (dateToString.size() >= stringToDate.size()) : "dateToString.size() = " + dateToString.size() + " < stringToDate.size() = " + stringToDate.size();
		}
		
		/**
		* Returns a description of any issues which this instance has encountered.
		* <p>
		* Contract: the result is never null, but will be an empty String if there are no isssues.
		* If non-empty, then the result always ends with a newline.
		*/
		public synchronized String getIssues() {
			StringBuilder sb = new StringBuilder();
			
			double hits = numberFormatHits.doubleValue();
			double misses = numberFormatMisses.doubleValue() - numberFormatPutFails.doubleValue();
			double formatCacheUse = hits / (hits + misses);
			if (formatCacheUse < 0.5) {
				sb.append("DATES MAY BE TOO IRREGULAR FOR FORMAT CACHING TO BE EFFECTIVE: formatCacheUse = numberFormatHits / totalNumberFormats = ").append( formatCacheUse ).append(" < 0.5").append('\n');
			}
			
			hits = numberParseHits.doubleValue();
			misses = numberParseMisses.doubleValue() - numberParsePutFails.doubleValue();
			double parseCacheUse = hits / (hits + misses);
			if (parseCacheUse < 0.5) {
				sb.append("DATES MAY BE TOO IRREGULAR FOR PARSE CACHING TO BE EFFECTIVE: parseCacheUse = numberParseHits / totalParseFormats = ").append( parseCacheUse ).append(" < 0.5").append('\n');
			}
			
			if (numberFormatPutFails.get() > 0) {
				sb.append("FORMAT CACHE MAY BE TOO SMALL: numberFormatPutFails = ").append( numberFormatPutFails.get() ).append(" > 0").append('\n');
			}
			
			if (numberParsePutFails.get() > 0) {
				sb.append("PARSE CACHE MAY BE TOO SMALL: numberParsePutFails = ").append( numberParsePutFails.get() ).append(" > 0").append('\n');
			}
			
if (sb.length() == 0) {
	sb.append("Good: there appear to be NO issues with this DateStringCache instance").append('\n');
}
// +++ hmm, am putting this in for now simply to guarantee that some status is given, but may not keep this line long term...
			
			if (sb.length() > 0) {
				sb.append("State of this instance: ").append( toString() ).append('\n');
			}
			
			return sb.toString();
		}
		
		/** Returns a labeled description of all the fields of this instance. */
		public synchronized String toString() {
			return
				"sizeMax = " + sizeMax
				+ ", dateToString.size() = " + dateToString.size()
				+ ", stringToDate.size() = " + stringToDate.size()
				+ ", numberFormatHits = " + numberFormatHits.get()
				+ ", numberFormatMisses = " + numberFormatMisses.get()
				+ ", numberFormatPutFails = " + numberFormatPutFails.get()
				+ ", numberParseHits = " + numberParseHits.get()
				+ ", numberParseMisses = " + numberParseMisses.get()
				+ ", numberParsePutFails = " + numberParsePutFails.get();
		}
		
	}
	
	// -------------------- IsoDateFormat (static inner class) --------------------
	
/*
	**
	* DateFormatSymbols subclass whose only new functionality
	* is that it guarantees to use the {@link #isoEras ISO 8601 Era symbls}.
	*
	public static class Iso8601EraSymbols extends DateFormatSymbols {
		
		** The ISO 8601 Era symbls (i.e. '-' for BC and '+' for AD). *
		private static final String[] isoEras = new String[] {"-", "+"};
		
		private static final long serialVersionUID = 1;
		
		public Iso8601EraSymbols() {
			setEras(isoEras);
		}
		
		public final void setEras(String[] newEras) throws IllegalArgumentException {
			if (!Arrays.equals(newEras, isoEras)) throw new IllegalArgumentException("newEras !equals isoEras");
			
			super.setEras(isoEras);
		}
		
	}
I originally wrote the class above, which can be assigned to a SimpleDateFormat instance,
in order to support ISO 8601 formating/parsing.

It works, however, the parsing performance (as of jdk 1.6_02) is 1000X slower
(see my bug report here: D:\software\java\proposalsQuestionsPostingsEtc\DateFormatSymbolsSubclassBug.java).

That bug alone was a show stopper, but a further issue is that I want the + era designator to be optional
for 4 digit years, as per the ISO 8601 rules, and there is no good way to specify that optionality in SimpleDateFormat.
(If you make it be lenient, it will allow leniency in many other areas that do not want it to be lenient.)

So, had to write the IsoDateFormat class below to get the desired functionality:
*/
	/**
	* Formats/parses dates that are ISO 8601 compliant.
	* <p>
	* An ISO 8601 year date must start with a -/+ char to denote the BC/AD era.
	* Next comes at least 4 digits (zero padded if necessary) that specify the year.
	* For the special case of an AD era year that has exactly 4 digits,
	* the ISO 8601 spec allows the + era prefix to be dropped, as it may then be implicitly understood;
	* this class follows that option, to conform with how people usually write dates.
	* It will correctly parse any ISO 8601 compliant String,
	* even ones that do not follow the above option (i.e. always start with a - or + era char).
	*/
	private static class IsoDateFormat extends SimpleDateFormat {
		
		private static final long serialVersionUID = 1;
		
		private IsoDateFormat(String pattern) throws IllegalArgumentException {
			super(pattern);
			Check.arg().notBlank(pattern);
			if (!pattern.startsWith("Gyyyy-")) throw new IllegalArgumentException("pattern fails to start with Gyyyy- as expected for an ISO 8601 compliant format");
		}
		
		/*
		Implementation notes:
		
		This class essentially uses its superclass to do all the work,
		it just intercepts the format/parse calls to its superclass and does the era token replacement.
		*/
		
		//public String format(Date date) {	// cannot override format(Date) because it is final, so override the other version:
		public StringBuffer format(Date date, StringBuffer sb, FieldPosition pos) {
			super.format(date, sb, pos);
			if ((sb.charAt(0) == 'A') && (sb.charAt(1) == 'D')) {
				int i = sb.indexOf("-", 2);
				if (i == -1) throw new IllegalStateException("sb = " + sb.toString() + " starts with AD, but fails to contain a subsequent hyphen ('-') char");
				if (i < 6) throw new IllegalStateException("sb = " + sb.toString() + " starts with AD, but appears to contain fewer than 4 year chars");
				else if (i == 6) return sb.replace(0, 2, "");	// i.e. looks like there are exactly 4 year chars, so can drop the era
				else return sb.replace(0, 2, "+");	// i.e. looks like there are > 4 year chars, so must retain the era
			}
			else if ((sb.charAt(0) == 'B') && (sb.charAt(1) == 'C')) {
				return sb.replace(0, 2, "-");
			}
			else throw new IllegalStateException("sb = " + sb.toString() + " fails to start with one of the expected era tokens (AD or BC)");
		}
		// Note: I was originally worried that since the code above changes the length of sb via the replace calls)
		// that it might cause a subtle bug with respect to the FieldPosition pos param.
		// However, if look at the source code of DateFormat.format(Date) as of jdk 1.6_02,
		// you can see that that method creates a new StringBuffer and a DontCareFieldPosition.INSTANCE
		// so it looks like that code is pretty safe...
		
		public Date parse(String s) throws ParseException {
			if (s.startsWith("+")) {
				return super.parse("AD" + s.substring(1));
			}
			else if (s.startsWith("-")) {
				return super.parse("BC" + s.substring(1));
			}
			else {
				return super.parse("AD" + s);
			}
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final Date dateMin = new Date(Long.MIN_VALUE);
		private static final Date dateMax = new Date(Long.MAX_VALUE);
		
		private static final Date date333BC;
		
		private static final Date dateEpoch = new Date(0);
		
		private static final Date date1994;
		
		private static final Date date2004;
		private static final Date date2004_6;	// i.e 2004-06-01 00:00:00
		private static final Date date2004_11_28;
		private static final Date date2004_11_30;
		private static final Date date2004_12_01;
		private static final Date date2004_12_24;
		private static final Date date2004_12_30;
		private static final Date date2004_12_31;
		private static final Date date2004_12_31_23;	// i.e 2004-12-31 23:00:00
		private static final Date date2004_12_31_23_59;	// i.e 2004-12-31 23:59:00
		private static final Date date2004_12_31_23_59_59;	// i.e 2004-12-31 23:59:59
		private static final Date date2004_12_31_23_59_59_999;	// i.e 2004-12-31 23:59:59:999
		
		private static final Date date2005;
		private static final Date date2005_01_02;
		private static final Date date2005_12_31;
		
		private static final Date date2006_12_31;
		
		private static final Date date2014;
		
		private static final Date date2114;
		
		private static final Date date12345_12_31_23_59_59_999;	// i.e 12345-12-31 23:59:59:999
		
		private static final Date dateFriday;
		private static final Date dateSaturday;
		private static final Date dateSunday;
		
		private static final Date dateFeb29OfLeapYear;
		private static final Date dayAfterLeapDay;
		
		private static final Date springForwardDay;
		private static final Date fallBackDay;
		
		static {
				// CRITICAL: must set these to guarantee that all of the code below works (especially the leap day and time zone change stuff):
			Locale.setDefault( Locale.US );
			TimeZone.setDefault( TimeZone.getTimeZone("America/New_York") );
			
			Calendar calendar = getCalendar();
			
			calendar.clear();
			calendar.set(Calendar.ERA, GregorianCalendar.BC);
			calendar.set(Calendar.YEAR, 333);
			date333BC = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 1994);
			date1994 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			date2004 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 5);	// NOTE: months have a 0 offset, so 5 is June
			date2004_6 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 10);	// NOTE: months have a 0 offset, so 10 is November
			calendar.set(Calendar.DAY_OF_MONTH, 28);
			date2004_11_28 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 10);	// NOTE: months have a 0 offset, so 10 is November
			calendar.set(Calendar.DAY_OF_MONTH, 30);
			date2004_11_30 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 1);
			date2004_12_01 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 24);
			date2004_12_24 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 30);
			date2004_12_30 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			date2004_12_31 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			date2004_12_31_23 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			calendar.set(Calendar.MINUTE, 59);
			date2004_12_31_23_59 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			calendar.set(Calendar.MINUTE, 59);
			calendar.set(Calendar.SECOND, 59);
			date2004_12_31_23_59_59 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2004);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			calendar.set(Calendar.MINUTE, 59);
			calendar.set(Calendar.SECOND, 59);
			calendar.set(Calendar.MILLISECOND, 999);
			date2004_12_31_23_59_59_999 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2005);
			date2005 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2005);
			calendar.set(Calendar.MONTH, 0);	// NOTE: months have a 0 offset, so 0 is January
			calendar.set(Calendar.DAY_OF_MONTH, 2);
			date2005_01_02 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2005);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			date2005_12_31 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2006);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			date2006_12_31 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2014);
			date2014 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 2114);
			date2114 = calendar.getTime();
			
			calendar.clear();
			calendar.set(Calendar.YEAR, 12345);
			calendar.set(Calendar.MONTH, 11);	// NOTE: months have a 0 offset, so 11 is December
			calendar.set(Calendar.DAY_OF_MONTH, 31);
			calendar.set(Calendar.HOUR_OF_DAY, 23);
			calendar.set(Calendar.MINUTE, 59);
			calendar.set(Calendar.SECOND, 59);
			calendar.set(Calendar.MILLISECOND, 999);
			date12345_12_31_23_59_59_999 = calendar.getTime();
			
			dateFriday = date2004_12_31;
			dateSaturday = date2005;
			dateSunday = date2005_01_02;
			
			calendar.clear();
			calendar.set(2004, 1, 29);	// 2004 is a leap year, so Feb 29th really exists
			dateFeb29OfLeapYear = calendar.getTime();
			
			calendar.clear();
			calendar.set(2004, 2, 1);
			dayAfterLeapDay = calendar.getTime();
			
			calendar.clear();
			calendar.set(2005, 3, 3);	// 2005/April/3 is a spring forward day
			springForwardDay = calendar.getTime();
			
			calendar.clear();
			calendar.set(2005, 9, 30);	// 2005/October/30 is a fall back day
			fallBackDay = calendar.getTime();
		}
		
		/**
		* Result of running this method on 2004/9/27: dateAfterLeapSecondFirst.getTime() % TimeLength.day = 0
		* which means that my machine did NOT take a leap second into account?
		*/
		@Test public void test_calendarLeapSecondBehavior() throws Exception {
			DateFormat formatGmt = new SimpleDateFormat(timeStampPattern);	// need our own special SimpleDateFormat instance which uses the GMT+0 time zone
			formatGmt.setTimeZone( TimeZone.getTimeZone("GMT+0") );
			
			Calendar calendar = (Calendar) getCalendar().clone();	// use clone because do not want the time zone change below to affect future calls to getCalendar
			calendar.setTimeZone( TimeZone.getTimeZone("GMT+0") );
			calendar.clear();
			calendar.set(1972, 6, 1, 0, 0);	// NOTE: months have a 0 offset, so this date is 1972/July/1 00:00:00:000; the first leap second was on 1972/6/1 23:59:60:000 (note that the second field is 60 not 59)
			Date dateAfterLeapSecondFirst = calendar.getTime();
			System.out.println("dateAfterLeapSecondFirst = " + formatGmt.format(dateAfterLeapSecondFirst));
			
			long remainder = dateAfterLeapSecondFirst.getTime() % TimeLength.day;
			if (remainder == 0) {
				System.out.println("This computer does NOT appear to account for leap seconds, since dateAfterLeapSecondFirst is evenly divisible by TimeLength.day");
			}
			else if (remainder == TimeLength.second) {
				System.out.println("This computer appears to account for leap seconds, since dateAfterLeapSecondFirst, after dividing by TimeLength.day, has a remainder of 1 (i.e. the first leap second, which happens to be positive");
			}
			else {
				System.err.println("THIS COMPUTER APPEARS TO ACCOUNT FOR LEAP SECONDS IN A VERY BIZARRE WAY: dateAfterLeapSecondFirst, after dividing by TimeLength.day, has a remainder of " + remainder + " (it should be either 0 if leap seconds unaccounted for, or " + TimeLength.second + " ms since the first leap second is positive");
			}
		}
		
		@Test public void test_isXXX() throws Exception {
			Assert.assertTrue( isSameCenturyOfMillenia(date2004, date2014) );
			Assert.assertFalse( isSameCenturyOfMillenia(date1994, date2014) );
			
			Assert.assertTrue( isSameDecadeOfCentury(date2004, date2005) );
			Assert.assertFalse( isSameDecadeOfCentury(date2004, date2014) );
			
			Assert.assertTrue( isSameYear(date2004, date2004_6) );
			Assert.assertFalse( isSameYear(date2004, date2005) );
			
			Assert.assertTrue( isDayOfYearLast(date2004_12_31) );
			Assert.assertFalse( isDayOfYearLast(date2004) );
			
			Assert.assertTrue( isSameDayOfYear(date2005_12_31, date2006_12_31) );
			Assert.assertFalse( isSameDayOfYear(date2004_12_31, date2005_12_31) );
			
			Assert.assertTrue( isSameMonth(date2004, date2005) );
			Assert.assertFalse( isSameMonth(date2004, date2004_6) );
			
			Assert.assertTrue( isDayOfMonthLast(date2004_12_31) );
			Assert.assertFalse( isDayOfMonthLast(date2004) );
			
			Assert.assertTrue( isSameDayOfMonth(date2004_12_31, date2005_12_31) );
			Assert.assertFalse( isSameDayOfMonth(date2004_12_30, date2004_12_31) );
			
			Assert.assertTrue( isExactWeek(date2004_12_30, date2004_12_31) );
			Assert.assertFalse( isExactWeek(date2004_12_31, date2005_12_31) );
			
			Assert.assertTrue( isSameWeekOfYear(date2004_12_30, date2004_12_31) );
			Assert.assertFalse( isSameWeekOfYear(date2004_12_24, date2004_12_31) );
			
			Assert.assertTrue( isSameWeekOfMonth(date2004_11_30, date2005_12_31) );
			Assert.assertFalse( isSameWeekOfMonth(date2004_12_24, date2004_12_31) );
			
			Assert.assertTrue( isExactDay(date2004_12_31, date2004_12_31_23_59_59) );
			Assert.assertFalse( isExactDay(date2004_12_24, date2004_12_31) );
			
			Assert.assertTrue( isSameDayOfWeek(date2004_12_24, date2004_12_31) );
			Assert.assertFalse( isSameDayOfWeek(date2004_12_31, date2005_12_31) );
			
			Assert.assertTrue( isWeekDay(dateFriday) );
			Assert.assertFalse( isWeekDay(dateSaturday) );
			
			Assert.assertTrue( isWeekEnd(dateSaturday) );
			Assert.assertFalse( isWeekEnd(dateFriday) );
			
			Assert.assertTrue( isWithinDays(dateFriday, dateSaturday, 1) );
			Assert.assertFalse( isWithinDays(dateFriday, dateSunday, 1) );
			
			Assert.assertTrue( isLeapDay(dateFeb29OfLeapYear) );
			Assert.assertFalse( isLeapDay(dayAfterLeapDay) );
			
			Assert.assertTrue( isSameHourOfDay(date2004, date2005) );
			Assert.assertFalse( isSameHourOfDay(date2004_12_31, date2004_12_31_23) );
			
			Assert.assertTrue( isSameMinuteOfHour(date2004, date2004_12_31_23) );
			Assert.assertFalse( isSameMinuteOfHour(date2004_12_31_23, date2004_12_31_23_59) );
			
			Assert.assertTrue( isSameSecondOfMinute(date2004, date2004_12_31_23_59) );
			Assert.assertFalse( isSameSecondOfMinute(date2004_12_31_23_59, date2004_12_31_23_59_59) );
		}
		
		/**
		* Results on 2010-03-11 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_18 server jvm):
		* <pre><code>
			n = 16 * 1024
				isExactDay with ZERO cache: first = 8.296 us, mean = 411.885 ns (CI deltas: -82.962 ps, +128.228 ps), sd = 823.586 ns (CI deltas: -252.314 ns, +573.272 ns) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
				
				isExactDay when cache has ~2 DateInfo per bin (suboptimal): first = 50.626 us, mean = 141.782 ns (CI deltas: -137.156 ps, +256.614 ps), sd = 2.082 us (CI deltas: -863.881 ns, +1.037 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
				**********
				The following issues were detected with the caching inside DateUtil:
				DateInfoCache:
				numberDateInfos = 16384
				numberBinsWithInfos = 8192
				numberBinsOverloaded = 8192
				fraction of overloaded Bins (numberBinsOverloaded / numberBinsWithInfos) = 1.0
				average number of DateInfos per DateInfoBin (considering only Bins with DateInfos) = 2.0
				maxInfosInABin = 2
				
				isExactDay when cache has ~1 DateInfo per bin (optimal): first = 25.749 us, mean = 127.110 ns (CI deltas: -47.740 ps, +98.323 ps), sd = 749.611 ns (CI deltas: -395.588 ns, +488.907 ns) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
		* </code></pre>		
<!--
+++ The above results when cache is non-zero may be higher than the intrinsic cpu required
because of cache effects; see comment in benchmark_parseTimeStamp below for additional discussion.
-->
		*/
		@Test public void benchmark_isSameDayOfWeek() {
			class IsSameDayRun implements Runnable {
				private final Date[] dates;
				private boolean state;	// needed to prevent DCE since this is a Runnable
				
				private IsSameDayRun(int n, int m) {
					dates = new Date[n];
					for (int i = 0; i < dates.length; i++) {
						long day = ((long) i) * ((long) m) * TimeLength.day;
						long timeOfDay = (i * TimeLength.second) % TimeLength.day;
						dates[i] = new Date( day + timeOfDay );
					}
				}
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				@Override public void run() {
					for (int i = 0; i < dates.length - 1; i++) {
						state ^= isSameDayOfWeek(dates[i], dates[i+1]);
					}
				}
			}
			
			int n = 16 * 1024;
			
			setDateInfoCacheSizeMax(0);
			Runnable task_cache0Per = new IsSameDayRun(n, 1);
			System.out.println();
			System.out.println("isExactDay with ZERO cache: " + new Benchmark(task_cache0Per, n - 1));	// CRITICAL: supply n - 1 and not n because n is the number of Dates, of which there are n - 1 calls to isSameDay made
			System.out.println(getCacheIssues());
			
			setDateInfoCacheSizeMax(n);
			Runnable task_cache2Per = new IsSameDayRun(n, 2);	// the 2, by doubling the spacing between dates to 2 days, should cause the cache to contain 2 DateInfos per bin because now the day number is always even
			System.out.println();
			System.out.println("isExactDay when cache has ~2 DateInfo per bin (suboptimal): " + new Benchmark(task_cache2Per, n - 1));	// CRITICAL: supply n - 1 and not n because n is the number of Dates, of which there are n - 1 calls to isSameDay made
			System.out.println(getCacheIssues());
			
			setDateInfoCacheSizeMax(n);	// should cause the cache to easily contain one DateInfo per bin
			Runnable task_cache1Per = new IsSameDayRun(n, 1);
			System.out.println();
			System.out.println("isExactDay when cache has ~1 DateInfo per bin (optimal): " + new Benchmark(task_cache1Per, n - 1));	// CRITICAL: supply n - 1 and not n because n is the number of Dates, of which there are n - 1 calls to isSameDay made
			System.out.println(getCacheIssues());
			
			setDateInfoCacheSizeMax(32 * 1024);	// restores the normal behavior
		}
		
		@Test public void test_getXXX_pass() throws Exception {
			Assert.assertEquals( GregorianCalendar.BC, getEra(date333BC) );
			Assert.assertEquals( GregorianCalendar.AD, getEra(date2004) );
			
			Assert.assertEquals( 0, getCenturyOfMillenia(date2004) );
			Assert.assertEquals( 1, getCenturyOfMillenia(date2114) );
			
			Assert.assertEquals( 0, getDecadeOfCentury(date2004) );
			Assert.assertEquals( 1, getDecadeOfCentury(date2014) );
			
			Assert.assertEquals( 2004, getYear(date2004) );
			
			Assert.assertEquals( date2004, getYearStart(date2004_12_31) );
			Assert.assertEquals( date2004_12_31_23_59_59_999, getYearEnd(date2004_6) );
			
			Assert.assertEquals( date2005_12_31, getSameTimeNextYear(date2004_12_31) );
			Assert.assertEquals( date2004_12_31, getSameTimePreviousYear(date2005_12_31) );
			Assert.assertEquals( date2014, getSameTimeOtherYear(date2004, 10) );
			Assert.assertEquals( parseTimeStamp("2003-02-28T00:00:00.000-0500"), getSameTimeOtherYear(dateFeb29OfLeapYear, -1) );
			
			Assert.assertEquals( 11, getMonth(date2004_12_31) );
			Assert.assertEquals( date2004_12_01, getMonthStart(date2004_12_31) );
			Assert.assertEquals( date2004_12_31_23_59_59_999, getMonthEnd(date2004_12_01) );
			Assert.assertEquals( date2004_12_30, getSameTimeNextMonth(date2004_11_30) );
			Assert.assertEquals( date2004_11_30, getSameTimePreviousMonth(date2004_12_30) );
			Assert.assertEquals( date2005, getSameTimeOtherMonth(date2004, 12) );
			Assert.assertEquals( parseTimeStamp("2004-11-30T00:00:00.000-0500"), getSameTimeOtherMonth(date2004_12_31, -1) );
			
			Assert.assertEquals( 1, getWeekOfYear(date2004) );
			Assert.assertEquals( 23, getWeekOfYear(date2004_6) );
			Assert.assertEquals( 49, getWeekOfYear(date2004_11_30) );
			Assert.assertEquals( 52, getWeekOfYear(date2004_12_24) );
			Assert.assertEquals( 1, getWeekOfYear(date2004_12_30) );
			
			Assert.assertEquals( 5, getWeekOfMonth(date2004_12_30) );
			Assert.assertEquals( date2004_11_28, getWeekStart(date2004_11_30) );
			Assert.assertEquals( date2004_12_31, getSameTimeNextWeek(date2004_12_24) );
			Assert.assertEquals( date2004_12_24, getSameTimePreviousWeek(date2004_12_31) );
			Assert.assertEquals( date2004_12_30, getSameTimeOtherWeek(date2004, 52) );
			
			Assert.assertEquals( 1, getDayOfYear(date2004) );
			Assert.assertEquals( 366, getDayOfYear(date2004_12_31) );
			Assert.assertEquals( 1, getDayOfMonth(date2004) );
			Assert.assertEquals( 31, getDayOfMonth(date2004_12_31) );
			Assert.assertEquals( 5, getDayOfWeek(date2004) );
			Assert.assertEquals( 6, getDayOfWeek(date2004_12_31) );
			
			Assert.assertEquals( date2004_12_31, getDayStart(date2004_12_31_23_59_59_999) );
			Assert.assertEquals( date2004_12_31_23_59_59_999, getDayEnd(date2004_12_31) );
			
			long tod = 0;
			Assert.assertEquals( tod, getTimeOfDay(date2004_12_31) );
			tod += (23 * TimeLength.hour);
			Assert.assertEquals( tod, getTimeOfDay(date2004_12_31_23) );
			tod += (59 * TimeLength.minute);
			Assert.assertEquals( tod, getTimeOfDay(date2004_12_31_23_59) );
			tod += (59 * TimeLength.second);
			Assert.assertEquals( tod, getTimeOfDay(date2004_12_31_23_59_59) );
			tod += 999;
			Assert.assertEquals( tod, getTimeOfDay(date2004_12_31_23_59_59_999) );
			tod = (1 * TimeLength.hour) + (59 * TimeLength.minute) + (59 * TimeLength.second) + 999;	// 1 ms before the TZ change
			Assert.assertEquals( tod, getTimeOfDay( new Date(springForwardDay.getTime() + tod) ) );
			tod += 1;	// tod is now the exact moment of the TZ change
			Assert.assertEquals( tod + TimeLength.hour, getTimeOfDay( new Date(springForwardDay.getTime() + tod) ) );
			tod = (1 * TimeLength.hour) + (59 * TimeLength.minute) + (59 * TimeLength.second) + 999;	// 1 ms before the TZ change
			Assert.assertEquals( tod, getTimeOfDay( new Date(fallBackDay.getTime() + tod) ) );
			tod += 1;	// tod is now the exact moment of the TZ change
			Assert.assertEquals( tod - TimeLength.hour, getTimeOfDay( new Date(fallBackDay.getTime() + tod) ) );
			
			Assert.assertEquals( TimeLength.day, getDayLength(date2004) );
			Assert.assertEquals( TimeLength.dayTzChPos, getDayLength(springForwardDay) );
			Assert.assertEquals( TimeLength.dayTzChNeg, getDayLength(fallBackDay) );
			
			Assert.assertEquals( date2004_12_31, getSameTimeNextDay(date2004_12_30) );
			Assert.assertEquals( date2004_12_30, getSameTimePreviousDay(date2004_12_31) );
			Assert.assertEquals( date2004_12_30, getSameTimeOtherDay(date2004, 364) );
			
			Assert.assertEquals( date2004_12_31, get24HoursLater(date2004_12_30) );
			
			Assert.assertEquals( 1, getAmountTimeZoneChange(springForwardDay) );
			Assert.assertEquals( -1, getAmountTimeZoneChange(fallBackDay) );
			
			// Concerning testing getLeapSecond(Date date):
			// most operating systems do not seem to support leap seconds; see test_calendarLeapSecondBehavior for my investigation
			
			Assert.assertEquals( 23, getHourOfDay(date2004_12_31_23) );
			Assert.assertEquals( date2004_12_31_23, getHourStart(date2004_12_31_23_59) );
			Assert.assertEquals( 59, getMinuteOfHour(date2004_12_31_23_59) );
			Assert.assertEquals( 59, getSecondOfMinute(date2004_12_31_23_59_59) );
			Assert.assertEquals( 999, getMilliSecondOfSecond(date2004_12_31_23_59_59_999) );
			
			Assert.assertEquals( "Sunday", getDayOfWeekName(Calendar.SUNDAY) );
			Assert.assertEquals( "Monday", getDayOfWeekName(Calendar.MONDAY) );
			Assert.assertEquals( "Tuesday", getDayOfWeekName(Calendar.TUESDAY) );
			Assert.assertEquals( "Wednesday", getDayOfWeekName(Calendar.WEDNESDAY) );
			Assert.assertEquals( "Thursday", getDayOfWeekName(Calendar.THURSDAY) );
			Assert.assertEquals( "Friday", getDayOfWeekName(Calendar.FRIDAY) );
			Assert.assertEquals( "Saturday", getDayOfWeekName(Calendar.SATURDAY) );
		}
		
		@Test(expected=AssertionError.class) public void test_getXXX_fail1() throws Exception {
			getDayStart(dateMin);
		}
			
		@Test(expected=AssertionError.class) public void test_getXXX_fail2() throws Exception {
			getDayEnd(dateMax);
		}
		
		@Test public void test_selfConsistencyOfManyMethods() throws Exception {
			Random r = new Random();
			for (int i = 0; i < 100 * 1000; i++) {
				Date date = new Date( r.nextLong() );
				String dateText = getTimeStamp(date);
				
				Date yearStart = getYearStart(date);
				Assert.assertTrue( dateText, isSameYear(date, yearStart) );
				Assert.assertTrue( dateText, yearStart.compareTo(date) <= 0 );
				String yearStartText = getTimeStamp(yearStart);
				Assert.assertTrue( dateText, yearStartText.startsWith(dateText.substring(0, 5)) );
				Assert.assertTrue( dateText, yearStartText.contains("00:00:00.000") );
				Date yearBeforeEnd = new Date(yearStart.getTime() - 1);
				Assert.assertFalse( dateText, isSameYear(yearBeforeEnd, yearStart) );
				
				Date monthStart = getMonthStart(date);
				Assert.assertTrue( dateText, isSameMonth(date, monthStart) );
				Assert.assertTrue( dateText, monthStart.compareTo(date) <= 0 );
				String monthStartText = getTimeStamp(monthStart);
				Assert.assertTrue( dateText, monthStartText.startsWith(dateText.substring(0, 8)) );
				Assert.assertTrue( dateText, monthStartText.contains("00:00:00.000") );
				Date monthBeforeEnd = new Date(monthStart.getTime() - 1);
				Assert.assertFalse( dateText, isSameMonth(monthBeforeEnd, monthStart) );
				
				Date weekStart = getWeekStart(date);
				Assert.assertTrue( dateText, isExactWeek(date, weekStart) );
				Assert.assertTrue( dateText, weekStart.compareTo(date) <= 0 );
				String weekStartText = getTimeStamp(weekStart);
				if (getDayOfMonth(date) > 7) {
					Assert.assertTrue( dateText, weekStartText.startsWith(dateText.substring(0, 8)) );
				}
				else if (isSameYear(date, weekStart)) {
					Assert.assertTrue( dateText, weekStartText.startsWith(dateText.substring(0, 5)) );
				}
				Assert.assertTrue( dateText, weekStartText.contains("00:00:00.000") );
				Date weekBeforeEnd = new Date(weekStart.getTime() - 1);
				Assert.assertFalse( dateText, isExactWeek(weekBeforeEnd, weekStart) );
				
				Date dayStart = getDayStart(date);
				Assert.assertTrue( dateText, isExactDay(date, dayStart) );
				Assert.assertTrue( dateText, dayStart.compareTo(date) <= 0 );
				String dayStartText = getTimeStamp(dayStart);
				Assert.assertTrue( dateText, dayStartText.startsWith(dateText.substring(0, 11)) );
				Assert.assertTrue( dateText, dayStartText.contains("00:00:00.000") );
				Date dayBeforeEnd = new Date(dayStart.getTime() - 1);
				Assert.assertFalse( dateText, isExactDay(dayBeforeEnd, dayStart) );
				
				Date hourStart = getHourStart(date);
				Assert.assertTrue( dateText, isSameHourOfDay(date, hourStart) );
				int tzc = getAmountTimeZoneChange(date);
				if (tzc == 0) {
					Assert.assertTrue( dateText, hourStart.compareTo(date) <= 0 );
					Assert.assertTrue( dateText, isSameHourOfDay(date, hourStart) );
				}
				String hourStartText = getTimeStamp(hourStart);
				Assert.assertTrue( dateText, hourStartText.startsWith(dateText.substring(0, 14)) );
				Assert.assertTrue( dateText, hourStartText.contains("00:00.000") );
				Date hourBeforeEnd = new Date(hourStart.getTime() - 1);
				if (tzc == 0) Assert.assertFalse( dateText, isSameHourOfDay(hourBeforeEnd, hourStart) );
			}
		}
		
		/**
		* Results on 2010-03-11 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_18 server jvm):
		* <pre><code>
			n = 16 * 1024
				getDayStart with ZERO cache: first = 12.227 us, mean = 1.028 us (CI deltas: -254.421 ps, +341.703 ps), sd = 1.188 us (CI deltas: -283.974 ns, +447.445 ns) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
				getDayStart with a perfectly sized cache: first = 30.391 us, mean = 71.381 ns (CI deltas: -62.114 ps, +65.987 ps), sd = 1.039 us (CI deltas: -143.734 ns, +196.449 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
<!--
+++ The above results when cache is non-zero may be higher than the intrinsic cpu required
because of cache effects; see comment in benchmark_parseTimeStamp below for additional discussion.
-->
		*/
		@Test public void benchmark_getDayStart() {
			class GetDayStartRun implements Runnable {
				private final Date[] dates;
				private long state;	// needed to prevent DCE since this is a Runnable
				
				private GetDayStartRun(int n) {
					dates = new Date[n];
					for (int i = 0; i < dates.length; i++) {
						long day = i * TimeLength.day;
						long timeOfDay = (i * TimeLength.second) % TimeLength.day;
						dates[i] = new Date( day + timeOfDay );
					}
				}
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				@Override public void run() {
					for (Date date : dates) {
						state ^= getDayStart(date).getTime();
					}
				}
			}
			
			int n = 16 * 1024;
			
			GetDayStartRun task = new GetDayStartRun(n);
			
			setDateInfoCacheSizeMax(0);
			System.out.println();
			System.out.println("getDayStart with ZERO cache: " + new Benchmark(task, n));
			System.out.println(getCacheIssues());
			
			setDateInfoCacheSizeMax(n);
			GetDayStartRun task_cacheBig = new GetDayStartRun(n);
			System.out.println();
			System.out.println("getDayStart with a perfectly sized cache: " + new Benchmark(task, n));
			System.out.println(getCacheIssues());
			
			setDateInfoCacheSizeMax(32 * 1024);	// restores the normal behavior
		}
		
		@Test public void test_getXXXStamp() throws Exception {
			System.out.println("getDayStamp() = " + getDayStamp());
			Assert.assertEquals( "-0333-01-01", getDayStamp(date333BC) );
			Assert.assertEquals( "2004-12-31", getDayStamp(date2004_12_31_23_59_59_999) );
			
			System.out.println("getTimeOfDayStamp() = " + getTimeOfDayStamp());
			long tod = 0;
			Assert.assertEquals( "00:00:00.000", getTimeOfDayStamp( new Date(date2004_12_31.getTime() + tod) ) );
			Assert.assertEquals( "00:00:00.000", getTimeOfDayStamp( tod ) );
			tod += (23 * TimeLength.hour);
			Assert.assertEquals( "23:00:00.000", getTimeOfDayStamp( new Date(date2004_12_31.getTime() + tod) ) );
			Assert.assertEquals( "23:00:00.000", getTimeOfDayStamp( tod ) );
			tod += (59 * TimeLength.minute);
			Assert.assertEquals( "23:59:00.000", getTimeOfDayStamp( new Date(date2004_12_31.getTime() + tod) ) );
			Assert.assertEquals( "23:59:00.000", getTimeOfDayStamp( tod ) );
			tod += (59 * TimeLength.second);
			Assert.assertEquals( "23:59:59.000", getTimeOfDayStamp( new Date(date2004_12_31.getTime() + tod) ) );
			Assert.assertEquals( "23:59:59.000", getTimeOfDayStamp( tod ) );
			tod += 999;
			Assert.assertEquals( "23:59:59.999", getTimeOfDayStamp( new Date(date2004_12_31.getTime() + tod) ) );
			Assert.assertEquals( "23:59:59.999", getTimeOfDayStamp( tod ) );
			tod = (1 * TimeLength.hour) + (59 * TimeLength.minute) + (59 * TimeLength.second) + 999;	// 1 ms before the TZ change
			Assert.assertEquals( "01:59:59.999", getTimeOfDayStamp( new Date(springForwardDay.getTime() + tod) ) );
			tod += 1;	// tod is now the exact moment of the TZ change
			Assert.assertEquals( "03:00:00.000", getTimeOfDayStamp( new Date(springForwardDay.getTime() + tod) ) );
			tod = (1 * TimeLength.hour) + (59 * TimeLength.minute) + (59 * TimeLength.second) + 999;	// 1 ms before the TZ change
			Assert.assertEquals( "01:59:59.999", getTimeOfDayStamp( new Date(fallBackDay.getTime() + tod) ) );
			tod += 1;	// tod is now the exact moment of the TZ change
			Assert.assertEquals( "01:00:00.000", getTimeOfDayStamp( new Date(fallBackDay.getTime() + tod) ) );
			
			long tod1 = (11*TimeLength.hour) + (22*TimeLength.minute) + (33*TimeLength.second) + 444;
			Assert.assertEquals( "11:22:33.444", getTimeOfDayStampConcise(tod1) );
			long tod2 = (11*TimeLength.hour) + (22*TimeLength.minute) + (33*TimeLength.second);
			Assert.assertEquals( "11:22:33", getTimeOfDayStampConcise(tod2) );
			long tod3 = (11*TimeLength.hour) + (22*TimeLength.minute);
			Assert.assertEquals( "11:22", getTimeOfDayStampConcise(tod3) );
			
			System.out.println("getTimeOfDayStampForFile() = " + getTimeOfDayStampForFile());
			Assert.assertEquals( "23-59-59.999", getTimeOfDayStampForFile(date2004_12_31_23_59_59_999) );
			
			System.out.println("getTimeStamp() = " + getTimeStamp());
			Assert.assertEquals( "-0333-01-01T00:00:00.000-0500", getTimeStamp(date333BC) );
			Assert.assertEquals( "2004-12-31T23:59:59.999-0500", getTimeStamp(date2004_12_31_23_59_59_999) );
			
			System.out.println("getTimeStampConcise(date2004_6) = " + getTimeStampConcise(date2004_6));
			Assert.assertEquals( "+12345-12-31T23:59:59.999", getTimeStampConcise(date12345_12_31_23_59_59_999) );
			Assert.assertEquals( "2004-12-31T23:59:59.999", getTimeStampConcise(date2004_12_31_23_59_59_999) );
			Assert.assertEquals( "2004-12-31T23:59:59", getTimeStampConcise(date2004_12_31_23_59_59) );
			Assert.assertEquals( "2004-12-31T23:59", getTimeStampConcise(date2004_12_31_23_59) );
			Assert.assertEquals( "2004-12-31T23", getTimeStampConcise(date2004_12_31_23) );
			Assert.assertEquals( "2004-12-31", getTimeStampConcise(date2004_12_31) );
			Assert.assertEquals( "2004-12", getTimeStampConcise(date2004_12_01) );
			Assert.assertEquals( "2004", getTimeStampConcise(date2004) );
			Assert.assertEquals( "-0333", getTimeStampConcise(date333BC) );
			
			System.out.println("getTimeStampForFile() = " + getTimeStampForFile());
			Assert.assertEquals( "-0333-01-01T00-00-00.000-0500", getTimeStampForFile(date333BC) );
			Assert.assertEquals( "2004-12-31T23-59-59.999-0500", getTimeStampForFile(date2004_12_31_23_59_59_999) );
		}
		
		/**
		* Results on 2010-03-11 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_18 server jvm):
		* <pre><code>
			n = 16 * 1024
				DateStringCache.format (for timeStampPattern) with ZERO cache: first = 15.570 us, mean = 1.995 us (CI deltas: -2.241 ns, +2.398 ns), sd = 6.630 us (CI deltas: -1.111 us, +1.534 us) WARNING: execution times have mild outliers, execution times may have serial correlation, SD VALUES MAY BE INACCURATE
					FORMAT CACHE MAY BE TOO SMALL: numberFormatPutFails = 40878080 > 0
					State of this instance: sizeMax = 0, dateToString.size() = 0, stringToDate.size() = 0, numberFormatHits = 0, numberFormatMisses = 40878080, numberFormatPutFails = 40878080, numberParseHits = 0, numberParseMisses = 0, numberParsePutFails = 0

				DateStringCache.format (for timeStampPattern) with a perfectly sized cache: first = 21.269 us, mean = 81.715 ns (CI deltas: -66.012 ps, +187.403 ps), sd = 1.652 us (CI deltas: -1.176 us, +1.787 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
					Good: there appear to be NO issues with this DateStringCache instance
					State of this instance: sizeMax = 16384, dateToString.size() = 16384, stringToDate.size() = 16384, numberFormatHits = 1174372352, numberFormatMisses = 16384, numberFormatPutFails = 0, numberParseHits = 0, numberParseMisses = 0, numberParsePutFails = 0
		* </code></pre>
<!--
+++ The above results when cache is non-zero may be higher than the intrinsic cpu required
because of cache effects; see comment in benchmark_parseTimeStamp below for additional discussion.
-->
		*/
		@Test public void benchmark_getTimeStamp() {
			class FormatDate implements Runnable {
				private final DateStringCache dateStringCache;
				private final Date[] dates;
				private int state;	// needed to prevent DCE since this is a Runnable
				
				private FormatDate(int sizeMax, int n) {
					dateStringCache = new DateStringCache(timeStampPattern, sizeMax, true);
					
					dates = new Date[n];
					for (int i = 0; i < dates.length; i++) {
						dates[i] = new Date(i);
					}
				}
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				public void run() {
					for (Date date : dates) {
						String s = dateStringCache.format(date);
						state ^= s.length();
					}
				}
				
				private void printIssues() {
					String s = dateStringCache.getIssues();
					if (s.length() > 0) System.out.print("\t" + s);
				}
			}
			
			int n = 16 * 1024;
			
			FormatDate task_cacheAbsent = new FormatDate(0, n);
			System.out.println();
			System.out.println("DateStringCache.format (for timeStampPattern) with ZERO cache: " + new Benchmark(task_cacheAbsent, n));
			task_cacheAbsent.printIssues();
			
			FormatDate task_cacheBig = new FormatDate(n, n);
			System.out.println();
			System.out.println("DateStringCache.format (for timeStampPattern) with a perfectly sized cache: " + new Benchmark(task_cacheBig, n));
			task_cacheBig.printIssues();
		}
		
		@Test public void test_parseXXXStamp() throws Exception {
			Assert.assertEquals( date1994, parseDayStamp(getDayStamp(date1994)) );
			Assert.assertEquals( date2004, parseDayStamp(getDayStamp(date2004)) );
			Assert.assertEquals( date2004_6, parseDayStamp(getDayStamp(date2004_6)) );
			Assert.assertEquals( date2005, parseDayStamp(getDayStamp(date2005)) );
			Assert.assertEquals( date2014, parseDayStamp(getDayStamp(date2014)) );
			Assert.assertEquals( date2114, parseDayStamp(getDayStamp(date2114)) );
			Assert.assertFalse( parseDayStamp(getDayStamp(date2004_12_31_23)).equals(date2004_12_31_23) );
			
			Assert.assertEquals( date2004, parseTimeStamp(getTimeStamp(date2004)) );
			Assert.assertEquals( date2004_12_31, parseTimeStamp(getTimeStamp(date2004_12_31)) );
			Assert.assertEquals( date2004_12_31_23_59, parseTimeStamp(getTimeStamp(date2004_12_31_23_59)) );
			Assert.assertEquals( date2004_12_31_23_59_59, parseTimeStamp(getTimeStamp(date2004_12_31_23_59_59)) );
			Assert.assertEquals( date2004_12_31_23_59_59_999, parseTimeStamp(getTimeStamp(date2004_12_31_23_59_59_999)) );
			
			Assert.assertEquals( date2004, parseTimeStampForFile(getTimeStampForFile(date2004)) );
			Assert.assertEquals( date2004_12_31, parseTimeStampForFile(getTimeStampForFile(date2004_12_31)) );
			Assert.assertEquals( date2004_12_31_23_59, parseTimeStampForFile(getTimeStampForFile(date2004_12_31_23_59)) );
			Assert.assertEquals( date2004_12_31_23_59_59, parseTimeStampForFile(getTimeStampForFile(date2004_12_31_23_59_59)) );
			Assert.assertEquals( date2004_12_31_23_59_59_999, parseTimeStampForFile(getTimeStampForFile(date2004_12_31_23_59_59_999)) );
			
				// test all positive Dates after the epoch:
			/*
			for (long t = Long.MIN_VALUE; t < Long.MAX_VALUE; t++) {
				Date date = new Date(t);
				if ((t % TimeLength.year) == 0) System.out.println(getDayStamp(date));
				String errorMsg = "failed at time = " + time;
				Assert.assertEquals( errorMsg, date, parseTimeStamp(getTimeStamp(date)) );
				Assert.assertEquals( errorMsg, date, parseTimeStampForFile(getTimeStampForFile(date)) );
			}
			*/
				// Above takes orders of magnitude too long, so test random Dates instead:
			int numberTests = 10 * 1000;
			Random random = new Random( System.currentTimeMillis() );
					// test random Dates within +-5000 years of the epoch, since that includes most historical dates, which are the most important dates of all:
			long length5000Years = 5000 * TimeLength.year;
			for (int i = 0; i < numberTests; i++) {
				long time = random.nextLong() % length5000Years;
				Date date = new Date(time);
				String errorMsg = "failed at time = " + time;
				Assert.assertEquals( errorMsg, date, parseTimeStamp(getTimeStamp(date)) );
				Assert.assertEquals( errorMsg, date, parseTimeStampForFile(getTimeStampForFile(date)) );
			}
					// test completely random Dates:
			for (int i = 0; i < numberTests; i++) {
				long time = random.nextLong();
				Date date = new Date(time);
				String errorMsg = "failed at time = " + time;
				Assert.assertEquals( errorMsg, date, parseTimeStamp(getTimeStamp(date)) );
				Assert.assertEquals( errorMsg, date, parseTimeStampForFile(getTimeStampForFile(date)) );
			}
		}
		
		/**
		* Results on 2010-03-11 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_18 server jvm):
		* <pre><code>
			n = 16 * 1024
				DateStringCache.parse (for timeStampPattern) with ZERO cache: first = 10.790 us, mean = 4.114 us (CI deltas: -3.944 ns, +3.702 ns), sd = 7.742 us (CI deltas: -1.197 us, +1.543 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
					PARSE CACHE MAY BE TOO SMALL: numberParsePutFails = 20430848 > 0
					State of this instance: sizeMax = 0, dateToString.size() = 0, stringToDate.size() = 0, numberFormatHits = 0, numberFormatMisses = 0, numberFormatPutFails = 0, numberParseHits = 0, numberParseMisses = 20430848, numberParsePutFails = 20430848

				DateStringCache.parse (for timeStampPattern) with a perfectly sized cache: first = 5.427 us, mean = 100.461 ns (CI deltas: -75.079 ps, +187.716 ps), sd = 1.796 us (CI deltas: -923.772 ns, +1.849 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
					Good: there appear to be NO issues with this DateStringCache instance
					State of this instance: sizeMax = 16384, dateToString.size() = 16384, stringToDate.size() = 16384, numberFormatHits = 0, numberFormatMisses = 0, numberFormatPutFails = 0, numberParseHits = 1174372352, numberParseMisses = 16384, numberParsePutFails = 0
		* </code></pre>
<!--
+++ The above results for a perfectly sized cache really puzzle me: should have been much smaller (faster).
Reason: my BenchmarkDataStructureAccess class found that ConcurrentHashMap.get
should have access times on this machine of 20-40 ns for n = 16 * 1024 mappings.

I investigated this extensively on 2007/8/1-2 and could not trace down the main reason.
(A minor reason is that DateStringCache.parse currently has to return a new Date for encapsulation reasons;
this contributes about 30 ns to the above time.)

At one point, I found that if my DateStringCache.parse simply commented out the parsing
(i.e. the code inside the synchronized block) and used bogus Date mappings,
then the benchmark below was indeed returning results around 40 ns.

BUT THIS WAS BIZARRE because that parsing code is never called once benchmarking starts
(the warmup runs cause all the mappings to be put into the cache first).
EVEN MORE ANNOYING: when I wrote up a dedicated test class to submit to Sun, I was unable to replicate the effect!

I have no idea what is going on here.
My best guess is that maybe the cpu's cache is not used optimally in certain cases,
such as if there could be a synchronized block in a code branch,
which causes it to have to fetch most stuff from main memory, which is very slow.
Reason:  my BenchmarkDataStructureAccess class found that ConcurrentHashMap.get
has access times on this machine of 50-150 ns for n = 1 * 1024 * 1024 mappings
(which should not fit in my cpus 4 MB L2 cache, forcing most accesses to be to main memory).

this brings up an issue that may be relevant for the strangeness found in benchmark_isSameDayOfWeek...
IF IT DOES TURN OUT THAT CACHING IS THE ISSUE, THEN THIS SUGGESTS AN ALTERNATE STRATEGY
maybe DateInfo should be changed to be as small in memory as possible and to be as localized in memory as possible,
so that perhaps cache blow out is postponed; here DateInfo would just store the hardest to compute info and would leave
the remaining fields to be calculated; thus you would be trading off cache misses for extra cpu computations...
-->
		*/
		@Test public void benchmark_parseTimeStamp() {
			class ParseTimeDate implements Runnable {
				private final DateStringCache dateStringCache;
				private final String[] dateStrings;
				private long state;	// needed to prevent DCE since this is a Runnable
				
				private ParseTimeDate(int sizeMax, int n) {
					dateStringCache = new DateStringCache(timeStampPattern, sizeMax, true);
					dateStrings = new String[n];
					for (int i = 0; i < dateStrings.length; i++) {
						dateStrings[i] = "+" + (1000 + i) + "-01-01T00:00:00.000+0000";	// do 1000 + i to ensure that has at least 4 digits; cheap way to avoid zero padding
					}
				}
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				public void run() {
					for (String dateString : dateStrings) {
						try {
							Date d = dateStringCache.parse(dateString);
							state ^= d.getTime();
						}
						catch (Throwable t) {
							t.printStackTrace();
						}
					}
				}
				
				private void printIssues() {
					String s = dateStringCache.getIssues();
					if (s.length() > 0) System.out.print("\t" + s);
				}
			}
			
			int n = 16 * 1024;
			
			ParseTimeDate task_cacheAbsent = new ParseTimeDate(0, n);
			System.out.println();
			System.out.println("DateStringCache.parse (for timeStampPattern) with ZERO cache: " + new Benchmark(task_cacheAbsent, n));
			task_cacheAbsent.printIssues();
			
			ParseTimeDate task_cacheBig = new ParseTimeDate(n, n);
			System.out.println();
			System.out.println("DateStringCache.parse (for timeStampPattern) with a perfectly sized cache: " + new Benchmark(task_cacheBig, n));
			task_cacheBig.printIssues();
		}
		
	}
	
}
