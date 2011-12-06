/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ here are methods that need to add in the future:
	validRange(Number value, Number min, Number max)
		min & max both inclusive
	validFraction
		for float/double which would take a single arg and call validRange(value, 0.0, 1.0)
	validIndex
		is already present below, but it should call validRange(value, 0.0, array.length - 1)
		??? hmm, may not the best idea if it requires a calculation...
	
	lessThan, lessThanEqual, greaterThan, greaterThanEqual

--any way to add message customization?
	--original version of this class had the checking methods take two parameters, with the second being the name of the variable in question; it was added to the error message
		--if I did not have all the primitive variations to deal with, I would probably make a second version of each method which has this
		--I gave up on this approach to make usage of Check easier, requiring less typing
			--the disadvantage, of course, is that you will only know from the line number of the stack trace where the error occured; there will be no variable name now
	--another approach that I thought of was to have these methods all take a final String vararg, but this would be very bad for performance
		--if wanted to just have a String 2nd arg, as described above, but want to embed more in it than the variable name,
		one hack is to note that since the colon ':' char should never be part of a proper java variable name,
		then you could have the convention that if the name arg has a colon char in it then everything before the ':' is the var's name
		and everything from the ':' onwards is a custom message that should be appended to the end of the normal message.
		For example, calling Check.arg().notNull(null, "someVar: this should never happen") should result in the exception message
			someVar == null: this should never happen

--other people are thinking about robustness, method contracts, easy checking, etc:
	--overview: http://technocrat.net/d/2006/11/10/10671
	--JML (Java Modeling Language): http://www.jmlspecs.org
	--ESC/Java2:
		http://secure.ucd.ie/products/opensource/ESCJava2/
		http://secure.ucd.ie/documents/tutorials/slides/3_warnings.pdf
	--IntelliJ is starting to support a weak subset of these annotations, such as @NotNull
	--there is also the java-derivative Nice language
		http://nice.sourceforge.net/manual.html
	and see this section for how they handle nulls:
		http://nice.sourceforge.net/manual.html#optionTypes
	But it appears to be academic at the moment...
	--The Bea$t is working on Spec# for C# to do similar things
	--cool JavaOne 2009 talk:
		Preventing Bugs with Pluggable Type Checking
		Michael D. Ernst
		U. of Washington
		Joint work with Mahmood Ali
		http://developers.sun.com/learning/javaoneonline/sessions/2009/pdf/TS-3798.pdf
	See also this project's homepage:
		http://groups.csail.mit.edu/pag/jsr308/
	--another Java library:
		http://jcontractor.sourceforge.net/

The great promise of annotations is that you simply write a comment, and they autogenerate the code for checking aas well as documentation.
Plus tools like ESC/Java2 can then go on to do additional static, deep, code analysis.

Old approaches, such as this class, require you to write both the code as well as the javadoc comment,
which is extra work plus things can get out of synch.
And the analysis tools like ESC/Java2 will not be supported.

Problems with annotations include:
a) being at the mercy of the people writing the annotations to cover the checks you are interested in (unless they are extensible)
b) they are currently non-standard in the JDK and so require lots of extra tools to use
c) while they may offer static (compile time) guarantees which is better than the runtime checks of this class, those guarantees may be less strong than this class because they may be more easily subverted by, say, reflection

In view of the above, for now, have decided to use this Check class.
*/


package bb.util;

