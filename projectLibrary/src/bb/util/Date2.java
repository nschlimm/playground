/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer's notes:

+++ this project replaces the JDK's Date and Calendar classes:
	http://joda-time.sourceforge.net/
And jsr-310
	https://jsr-310.dev.java.net/
aims to include the above code in the JDK.
KEEP AN EYE ON IT BECAUSE IT WILL OBSOLETE THIS CLASS (its equivalent to date is immutable)
	http://joda-time.sourceforge.net/faq.html#threading
*/

package bb.util;

import java.util.Date;

/**
* A replacement for {@link Date} which corrects all its design mistakes.
* In particular, this class:
* <ol>
*  <li>is <a href="#immutable">is immutable</a></li>
*  <li>eliminates <a href="#deprecated">deprecated methods</a></li>
*  <li>has a correct {@link #toString toString} implementaion</li>
* </ol>
* Because this class extends Date, it is a drop in replacement for Date (provided deprecated methods are avoided; see below).

<a name="immutable"/>
* <h4>Immutability</h4>

* This class achieves <i>partial</i> immutability by overriding every mutator method of Date to throw an {@link UnsupportedOperationException}.
* <p>
* This type of immutability means that instances are safe to be directly returned from accessor methods without copying,
* used as keys in hash tables, used in Date object pools, etc because their state cannot be changed.
* <p>
* <b>Unfortunately, this <i>partial</i> immutability is distinct from full immutability.</b>
* The chief shortcoming is that this class cannot fix it's superclass's mistake of not making all of its fields final.
* <i>This has some subtle implications for <a href="#multithread">multithread safety</a></i>.

<a name="deprecated"/>
* <h4>Deprecated methods</h4>

* Being a subclass of Date, this class cannot literally remove its deprecated methods,
* but what it does do is override them all to always throw an UnsupportedOperationException.
* This, at least, guarantees that users cannot use that bad API.

<a name="multithread"/>
* <h4>Multithread safety</h4>

* A truly immutable class is always multithread safe.
* Unfortunately, this class's <a href="#immutable">partial immutability</a>, due to its lack of final fields, means that it requires
* <a href="http://book.javanb.com/java-concurrency-in-Practice/ch03lev1sec5.html">safe publication</a>
* in order to be multithread safe.
* If you are not familiar with this concept, carefully read <a href="http://www.javaconcurrencyinpractice.com/">Java Concurrency in Practice</a> by Goetz et al, especially Chapter 3.
* Once safely published, this class is fully multithread safe because of its partial immutability.
* Perhaps the most common way to safely publish instances of this class is to assign them to a data structure
* that is only reachable via a final field (so that field's finalness provides the necessary memory visibility ordering).

* <p>
* @author Brent Boyer
*/
public final class Date2 extends Date {
	
	private static final long serialVersionUID = 1;
	
	// -------------------- constructors --------------------
	
	public Date2(Date date) { this( date.getTime() ); }
	
	public Date2(long time) { super(time); }
	
	// -------------------- deprecated accessors --------------------
	
	@Override @SuppressWarnings("deprecation")
	public int getDate() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getDay() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getHours() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getMinutes() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getMonth() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getSeconds() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getTimezoneOffset() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public int getYear() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	// -------------------- ALL mutators (deprecated or not) --------------------
	
	@Override @SuppressWarnings("deprecation")
	public void setDate(int date) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override @SuppressWarnings("deprecation")
	public void setHours(int hours) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override @SuppressWarnings("deprecation")
	public void setMinutes(int minutes) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override @SuppressWarnings("deprecation")
	public void setMonth(int month) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override @SuppressWarnings("deprecation")
	public void setSeconds(int seconds) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override public void setTime(long time) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	@Override @SuppressWarnings("deprecation")
	public void setYear(int year) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to enforce immutability");
	}
	
	// -------------------- all other deprecated methods --------------------
	
	@SuppressWarnings("deprecation")
	public static long parse(String s) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public String toGMTString() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@Override @SuppressWarnings("deprecation")
	public String toLocaleString() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	@SuppressWarnings("deprecation")
	public static long UTC(int year, int month, int date, int hrs, int min, int sec) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("suppressed by Date2 to disallow use of a deprecated method");
	}
	
	// -------------------- toString --------------------
	
	/**
	* Returns an <a href="http://en.wikipedia.org/wiki/ISO_8601">ISO 8601</a> formated description of all the date/time fields of this instance.
	*/
	@Override public String toString() {
		return DateUtil.getTimeStamp(this);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None simply because I cannot think of any worthwhile tests...
	
}
