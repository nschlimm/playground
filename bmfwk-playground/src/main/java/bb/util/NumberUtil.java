/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
--results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11); see the UnitTest inner class below:

                          client JVM          server JVM
----------------------------------------------------------------------------
Double.parseDouble                 ?          163.888 ns
NumberUtil.parseDouble             ?           105.897 ns

Integer.parseInt                   ?            60.732 ns
NumberUtil.parseInt                ?            40.102 ns

Long.parseLong                     ?           172.169 ns
NumberUtil.parseLong               ?            70.039 ns

+++ need to get Sun to use my code?

+++ this guy claims to have fast number parsers:
	http://www.onjava.com/pub/a/onjava/2000/12/15/formatting_doubles.html
*/

package bb.util;

import bb.science.Math2;
import java.io.DataInputStream;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Random;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
* Provides static utility methods related to numbers.
* <p>
* This class is multithread safe: most of its state is immutable (both its immediate state, as well as the deep state of its fields).
* The sole exception is the {@link #scientificNotationFormat} field, however, all uses of that field first synchronize on it.
* <p>
* @author Brent Boyer
* @see <a href="http://research.microsoft.com/~hollasch/cgindex/coding/ieeefloat.html">IEEE Standard 754  Floating Point Numbers</a>
*/
public final class NumberUtil {
	
	// -------------------- int constants --------------------
	
	/**
	* If an int is written using decimal digits,
	* this field records the maximum number of digits that could be present.
	* Note: this values does not include any optional sign char.
	*/
	private static final int nDigitsIntMax = 9;	// Note: FloatingDecimal has a field named nDigitsMantissaMax analagous to this
	
	// -------------------- long constants --------------------
	
	/**
	* If a long is written using decimal digits,
	* this field records the maximum number of digits that could be present.
	* Note: this values does not include any optional sign char.
	*/
	private static final int nDigitsLongMax = 19;	// NOTE: FloatingDecimal appears to have NO field analagous to this
	
	// -------------------- unsigned constants --------------------
	
	/** Maximum value of an unsigned 2 byte integer, namely, 2^16 - 1 = 65535. */
	public static final int unsigned2ByteMaxValue = 65535;
	
	// -------------------- IEEE double precision constants --------------------
	
	/**
	* If the mantissa of a double is written as a (positive) integer using decimal digits,
	* this field records the maximum number of digits that could be present.
	*/
	private static final int nDigitsMantissaMax = 16;	// WARNING: FloatingDecimal has a field named maxDecimalDigits sort of analagous to this, EXCEPT it has value of 15, so the semantics must slightly differ
	
	private static final int exponentDoubleMax = 308;	// get from Double.MAX_VALUE = 1.7976931348623157E308; Note: FloatingDecimal has a field named maxDecimalExponent which are analagous to this
	private static final int exponentDoubleMin = -324;	// get from Double.MIN_VALUE = 4.9E-324; Note: FloatingDecimal has a field named minDecimalExponent which are analagous to this
	
	private static final int magnitudeExactMax = 22;	// Note: FloatingDecimal has a field named maxSmallTen which is analagous to this
	
	/**
	* All the positive powers of 10 that can be represented exactly as a double.
	* @see UnitTest#find_magnitudeExactMax UnitTest.find_magnitudeExactMax
	*/
	private static final double[] magnitudesExact = {
		1.0e0, 1.0e1, 1.0e2, 1.0e3, 1.0e4, 1.0e5, 1.0e6, 1.0e7, 1.0e8, 1.0e9,
		1.0e10, 1.0e11, 1.0e12, 1.0e13, 1.0e14, 1.0e15, 1.0e16, 1.0e17, 1.0e18, 1.0e19,
		1.0e20, 1.0e21, 1.0e22
	};	// Note: FloatingDecimal has a field named small10pow which is analagous to this
	
	// -------------------- specific constants --------------------
	
	private static final DecimalFormat scientificNotationFormat = new DecimalFormat(
		"0.0" +                                                  // insist that at least a decimal digit and the first fractional digit be present
		StringUtil.repeatChars('#', nDigitsMantissaMax - 1) +    // after the previous 2 digits, allow an optional number of digits up to the limit of what makes sense for a double; NOTE: must use nDigitsMantissaMax - 1 instead of nDigitsMantissaMax - 2 as you might expect in order for test_scientificNotationFormat to pass; I am not 100% sure why, but suspct that it has to do with rounding and base 10 versus base 2 issues
		"E0"                                                     // insist that the exponent be present and have at least 1 digit
	);
	
	private static final String nanText = String.valueOf( Double.NaN );
	private static final String negativeInfinityText = String.valueOf( Double.NEGATIVE_INFINITY );
	private static final String positiveInfinityText = String.valueOf( Double.POSITIVE_INFINITY );
	
	private static final char decimalSeparator = scientificNotationFormat.getDecimalFormatSymbols().getDecimalSeparator();
	private static final char minusSign = scientificNotationFormat.getDecimalFormatSymbols().getMinusSign();
	private static final char zeroDigit = scientificNotationFormat.getDecimalFormatSymbols().getZeroDigit();
	private static final boolean isZeroDigit0 = (zeroDigit == '0');
	
	// -------------------- isXXX --------------------
	
	/** Determines whether or not i is an even number. */
	public static boolean isEven(int i) {
		return ((i & 1) == 0);
	}
	
	/** Determines whether or not i is an odd number. */
	public static boolean isOdd(int i) {
		return ((i & 1) == 1);
	}
	
	/** Determines whether or not d is a "normal" double (i.e. that d is not NaN or infinite). */
	public static boolean isNormal(double d) {
		return !(Double.isNaN(d) || Double.isInfinite(d));
	}
	
	/** Determines whether or not f is a "normal" float, which is defined as f is not NaN or infinite. */
	public static boolean isNormal(float f) {
		return !(Float.isNaN(f) || Float.isInfinite(f));
	}
	
	// -------------------- bytesXXXToInt, bytesXXXToLong --------------------
	
	/**
	* Converts an array of 4 bytes in big endian order (i.e. bytes[0] is the most significant byte) to an int which is returned.
	* Just like {@link DataInputStream#readInt DataInputStream.readInt}, the bytes are treated as unsigned bit groups.
	* <p>
	* @throws IllegalArgumentException if bytes == null; bytes.length != 4
	*/
	public static int bytesBigEndianToInt(byte[] bytes) throws IllegalArgumentException {
		Check.arg().hasSize(bytes, 4);
		
		return
			((bytes[0] & 0xFF) << 24) |
			((bytes[1] & 0xFF) << 16) |
			((bytes[2] & 0xFF) <<  8) |
			(bytes[3] & 0xFF);
		// see also the code in DataInputStream.readInt
	}
	
	/**
	* Converts an array of 4 bytes in little endian order (i.e. bytes[0] is the least significant byte) to an int which is returned.
	* Just like {@link DataInputStream#readInt DataInputStream.readInt}, the bytes are treated as unsigned bit groups.
	* <p>
	* @throws IllegalArgumentException if bytes == null; bytes.length != 4
	*/
	public static int bytesLittleEndianToInt(byte[] bytes) throws IllegalArgumentException {
		Check.arg().hasSize(bytes, 4);
		
		return
			((bytes[3] & 0xFF) << 24) |
			((bytes[2] & 0xFF) << 16) |
			((bytes[1] & 0xFF) <<  8) |
			(bytes[0] & 0xFF);
		// is identical to the code in bytesBigEndianToInt except for the byte positions
	}
	
	/**
	* Converts an array of 8 bytes in big endian order (i.e. bytes[0] is the most significant byte) to a long which is returned.
	* Just like {@link DataInputStream#readLong DataInputStream.readLong}, the bytes are treated as unsigned bit groups.
	* <p>
	* @throws IllegalArgumentException if bytes == null; bytes.length != 8
	*/
	public static long bytesBigEndianToLong(byte[] bytes) throws IllegalArgumentException {
		Check.arg().hasSize(bytes, 8);
		
		long bitsHigh =
			((bytes[0] & 0xFFL) << 56) |	// performance optimization: use bitwise or instead of addition, since in theory should be faster to implement in hardware (no carry over to worry about)
			((bytes[1] & 0xFFL) << 48) |
			((bytes[2] & 0xFFL) << 40) |
			((bytes[3] & 0xFFL) << 32);
			
		long bitsLow = 0xFFFFFFFFL & (	// performance optimization: do a single conversion of all the low order bits to a long here
			((bytes[4] & 0xFF) << 24) |
			((bytes[5] & 0xFF) << 16) |
			((bytes[6] & 0xFF) <<  8) |
			(bytes[7] & 0xFF)
		);
		
		return bitsHigh | bitsLow;
		// see also the code in DataInputStream.readLong; I find my version to be more readable; benchmarking indicates identical performance
	}
	
	/**
	* Converts an array of 8 bytes in little endian order (i.e. bytes[0] is the least significant byte) to a long which is returned.
	* Just like {@link DataInputStream#readLong DataInputStream.readLong}, the bytes are treated as unsigned bit groups.
	* <p>
	* @throws IllegalArgumentException if bytes == null; bytes.length != 8
	*/
	public static long bytesLittleEndianToLong(byte[] bytes) throws IllegalArgumentException {
		Check.arg().hasSize(bytes, 8);
		
		long bitsHigh =
			((bytes[7] & 0xFFL) << 56) |	// performance optimization: use bitwise or instead of addition, since in theory should be faster to implement in hardware (no carry over to worry about)
			((bytes[6] & 0xFFL) << 48) |
			((bytes[5] & 0xFFL) << 40) |
			((bytes[4] & 0xFFL) << 32);
			
		long bitsLow = 0xFFFFFFFFL & (	// performance optimization: do a single conversion of all the low order bits to a long here
			((bytes[3] & 0xFF) << 24) |
			((bytes[2] & 0xFF) << 16) |
			((bytes[1] & 0xFF) <<  8) |
			(bytes[0] & 0xFF)
		);
		
		return bitsHigh | bitsLow;
		// is identical to the code in bytesBigEndianToLong except for the byte positions
	}
	
	// -------------------- min, max --------------------
	
	/**
	* Returns the minimum value of d1 and d2.
	* This method differs from {@link Math#min Math.min} solely in how it handles NaN inputs:
	* it only returns NaN if both args are NaN;
	* if exactly one arg is NaN, the other arg is always returned regardless of its value;
	* and if neither arg is NaN then the result of Math.min(d1, d2) is returned.
	* In contrast, Math.min always returns NaN if either arg is NaN.
	*/
	public static double min(double d1, double d2) {
		if (Double.isNaN(d1)) return d2;
		else if (Double.isNaN(d2)) return d1;
		else return Math.min(d1, d2);
	}
	
	/**
	* Returns the maximum value of d1 and d2.
	* This method differs from {@link Math#max Math.max} solely in how it handles NaN inputs:
	* it only returns NaN if both args are NaN;
	* if exactly one arg is NaN, the other arg is always returned regardless of its value;
	* and if neither arg is NaN then the result of Math.max(d1, d2) is returned.
	* In contrast, Math.max always returns NaN if either arg is NaN.
	*/
	public static double max(double d1, double d2) {
		if (Double.isNaN(d1)) return d2;
		else if (Double.isNaN(d2)) return d1;
		else return Math.max(d1, d2);
	}
	
	// -------------------- cents, mills methods --------------------
	
	/**
	* Rounds d to the nearest cent (i.e. second decimal place, 0.01) and returns this.
	* <p>
	* @throws IllegalArgumentException if d is not {@link Check#normal normal};
	*/
	public static double roundToCent(double d) throws IllegalArgumentException {
		Check.arg().normal(d);
		
		double dCents = Math.round( 100 * d );
		return dCents / 100.0;
	}
	
	/**
	* Rounds d to the nearest mill (i.e. third decimal place, 0.001) and returns this.
	* <p>
	* @throws IllegalArgumentException if d is not {@link Check#normal normal};
	*/
	public static double roundToMill(double d) throws IllegalArgumentException {
		Check.arg().normal(d);
		
		double dMills = Math.round( 1000 * d );
		return dMills / 1000.0;
	}
	
	/**
	* Rounds d to the nearest mill (i.e. third decimal place, 0.001) and returns the mill digit.
	* <p>
	* @throws IllegalArgumentException if d is not {@link Check#normal normal};
	*/
	public static int getMillValue(double d) throws IllegalArgumentException {
		Check.arg().normal(d);
		
		int dMills = (int) Math.round( 1000 * d );
		return (dMills % 10);
	}
	
	// -------------------- toScientificNotation --------------------
	
	/**
	* Formats d as a String in scientific notation.
	* The special values NaN, NEGATIVE_INFINITY, and POSITIVE_INFINITY are returned as String.valueOf(d).
	*/
	public static synchronized String toScientificNotation(double d) {
		if (Double.isNaN(d)) return nanText;
		if (d == Double.NEGATIVE_INFINITY) return negativeInfinityText;
		if (d == Double.POSITIVE_INFINITY) return positiveInfinityText;
		
		return scientificNotationFormat.format(d);
	}
	
	// -------------------- parseXXX --------------------
	
/*
+++ PERFORMANCE IMPROVEMENT #1: for all the parse methods below, instead of doing lookup based on single char using the digitToXXX methods,
could avoid even more computations by doing lookups on 2 chars (e.g. do a switch and then another switch inside each case)
Should be able to speed up the parsing code on average by almost 2X if do this (assuming that the multiplies dominate the execution)?
Only cost would be the large source code.

+++ See the code at the end of this class: when I tried this, I found that it not only did not speed up, it slowed down the execution...

MAYBE there is another way to do multi digit lookups; maybe could take say 2 digit chars,
do some kind of fast operation on them that produces unique numbers for every pair combination,
and then have a single switch statement that maps that operation's result to the parsed integer.
	--one is to calculate
		int hash = (c1 << 6) + c0;
	where c1 is the first digit (read left to right) and c0 is the second digit.
	This technique relies on the fact that the ASCII values for the digits 0-9 have the decimal values 48-57, but all are < 64 = 2^6.
	So, the << shifts the bits of c1 enough to guarantee no overlap with c0 when sum them, which means that can do an unambiguous table lookup.
	--the technique above has 1 fast bitwise operation (the <<) and one slower operation (the +); see if can come up with something similar that uses only fast bitwise operators
*/
	
/*
+++ PERFORMANCE IMPROVEMENT #2: the accumulate operation looks like
	intNew = (10 * intOld) + digitNew
How can it be sped up?  Maybe by avoiding that 10X operation, by noting that it can be broken down as follows:
	10 * x = 2 * 5 * x
Since 2 * x = x << 1 which is a super fast single clock instruction, concentrate on
	5 * y = (4 + 1) * y = (4 * y) + (1 * y) = (y << 2) + x
So, have replaced a multiple with 2 bit shifts and a single addition; need to benchmark to see if this is a win...
*/
	
// uncomment this if want to call my version of FloatingDecimal so can insert debugging lines inside it:
//public static double parseDoubleOrig(String s) throws NumberFormatException {
//	return FloatingDecimal.readJavaFormatString(s).doubleValue();
//}
	
	/**
	* Parses a double from s.
	* <p>
	* This method can <i>usually</i> parse s in any format that is parsable by {@link Double#parseDouble Double.parseDouble}.
	* (See {@link Double#valueOf Double.valueOf} for details.)
	* In particular, this means that:
	* <ol>
	*  <li>s should contain no grouping separator chars (e.g. commas)</li>
	*  <li>leading zeroes (in both the mantissa and exponent) will correctly be ignored</li>
	* </ol>
	* <i>Exceptions regarding the format of s:</i>
	* <ol>
	*  <li>
	*		this method uses the locale specific zero char, which may or may not be '0';
	*		Double.parseDouble rigidly assumes '0' always
	*  </li>
	*  <li>
	*		like parseInt/parseLong (from either NumberUtil or Integer/Long),
	*		but unlike Double.parseDouble,
	*		<i>this method does not trim leading and trailing whitespace</i>
	*		and will throw a NumberFormatException if such whitespace is encountered.
	*  </li>
	* </ol>
	* <p>
	* Some of the code here was inspired by the non-public class java.lang.FloatingDecimal
	* (must have access to Sun's java source code to view this).
	* <p>
	* @throws NumberFormatException if s is in the wrong format
	*/
	public static double parseDouble(String s) throws NumberFormatException {
		if (s == null) throw new NumberFormatException("s == null");
		if (s.length() == 0) throw new NumberFormatException("s is zero-length");
		
		if (s.equals(nanText)) return Double.NaN;
		if (s.equals(negativeInfinityText)) return Double.NEGATIVE_INFINITY;
		if (s.equals(positiveInfinityText)) return Double.POSITIVE_INFINITY;
		
			// determine sign:
		boolean isPositive = (s.charAt(0) != minusSign);
		
			// parse mantissa as an integer (noting, but skipping over, the decimal point):
		int indexMantissaStart = isPositive ? 0 : 1;
		int indexFirstNonZero = -1;
		int indexDecimalSeparator = -1;
		int indexExponentChar = -1;
		
		int nMantissaDigits = 0;	// records the literal number of mantissa digits present in s;
		int nMantissaDigitsUsed = 0;	// records the actual number of digits used to calculate the mantissa; this may be less than nMantissaDigits due to leading zeroes that are ignored, as well as trailing digits of too low precision
		
		int index = indexMantissaStart;
		
				// performace optimization: start off conversion as an int:
		int mantissaInt = 0;
		for ( ; index < s.length(); index++) {
			char c = s.charAt(index);
			if (c == decimalSeparator) {
				if (indexDecimalSeparator == -1) indexDecimalSeparator = index;
				else throw new NumberFormatException("s = " + s + " contains more than one index decimal separator char = " + decimalSeparator);
			}
			else if ((c == 'E') || (c == 'e')) {	// test for 'E' first as a shortcircuit optimization, since that is what toScientificNotation produces
				indexExponentChar = index;
				break;
			}
			else {
				++nMantissaDigits;
				
				if (indexFirstNonZero == -1) {
					if (c == zeroDigit) continue;	// a precision optimization is that can skip leading zeroes, saving our precision for digits that matter
					else indexFirstNonZero = index;
				}
				
				++nMantissaDigitsUsed;
				mantissaInt = (10*mantissaInt) + digitToInt( c );
				if (nMantissaDigitsUsed == (nDigitsIntMax - 1)) break;	// do - 1 since not all integers with nDigitsIntMax digits can be represented as an int
			}
		}
				// now convert mantissaInt to a long:
		long mantissaLong = (long) mantissaInt;
				// and continue parsing mantissa if there are more digits:
		if (indexExponentChar == -1) {
			++index;	// advance over the last char parsed into mantissaInt
			for ( ; index < s.length(); index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				char c = s.charAt(index);
				if (c == decimalSeparator) {
					if (indexDecimalSeparator == -1) indexDecimalSeparator = index;
					else throw new NumberFormatException("s = " + s + " contains more than one index decimal separator char = " + decimalSeparator);
				}
				else if ((c == 'E') || (c == 'e')) {	// test for 'E' first as a shortcircuit optimization, since that is what toScientificNotation produces
					indexExponentChar = index;
					break;
				}
				else {
					++nMantissaDigits;
					
					if (indexFirstNonZero == -1) {
						if (c == zeroDigit) continue;	// a precision optimization is that can skip leading zeroes, saving our precision for digits that matter
						else indexFirstNonZero = index;
					}
					
					if (nMantissaDigitsUsed < nDigitsMantissaMax) {
// +++ should keep going until reach the -long- limit and then should round the long?
						++nMantissaDigitsUsed;
						mantissaLong = (10L*mantissaLong) + digitToLong( c );
					}
					else {	// Have already added as many digits to mantissaLong as its precision allows.  Continue executing the for loop so that march over the low precision digits until hit the exponent or the end.
						digitToInt( c );	// done solely to check the validity of c
					}
				}
			}
		}
		
			// can immediately return if detect special case that have ONLY encountered zeroes in the mantissa:
		if (indexFirstNonZero == -1) return 0.0;
		
			// calculate an implicit exponent that accounts for
		int exponentImplicit = 0;
				// (a) any low precision digits that were skipped:
		boolean decimalIntervenes = (indexMantissaStart <= indexDecimalSeparator) && (indexDecimalSeparator < indexFirstNonZero);
		int nLeadingZeroes = indexFirstNonZero - indexMantissaStart - (decimalIntervenes ? 1 : 0);
		int nMantissaDigitsEffective = nMantissaDigits - nLeadingZeroes;
		int nSkippedDigits = nMantissaDigitsEffective - nMantissaDigitsUsed;
		exponentImplicit += nSkippedDigits;	// each skipped digit is implicitly a power of 10 times the mantissa
				// (b) the decimal point, if present:
		if (indexDecimalSeparator != -1) {
			int nWholeDigits = indexDecimalSeparator - indexMantissaStart;
			int nFractionDigits = nMantissaDigits - nWholeDigits;
			exponentImplicit -= nFractionDigits; // each fractional digit is implicitly a power of 10 dividing the mantissa
		}
		
			// parse the explicit exponent as an int, if present:
			// (the code below is an inlined customized version of that found in parseInt)
		int exponentExplicit = 0;
		if (indexExponentChar != -1) {
			index = indexExponentChar + 1;	// skip over the 'E' or 'e'
				// negative case: accumulate negatively, and test for positive overflow:
			if (s.charAt(index) == minusSign) {
				if (index == s.length() - 1) throw new NumberFormatException("the exponent part of s = " + s + " contains only the minus sign");
				
				++index;	// skip over the minus sign
				for ( ; index < s.length(); index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
					char c = s.charAt(index);
					exponentExplicit = (10*exponentExplicit) - digitToInt( c );
					if (exponentExplicit > 0) throw new NumberFormatException("the exponent part of s = " + s + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
				}
			}
				// positive case: accumulate positively, and test for negative overflow:
			else {
				if (index == s.length()) throw new NumberFormatException("the exponent part of s = " + s + " is empty");
				
				for ( ; index < s.length(); index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
					char c = s.charAt(index);
					exponentExplicit = (10*exponentExplicit) + digitToInt( c );
					if (exponentExplicit < 0) throw new NumberFormatException("the exponent part of s = " + s + " is greater than the maximum int value of " + Integer.MAX_VALUE);
				}
			}
			// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
		}
		
			// calculate the true effective exponent that need to multiply the mantissa by:
		int exponent = exponentExplicit + exponentImplicit;
		
			// convert mantissaLong to a double
		double mantissaDouble = (double) mantissaLong;
// +++ this should be a rounding, right?  (see comment above)
		
			// if there are sufficiently few digits, the above double conversion was perfect, so can check for easy cases:
		if (nMantissaDigitsEffective < nDigitsMantissaMax) {
				// easiest case: zero exponent and/or mantissa:
			if ( (exponent == 0) || (mantissaDouble == 0.0) ) {
				return (isPositive) ? mantissaDouble : -mantissaDouble;
			}
				// check for easy positive exponent cases:
			if (exponent > 0) {
					// 1st easy case: exponent corresponds with one of our precomputed exact magnitudes:
				if (exponent <= magnitudeExactMax) {
						// multiply by all the powers of 10; result is exact up to the single final rounding
					double d = mantissaDouble * magnitudesExact[exponent];
					return isPositive ? d : -d;
				}
				
					// 2nd easy case: there are less digits of precision in mantissaDouble then it is capable of holding
					// and the excess of exponent above magnitudeExactMax can be safely absorbed into mantissaDouble:
				int precisionAvailable = (nDigitsMantissaMax - 1) - nMantissaDigitsUsed;	// - 1 since there are some numbers that are out of range when full nDigitsMantissaMax is present
				if (exponent - precisionAvailable <= magnitudeExactMax) {
						// multiply mantissaDouble by 10^precisionAvailable; it is still exact
					mantissaDouble *= magnitudesExact[precisionAvailable];
						// multiply by remaining powers of 10; result is exact up to the single final rounding
					double d = mantissaDouble * magnitudesExact[exponent - precisionAvailable];
					return (isPositive) ? d : -d;
				}
			}
				// check for easy negative exponent cases:
			else {
					// 1st easy case: exponent is one of our precomputed exact magnitudes:
				if (exponent >= -magnitudeExactMax) {
						// divide by all the powers of 10; result is exact up to the single final rounding
					double d = mantissaDouble / magnitudesExact[-exponent];
// +++ would be quicker to multiply by a precomputed divisor? but then would get 2 rounding errors instead of 1 as with above?
					return isPositive ? d : -d;
				}
			}
		}
		
// If get here, you are faced with very nasty logic if want to guarantee best rounding.
// +++ In the future, if decide to use this class, need to implement the logic; see FloatingDecimal.doubleValue.
// For now, just record that hit this case and punt and let Double.parseDouble do the work:
++countHardCases;
return Double.parseDouble(s);

// +++ One alternative to the above is to accept extra rounding error:
//double d = mantissaDouble * Math.pow(10.0, exponent);
//return isPositive ? d : -d;
		
	}
	
/** Number of hard to parse cases that {@link #parseDouble parseDouble} has encountered so far. */
public static volatile int countHardCases = 0;
// +++ this is a temp hack; get rid of it in the future...
	
	/**
	* Parses an int from s.
	* This method simply returns <code>{@link #parseInt(String, int, int) parseInt}(s, 0, s.length())</code>.
	* <p>
	* @throws NumberFormatException if s is in the wrong format
	*/
	public static int parseInt(String s) throws NumberFormatException {
		return parseInt(s, 0, s.length());
	}
	
	/**
	* Parses an int from the specified substring of s.
	* <p>
	* The substring must follow the same format as the String supplied to {@link Integer#parseInt Integer.parseInt}.
	* In particular, this means that:
	* <ol>
	*  <li>the substring should contain no grouping separator chars (e.g. commas)</li>
	*  <li>the substring should have no leading or trailing whitespace</li>
	*  <li>leading zeroes may be present</li>
	* </ol>
	* <p>
	* @param s the String which is the source of chars that will parse into an int
	* @param indexStart the index (<i>inclusive</i>) of s that will begin parsing from
	* @param indexStop the index (<i>exclusive</i>) of s that will stop parsing at
	* @throws NumberFormatException if there is any problem with a parameter
	*/
	public static int parseInt(String s, int indexStart, int indexStop) throws NumberFormatException {
		if (s == null) throw new NumberFormatException("s == null");
		if (indexStart < 0) throw new NumberFormatException("indexStart = " + indexStart + " < 0");
		if (indexStop > s.length()) throw new NumberFormatException("indexStop = " + indexStop + " > s.length() = " + s.length());
		if (indexStart >= indexStop) throw new NumberFormatException("indexStart = " + indexStart + " >= indexStop = " + indexStop);
		
		int value = 0;
			// negative case: accumulate negatively, and test for positive overflow:
		if (s.charAt(indexStart) == minusSign) {
			if (indexStart == indexStop - 1) throw new NumberFormatException("over the specified range, s contains only the minus sign");
			
			for (int index = indexStart + 1; index < indexStop; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				value = (10*value) - digitToInt( s.charAt(index) );
				if (value > 0) throw new NumberFormatException("s.substring(" + indexStart + ", " + indexStop + ") = " + s.substring(indexStart, indexStop) + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
		}
			// positive case: accumulate positively, and test for negative overflow:
		else {
			//if (indexStart == indexStop) throw new NumberFormatException("over the specified range, chars is zero length");
			// NO NEED for the line above: is detected during arg checks
			
			for (int index = indexStart; index < indexStop; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				value = (10*value) + digitToInt( s.charAt(index) );
				if (value < 0) throw new NumberFormatException("s.substring(" + indexStart + ", " + indexStop + ") = " + s.substring(indexStart, indexStop) + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
		}
		// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
		return value;
	}
	
	/**
	* Same as {@link #parseInt(String) parseInt(s)}, except that this version operates on a char[] instead of a String.
	* This method is generally faster for an equivalent stream of chars,
	* since can immediately access all the chars without invoking an accessor method.
	* <p>
	* @param chars the source of chars that will parse into an int
	* @param indexStart the index (<i>inclusive</i>) of chars that will begin parsing from
	* @param indexStop the index (<i>exclusive</i>) of chars that will stop parsing at
	* @throws NumberFormatException if chars is in the wrong format or if there is any problem with the other parameters
	*/
	public static int parseInt(char[] chars, int indexStart, int indexStop) throws NumberFormatException {
		if (chars == null) throw new NumberFormatException("chars == null");
		if (indexStart < 0) throw new NumberFormatException("indexStart = " + indexStart + " < 0");
		if (indexStop > chars.length) throw new NumberFormatException("indexStop = " + indexStop + " > chars.length = " + chars.length);
		if (indexStart >= indexStop) throw new NumberFormatException("indexStart = " + indexStart + " >= indexStop = " + indexStop);
		
		int value = 0;
			// negative case: accumulate negatively, and test for positive overflow:
		if (chars[indexStart] == minusSign) {
			if (indexStart == indexStop - 1) throw new NumberFormatException("over the specified range, chars contains only the minus sign");
			
			for (int index = indexStart + 1; index < indexStop; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				value = (10*value) - digitToInt( chars[index] );
				if (value > 0) throw new NumberFormatException("chars[" + indexStart + ", " + indexStop + ") = " + (new String(chars, indexStart, indexStop - indexStart)) + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
		}
			// positive case: accumulate positively, and test for negative overflow:
		else {
			//if (indexStart == indexStop) throw new NumberFormatException("over the specified range, chars is zero length");
			// NO NEED for the line above: is detected during arg checks
			
			for (int index = indexStart; index < indexStop; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				value = (10*value) + digitToInt( chars[index] );
				if (value < 0) throw new NumberFormatException("chars[" + indexStart + ", " + indexStop + ") = " + (new String(chars, indexStart, indexStop - indexStart)) + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
		}
		// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
		return value;
	}
	
	/**
	* Parses a long from s.
	* <p>
	* This method can parse s in any format that is parsable by {@link Long#parseLong Long.parseLong}.
	* In particular, this means that:
	* <ol>
	*  <li>s should contain no grouping separator chars (e.g. commas)</li>
	*  <li>
	*		leading or trailing whitespace has previously been trimmed from s
	*		(e.g. for either NumberUtil or Long, calling <code>parseLong(" 123 ")</code>
	*		will result in a NumberFormatException)
	*  </li>
	*  <li>leading zeroes will correctly be ignored</li>
	* </ol>
	* <p>
	* @throws NumberFormatException if s is in the wrong format
	*/
	public static long parseLong(String s) throws NumberFormatException {
		if (s == null) throw new NumberFormatException("s == null");
		
			// negative case: accumulate negatively, and test for positive overflow:
		if (s.charAt(0) == minusSign) {
			if (s.length() == 1) throw new NumberFormatException("s contains only the minus sign");
			
				// performance optimization: start negative accumulation as an int:
			int index = 1;
			int valueInt = 0;
			int indexIntLimit = (nDigitsIntMax < s.length()) ? nDigitsIntMax : s.length();
			for ( ; index < indexIntLimit; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				char c = s.charAt(index);
				valueInt = (10*valueInt) - digitToInt( c );
				// NOTE: no need to test for negative overflow because if index started at 1 and limited to < nDigitsIntMax, there is no way that this can occur
			}
			long valueLong = (long) valueInt;
			if (index == s.length()) return valueLong;
				// if get here, then need to keep accumulating now as a long
			for ( ; index < s.length(); index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				char c = s.charAt(index);
				valueLong = (10*valueLong) - digitToLong( c );
				if (valueLong > 0) throw new NumberFormatException("s = " + s + " is smaller than the minimum long value of " + Long.MIN_VALUE);
			}
			return valueLong;
		}
			// positive case: accumulate positively, and test for negative overflow:
		else {
			if (s.length() == 0) throw new NumberFormatException("s is a zero length String");
			
				// performance optimization: start positive accumulation as an int:
			int index = 0;
			int valueInt = 0;
			int nDigitsIntMaxMinus1 = nDigitsIntMax - 1;
			int indexIntLimit = (nDigitsIntMaxMinus1 < s.length()) ? nDigitsIntMaxMinus1 : s.length();
			for ( ; index < indexIntLimit; index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				char c = s.charAt(index);
				valueInt = (10*valueInt) + digitToInt( c );
				// NOTE: no need to test for positive overflow because if index started at 0 and limited to < nDigitsIntMax - 1, there is no way that this can occur
			}
			long valueLong = (long) valueInt;
			if (index == s.length()) return valueLong;
				// if get here, then need to keep accumulating now as a long
			for ( ; index < s.length(); index++) {	// Note: no need to worry about index ever being incremented beyond Integer.MAX_VALUE and into a negative number, because s.length() can never be more than Integer.MAX_VALUE so that will detect this condition first
				char c = s.charAt(index);
				valueLong = (10*valueLong) + digitToLong( c );
				if (valueLong < 0) throw new NumberFormatException("s = " + s + " is larger than the maximum long value of " + Long.MAX_VALUE);
			}
			return valueLong;
		}
		// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
	}
	
	// -------------------- helper methods --------------------
	
// +++ looking back at this code, I do not understand why I used this lookup table approach instead of simply subtracting the ASCII offset.
// BEST GUESS: it handles the locale specific zero digit logic correctly, while simple substraction does not.
	
	private static int digitToInt(char c) throws NumberFormatException {
		switch (c) {
			case '0':
				if (isZeroDigit0) return 0;
				else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
			case '1': return 1;
			case '2': return 2;
			case '3': return 3;
			case '4': return 4;
			case '5': return 5;
			case '6': return 6;
			case '7': return 7;
			case '8': return 8;
			case '9': return 9;
			default:
				if (c == zeroDigit) return 0;
				else throw new NumberFormatException("char c = " + c + " is not a decimal digit");
		}
	}
	
	private static long digitToLong(char c) throws NumberFormatException {
		switch (c) {
			case '0':
				if (isZeroDigit0) return 0L;
				else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
			case '1': return 1L;
			case '2': return 2L;
			case '3': return 3L;
			case '4': return 4L;
			case '5': return 5L;
			case '6': return 6L;
			case '7': return 7L;
			case '8': return 8L;
			case '9': return 9L;
			default:
				if (c == zeroDigit) return 0L;
				else throw new NumberFormatException("char c = " + c + " is not a decimal digit");
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private NumberUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void find_maxMantissaDigits() {
			int numberBitsMantissa = 52;	// see http://steve.hollasch.net/cgindex/coding/ieeefloat.html
			int effectiveBitsMantissa = numberBitsMantissa + 1;	// because of the implicit leading 1 in binary floating point
			
			long mantissaMax = (1L << effectiveBitsMantissa) - 1L;	// if have n bits available, then the max integer representable is 2^n - 1
			System.out.println("mantissaMax for a double = 2^" + effectiveBitsMantissa + " - 1 = " + mantissaMax);
			
			double nDigitsMantissaMax = Math.ceil( Math2.log10(mantissaMax) );
			System.out.println("number of digits of mantissaMax for a double = " + nDigitsMantissaMax);
		}
		
		/**
		* This method finds the maximum power of 10 which can be represented with no loss of precision by a double.
		* Comment on the algorithm below:
		* since 10 = 5*2 we can instead simply find the maximum power of 5
		* which can be represented with no loss of precision by a double,
		* since as multiply 10s together, the factors of 2
		* can all be perfectly absorbed into the double's (power of 2) exponent.
		*/
		@Test public void find_magnitudeExactMax() {
			long mantissa = 1L;
			int i = 1;
			for ( ; ; i++) {
				mantissa *= 5L;
				String mantissaAsDecimalInteger = Long.toString(mantissa, 10);
				if (mantissaAsDecimalInteger.length() > nDigitsMantissaMax) {
					System.out.println( "i = " + i + " EXCEEDS THE PRECISION LIMIT of the mantissa of a double" );
					break;
				}
				else System.out.println( "5^" + i + " = " + mantissaAsDecimalInteger );
			}
			System.out.println( "Therefore, the maximum power of 10 which can be represented with no loss of precision by a double = " + (i - 1));
		}
		
		@Test public void test_bytesXXXToLong() {
			byte[] bytesBigEndianFor127 = new byte[] {0, 0, 0, 0, 0, 0, 0, 127};
			Assert.assertEquals(127, bytesBigEndianToLong(bytesBigEndianFor127));
			
			byte[] bytesBigEndianFor255 = new byte[] {0, 0, 0, 0, 0, 0, 0, -1};
			Assert.assertEquals(255, bytesBigEndianToLong(bytesBigEndianFor255));
			
			byte[] bytesBigEndianFor6912 = new byte[] {0, 0, 0, 0, 0, 0, 27, 0};	// 6912 = 27 * 256
			Assert.assertEquals(6912, bytesBigEndianToLong(bytesBigEndianFor6912));
			
			byte[] bytesBigEndianForMAX = new byte[] {127, -1, -1, -1, -1, -1, -1, -1};
			Assert.assertEquals(Long.MAX_VALUE, bytesBigEndianToLong(bytesBigEndianForMAX));
			
			byte[] bytesLittleEndianFor127 = new byte[] {127, 0, 0, 0, 0, 0, 0, 0};
			Assert.assertEquals(127, bytesLittleEndianToLong(bytesLittleEndianFor127));
			
			byte[] bytesLittleEndianForMIN = new byte[] {0, 0, 0, 0, 0, 0, 0, -128};
			Assert.assertEquals(Long.MIN_VALUE, bytesLittleEndianToLong(bytesLittleEndianForMIN));
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			bytesBigEndianToLong: first = 445.636 ns, mean = 12.820 ns (CI deltas: -10.711 ps, +11.883 ps), sd = 513.376 ns (CI deltas: -84.393 ns, +127.359 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
			bytesBigEndianToLongSunAlgorithm: first = 318.513 ns, mean = 15.189 ns (CI deltas: -3.910 ps, +4.265 ps), sd = 132.630 ns (CI deltas: -19.080 ns, +28.666 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_bytesXXXToLong() {
			final byte[][] matrixOfBytes = new byte[1024][8];	// CRITICAL: the second dimension must be 8; the first dimension is somewhat arbitrary, but chose the small value of 1024 here so that hopefull the whole thing can fit into the cpu cache
			byte b = 0;
			for (int i = 0; i < matrixOfBytes.length; i++) {
				for (int j = 0; j < matrixOfBytes[i].length; j++) {
					matrixOfBytes[i][j] = ++b;
				}
			}
			
			final long[] results = new long[ matrixOfBytes.length ];
			Runnable task = new Runnable() {	// NOTE: no need for a  state field and a toString method to prevent DCE as described in my benchmarking article because results is used below
				public void run() {
					for (int i = 0; i < matrixOfBytes.length; i++) {
						results[i] = bytesBigEndianToLong( matrixOfBytes[i] );
					}
				}
			};
			System.out.println("bytesBigEndianToLong: " + new Benchmark(task, matrixOfBytes.length));
			
			final long[] resultsSun = new long[ matrixOfBytes.length ];
			Runnable taskSun = new Runnable() {	// NOTE: no need for a  state field and a toString method to prevent DCE as described in my benchmarking article because resultsSun is used below
				public void run() {
					for (int i = 0; i < matrixOfBytes.length; i++) {
						resultsSun[i] = bytesBigEndianToLongSunAlgorithm( matrixOfBytes[i] );
					}
				}
			};
			System.out.println("bytesBigEndianToLongSunAlgorithm: " + new Benchmark(taskSun, matrixOfBytes.length));
			
			for (int i = 0; i < results.length; i++) {
				Assert.assertEquals("resultsSun[" + i + "] !equals results[" + i + "]", resultsSun[i], results[i]);
			}
			System.out.println("bytesBigEndianToLong is consistent with bytesBigEndianToLongSunAlgorithm");
		}
		
// +++ code below was copied from some other class, I recall?
		private long bytesBigEndianToLongSunAlgorithm(byte[] bytes) throws IllegalArgumentException {
			Check.arg().hasSize(bytes, 8);
			
			return (((long)bytes[0] << 56) +
					((long)(bytes[1] & 255) << 48) +
					((long)(bytes[2] & 255) << 40) +
					((long)(bytes[3] & 255) << 32) +
					((long)(bytes[4] & 255) << 24) +
					((bytes[5] & 255) << 16) +
					((bytes[6] & 255) <<  8) +
					((bytes[7] & 255) <<  0));
		}
		
		@Test public void test_getMillValue() {
			Assert.assertEquals( 0, getMillValue(0.000) );
			Assert.assertEquals( 1, getMillValue(0.001) );
			Assert.assertEquals( 3, getMillValue(10.0 / 3.0) );
		}
		
		@Test public void test_scientificNotationFormat() throws ParseException {
			synchronized (scientificNotationFormat) {
					// test some specific cases:
				double[] numbers = new double[] {
						// misc numbers:
					123456789012345678901234567890.,	// decimal on right
					1234.56789012345678901234567890,	// decimal in middle
					.00123456789012345678901234567890,	// decimal on left
						// special values:
					Double.MIN_VALUE,
					Double.MAX_VALUE,
					Double.NaN,
					Double.NEGATIVE_INFINITY,
					Double.POSITIVE_INFINITY
				};
				
				for (double d : numbers) {
					String s = scientificNotationFormat.format(d);
					System.out.println( d + " --> " + s );
					Assert.assertEquals( "Failed at d = " + d, d, scientificNotationFormat.parse(s).doubleValue(), 0 );
				}
				
					// test some random cases:
// +++ the Random testing code below and in the rest of this class is cpu intensive,
// so ought to use a thread pool to exploit all cpu resources and complete the computations faster...
				Random random = new Random();
				int nSamples = 100 * 1000;
				for (int i = 0; i < nSamples; i++) {
					if (i % (10*1000) == 0) System.out.printf("test_scientificNotationFormat random case #%,d%n", i);	// do this merely to get an idea of the progress, since it can take so long
					
					long bits = random.nextLong();
					double d = Double.longBitsToDouble(bits);	// Note: cannot do random.nextDouble() since that result is only in the range [0, 1.0)
					String s = scientificNotationFormat.format(d);
					//System.out.println( d + " --> " + s );
					Assert.assertEquals( "Failed at d = " + d, d, scientificNotationFormat.parse(s).doubleValue(), 0 );
				}
			}
		}
		
		@Test public void test_parseDouble() {
				// test some specific cases:
			String[] numberStrings = new String[] {
					// misc numbers:
				"123456789012345678901234567890.",	// decimal on right
				"1234.56789012345678901234567890",	// decimal in middle
				".00123456789012345678901234567890",	// decimal on left
					// special values:
				String.valueOf( Double.MIN_VALUE ),
				String.valueOf( Double.MAX_VALUE ),
				String.valueOf( Double.NaN ),
				String.valueOf( Double.NEGATIVE_INFINITY ),
				String.valueOf( Double.POSITIVE_INFINITY ),
					// weird decimal points:
				"55.e6",
				"-2.e5",
				".543e8",
				"-.6316e-02"
			};
			for (int i = 0; i < numberStrings.length; i++) {
				double dSun = Double.parseDouble(numberStrings[i]);
				double dMine = parseDouble(numberStrings[i]);
				Assert.assertEquals( "Failed at numberStrings[" + i + "] = " + numberStrings[i], dSun, dMine, 0 );
			}
			
				// test some random cases:
			Random random = new Random();
			int nSamples = 1 * 1000 * 1000;
			for (int i = 0; i < nSamples; i++) {
				if (i % (10*1000) == 0) System.out.printf("test_parseDouble random case #%,d%n", i);	// do this merely to get an idea of the progress, since it can take so long
				
				String s = generateDoubleText(random);
				double dSun = Double.parseDouble(s);
				double dMine = parseDouble(s);
				Assert.assertEquals( "Failed at s = " + s, dSun, dMine, 0 );
			}
		}
		
		private static String generateDoubleText(Random random) {
			boolean isPositive = random.nextBoolean();
			int nDigits = 1 + random.nextInt(nDigitsMantissaMax + 4);
			int indexDecimalPoint = random.nextInt(nDigits + 1);
			boolean isExponentPositive = random.nextBoolean();
			int nExponentialDigits = 1 + random.nextInt(4);
			
			StringBuilder sb = new StringBuilder(1 + 20 + 1 + 1 + 1 + 4);
			if (!isPositive) sb.append('-');
			for (int i = 0; i < nDigits; i++) {
				if (i == indexDecimalPoint) sb.append('.');
				sb.append( (char) ('0' + random.nextInt(10)) );
			}
			sb.append('e');
			if (!isExponentPositive) sb.append('-');
			for (int i = 0; i < nExponentialDigits; i++) {
				sb.append( (char) ('0' + random.nextInt(10)) );
			}
			return sb.toString();
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			parseDouble: first = 9.112 us, mean = 105.897 ns (CI deltas: -85.813 ps, +87.547 ps), sd = 1.107 us (CI deltas: -142.445 ns, +177.904 ns) WARNING: SD VALUES MAY BE INACCURATE
			Double.parseDouble: first = 6.485 us, mean = 163.888 ns (CI deltas: -88.143 ps, +93.370 ps), sd = 1.163 us (CI deltas: -153.990 ns, +194.557 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		* <i>So my code is somewhat faster than Sun's.</i>
		*/
		@Test public void benchmark_parseDouble() {
			final String[] numberStrings = new String[] {
				".1e1", ".12e-2", ".123e3", "-.1234e-4", ".12345e5", ".123456e-6", ".1234567e7", "-.12345678e-8", ".123456789e9", ".1234567890e-10"
			};
// +++ the above choice hits the easy cases, not the hard cases (see the countHardCases field), may want to include a broader mix?
			
			Runnable task = new Runnable() {
				private double state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += parseDouble(s); }
				}
			};
			System.out.println("parseDouble: " + new Benchmark(task, numberStrings.length));
			
			Runnable taskDouble = new Runnable() {
				private double state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += Double.parseDouble(s); }
				}
			};
			System.out.println("Double.parseDouble: " + new Benchmark(taskDouble, numberStrings.length));
		}
		
		@Ignore("Not running because it takes a couple of hours, so go with the random tests below")
		@Test public void test_parseInt_pass1() {
				// test every single case:
			for (int i = Integer.MIN_VALUE; i < Integer.MAX_VALUE; i++) {
				if (i % (1000*1000) == 0) System.out.printf("test_parseInt_pass i = %,d%n", i);	// do this merely to get an idea of the progress, since it can take so long
				parseIntCheck(i);
			}
			parseIntCheck(Integer.MAX_VALUE);
		}
		
		private void parseIntCheck(int i) {
			String s = String.valueOf(i);
			int iString = parseInt(s);
			Assert.assertTrue( "Failed at i = " + i, i == iString );
			int iCharArray = parseInt( s.toCharArray(), 0, s.length() );
			Assert.assertTrue( "Failed at i = " + i, i == iCharArray );
		}
		
		@Test public void test_parseInt_pass2() {
				// test some specific cases:
			String[] numberStrings = new String[] {
					// min and max values:
				String.valueOf(Integer.MIN_VALUE),
				String.valueOf(Integer.MAX_VALUE)
			};
			
			for (int i = 0; i < numberStrings.length; i++) {
				int iSun = Integer.parseInt(numberStrings[i]);
				int iMine = parseInt(numberStrings[i]);
				Assert.assertTrue( "Failed at numberStrings[" + i + "] = " + numberStrings[i], iSun == iMine );
			}
			
				// test some random cases:
			Random random = new Random();
			int nSamples = 1 * 10 * 1000 * 1000;
			for (int i = 0; i < nSamples; i++) {
				if (i % (1000*1000) == 0) System.out.printf("test_parseInt_pass2 i = %,d%n", i);	// do this merely to get an idea of the progress, since it can take so long
				
				parseIntCheck( random.nextInt() );
			}
		}
		
		@Test(expected=NumberFormatException.class) public void test_parseInt_fail1() {
			parseInt("-2147483649");	// is 1 less than Integer.MIN_VALUE
		}
		
		@Test(expected=NumberFormatException.class) public void test_parseInt_fail2() {
			parseInt("2147483648");	// is 1 greater than Integer.MAX_VALUE
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			parseInt: first = 4.538 us, mean = 40.102 ns (CI deltas: -33.837 ps, +32.933 ps), sd = 811.540 ns (CI deltas: -116.909 ns, +146.693 ns) WARNING: SD VALUES MAY BE INACCURATE
			Integer.parseInt: first = 5.191 us, mean = 60.732 ns (CI deltas: -40.983 ps, +42.931 ps), sd = 720.814 ns (CI deltas: -110.752 ns, +172.153 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
		* </code></pre>
		* <i>So my code is somewhat faster than Sun's.</i>
		*/
		@Test public void benchmark_parseInt() {
			final String[] numberStrings = new String[] {
				"1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789"
			};
// +++ the above is an equal sampling across all the number of digits possibilities, but may want a version which benchmarks the performance against just the small number of digits cases (e.g. 1-4 digits) since that is possibly more commonly encountered
			
			Runnable task = new Runnable() {
				private int state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += parseInt(s); }
				}
			};
			System.out.println("parseInt: " + new Benchmark(task, numberStrings.length));
			
			Runnable taskInteger = new Runnable() {
				private int state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += Integer.parseInt(s); }
				}
			};
			System.out.println("Integer.parseInt: " + new Benchmark(taskInteger, numberStrings.length));
		}
		
		@Test public void test_parseLong_pass() {
				// test some specific cases:
			String[] numberStrings = new String[] {
					// min and max values:
				String.valueOf(Long.MIN_VALUE),
				String.valueOf(Long.MAX_VALUE)
			};
			
			for (int i = 0; i < numberStrings.length; i++) {
				long lSun = Long.parseLong(numberStrings[i]);
				long lMine = parseLong(numberStrings[i]);
				Assert.assertTrue( "Failed at numberStrings[" + i + "] = " + numberStrings[i], lSun == lMine );
			}
			
				// test some random cases:
			Random random = new Random();
			int nSamples = 1 * 10 * 1000 * 1000;
			for (int i = 0; i < nSamples; i++) {
				if (i % (1000*1000) == 0) System.out.printf("test_parseLong_pass i = %,d%n", i);	// do this merely to get an idea of the progress, since it can take so long
				
				long longRandom = random.nextLong();
				String s = String.valueOf( longRandom );
				long longParsed = parseLong(s);
				Assert.assertTrue( "Failed at longRandom = " + longRandom, longRandom == longParsed );
			}
		}
		
		@Test(expected=NumberFormatException.class) public void test_parseLong_fail1() {
			parseLong("-9223372036854775809");	// is 1 less than Long.MIN_VALUE
		}
		
		@Test(expected=NumberFormatException.class) public void test_parseLong_fail2() {
			parseLong("9223372036854775808");	// is 1 greater than Long.MAX_VALUE
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			parseLong: first = 3.153 us, mean = 70.039 ns (CI deltas: -54.772 ps, +44.126 ps), sd = 863.480 ns (CI deltas: -205.259 ns, +446.310 ns) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
			Long.parseLong: first = 2.082 us, mean = 172.169 ns (CI deltas: -222.372 ps, +310.273 ps), sd = 3.245 us (CI deltas: -764.768 ns, +1.215 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
		* </code></pre>
		* <i>So my code is much faster than Sun's.</i>
		*/
		@Test public void benchmark_parseLong() throws Exception {
			final String[] numberStrings = new String[] {
				"1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789",
				"1234567890", "12345678901", "123456789012", "1234567890123", "12345678901234",
				"123456789012345", "1234567890123456", "12345678901234567", "123456789012345678",
				"1234567890123456789"
			};
// +++ the above is an equal sampling across all the number of digits possibilities, but may want a version which benchmarks the performance against just the small number of digits cases (e.g. 1-4 digits) since that is possibly more commonly encountered
			
			Runnable task = new Runnable() {
				private long state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += parseLong(s); }
				}
			};
			System.out.println("parseLong: " + new Benchmark(task, numberStrings.length));
			
			Runnable taskLong = new Runnable() {
				private long state = 0;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (String s : numberStrings) { state += Long.parseLong(s); }
				}
			};
			System.out.println("Long.parseLong: " + new Benchmark(taskLong, numberStrings.length));
		}
		
	}
	
}


/*
Below are 2 versions of parseInt in which I convert 2 digits at once, along with an implementation of digitsToInt.
Oddly enough, I found both to be SLOWER than the single digit conversion; must investigate further...
Maybe Hotspot did not inline it because the code was too big?


	private static final int[][] digits = new int[][] {
		{ 0,  1,  2,  3,  4,  5,  6,  7,  8,  9},
		{10, 11, 12, 13, 14, 15, 16, 17, 18, 19},
		{20, 21, 22, 23, 24, 25, 26, 27, 28, 29},
		{30, 31, 32, 33, 34, 35, 36, 37, 38, 39},
		{40, 41, 42, 43, 44, 45, 46, 47, 48, 49},
		{50, 51, 52, 53, 54, 55, 56, 57, 58, 59},
		{60, 61, 62, 63, 64, 65, 66, 67, 68, 69},
		{70, 71, 72, 73, 74, 75, 76, 77, 78, 79},
		{80, 81, 82, 83, 84, 85, 86, 87, 88, 89},
		{90, 91, 92, 93, 94, 95, 96, 97, 98, 99}
	};


	public static int parseInt(String s) throws NumberFormatException {
		if (s == null) throw new NumberFormatException("s == null");

		int value = 0;
			// negative case: accumulate negatively, and test for positive overflow:
		if (s.charAt(0) == minusSign) {
			if (s.length() == 1) throw new NumberFormatException("s contains only the minus sign");
			
				// do 2 digits at a time:
			int index = 1;
			for ( ; index < s.length() - 1; ) {
				int i1 = digitToInt( s.charAt(index++) );
				int i2 = digitToInt( s.charAt(index++) );
				value = (100*value) - digits[i1][i2];
				if (value > 0) throw new NumberFormatException("s = " + s + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
				// check if there is a final digit to do:
			if (index == s.length() - 1) {
				char c = s.charAt(index);
				value = (10*value) - digitToInt( c );
				if (value > 0) throw new NumberFormatException("s = " + s + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
		}
			// positive case: accumulate positively, and test for negative overflow:
		else {
			if (s.length() == 0) throw new NumberFormatException("s is a zero length String");

				// do 2 digits at a time:
			int index = 0;
			for ( ; index < s.length() - 1; ) {
				int i1 = digitToInt( s.charAt(index++) );
				int i2 = digitToInt( s.charAt(index++) );
				value = (100*value) + digits[i1][i2];
				if (value < 0) throw new NumberFormatException("s = " + s + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
				// check if there is a final digit to do:
			if (index == s.length() - 1) {
				char c = s.charAt(index);
				value = (10*value) + digitToInt( c );
				if (value < 0) throw new NumberFormatException("s = " + s + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
		}
		// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
		return value;
	}


	public static int parseInt(String s) throws NumberFormatException {
		if (s == null) throw new NumberFormatException("s == null");

		int value = 0;
			// negative case: accumulate negatively, and test for positive overflow:
		if (s.charAt(0) == minusSign) {
			if (s.length() == 1) throw new NumberFormatException("s contains only the minus sign");
			
				// do 2 digits at a time:
			int index = 1;
			for ( ; index < s.length() - 1; ) {
				char c1 = s.charAt(index++);
				char c2 = s.charAt(index++);
				value = (100*value) - digitsToInt( c1, c2 );
				if (value > 0) throw new NumberFormatException("s = " + s + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
				// check if there is a final digit to do:
			if (index == s.length() - 1) {
				char c = s.charAt(index);
				value = (10*value) - digitToInt( c );
				if (value > 0) throw new NumberFormatException("s = " + s + " is smaller than the minimum int value of " + Integer.MIN_VALUE);
			}
		}
			// positive case: accumulate positively, and test for negative overflow:
		else {
			if (s.length() == 0) throw new NumberFormatException("s is a zero length String");

				// do 2 digits at a time:
			int index = 0;
			for ( ; index < s.length() - 1; ) {
				char c1 = s.charAt(index++);
				char c2 = s.charAt(index++);
				value = (100*value) + digitsToInt( c1, c2 );
				if (value < 0) throw new NumberFormatException("s = " + s + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
				// check if there is a final digit to do:
			if (index == s.length() - 1) {
				char c = s.charAt(index);
				value = (10*value) + digitToInt( c );
				if (value < 0) throw new NumberFormatException("s = " + s + " is greater than the maximum int value of " + Integer.MAX_VALUE);
			}
		}
		// Note: for either case above, any leading zeroes are effectively skipped since the 10*... code correctly produces 0 for any leading 0 digits
		return value;
	}


	private static int digitsToInt(char c1, char c2) throws NumberFormatException {
		switch (c1) {
			case '0':
				if (isZeroDigit0) {
					switch (c2) {
						case '0':
							if (isZeroDigit0) return 0;
							else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
						case '1': return 1;
						case '2': return 2;
						case '3': return 3;
						case '4': return 4;
						case '5': return 5;
						case '6': return 6;
						case '7': return 7;
						case '8': return 8;
						case '9': return 9;
						default:
							if (c2 == zeroDigit) return 0;
							else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
					}
				}
				else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
			case '1':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 10;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 11;
					case '2': return 12;
					case '3': return 13;
					case '4': return 14;
					case '5': return 15;
					case '6': return 16;
					case '7': return 17;
					case '8': return 18;
					case '9': return 19;
					default:
						if (c2 == zeroDigit) return 10;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '2':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 20;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 21;
					case '2': return 22;
					case '3': return 23;
					case '4': return 24;
					case '5': return 25;
					case '6': return 26;
					case '7': return 27;
					case '8': return 28;
					case '9': return 29;
					default:
						if (c2 == zeroDigit) return 20;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '3':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 30;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 31;
					case '2': return 32;
					case '3': return 33;
					case '4': return 34;
					case '5': return 35;
					case '6': return 36;
					case '7': return 37;
					case '8': return 38;
					case '9': return 39;
					default:
						if (c2 == zeroDigit) return 30;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '4':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 40;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 41;
					case '2': return 42;
					case '3': return 43;
					case '4': return 44;
					case '5': return 45;
					case '6': return 46;
					case '7': return 47;
					case '8': return 48;
					case '9': return 49;
					default:
						if (c2 == zeroDigit) return 40;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '5':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 50;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 51;
					case '2': return 52;
					case '3': return 53;
					case '4': return 54;
					case '5': return 55;
					case '6': return 56;
					case '7': return 57;
					case '8': return 58;
					case '9': return 59;
					default:
						if (c2 == zeroDigit) return 50;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '6':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 60;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 61;
					case '2': return 62;
					case '3': return 63;
					case '4': return 64;
					case '5': return 65;
					case '6': return 66;
					case '7': return 67;
					case '8': return 68;
					case '9': return 69;
					default:
						if (c2 == zeroDigit) return 60;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '7':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 70;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 71;
					case '2': return 72;
					case '3': return 73;
					case '4': return 74;
					case '5': return 75;
					case '6': return 76;
					case '7': return 77;
					case '8': return 78;
					case '9': return 79;
					default:
						if (c2 == zeroDigit) return 70;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '8':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 80;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 81;
					case '2': return 82;
					case '3': return 83;
					case '4': return 84;
					case '5': return 85;
					case '6': return 86;
					case '7': return 87;
					case '8': return 88;
					case '9': return 89;
					default:
						if (c2 == zeroDigit) return 80;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			case '9':
				switch (c2) {
					case '0':
						if (isZeroDigit0) return 90;
						else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
					case '1': return 91;
					case '2': return 92;
					case '3': return 93;
					case '4': return 94;
					case '5': return 95;
					case '6': return 96;
					case '7': return 97;
					case '8': return 98;
					case '9': return 99;
					default:
						if (c2 == zeroDigit) return 90;
						else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
				}
			default:
				if (c1 == zeroDigit) {
					switch (c2) {
						case '0':
							if (isZeroDigit0) return 0;
							else throw new NumberFormatException("0 is not a decimal digit in this locale; the zero digit is " + zeroDigit);
						case '1': return 1;
						case '2': return 2;
						case '3': return 3;
						case '4': return 4;
						case '5': return 5;
						case '6': return 6;
						case '7': return 7;
						case '8': return 8;
						case '9': return 9;
						default:
							if (c2 == zeroDigit) return 0;
							else throw new NumberFormatException("char c2 = " + c2 + " is not a decimal digit");
					}
				}
				else throw new NumberFormatException("char c1 = " + c1 + " is not a decimal digit");
		}
	}


*/