import bb.gui.SwingUtil;
import bb.io.FileUtil;
import bb.net.NetUtil;
import java.awt.EventQueue;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
* Contains methods that perform various checks on data.
* The goal is to support <a href="http://en.wikipedia.org/wiki/Design_By_Contract">Design By Contract</a> style programming.
* <p>
* This class may not be directly used because it is abstract:
* while it contains full implementations of all the checking methods,
* it does not implement {@link #onFailure(String, Throwable) onFailure}.
* Instead, a concrete subclass which implements onFailure must be used.
* <i>The action performed by the subclass's onFailure is arbitrary:</i>
* it could throw a RuntimeException (usually a more specific subclass), log the failure, send it over the network, ignore it, etc.
* <p>
* This class provides three inner classes, {@link Arg}, {@link Check.State}, and {@link Assert},
* which are concrete subclasses meant for direct use.
* Arg is meant for checking method arguments, while State and Assert are meant for checking arbitrary state.
* Instances of these classes need not be created by the user, but may be conveniently obtained
* by the static accessors {@link #arg() arg}, {@link #state() state} and {@link #azzert() azzert}.
* Here is a typical scenario for checking preconditions and postconditions:
* <pre><code>
	&#47;&#42;&#42;
	&#42; Returns the value from map that is associated with key.
	&#42; &lt;p>
	&#42; &#64;param map the Map that will be queried; <i>precondition: must not be null</i>
	&#42; &#64;param key the key used to query map; <i>precondition: must not be null</i>
	&#42; &#64;return the value from map that is associated with key; <i>postcondition: result is never null</i>
	&#42; &#64;throws IllegalArgumentException if any precondition is violated
	&#42; &#64;throws IllegalStateException if any postcondition is violated
	&#42;&#47;
	public Object getValue(Map&lt;Object,Object> map, Object key) {
		Check.arg().notNull(map);
		Check.arg().notNull(key);
		
		Object value = map.get(key);
		Check.state().notNull(value);
		return value;
	}
* </code></pre>
* <p>
* The design priorities of this class, which will hopefully promote its use across all projects, are
* <ol>
*  <li>code clarity: usage should read well, be very clear as to what constraint is being checked</li>
*  <li>ease of use: usage should require minimal typing, so method names are short and require minimal arguments</li>
*  <li>
*		low impact: the checks themselves must not significantly degrade performance;
*		only the abnormal case (failure to pass the check) should result in significant processing
*  </li>
* </ol>
* <p>
* Every checking method has some constraint stated in its name that the variable is expected to <i>pass</i>.
* For instance, <code>{@link #notNull notNull}(object)</code> means "check that object is not null".
* <p>
* Most of these checking methods also return the data if the check is passed.
* This allows them to be used in contexts like field assignments, for example
* <pre><code>
	&#47;&#42;&#42;
	&#42; Caches the value of the line.separator System property.
	&#42; &lt;p>
	&#42; Contract: is never blank.
	&#42;&#47;
	private final String lineEnd = Check.state().notBlank( System.getProperty("line.separator") );
* </code></pre>
* We also could have written the <code>getValue</code> method above in this more compact form:
* <pre><code>
	public Object getValue(Map&lt;Object,Object> map, Object key) {
		Check.arg().notNull(map);
		Check.arg().notNull(key);
		return Check.state().notNull( map.get(key) );
	}
* </code></pre>
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public abstract class Check {
	
	// -------------------- constants --------------------
	
	private static final Arg argInstance = new Arg();
	private static final State stateInstance = new State();
	private static final Assert assertInstance = new Assert();
	
	// -------------------- instance fields --------------------
	
	/**
	* Records whether or not to perform each check.
	* Normally, this is true, however,
	* subclasses like {@link Assert} may choose to suppress checks.
	*/
	private final boolean doCheck;
	
	// -------------------- static methods --------------------
	
	/** Returns a {@link Arg} instance. The same instance is always returned. */
	public static Arg arg() { return argInstance; }
	
	/** Returns a {@link Check.State} instance. The same instance is always returned. */
	public static State state() { return stateInstance; }
	
	/** Returns a {@link Assert} instance. The same instance is always returned. This method should be named "assert", but that is now a Java keyword. */
	public static Assert azzert() { return assertInstance; }
	
	// -------------------- constructors --------------------
	
	/** Convenience constructor that merely calls <code>{@link Check#Check(boolean) this}(true)</code>. */
	public Check() {
		this(true);
	}
	
	/** Fundamental constructor. */
	public Check(boolean doCheck) {
		this.doCheck = doCheck;
	}
	
	// -------------------- onFailure --------------------
	
	/** Called whenever a check fails and the code has just errMsg to report. */
	public abstract void onFailure(String errMsg);
	
	/** Called whenever a check fails and the code has both errMsg and throwable to report. */
	public abstract void onFailure(String errMsg, Throwable throwable);
	
	
	// ==================== CHECKING METHODS, SECTION 1: primitive methods ====================
	
	
	// -------------------- isTrue, isFalse --------------------
	
	/**
	* Checks that value == true.
	* <p>
	* @return value, if it passes the check
	*/
	public boolean isTrue(boolean value) {
		if (doCheck && !(value == true)) onFailure(value + " is not true");
		return value;
	}
	
	/**
	* Checks that value == false.
	* <p>
	* @return value, if it passes the check
	*/
	public boolean isFalse(boolean value) {
		if (doCheck && !(value == false)) onFailure(value + " is not false");
		return value;
	}
	
	
	// -------------------- zero --------------------
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public byte zero(byte value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public short zero(short value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public int zero(int value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public long zero(long value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public float zero(float value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	/**
	* Checks that value == 0.
	* <p>
	* @return value, if it passes the check
	*/
	public double zero(double value) {
		if (doCheck && !(value == 0)) onFailure(value + " is not zero");
		return value;
	}
	
	// -------------------- notZero --------------------
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public byte notZero(byte value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public short notZero(short value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public int notZero(int value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public long notZero(long value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public float notZero(float value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	/**
	* Checks that value != 0.
	* <p>
	* @return value, if it passes the check
	*/
	public double notZero(double value) {
		if (doCheck && !(value != 0)) onFailure(value + " is zero");
		return value;
	}
	
	// -------------------- negative --------------------
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* <p>
	* @return value, if it passes the check
	*/
	public byte negative(byte value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");
		return value;
	}
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* <p>
	* @return value, if it passes the check
	*/
	public short negative(short value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");
		return value;
	}
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* <p>
	* @return value, if it passes the check
	*/
	public int negative(int value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");
		return value;
	}
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* <p>
	* @return value, if it passes the check
	*/
	public long negative(long value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");
		return value;
	}
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public float negative(float value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	/**
	* Checks that value is strictly negative (i.e. value < 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public double negative(double value) {
		if (doCheck && !(value < 0)) onFailure(value + " is not negative");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	// -------------------- notNegative --------------------
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public byte notNegative(byte value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");
		return value;
	}
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public short notNegative(short value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");
		return value;
	}
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public int notNegative(int value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");
		return value;
	}
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public long notNegative(long value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");
		return value;
	}
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public float notNegative(float value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	/**
	* Checks that value is not strictly negative (i.e. value >= 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public double notNegative(double value) {
		if (doCheck && !(value >= 0)) onFailure(value + " is negative");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	// -------------------- positive --------------------
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* <p>
	* @return value, if it passes the check
	*/
	public byte positive(byte value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");
		return value;
	}
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* <p>
	* @return value, if it passes the check
	*/
	public short positive(short value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");
		return value;
	}
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* <p>
	* @return value, if it passes the check
	*/
	public int positive(int value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");
		return value;
	}
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* <p>
	* @return value, if it passes the check
	*/
	public long positive(long value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");
		return value;
	}
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public float positive(float value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	/**
	* Checks that value is strictly positive (i.e. value > 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public double positive(double value) {
		if (doCheck && !(value > 0)) onFailure(value + " is not positive");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	// -------------------- notPositive --------------------
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public byte notPositive(byte value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");
		return value;
	}
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public short notPositive(short value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");
		return value;
	}
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public int notPositive(int value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");
		return value;
	}
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* <p>
	* @return value, if it passes the check
	*/
	public long notPositive(long value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");
		return value;
	}
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public float notPositive(float value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	/**
	* Checks that value is not strictly positive (i.e. value <= 0).
	* NaN fails this check.
	* <p>
	* @return value, if it passes the check
	*/
	public double notPositive(double value) {
		if (doCheck && !(value <= 0)) onFailure(value + " is positive");	// Note: this logic still works even if value is NaN
		return value;
	}
	
	// -------------------- IEEE floating point special value methods --------------------
	
	/**
	* Checks that value is NaN.
	* <p>
	* @return value, if it passes the check
	*/
	public float naN(float value) {
		if (doCheck && !Float.isNaN(value)) onFailure(value + " is not NaN");
		return value;
	}
	
	/**
	* Checks that value is NaN.
	* <p>
	* @return value, if it passes the check
	*/
	public double naN(double value) {
		if (doCheck && !Double.isNaN(value)) onFailure(value + " is not NaN");
		return value;
	}
	
	/**
	* Checks that value is not NaN.
	* <p>
	* @return value, if it passes the check
	*/
	public float notNaN(float value) {
		if (doCheck && Float.isNaN(value)) onFailure("is NaN");
		return value;
	}
	
	/**
	* Checks that value is not NaN.
	* <p>
	* @return value, if it passes the check
	*/
	public double notNaN(double value) {
		if (doCheck && Double.isNaN(value)) onFailure("is NaN");
		return value;
	}
	
	/**
	* Checks that value is infinite.
	* <p>
	* @return value, if it passes the check
	*/
	public float infinite(float value) {
		if (doCheck && !Float.isInfinite(value)) onFailure(value + " is not infinite");
		return value;
	}
	
	/**
	* Checks that value is infinite.
	* <p>
	* @return value, if it passes the check
	*/
	public double infinite(double value) {
		if (doCheck && !Double.isInfinite(value)) onFailure(value + " is not infinite");
		return value;
	}
	
	/**
	* Checks that value is not infinite.
	* <p>
	* @return value, if it passes the check
	*/
	public float notInfinite(float value) {
		if (doCheck && Float.isInfinite(value)) onFailure(value + " is infinite");
		return value;
	}
	
	/**
	* Checks that value is not infinite.
	* <p>
	* @return value, if it passes the check
	*/
	public double notInfinite(double value) {
		if (doCheck && Double.isInfinite(value)) onFailure(value + " is infinite");
		return value;
	}
	
	// -------------------- convenience compound methods (combine multiple methods above) --------------------
	
	/**
	* Checks that value is "normal", which is defined as it passing all these tests:
	* <ol>
	*  <li>is not NaN</li>
	*  <li>is not infinite</li>
	* </ol>
	* <p>
	* @return value, if it passes the check
	*/
	public float normal(float value) {
		notNaN(value);
		notInfinite(value);
		return value;
	}
	
	/**
	* Checks that value is "normal", which is defined as it passing all these tests:
	* <ol>
	*  <li>is not NaN</li>
	*  <li>is not infinite</li>
	* </ol>
	* <p>
	* @return value, if it passes the check
	*/
	public double normal(double value) {
		notNaN(value);
		notInfinite(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #negative negative}.
	* <p>
	* @return value, if it passes the check
	*/
	public float normalNegative(float value) {
		normal(value);
		negative(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #negative negative}.
	* <p>
	* @return value, if it passes the check
	*/
	public double normalNegative(double value) {
		normal(value);
		negative(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #notNegative not negative}.
	* <p>
	* @return value, if it passes the check
	*/
	public float normalNotNegative(float value) {
		normal(value);
		notNegative(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #notNegative not negative}.
	* <p>
	* @return value, if it passes the check
	*/
	public double normalNotNegative(double value) {
		normal(value);
		notNegative(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #positive positive}.
	* <p>
	* @return value, if it passes the check
	*/
	public float normalPositive(float value) {
		normal(value);
		positive(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #positive positive}.
	* <p>
	* @return value, if it passes the check
	*/
	public double normalPositive(double value) {
		normal(value);
		positive(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #notPositive not positive}.
	* <p>
	* @return value, if it passes the check
	*/
	public float normalNotPositive(float value) {
		normal(value);
		notPositive(value);
		return value;
	}
	
	/**
	* Checks that value is {@link #normal normal} and {@link #notPositive not positive}.
	* <p>
	* @return value, if it passes the check
	*/
	public double normalNotPositive(double value) {
		normal(value);
		notPositive(value);
		return value;
	}
	
	// -------------------- validXXX --------------------
	
	/**
	* Checks that value is a {@link NetUtil#isValidPort valid TCP or UDP port number}.
	* <p>
	* @return value, if it passes the check
	*/
	public int validPort(int value) {
		if (doCheck && !NetUtil.isValidPort(value)) onFailure(value + " is outside the proper range for a TCP/IP port number, which is [0, " + NumberUtil.unsigned2ByteMaxValue + "]");
		return value;
	}
	
	/**
	* Checks that value is a valid probability.
	* The requirement is that value lies in the closed interval [0, 1].
	* See <a href="http://en.wikipedia.org/wiki/Probability#Mathematical_treatment">this probability article</a>.
	* <p>
	* @return value, if it passes the check
	*/
	public double validProbability(double value) {
		if (doCheck && (
			Double.isNaN(value)
			|| (value < 0)
			|| (value > 1)
		) ) onFailure(value + " is outside the proper range for a probability, which is [0, 1]");
		return value;
	}
	
	
	// ==================== CHECKING METHODS, SECTION 2: generic Object methods ====================
	
	
	// -------------------- null methods --------------------
	
	/**
	* Checks that obj is null.
	* <p>
	* @return obj, if it passes the check
	*/
	public Object isNull(Object object) {
// +++ remove is?
		if (doCheck && !(object == null)) onFailure("object is not null");
		return object;
	}
	
	/**
	* Checks that obj is not null.
	* <p>
	* @return obj, if it passes the check
	*/
	public Object notNull(Object object) {
		if (doCheck && !(object != null)) onFailure("object is null");
		return object;
	}
	
	
	// ==================== CHECKING METHODS, SECTION 3: array/Collection methods ====================
	
	
	// -------------------- empty --------------------
	
	/**
	* Checks that array is empty (i.e. either is null or array.length == 0).
	* <p>
	* This method was introduced for strict argument checking of <code>main</code> methods
	* which do not use the supplied <code>String[]</code> parameter.
	* Here, you want to crash if the user has supplied values, to inform them that they have misused the method.
	* <p>
	* An alternative to this method, if you do not want a null array to pass, is to call <code>{@link #hasSize(Object[], int) hasSize}(array, 0)</code> parameter.
	* <p>
	* @return array, if it passes the check
	*/
	public <T> T[] empty(T[] array) {
		if ( (array != null) && (array.length > 0) ) onFailure("array is not null and");
		return array;
	}
	
	// -------------------- notEmpty --------------------
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public boolean[] notEmpty(boolean[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public byte[] notEmpty(byte[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public short[] notEmpty(short[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public char[] notEmpty(char[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public int[] notEmpty(int[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public long[] notEmpty(long[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public float[] notEmpty(float[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public double[] notEmpty(double[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that array is not empty (i.e. is not null and array.length > 0).
	* <p>
	* @return array, if it passes the check
	*/
	public <T> T[] notEmpty(T[] array) {
		notNull(array);
		notZero(array.length);
		return array;
	}
	
	/**
	* Checks that collection is not empty (i.e. is not null and collection.size() > 0).
	* <p>
	* @return collection, if it passes the check
	*/
	public <T> Collection<T> notEmpty(Collection<T> collection) {
		notNull(collection);
		notZero(collection.size());
		return collection;
	}
	
	/**
	* Checks that map is not empty (i.e. is not null and map.size() > 0).
	* <p>
	* @return map, if it passes the check
	*/
	public <K, V> Map<K, V> notEmpty(Map<K, V> map) {
		notNull(map);
		notZero(map.size());
		return map;
	}
	
	// -------------------- hasSize --------------------
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public boolean[] hasSize(boolean[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public byte[] hasSize(byte[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public short[] hasSize(short[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public char[] hasSize(char[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public int[] hasSize(int[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public long[] hasSize(long[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public float[] hasSize(float[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public double[] hasSize(double[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that array is not null and has the required number of elements (i.e. array.length == sizeRequired).
	* <p>
	* @return array, if it passes the check
	*/
	public <T> T[] hasSize(T[] array, int sizeRequired) {
		notNull(array);
		equals(array.length, sizeRequired);
		return array;
	}
	
	/**
	* Checks that collection is not null and has the required number of elements (i.e. collection.size() == sizeRequired).
	* <p>
	* @return collection, if it passes the check
	*/
	public <T> Collection<T> hasSize(Collection<T> collection, int sizeRequired) {
		notNull(collection);
		equals(collection.size(), sizeRequired);
		return collection;
	}
	
	/**
	* Checks that map is not null and has the required number of elements (i.e. map.size() == sizeRequired).
	* <p>
	* @return map, if it passes the check
	*/
	public <K, V> Map<K, V> hasSize(Map<K, V> map, int sizeRequired) {
		notNull(map);
		equals(map.size(), sizeRequired);
		return map;
	}
	
	// -------------------- sameSize --------------------
	
/*
+++ I wanted to write a method called sameSize in which you could supply two args,
both being either an array/Collection/Map, and the method would confirm that both args are either null
or both are non-null and have the same size.
I gave up on this, however, because there are 6 combinations of arg types BEFORE you consider the primitive array variations...
Java's primitives are such a programmers nightmare...

The work around for now is to use hasSize in partly awkward ways like
	Check.arg().hasSize( someArray, anotherArray.length );
*/
	
	// -------------------- unmodifiable --------------------
	
	/**
	* Checks that collection is unmodifiable (i.e. cannot be mutated).
	* <p>
	* The current algorithm is to call {@link Collection#clear} and confirm that an UnsupportedOperationException is thrown.
	* If said UnsupportedOperationException is caught, then it is otherwise ignored,
	* however, if it is not raised at all then {@link #onFailure onFailure} is called.
	* <p>
	* <b>Warning: do not call this method with Collections that you expect may fail, because a side effect of failure is that collection will be cleared.</b>
	* <p>
	* Unmodifiable Collections are conveniently generated by
	* {@link Collections#unmodifiableCollection Collections.unmodifiableCollection}, {@link Collections#unmodifiableList Collections.unmodifiableList}
	* {@link Collections#unmodifiableSet Collections.unmodifiableSet}, {@link Collections#unmodifiableSortedSet Collections.unmodifiableSortedSet}.
	* They may be used when exposing Collection type fields in order to maintain encapsulation.
	* <p>
	* @return collection, if it passes the check
	*/
	public <T> Collection<T> unmodifiable(Collection<T> collection) {
		notNull(collection);
		
		try {
			collection.clear();
			onFailure("collection appears to be modifiable (called collection.clear, but that failed to throw a UnsupportedOperationException)");
		}
		catch (UnsupportedOperationException uoe) {
			// GOOD: if this occurs, it means that is unmodifiable
		}
		
		return collection;
	}
	
	/**
	* Checks that map is unmodifiable (i.e. cannot be mutated).
	* <p>
	* The current algorithm is to call {@link Map#clear} and confirm that an UnsupportedOperationException is thrown.
	* If said UnsupportedOperationException is caught, then it is otherwise ignored,
	* however, if it is not raised at all then {@link #onFailure onFailure} is called.
	* <p>
	* <b>Warning: do not call this method with Maps that you expect may fail, because a side effect of failure is that map will be cleared.</b>
	* <p>
	* Unmodifiable Maps are conveniently generated by
	* {@link Collections#unmodifiableMap Collections.unmodifiableMap}, {@link Collections#unmodifiableSortedMap Collections.unmodifiableSortedMap}.
	* They may be used when exposing Map type fields in order to maintain encapsulation.
	* <p>
	* @return map, if it passes the check
	*/
	public <K, V> Map<K, V> unmodifiable(Map<K, V> map) {
		notNull(map);
		
		try {
			map.clear();
			onFailure("called map.clear, but that failed to throw a UnsupportedOperationException, which indicates that map is not, in fact, unmodifiable");
		}
		catch (UnsupportedOperationException uoe) {
			// GOOD: if this occurs, it means that is unmodifiable
		}
		
		return map;
	}

	
	// -------------------- validIndex --------------------
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, boolean[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (index < array.length) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, byte[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (doCheck && !(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, short[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, char[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, int[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, long[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, float[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public int validIndex(int index, double[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for array (i.e. 0 <= index < array.length).
	* The check necessarily fails if array is null, so this method simultaneously serves as a null check on array.
	* <p>
	* @return index, if it passes the check
	*/
	public <T> int validIndex(int index, T[] array) {
		notNegative(index);
		if (doCheck) {
			if (array == null) onFailure("index = " + index + " can not be a valid index of array because array is null");
			if (!(index < array.length)) onFailure("index = " + index + " is not < array.length = " + array.length);
		}
		return index;
	}
	
	/**
	* Checks that index is valid for collection (i.e. 0 <= index < collection.size()).
	* The check necessarily fails if collection is null, so this method simultaneously serves as a null check on collection.
	* <p>
	* @return index, if it passes the check
	*/
	public <T> int validIndex(int index, Collection<T> collection) {
		notNegative(index);
		if (doCheck) {
			if (collection == null) onFailure("index = " + index + " can not be a valid index of collection because collection is null");
			if (!(index < collection.size())) onFailure("index = " + index + " is not < collection.size() = " + collection.size());
		}
		return index;
	}
	
	// -------------------- validOffsetLength --------------------
	
	// Note: would have preferred to supply the array itself (as opposed to the arrayLength param).
	// The reason why this fails is that want the methods to work for all arrays,
	// and even if use a parameterised type, that fails to cover primitive typed arrays.
	
	/**
	* Checks that offset >= 0, length >= 0, and (offset + length) <= lengthArray.
	* <p>
	* @param offset some proposed index offset into the array in question
	* @param length some proposed number of array elements (e.g. to read into) of the array in question
	* @param lengthArray should be the result of a call to a.length where a is the array in question
	*/
	public void validOffsetLength(int offset, int length, int lengthArray) {
		if (doCheck) {
			if (!(offset >= 0)) onFailure("offset = " + offset + " is not >= 0, so cannot be a valid array index offset");
			if (!(length >= 0)) onFailure("length = " + length + " is not >= 0, so cannot be a valid array index length");
			if (!(offset + length <= lengthArray)) onFailure("offset + length = (" + offset + " + " + length + ") = " + (offset + length) + " is not <= array length = " + lengthArray);
		}
	}
// +++ change to take array as param...
	
	
	// ==================== CHECKING METHODS, SECTION 4: File methods ====================
	
	
	/**
	* Checks that directory is a valid filesystem directory, which is defined as it passing all these tests:
	* <ol>
	*  <li>is not null</li>
	*  <li>is a path that exists</li>
	*  <li>can be read by this application</li>
	*  <li>resolves to an actual directory, and not some other type of file (e.g. a normal file)</li>
	* </ol>
	* <p>
	* @return directory, if it passes the check
	*/
	public File validDirectory(File directory) {
		try {
			if (doCheck) {
				if (!(directory != null)) onFailure("directory is null");
				if (!directory.exists()) onFailure("directory " + directory.getPath() + " does not exist");
				if (!directory.canRead()) onFailure("directory " + directory.getPath() + " cannot be read by this application");
				if (!directory.isDirectory()) onFailure("directory " + directory.getPath() + " is not a file system directory");
			}
		}
		catch (Throwable t) {	// must have error handling, because some of the calls above can raise Throwables
			 onFailure("Caught a Throwable (see cause) while examining directory = " + (directory != null ? directory.getPath() : "null"), t);
		}
		return directory;
	}
	
	/**
	* Checks that file is a valid filesystem file, which is defined as it passing all these tests:
	* <ol>
	*  <li>is not null</li>
	*  <li>is a path that exists</li>
	*  <li>can be read by this application</li>
	*  <li>resolves to a normal filesystem file</li>
	* </ol>
	* <p>
	* @return file, if it passes the check
	*/
	public File validFile(File file) {
		try {
			if (doCheck) {
				if (!(file != null)) onFailure("file is null");
				if (!file.exists()) onFailure("file " + file.getPath() + " does not exist");
				if (!file.canRead()) onFailure("file " + file.getPath() + " cannot be read by this application");
				if (!file.isFile()) onFailure("file " + file.getPath() + " is not a filesystem file");
			}
		}
		catch (Throwable t) {	// must have error handling, because some of the calls above can raise Throwables
			 onFailure("Caught a Throwable (see cause) while examining file = " + (file != null ? file.getPath() : "null"), t);
		}
		return file;
	}
	
	
	// ==================== CHECKING METHODS, SECTION 5: String methods ====================
	
	
	/**
	* Checks that s is not {@link StringUtil#isBlank "blank"}.
	* <p>
	* @return s, if it passes the check
	*/
	public String notBlank(String string) {
		if (doCheck && StringUtil.isBlank(string)) onFailure("string is blank");
		return string;
    }
	
	
	// ==================== CHECKING METHODS, SECTION 6: Thread methods ====================
	
	
	/**
	* Checks that the current thread is {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread} (aka the "EDT").
	* <p>
	* The reason why this check is important is because typical Java gui code is not multithread safe.
	* Most GUI code, including initialization, should only be called by EventQueue's dispatch thread.
	* Some references that discuss this include:
	* <ol>
	*  <li><a href="http://java.sun.com/docs/books/tutorial/uiswing/concurrency/initial.html">Lesson: Concurrency in Swing</a></li>
	*  <li><a href="http://java.sun.com/developer/JDCTechTips/2003/tt1208.html#1">Multithreading In Swing</a></li>
	*  <li><a href="http://java.sun.com/developer/JDCTechTips/2004/tt0611.html#1">More Multithreading In Swing</a></li>
	* </ol>
	* So, this method was written in order to support easy enforcement of that requirement.
	* <p>
	* @return the current thread, if it passes the check
	*/
	public Thread edt() {
		if (doCheck && !EventQueue.isDispatchThread()) onFailure("the current thread (" + Thread.currentThread().toString() + ") is not EventQueue's dispatch thread");
		
		return Thread.currentThread();
    }
	
	
	// ==================== CHECKING METHODS, SECTION 7: multiple value methods ====================
	
	
	// -------------------- equals --------------------
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public boolean equals(boolean value1, boolean value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public byte equals(byte value1, byte value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public short equals(short value1, short value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public char equals(char value1, char value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public int equals(int value1, int value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public long equals(long value1, long value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public float equals(float value1, float value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that value1 == value2.
	* <p>
	* @return value1, if the check is passed
	*/
	public double equals(double value1, double value2) {
		if (doCheck && !(value1 == value2)) onFailure(value1 + " != " + value2);
		return value1;
	}
	
	/**
	* Checks that obj1.equals(obj2).
	* Concerning nulls: passes check if obj1 == obj2 == null, but fails if one is null and the other is not.
	* <p>
	* @return obj1, if the check is passed
	*/
	public Object equals(Object obj1, Object obj2) {
		if (doCheck) {
			if (obj1 == null) {
				if (obj2 != null) onFailure(obj1 + " !equals " + obj2);
			}
			else if (obj2 == null) onFailure(obj1 + " !equals " + obj2);	// if get here, then know that obj1 != null
			else if (!obj1.equals(obj2)) onFailure(obj1 + " !equals " + obj2);	// if get here, then know that both obj1 and obj2 != null
		}
		return obj1;
	}
	
	
	// -------------------- CheckAdaptor (static inner class) --------------------
	
	/**
	* Abstract subclass of {@link Check}
	* which merely implements {@link Check#onFailure(String)} to forward to {@link Check#onFailure(String, Throwable)}.
	* Since this is the typical behavior that subclasses will choose,
	* this adaptor will usually be the immediate superclass instead of Check itself.
	*/
	public abstract static class CheckAdaptor extends Check {
		
		public CheckAdaptor() {
			this(true);
		}
		
		public CheckAdaptor(boolean doCheck) {
			super(doCheck);
		}
		
		/** {@inheritDoc} */
		@Override public void onFailure(String errMsg) {
			onFailure(errMsg, null);
		}
		
	}
	
	// -------------------- Arg (static inner class) --------------------
	
	/**
	* Concrete subclass of {@link Check} which is meant for checking method arguments.
	* Hence, it merely implements {@link #onFailure onFailure} (which here always throws an IllegalArgumentException)
	* and overrides no other methods.
	* <p>
	* This class is multithread safe: it adds no state to its multithread safe superclass.
	*/
	public static class Arg extends CheckAdaptor {
		
		/**
		* {@inheritDoc}
		* <p>
		* @throws IllegalArgumentException with errMsg as its message
		*/
		@Override public void onFailure(String errMsg, Throwable throwable) throws IllegalArgumentException {
			throw new IllegalArgumentException(errMsg, throwable);	// note: throwable can safely be null; it will be ignored in that case
		}
		
	}
	
	// -------------------- State (static inner class) --------------------
	
	/**
	* Concrete subclass of {@link Check} which is meant for checking arbitrary state.
	* Hence, it merely implements {@link #onFailure onFailure} (which here always throws an IllegalStateException)
	* and overrides no other methods.
	* <p>
	* This class is multithread safe: it adds no state to its multithread safe superclass.
	*/
	public static class State extends CheckAdaptor {
		
		/**
		* {@inheritDoc}
		* <p>
		* @throws IllegalStateException with errMsg as its message
		*/
		@Override public void onFailure(String errMsg, Throwable throwable) throws IllegalStateException {
			throw new IllegalStateException(errMsg, throwable);	// note: throwable can safely be null; it will be ignored in that case
		}
		
	}
	
	// -------------------- Assert (static inner class) --------------------
	
	/**
	* Concrete subclass of {@link Check} which is meant for checking arbitrary state.
	* Hence, it merely implements {@link #onFailure onFailure} (which here always throws an AssertionError)
	* and overrides no other methods.
	* <p>
	* The essential new feature added by this class compared to {@link Check.State} is that it only performs checks
	* if <a href="http://java.sun.com/j2se/1.4.2/docs/guide/lang/assert.html">assert</a>
	* is <a href="http://java.sun.com/j2se/1.4.2/docs/guide/lang/assert.html#enable-disable">enabled for this class</a>.
	* <i>However, if assert is disabled for this class, then all of the checking methods do nothing</i>
	* (indeed, a good optimizing compiler like hotspot may effectively eliminate them).
	* <p>
	* <i>This class should only be used if it is necessary to have the option of suppressing checking.</i>
	* Because checking methods typically have minimal performance impact,
	* {@link Check.State} should be used in preference to this class
	* unless checking is known to degrade performance excessively.
	* <p>
	* Another issue is that assert can not only be enabled globally, but can also be selectively enabled for just a package or class.
	* So, if assert is enabled for this class,
	* then all other classes which call this class will have checks performed
	* <i>even if assert is disabled for those calling classes</i>.
	* Conversely, if assert is disabled for this class,
	* then all other classes which call this class will have no checks performed
	* <i>even if assert is enabled for those calling classes</i>.
	* <p>
	* Users should also know that the AssertionError thrown by this class's onFailure method
	* will not caught by a standard <code>catch (Exception e)</code> clause.
	* The user may need to change their generic catching code to <code>catch (Throwable t)</code> instead.
	* <p>
	* This class is multithread safe: it adds no state to its multithread safe superclass.
	*/
	public static class Assert extends CheckAdaptor {
		
		static final boolean assertEnabled;
		static {	// the clever logic in this method was adapted from the end of this webpage: http://java.sun.com/j2se/1.4.2/docs/guide/lang/assert.html#usage
			boolean b = false;
			assert b = true; // intentional side effect: b only gets reassigned if assert is enabled
			assertEnabled = b;
		}
		
		/** Constructor. */
		public Assert() {
			super(assertEnabled);
		}
		
		/**
		* {@inheritDoc}
		* <p>
		* @throws AssertionError with errMsg as its message
		*/
		@Override public void onFailure(String errMsg, Throwable throwable) throws AssertionError {
			AssertionError ae = new AssertionError(errMsg);	// AssertionError annoyingly lacks a constructor with a Throwable param, so have to use multiple lines here...
			ae.initCause(throwable);	// note: throwable can safely be null; it will be ignored in that case
			throw ae;
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		//private final Check check = arg();
		private final Check check = state();
		//private final Check check = azzert();
		
		// -------------------- isTrue, isFalse --------------------
		
		@Test public void test_isTrue_pass() {
			check.isTrue(true);
		}
		
		@Test(expected=Throwable.class) public void test_isTrue_fail() throws Throwable {
			check.isTrue(false);
		}
		
		@Test public void test_isFalse_pass() {
			check.isFalse(false);
		}
		
		@Test(expected=Throwable.class) public void test_isFalse_fail() throws Throwable {
			check.isFalse(true);
		}
		
		// -------------------- zero --------------------
		
// +++ in this and many of the other numeric tests, need tests for byte, short, float...
		
		@Test public void test_zero_int_pass() {
			check.zero(0);
		}
		
		@Test(expected=Throwable.class) public void test_zero_int_fail() throws Throwable {
			check.zero(-1);
		}
		
		@Test public void test_zero_double_pass() {
			check.zero(0.0);
		}
		
		@Test(expected=Throwable.class) public void test_zero_double_fail_1() throws Throwable {
			check.zero(Double.NEGATIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_zero_double_fail_2() throws Throwable {
			check.zero(Double.NaN);
		}
		
		// -------------------- notZero --------------------
		
		@Test public void test_notZero_int_pass() {
			check.notZero(-1);
			check.notZero(1);
			check.notZero(Integer.MIN_VALUE);
			check.notZero(Integer.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notZero_int_fail() throws Throwable {
			check.notZero(0);
		}
		
		@Test public void test_notZero_double_pass() {
			check.notZero(-1.0);
			check.notZero(1.0);
			check.notZero(Double.MIN_VALUE);
			check.notZero(Double.MAX_VALUE);
			check.notZero(Double.NEGATIVE_INFINITY);
			check.notZero(Double.POSITIVE_INFINITY);
			check.notZero(Double.NaN);
		}
		
		@Test(expected=Throwable.class) public void test_notZero_double_fail() throws Throwable {
			check.notZero(0.0);
		}
		
		// -------------------- negative --------------------
		
		@Test public void test_negative_int_pass() {
			check.negative(Integer.MIN_VALUE);
			check.negative(-1);
		}
		
		@Test(expected=Throwable.class) public void test_negative_int_fail() throws Throwable {
			check.negative(0);
		}
		
		@Test public void test_negative_long_pass() {
			check.negative(Long.MIN_VALUE);
			check.negative(-1L);
		}
		
		@Test(expected=Throwable.class) public void test_negative_long_fail() throws Throwable {
			check.negative(0L);
		}
		
		@Test public void test_negative_double_pass() {
			check.negative(Double.NEGATIVE_INFINITY);
			check.negative(-Double.MAX_VALUE);
			check.negative(-Double.MIN_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_negative_double_fail_1() throws Throwable {
			check.negative(0.0);
		}
		
		@Test(expected=Throwable.class) public void test_negative_double_fail_2() throws Throwable {
			check.negative(Double.NaN);
		}
		
		// -------------------- notNegative --------------------
		
		@Test public void test_notNegative_int_pass() {
			check.notNegative(0);
			check.notNegative(Integer.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notNegative_int_fail() throws Throwable {
			check.notNegative(-1);
		}
		
		@Test public void test_notNegative_long_pass() {
			check.notNegative(0L);
			check.notNegative(Long.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notNegative_long_fail() throws Throwable {
			check.notNegative(-1L);
		}
		
		@Test public void test_notNegative_double_pass() {
			check.notNegative(0.0);
			check.notNegative(Double.MAX_VALUE);
			check.notNegative(Double.POSITIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_notNegative_double_fail_1() throws Throwable {
			check.notNegative(-Double.MIN_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notNegative_double_fail_2() throws Throwable {
			check.notNegative(Double.NaN);
		}
		
		// -------------------- positive --------------------
		
		@Test public void test_positive_int_pass() {
			check.positive(1);
			check.positive(Integer.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_positive_int_fail() throws Throwable {
			check.positive(0);
		}
		
		@Test public void test_positive_long_pass() {
			check.positive(1L);
			check.positive(Long.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_positive_long_fail() throws Throwable {
			check.positive(0L);
		}
		
		@Test public void test_positive_double_pass() {
			check.positive(Double.MIN_VALUE);
			check.positive(Double.MAX_VALUE);
			check.positive(Double.POSITIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_positive_double_fail_1() throws Throwable {
			check.positive(0.0);
		}
		
		@Test(expected=Throwable.class) public void test_positive_double_fail_2() throws Throwable {
			check.positive(Double.NaN);
		}
		
		// -------------------- notPositive --------------------
		
		@Test public void test_notPositive_int_pass() {
			check.notPositive(Integer.MIN_VALUE);
			check.notPositive(0);
		}
		
		@Test(expected=Throwable.class) public void test_notPositive_int_fail() throws Throwable {
			check.notPositive(1);
		}
		
		@Test public void test_notPositive_long_pass() {
			check.notPositive(Long.MIN_VALUE);
			check.notPositive(0L);
		}
		
		@Test(expected=Throwable.class) public void test_notPositive_long_fail() throws Throwable {
			check.notPositive(1L);
		}
		
		@Test public void test_notPositive_double_pass() {
			check.notPositive(Double.NEGATIVE_INFINITY);
			check.notPositive(-Double.MIN_VALUE);
			check.notPositive(0.0);
		}
		
		@Test(expected=Throwable.class) public void test_notPositive_double_fail_1() throws Throwable {
			check.notPositive(Double.MIN_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notPositive_double_fail_2() throws Throwable {
			check.notPositive(Double.NaN);
		}
		
		// -------------------- IEEE floating point specific methods --------------------
		
		@Test public void test_naN_pass() {
			check.naN(Float.NaN);
			check.naN(Double.NaN);
		}
		
		@Test(expected=Throwable.class) public void test_naN_fail_float() throws Throwable {
			check.naN(0.0f);
		}
		
		@Test(expected=Throwable.class) public void test_naN_fail_double() throws Throwable {
			check.naN(0.0);
		}
		
		@Test public void test_notNaN_pass() {
			check.notNaN(0.0f);
			check.notNaN(0.0);
		}
		
		@Test(expected=Throwable.class) public void test_notNaN_fail_float() throws Throwable {
			check.notNaN(Float.NaN);
		}
		
		@Test(expected=Throwable.class) public void test_notNaN_fail_double() throws Throwable {
			check.notNaN(Double.NaN);
		}
		
		@Test public void test_infinite_pass() {
			check.infinite(Float.NEGATIVE_INFINITY);
			check.infinite(Float.POSITIVE_INFINITY);
			check.infinite(Double.NEGATIVE_INFINITY);
			check.infinite(Double.POSITIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_infinite_fail_float() throws Throwable {
			check.infinite(Float.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_infinite_fail_double() throws Throwable {
			check.infinite(Double.MAX_VALUE);
		}
		
		@Test public void test_notInfinite_pass() {
			check.notInfinite(Float.MAX_VALUE);
			check.notInfinite(Double.MAX_VALUE);
		}
		
		@Test(expected=Throwable.class) public void test_notInfinite_fail_float1() throws Throwable {
			check.notInfinite(Float.NEGATIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_notInfinite_fail_float2() throws Throwable {
			check.notInfinite(Float.POSITIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_notInfinite_fail_double1() throws Throwable {
			check.notInfinite(Double.NEGATIVE_INFINITY);
		}
		
		@Test(expected=Throwable.class) public void test_notInfinite_fail_double2() throws Throwable {
			check.notInfinite(Double.POSITIVE_INFINITY);
		}
		
		// -------------------- convenience compound methods (combine multiple methods above) --------------------
		
		// these need not be tested, since the component methods they call are already tested
		
		// -------------------- validPort --------------------
		
		@Test public void test_validPort_pass() {
			check.validPort(0);
			check.validPort(NumberUtil.unsigned2ByteMaxValue);
		}
		
		@Test(expected=Throwable.class) public void test_validPort_fail_1() throws Throwable {
			check.validPort(0 - 1);
		}
		
		@Test(expected=Throwable.class) public void test_validPort_fail_2() throws Throwable {
			check.validPort(NumberUtil.unsigned2ByteMaxValue + 1);
		}
		
		// -------------------- null methods --------------------
		
		@Test public void test_isNull_pass() {
			check.isNull(null);
		}
		
		@Test(expected=Throwable.class) public void test_isNull_fail() throws Throwable {
			check.isNull(new Object());
		}
		
		@Test public void test_notNull_pass() {
			check.notNull(new Object());
		}
		
		@Test(expected=Throwable.class) public void test_notNull_fail() throws Throwable {
			check.notNull(null);
		}

		// -------------------- array/Collection methods --------------------
		
		@Test public void test_empty_pass() {
			check.empty( null );
			check.empty( new String[0] );
		}
		
		@Test(expected=Throwable.class) public void test_empty_fail() {
			check.empty( new String[] {"a"} );
		}
				
		@Test public void test_notEmpty_pass() {
			check.notEmpty( new int[] {1} );
			check.notEmpty( new String[] {"a", "b"} );
			
			List<String> list = new ArrayList<String>();
			list.addAll( Arrays.asList("c", "d") );
			check.notEmpty(list);
			
			Map<String, String> map = new HashMap<String, String>();
			map.put("e", "f");
			check.notEmpty(map);
		}
		
		@Test(expected=Throwable.class) public void test_notEmpty_fail_1() {
			check.notEmpty( new int[0] );
		}
		
		@Test(expected=Throwable.class) public void test_notEmpty_fail_2() {
			check.notEmpty( (Object[]) null );
		}
		
		@Test(expected=Throwable.class) public void test_notEmpty_fail_3() {
			check.notEmpty( new ArrayList<String>() );
		}
		
		@Test(expected=Throwable.class) public void test_notEmpty_fail_4() {
			check.notEmpty( new HashMap<Object,Object>() );
		}
		
		@Test public void test_hasSize_pass() {
			check.hasSize( new int[] {1}, 1 );
			check.hasSize( new String[] {"a", "b"}, 2 );
			
			List<String> list = new ArrayList<String>();
			list.addAll( Arrays.asList("c", "d") );
			check.hasSize(list, 2);
			
			Map<String, String> map = new HashMap<String, String>();
			map.put("e", "f");
			check.hasSize(map, 1);
		}
		
		@Test(expected=Throwable.class) public void test_hasSize_fail_1() {
			check.hasSize( new int[0], 1 );
		}
		
		@Test(expected=Throwable.class) public void test_hasSize_fail_2() {
			check.hasSize( (Object[]) null, 2 );
		}
		
		@Test(expected=Throwable.class) public void test_hasSize_fail_3() {
			check.hasSize( new ArrayList<String>(), 3 );
		}
		
		@Test(expected=Throwable.class) public void test_hasSize_fail_4() {
			check.hasSize( new HashMap<Object,Object>(), 4 );
		}
		
		@Test public void test_unmodifiable_pass() {
			List<String> list = new ArrayList<String>();
			list.addAll( Arrays.asList("c", "d") );
			check.unmodifiable( Collections.unmodifiableCollection(list) );
			
			Map<String, String> map = new HashMap<String, String>();
			map.put("e", "f");
			check.unmodifiable( Collections.unmodifiableMap(map) );
		}
		
		@Test(expected=Throwable.class) public void test_unmodifiable_fail_1() {
			check.unmodifiable( new ArrayList<String>() );
		}
		
		@Test(expected=Throwable.class) public void test_unmodifiable_fail_2() {
			check.unmodifiable( new HashMap<String, String>() );
		}
		
		@Test public void test_offsetAndLength_pass() {
			check.validOffsetLength(1, 2, 3);
		}
		
		@Test(expected=Throwable.class) public void test_offsetAndLength_fail() throws Throwable {
			check.validOffsetLength(1, 2, 1);
		}

		// -------------------- File methods --------------------
		
		@Test public void test_validDirectory_pass() {
			check.validDirectory(new File("./"));
		}
		
		@Test(expected=Throwable.class) public void test_validDirectory_fail() throws Throwable {
			check.validDirectory(new File("./vinklsdvioweufbsdcqwuy/"));
		}
		
		@Test public void test_validFile_pass() {
			File file = new File("./vmwsdvfmlfuferfwe.txt");
			if (file.exists()) throw new IllegalStateException("file = " + file.getPath() + " already exists");
			file = FileUtil.createTemp(file);
			check.validFile(file);
		}
		
		@Test(expected=Throwable.class) public void test_validFile_fail() throws Throwable {
			check.validFile(new File("./vinklsdvioweufbsdcqwuy.abc"));
		}
		
		// -------------------- String methods --------------------
		
		@Test public void test_nonBlank_pass() {
			check.notBlank("sdjksdkfsdklvf");
		}
		
		@Test(expected=Throwable.class) public void test_notBlank_fail_1() throws Throwable {
			check.notBlank(null);
		}
		
		@Test(expected=Throwable.class) public void test_notBlank_fail_2() throws Throwable {
			check.notBlank("");
		}
		
		@Test(expected=Throwable.class) public void test_notBlank_fail_3() throws Throwable {
			check.notBlank("   ");
		}
		
		// -------------------- Thread methods --------------------
		
		@Test public void test_edt_pass() throws Throwable {
			Runnable task = new Runnable() { public void run() {
				check.edt();
			} };
			SwingUtil.invokeNow(task);
		}
		
		@Test(expected=Throwable.class) public void test_edt_fail() throws Throwable {
			check.edt();
		}
		
		/**
		* Results on 2009-05-07 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			EventQueue.isDispatchThread: first = 95.801 ns, mean = 80.628 ns (CI deltas: -12.096 ps, +12.657 ps), sd = 201.052 ns (CI deltas: -28.766 ns, +34.778 ns) WARNING: execution times may have serial correlation, SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_edt() throws Exception {
			Runnable taskForEdt = new Runnable() { public void run() {	// need an outer task that submit to EventQueue's dispatch thread because otherwise the check.edt call below crashes
				final int n = 1024 * 1024;
				Runnable taskToBench = new Runnable() {
					public void run() {
						for (int i = 0; i < n; i++) {
							check.edt();
						}
					}
				};
				System.out.println("EventQueue.isDispatchThread: " + new Benchmark(taskToBench, n));
			} };
			SwingUtil.invokeNow(taskForEdt);
		}
		
		// -------------------- equals --------------------
		
		@Test public void test_equals_pass() {
			check.equals(true, true);
			check.equals(false, false);
			check.equals( (byte) 0, (byte) 0 );
			check.equals( (short) 1, (short) 1 );
			check.equals( '2', '2' );
			check.equals(3, 3);
			check.equals(4L, 4L);
			check.equals(5.0f, 5.0f);
			check.equals(6.0, 6.0);
			check.equals("a", "a");
			check.equals(null, null);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_1() throws Throwable {
			check.equals(true, false);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_2() throws Throwable {
			check.equals(false, true);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_3() throws Throwable {
			check.equals( (byte) 0, (byte) 1 );
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_4() throws Throwable {
			check.equals( (short) 1, (short) 2 );
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_5() throws Throwable {
			check.equals( '2', '3' );
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_6() throws Throwable {
			check.equals(3, 4);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_7() throws Throwable {
			check.equals(4L, 5L);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_8() throws Throwable {
			check.equals(5.0f, 6.0f);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_9() throws Throwable {
			check.equals(6.0, 7.0);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_10() throws Throwable {
			check.equals("a", "b");
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_11() throws Throwable {
			check.equals("a", null);
		}
		
		@Test(expected=Throwable.class) public void test_equals_fail_12() throws Throwable {
			check.equals(null, "b");
		}
		
	}
	
}
