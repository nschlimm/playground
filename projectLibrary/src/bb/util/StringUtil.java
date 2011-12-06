/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ many of these methods should be changed to take the CharSequence interface introduced in java 1.4
*/

package bb.util;

import bb.science.Math2;
import bb.util.logging.LogUtil;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides various static String (and sometimes char[]) utility methods.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class StringUtil {
	
	// -------------------- constants --------------------
	
	/**
	* Platform's standard for the char sequence that should separate lines.
	* Is assigned by a call to <code>{@link System#getProperty System.getProperty}("line.separator")</code>.
	* See this <a href="http://en.wikipedia.org/wiki/Newline">wikipedia entry</a> for more discussion.
	*/
	public static final String newline = System.getProperty("line.separator");
	
	/**
	* A regex which matches <a href="http://java.sun.com/javase/6/docs/api/java/util/regex/Pattern.html#lt">Pattern's line terminators</a>.
	* <p>
	* @see <a href="http://forums.sun.com/thread.jspa?threadID=5422027">this forum</a>
	*/
	private static final Pattern lineTerminatorPattern = Pattern.compile("\\r\\n|[\\n\\r\\u0085\\u2028\\u2029]");
	
	/**
	* A regex which matches any combination of one or more space, tab or comma chars.
	*/
	private static final Pattern spaceTabCommaPattern = Pattern.compile("[ \\t,]+");
	
	// -------------------- newString, bytesToChars --------------------
	
private static final boolean stringContructorTrimsGarbage = inspectStringConstructor();

private static boolean inspectStringConstructor() {
	try {
			// first prove that substring shares the same underlying char[] as the parent String:
		String s1 = "abc123def";
		String s2 = s1.substring(3, 6);
		char[] value1 = (char[]) ReflectUtil.get(s1, "value");
		char[] value2 = (char[]) ReflectUtil.get(s2, "value");
		if (value1 != value2) throw new Exception("substring does NOT share the same underlying char[] with its parent String");
		if (value2.length != s1.length()) throw new Exception("value2.length = " + value2.length + " != s1.length() = " + s1.length());
		
			// second prove that the String(String) constructor trims garbage chars:
		String s3 = new String(s2);
		char[] value3 = (char[]) ReflectUtil.get(s3, "value");
		if (value3 == value2) throw new Exception("new String shares the same underlying char[] with its String arg");
		if (!(value3.length < value2.length)) throw new Exception("value3.length = " + value3.length + " is not < value2.length = " + value2.length);
		
		return true;
	}
	catch (Exception e) {
		LogUtil.getLogger2().logp(Level.WARNING, "StringUtil", "<clinit>", "String does not behave as expected; see cause", e);
		return false;
	}
}
	
	/**
	* Immediately returns null if s == null.
	* Otherwise, returns a new String instance whose underlying char[] precisely represents the chars of s
	* (i.e. the length of the underlying char[] equals the length of s, there is no baggage).
	* This guarantees that the result uses no more memory than required.
	* <p>
	* This minimal memory guarantee can be crucial in many situations.
	* For example, consider the case of a String which was parsed
	* as a substring from a long line of text from some file.
	* Then a reference to all the chars of the original line of text
	* must be maintained because of how the substring method operates,
	* and so excess memory is used.
	*/
	public static String newString(String s) {
		if (s == null) return null;
//		else return new String(s);
// +++ cannot automatically use the line above, which is faster, until sun commits to a contract in that String constructor that it will behave as I require.
// I submitted a RFE to them about this in 2007/8.

	// In the meanwhile, have to see if the test for expected behavior is correct before can use the fast code path:
if (stringContructorTrimsGarbage) return new String(s);
	// else must fall back on a slower code path that is guaranteed to trim garbage:
else return new String( s.toCharArray() );
	}
	/*
	Below is a fragment from an email that I sent to a client who was concerned about why I called newString--he thought that the extra copying was a waste:

		It for sure expends an upfront cpu cost do the extra copy, but it can potentially be a huge memory saver (and maybe performance too, if cache sizes would otherwise get blown out by large data structures, or network or disk transfering is slow, etc).

		I thought that the javadoc outlines the reason, but let me give an example to see if that clarifies.

		Suppose that you have String of length 2000 that you read off the network that is the text payload of some market data message.  And suppose that in parsing this String, you only need to have bits and pieces of it parsed into stuff in your market data objects (this is the usual case).  Now, if the pieces that you are parsing are, say, primitives like ints and doubles no problem.  But if you are parsing substrings from the original String (which you always seem to have a few of), that is were you can have a problem: you are likely retaining a reference to ALL 2000 of the original chars, which is a huge waste of memory!

		Let me prove this statement (this applies at least thru jdk 6u14, and has been true for years, but hey it could change at any time in the future, so always recheck).

		Here is how the fundamental String.substring method is implemented (copied from the jdk source code):

			public String substring(int beginIndex, int endIndex) {
				// arg checks suppressed--brent

				return ((beginIndex == 0) && (endIndex == count)) ? this :
					new String(offset + beginIndex, endIndex - beginIndex, value);
			}

		Typically, the indices will have changed, so the last clause above (the "new String...") is what gets returned.  Here is what that constructor looks like:

			// Package private constructor which shares value array for speed.
			String(int offset, int count, char value[]) {
				this.value = value;
				this.offset = offset;
				this.count = count;
			}

		Also, note that the value field that is passed to that String constructor by substring is the fundamental chars stored by the String:

			private final char value[];

		So, when you do a substring call, it does a little checking and logic, all of which executes extremely quickly, and then a new String object is created which shares the SAME underlying char[] as the original (often much larger) parent String.  In particular, note that no copying of the char[] is ever done.  So, substring as a hole still executes very quickly.

		This is quite clever coding on Sun's part, and overall I approve, but the one danger is if you have a huge initial String but long term only want to retain fragments of it.  If you used substring to obtain your fragments, then unbeknownst to most programmers is the subtle danger that they are in fact retaining reference to all the original chars, most of which are garbage.  Sun's javadocs ought to warn of this danger; file a bug report!

		Incidentally, StringBuilder/StringBuffer toString() also USED to (up thru 1.4.x) use this same char[] sharing approach, but subsequent jdk code copies just the used characters of the char[].  See
			http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=cfea61c3a545dffffffffe0f5029c6f03b73?bug_id=6219959
		for more discussion.  The evaluation section is excellent. The author notes that copying can, in fact, sometimes lead to overal performance improvement, in addition to saving memory.

		So, how do you cure this potential memory inefficiency if you are using substring?  Answer: use a String constructor that copies precisely just those chars that are actually used.  Here is the jdk code for the String(String) constructor that my string method uses:

			public String(String original) {
				int size = original.count;
				char[] originalValue = original.value;
				char[] v;
				if (originalValue.length > size) {
					// The array representing the String is bigger than the new
					// String itself.  Perhaps this constructor is being called
					// in order to trim the baggage, so make a copy of the array.
						int off = original.offset;
						v = Arrays.copyOfRange(originalValue, off, off+size);
				} else {
					// The array representing the String is the same
					// size as the String, so no point in making a copy.
					v = originalValue;
				}
				this.offset = 0;
				this.count = size;
				this.value = v;
			}

		Notice how it trims garbage chars.
	*/
	
	/**
	* Converts bytes into a char[] which is returned.
	* <p>
	* The conversion is done using a new {@link CharsetDecoder}
	* created from this platform's {@link Charset#defaultCharset default Charset}.
	* Strict conversion mode is used
	* (i.e. all errors result in Exceptions; no char substitutions or other silent error handling is performed).
	* So, bytes must be perfectly encoded using this platform's default encoding.
	* <p>
	* @throws IllegalArgumentException if bytes is null
	* @throws MalformedInputException if an illegal byte sequence for this charset is encountered
	* @throws UnmappableCharacterException if a byte sequence is encountered which cannot be mapped to an equivalent character sequence
	* @throws CharacterCodingException if a decoding problem occurs
	*/
	public static char[] bytesToChars(byte[] bytes) throws IllegalArgumentException, MalformedInputException, UnmappableCharacterException, CharacterCodingException {
		Check.arg().notNull(bytes);
		
		//return new String(bytes).toCharArray();
		//return Charset.defaultCharset().decode( ByteBuffer.wrap(bytes) ).array();
			// do NOT use either of the above:
			//	a) has bad performance due to extra array generation and copy
			//	b) both have the bad choice of CodingErrorAction.REPLACE for onMalformedInput/onUnmappableCharacter.
			// Instead, custom create a CharsetDecoder that throws Exceptions:
		CharsetDecoder decoder = Charset.defaultCharset().newDecoder();
		decoder.onMalformedInput( CodingErrorAction.REPORT );
		decoder.onUnmappableCharacter( CodingErrorAction.REPORT );
		return decoder.decode( ByteBuffer.wrap(bytes) ).array();
	}
	
	// -------------------- equals, equalChars --------------------
	
	/**
	* Determines whether or not s1 is equal to s2.
	* The motivation for this method is that it correctly handles nulls, which frees the user from having to do that repetitive check.
	* Specifically, it first checks for <code>s1 == null</code>, and if that is true returns the value of <code>s2 == null</code>).
	* Otherwise, if s1 is not null, then it returns <code>s1.equals(s2)</code>.
	*/
	public static boolean equals(String s1, String s2) {
		if (s1 == null) return (s2 == null);
		else return s1.equals(s2);
	}
// +++ this method is actually generic--should use a generic type T--and so it should be put in some other class...
	
	/**
	* Determines whether a <code>char[]</code> has exactly the same chars as a <code>String</code>.
	* If one of the args, is null, then the other must be null as well for true to be returned.
	*/
	public static boolean equalChars(char[] chars, String s) {
		if (chars == null) return (s == null);
		else if (s == null) return false;
		
		if (chars.length != s.length()) return false;
		
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] != s.charAt(i)) return false;
		}
		
		return true;
	}
	
	// -------------------- ensureSuffix --------------------
	
	/**
	* Returns s itself if s already ends with suffix, else returns s concatenated with suffix.
	* <p>
	* @throws IllegalArgumentException if s or suffix is null
	*/
	public static String ensureSuffix(String s, char suffix) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		if (s.length() > 0) {
			char charLast = s.charAt(s.length() - 1);
			if (charLast == suffix) return s;
		}
		return s + suffix;
	}
	
	/**
	* Returns s itself if s already ends with suffix, else returns s concatenated with suffix.
	* <p>
	* @throws IllegalArgumentException if s or suffix is null
	*/
	public static String ensureSuffix(String s, String suffix) throws IllegalArgumentException {
		Check.arg().notNull(s);
		Check.arg().notNull(suffix);
		
		if (s.endsWith(suffix)) return s;
		else return s + suffix;
	}
	
	// -------------------- toLength, repeatChars, getTabs, keepWithinLength --------------------
	
	/**
	* Returns a String which represents number and consists of exactly length digits, with leading 0's padded if necessary.
	* <p>
	* @throws IllegalArgumentException if number is such that it cannot be represented by length digits
	*/
	public static String toLength(int number, int length) throws IllegalArgumentException {
		return toLength( Integer.toString(number), length, true, '0' );
	}
	
	/**
	* Returns a String of exactly length chars.
	* If s.length() == length, then s is returned.
	* Otherwise, c is added to s enough times to make the result have length chars.
	* If prepend is true, then c prepended to s, else it is appended.
	* <p>
	* @throws IllegalArgumentException if s == null; s.length() > length
	*/
	public static String toLength(String s, int length, boolean prepend, char c) throws IllegalArgumentException {
		Check.arg().notNull(s);
		if (s.length() > length) throw new IllegalArgumentException("s.length() = " + s.length() + " > length = " + length);
		
		if (s.length() == length) return s;
		
		StringBuilder sb = new StringBuilder(length);
		int numberToAdd = length - s.length();
		if (prepend) {
			for (int i = 0; i < numberToAdd; i++) {
				sb.append(c);
			}
		}
		sb.append(s);
		if (!prepend) {
			for (int i = 0; i < numberToAdd; i++) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	/**
	* Returns a String of the specified length which consists of entirely of the char c.
	* <p>
	* Contract: the result is never null, but will be empty if length = 0.
	* <p>
	* @param length the number of chars in the result
	* @throws IllegalArgumentException if length is negative
	*/
	public static String repeatChars(char c, int length) throws IllegalArgumentException {
		Check.arg().notNegative(length);
		
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(c);
		}
		return sb.toString();
	}
	
	/**
	* Convenience method that returns the <i>equivalent of</i> <code>{@link #repeatChars repeatChars}('\t', numberTabs)</code>.
	* (For top performance, when numberTabs is small, which it usually is, this method returns an appropriate String constant.
	* Otherwise, it makes a call to repeatChars.)
	* One use is to create indentation levels when formatting output.
	* <p>
	* @param numberTabs the number of tab chars in the result
	* @throws IllegalArgumentException if numberTabs is negative
	*/
	public static String getTabs(int numberTabs) throws IllegalArgumentException {
		// numberTabs checked by repeatChars below
		
		switch (numberTabs) {
			case 0: return "";
			case 1: return "\t";
			case 2: return "\t\t";
			case 3: return "\t\t\t";
			case 4: return "\t\t\t\t";
			case 5: return "\t\t\t\t\t";
			default: return repeatChars('\t', numberTabs);
		}
	}
	
	/**
	* Returns s if its length is less than limitLength.
	* Otherwise tries to return as much from both the beginning and end of s as it can
	* with an ellipsis "..." in the middle of the result to indicate that the middle was cut out.
	* For example, <code>keepWithinLength("abcdefghi", 7)</code> returns <code>"ab...hi"</code>.
	* <p>
	* @throws IllegalArgumentException if s == null; limitLength < 5
	*/
	public static String keepWithinLength(String s, int limitLength) throws IllegalArgumentException {
		Check.arg().notNull(s);
		if (limitLength < 5) throw new IllegalArgumentException("limitLength = " + limitLength + " is < 5");
		
		if (s.length() <= limitLength) return s;
		
		int numbersFromStart = (int) Math.ceil( (limitLength - 3) / 2.0 );
		String start = s.substring(0, numbersFromStart);
		int numbersFromEnd = (int) Math.floor( (limitLength - 3) / 2.0 );
		String end = s.substring(s.length() - numbersFromEnd, s.length());
		return start + "..." + end;
	}
	
	// -------------------- arraysToTextColumns, toMatrix --------------------
	
	/**
	* Returns a String representation of arrays as a series of text columns.
	* <p>
	* Since arrays is a double[][], then arrays[0], arrays[1], etc are double[] subarrays.
	* The result takes each subarray and uses it as a column of text:
	* arrays[0] is the first column, arrays[1] is the second column, etc.
	* <p>
	* Each row of the result is tab delimited: tab chars separate the numbers from each other.
	* Note that the subarrays need not have equal length:
	* this method will simply leave blanks in cells that have no data.
	* <p>
	* The header arg is optional (may be null),
	* but if present, must have the same length as the number of subarrays (i.e. arrays.length)
	* because it will be used to label each column.
	* <p>
	* To illustrate, the following code
	* <pre><code>
	*	double[][] arrays = new double[][] {
	*		new double[] {1},
	*		new double[] {1, 2},
	*		new double[] {1, 2, 3}
	*	};
	*	String[] header = new String[] {"A", "B", "C"};
	*	System.out.println( arraysToTextColumns(arrays, header) );
	* </code></pre>
	* produces this output:
	* <pre><code>
	*	A       B       C
	*	1.0     1.0     1.0
	*			2.0     2.0
	*					3.0
	* </code></pre>
	* <p>
	* @throws IllegalArgumentException if arrays == null;
	* header != null && header.length != arrays.length
	*/
	public static String arraysToTextColumns(double[][] arrays, String[] header) throws IllegalArgumentException {
		Check.arg().notNull(arrays);
		if ((header != null) && (header.length != arrays.length)) throw new IllegalArgumentException("header != null && header.length = " + header.length + " != arrays.length = " + arrays.length);
		
		int numberColumns = arrays.length;
		int numberDataRows = 0;
		for (double[] subarray : arrays) {
			if (numberDataRows < subarray.length) numberDataRows = subarray.length;
		}
		
		StringBuilder sb = new StringBuilder( numberDataRows * numberColumns * 32 );
		
		if (header != null) {
			for (int k = 0; k < header.length; k++) {
				if (k > 0) sb.append('\t');
				sb.append(header[k]);
			}
			sb.append('\n');
		}
		
		for (int j = 0; j < numberDataRows; j++) {
			for (int i = 0; i < numberColumns; i++) {
				if (i > 0) sb.append('\t');
				if (j < arrays[i].length) sb.append(arrays[i][j]);
			}
			sb.append('\n');
		}
		
		return sb.toString();
	}
	
	/**
	* Returns <code>{@link #toMatrix(CharSequence, Pattern, Pattern) toMatrix}(cs, {@link #lineTerminatorPattern}, {@link #spaceTabCommaPattern})</code>.
	* So, this convenience version can parse CharSequences where the rows are either space, tab, or comma delimited.
	* <p>
	* <b>Warning:</b> s must use spaces, tabs, or commas <i>only</i> as delimiters;
	* these characters cannot appear anywhere else (e.g. inside what the user thinks should be a token).
	* This means that this method cannot parse CharSequences that come from, say, true CSV files
	* (parsing these requires something more complicated; see <a href="http://stackoverflow.com/questions/1441556/parsing-csv-input-with-a-regex-in-java">this webpage</a>).
	*/
	public static String[][] toMatrix(CharSequence cs) throws IllegalArgumentException {
		return toMatrix(cs, lineTerminatorPattern, spaceTabCommaPattern);
	}
	
	/**
	* Returns a matrix representation of cs.
	* The result is always a rectangular matrix
	* (i.e. every row has the same number of elements; nulls being appended if necessary).
	* <p>
	* @param cs the CharSequence to be parsed
	* @param rowDelimiter the regex used to split cs into rows
	* @param columnDelimiter the regex used to split each row into column tokens
	* @throws IllegalArgumentException if any arg is null
	*/
	public static String[][] toMatrix(CharSequence cs, Pattern rowDelimiter, Pattern columnDelimiter) throws IllegalArgumentException {
		Check.arg().notNull(cs);
		Check.arg().notNull(rowDelimiter);
		Check.arg().notNull(columnDelimiter);
		
			// parse cs into rows:
		String[] rows = rowDelimiter.split(cs, 0);	// CRITICAL: use 0 and not -1 since want to discard any trailing empty strings
		
			// parse each row into its column tokens; can assign an initial version of the result during this step:
		String[][] matrix = new String[rows.length][];
		int numColsMax = 0;
		for (int i = 0; i < rows.length; i++) {
			matrix[i] = columnDelimiter.split(rows[i]);
			numColsMax = Math.max( matrix[i].length, numColsMax );
		}
		
			// ensure that every row has the same number of elements, null padding if necessary:
		for (int i = 0; i < rows.length; i++) {
			if (matrix[i].length < numColsMax) {
				String[] rowNew = new String[numColsMax];
				System.arraycopy(matrix[i], 0, rowNew, 0, matrix[i].length);
				matrix[i] = rowNew;
			}
		}
		
		return matrix;
	}
/*
+++ to really use the result, need a MatrixUtil class with methods like
	getColumn()
	getColumnNumber(int j)
*/
	
	// -------------------- isAllAsciiChars, asciiBytesToChars, toStringAscii --------------------
	
	/**
	* Determines whether or not s consists exclusively of <a href="http://en.wikipedia.org/wiki/ASCII">US-ASCII</a> chars.
	* <p>
	* The implementation here scans thru the chars of s
	* and returns false upon the first char encountered which is not {@link CharUtil#isAscii an ASCII value}.
	* Only if no such char is encountered is true returned.
	* Note: this algorithm safely handles all
	* <a href="http://en.wikipedia.org/wiki/Unicode">Unicode 4.0</a>
	* <a href="http://en.wikipedia.org/wiki/Code_points">code points</a>,
	* including all supplementary code points,
	* which Java's <a href="http://en.wikipedia.org/wiki/UTF-16">UTF-16</a> encoding uses a surrogate pair (i.e. two consecutive chars) for.
	* <p>
	* @throws IllegalArgumentException if s is null
	*/
	public static boolean isAllAsciiChars(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (!CharUtil.isAscii(c)) return false;	// Note: if c is either part of a surrogate pair, it will have a value which fails CharUtil.isAscii
		}
		return true;
	}
	
	/**
	* Converts each element of bytes to a char[] which is returned.
	* Every element of bytes must be a <a href="http://en.wikipedia.org/wiki/ASCII">US-ASCII</a> byte
	* (i.e. high bit must be 0, so the only permissible values are [0, 127]).
	* This method is much faster than going thru the standard Java ASCII converter.
	* <p>
	* @throws IllegalArgumentException if bytes == null; if bytes contains a non-ASCII byte (i.e. a negative value)
	*/
	public static char[] asciiBytesToChars(byte[] bytes) throws IllegalArgumentException {
		Check.arg().notNull(bytes);
		
		char[] chars = new char[bytes.length];
		for (int i = 0; i < bytes.length; i++) {
			byte b = bytes[i];
			Check.arg().notNegative(b);
			chars[i] = (char) b;
		}
		return chars;
	}
	
	// NOTE: asciiBytesToChars currently does not use this method, so would need to add it back in if want its functionality; see also UnitTest.test_diagnoseProblem
	/** Meant for use just by asciiBytesToChars to provide a better diagnostic message to the user. */
	private static String diagnoseProblem(byte[] bytes, int i) {
		return
			'\n' +
			"----------" + '\n' +
			"Context of chars about the offending byte:" + '\n' +
			getAsciiContext(bytes, i) + '\n' +
			"----------" + '\n' +
			"Hamming distance ranking of all US-ASCII chars from the offending byte:" + '\n' +
			describeAsciiCharsByHammingDistance(bytes[i]);
	}
	
	private static final int numCharsContext = 16;
	
	/** Meant for use just by diagnoseProblem to provide a better diagnostic message to the user. */
	private static String getAsciiContext(byte[] bytes, int i) {
		int start = Math.max( i - numCharsContext, 0 );
		int end = Math.min( i + numCharsContext, bytes.length - 1 );
		char[] chars = new char[end - start + 1];
		for (i = 0; i < chars.length; i++) {
			byte b = bytes[start + i];
			chars[i] = (b >= 0) ? (char) b : '?';
		}
		return new String(chars);
	}
	
	/** Meant for use just by diagnoseProblem to provide a better diagnostic message to the user. */
	private static String describeAsciiCharsByHammingDistance(byte b) {
		SortedMap<Integer,List<Character>> map = rankAsciiCharsByHammingDistance(b);
		StringBuilder sb = new StringBuilder(512);
		for (int distance : map.keySet()) {
			sb.append(distance).append(": ");
			boolean previous = false;
			for (char c : map.get(distance)) {
				if (previous) sb.append(", ");
				else previous = true;
				sb.append(c);
			}
			sb.append('\n');
		}
		return sb.toString();
	}
	
	/** Meant for use just by describeAsciiCharsByHammingDistance to provide a better diagnostic message to the user. */
	private static SortedMap<Integer,List<Character>> rankAsciiCharsByHammingDistance(byte b) {
		int bb = Math2.byteToUnsignedInt(b);
		SortedMap<Integer,List<Character>> map = new TreeMap<Integer,List<Character>>();
		//for (int i = 32; i <= 126; i++) {	// the printable ASCII chars
		for (int i = 0; i <= 127; i++) {	// the complete ASCII chars
			int distance = Math2.hammingDistance(i, bb);
			List<Character> list = map.get(distance);
			if (list == null) {
				list = new ArrayList<Character>();
				map.put(distance, list);
			}
			list.add((char) i);
		}
		return map;
	}
	
	/**
	* Converts s into an equivalent String that consists solely of <a href="http://en.wikipedia.org/wiki/ASCII">US-ASCII</a> chars.
	* Each char from s that is already a US-ASCII char is directly used in the result.
	* All other chars are represented as a <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#100850">Java Unicode escape sequence</a>
	* (i.e. have the format &#92;uXXXX where XXXX is the hexadecimal value of the char).
	* <p>
	* One use for this method is when a String must be printed, but it is possible that the char convertor will fail to represent certain chars.
	* <p>
	* @throws IllegalArgumentException if s == null
	*/
	public static String toStringAscii(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		StringBuilder sb = new StringBuilder( 2 * s.length() );	// here is how got factor of 2: assume that 80% of the chars are ascii, so that 20% will need to be represented as a Unicode escape sequence, then expected size = (.8 * 1) + (.2 * 6) = 2
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (CharUtil.isAscii(c)) {
				sb.append(c);
			}
			else {
				String hex = Integer.toHexString(c);
				String hex4digits = toLength(hex, 4, true, '0');
				sb.append("\\u").append( hex4digits );
			}
		}
		return sb.toString();
	}
	
	// -------------------- isBlank, isTrimmable --------------------
	
	/** Determines whether or not s is "blank" (i.e. is either null, zero-length, or solely consists of whitespace). */
	public static boolean isBlank(String s) {
		if (s == null) return true;
		if (s.length() == 0) return true;
		
		for (int i = 0; i < s.length(); i++) {
			if ( !Character.isWhitespace( s.charAt(i) ) )
				return false;
		}
		return true;
	}
	
	/**
	* Determines whether or not s is "trimmable" (i.e. either begins and/or ends with a whitespace char).
	* In other words, it determines if a call to s.{@link String#trim trim} would return a result that differs from s.
	* Special cases: if s is either null or zero-length, this method immediately returns false.
	*/
	public static boolean isTrimmable(String s) {
		if ((s == null) || (s.length() == 0)) return false;
		
		char cFirst = s.charAt(0);
		if (Character.isWhitespace(cFirst)) return true;
		
		char cLast = s.charAt( s.length() - 1 );
		if (Character.isWhitespace(cLast)) return true;
		
		return false;
	}
	
	// -------------------- isNewLineEnd, ensureEndsInNewLine --------------------
	
	/**
	* This method determines whether or not the supplied String ends in a newline char.
	* <p>
	* The {@link #newline} constant of this class is the newline char sequence that is used.
	* <p>
	* @throws IllegalArgumentException if s is null
	*/
	public static boolean isNewLineEnd(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		return s.endsWith(newline);
	}
	
	/**
	* This method returns the supplied String if it already ends in a newline char sequence.
	* Otherwise, the result is a newline char sequence appended to the original.
	* <p>
	* The {@link #newline} constant of this class is the newline char sequence that is used.
	* <p>
	* @throws IllegalArgumentException if s is null
	*/
	public static String ensureEndsInNewLine(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		if ( isNewLineEnd(s) )
			return s;
		else
			return s + newline;
	}
	
	// -------------------- normalizeWhitespace --------------------
	
	/**
	* Performs <i>whitespace normalization</i>, as per the XML spec.
	* <p>
	* @throws IllegalArgumentException if s is null
	* @see <a href="http://www.w3.org/TR/html401/struct/text.html#h-9.1">the HTML whitespace normalization spec</a>
	* @see <a href="www.w3.org/TR/REC-xml#AVNormalize">the XML whitespace normalization spec</a>
<!-- when i checked the above url on 2004/6/17, it now refers to a new spec and i was unable to find the section that i wanted... -->
	*/
	public static String normalizeWhitespace(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		StringBuilder result = new StringBuilder( s.length() );
		boolean lastWasWhitespace = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c)) {
				lastWasWhitespace = true;
			}
			else {
				if (lastWasWhitespace) {
					if (result.length() > 0) result.append(' ');	// need result.length() > 0 to enforce skipping of leading whitespace
					lastWasWhitespace = false;
				}
				result.append(c);
			}
		}
		
		return result.toString();
	}
	
	// -------------------- indentLines, parseLines --------------------
	
	/**
	* Returns <code>{@link #indentLines(String, int) indentLines}(s, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if s is null
	*/
	public static String indentLines(String s) throws IllegalArgumentException {
		return indentLines(s, 1);
	}
	
	/**
	* Parses individual lines out of s by a call to <code>{@link #parseLines(String, boolean) parseLines}(s, true)</code>.
	* Then returns a new String which consists of the concatenation of every line
	* after each line is first preceded with an indent equal to the number of tab chars specified by numberTabs.
	* <p>
	* The result is never empty.
	* It contains at least numberTabs tab chars.
	* It may or may not end with end of line sequence char(s), depending on whether or not s does.
	* <p>
	* @param s the String to split into lines and indent
	* @param numberTabs the number of tab chars to use in each line's indent
	* @throws IllegalArgumentException if s is null; numberTabs is negative
	*/
	public static String indentLines(String s, int numberTabs) throws IllegalArgumentException {
		Check.arg().notNull(s);
		Check.arg().notNegative(numberTabs);
		
		String[] lines = parseLines(s, true);
		String indent = getTabs(numberTabs);
		StringBuilder sb = new StringBuilder( lines.length * 64 );
		for (String line : lines) {
			sb.append(indent).append(line);
		}
		return sb.toString();
	}
	
	/**
	* Returns <code>{@link #parseLines(String, boolean) parseLines}(s, false)</code>.
	* <p>
	* @throws IllegalArgumentException if s is null
	*/
	public static String[] parseLines(String s) throws IllegalArgumentException {
		return parseLines(s, false);
	}
	
	/**
	* Parses individual lines out of s, which are collectively returned as String[].
	* <p>
	* The result is never empty.
	* It contains exactly one element if s has no end of line sequences,
	* which includes the special case that s is zero-length (i.e. "").
	* If includeEol == true, then the concatenating every element in the result
	* (e.g. by calling {@link #toString(Object[], String) toString}(lines, "")) reconstitutes s exactly.
	* <p>
	* @param s the String to split into lines
	* @param includeEol specifies whether or not to include the end of line sequence char(s) in the lines
	* @throws IllegalArgumentException if s is null
	*/
	public static String[] parseLines(String s, boolean includeEol) throws IllegalArgumentException {
		Check.arg().notNull(s);
		if (s.length() == 0) return new String[] {""};
		
		List<String> lines = initList(s);
		int start = 0;
		for (int i = 0; i < s.length(); i++) {
			switch (s.charAt(i)) {
				case '\n':
					int end = includeEol ? i + 1 : i;
					lines.add( s.substring(start, end) );
					start = includeEol ? end : end + 1;
					break;
				case '\r':
					if (nextCharNewline(s, i)) {
						end = includeEol ? i + 2 : i;
						lines.add( s.substring(start, end) );
						start = includeEol ? end : end + 2;
						++i;	// CRITICAL: need an extra increment (before do the normal loop increment) to get past the 2 char \r\n sequence
					}
					else {
						end = includeEol ? i + 1 : i;
						lines.add( s.substring(start, end) );
						start = includeEol ? end : end + 1;
					}
					break;
				default:
					// continue loop
			}
		}
		if (start < s.length()) {	// CRITICAL: grab any final text that does not end with a EOL
			lines.add( s.substring(start, s.length()) );
		}
		return lines.toArray( new String[lines.size()] );
	}
	/*
	Note: I once tried to implement the above with a regular expression, like calling s.split("\r?\n", -1), but this fails for pure Mac (\r only) files.
	Furthermore, String.split never includes the regex in its result, so the includeEol == true mode can never work.
	You CAN do the method above with regular expressions, but will have to do it at a lower level with Pattern and Matcher; see for example
		http://java.sun.com/j2se/1.4.2/docs/guide/nio/example/Grep.java
	I decided to retain the non-regex code above, even tho it is somewhat longer, because it should be top performance.
	*/
	
	private static List<String> initList(String s) {
		int lineCountGuess = (int) Math.ceil( s.length() / 20.0 );	// guess that each line has 20 chars on average; overestimating the line count prevents unnecessary array resizes above, albeit at possibly higher memory usage
		int size = Math.max(lineCountGuess, 16);	// insist that the size is at least 16
		return new ArrayList<String>(size);
	}
	
	private static boolean nextCharNewline(String s, int i) {
		if (i == s.length() - 1) return false;
		
		return (s.charAt(i + 1) == '\n');
	}
	
	// -------------------- splitByLiteral, splitByChar --------------------
	
	/**
	* Splits s into tokens.
	* These tokens are delimited by delimiter (one or more chars, always treated literally, and never included as part of a token).
	* <p>
	* Contract: the result is {@link Check#notEmpty never empty}.
	* This includes the special case that s is the empty String "", in which case the result has a single element that is "".
	* Furthermore, if nIsExact is true, then it is guaranteed to have exactly n elements.
	* The result's type always implements {@link RandomAccess}, so its {@link List#get(int)} method will be about as fast as an array's access.
	* Finally, the result should always be equivalent to calling {@link String#split(String, int) String.split}(delimiter, -1),
	* assuming that delimiter contains no special chars so that it too would be treated literally by the regex,
	* and that the String[] returned by String.split is compared element by element with the List returned by this method.
	* <p>
	* The reason why this method was written
	* is because its literal treatment of delimiter allows a more optimized parsing algorithm to be used.
	* (It is 3-4 times faster than String.split, and 2+ times faster than Pattern.split, results depending on the String to be split;
	* see {@link UnitTest#benchmark_splitByLiteral UnitTest.benchmark_splitByLiteral} for details.)
	* <p>
	* @param s the String to split into tokens
	* @param delimiter the token delimiting chars
	* @param n the expected number of tokens
	* @param nIsExact if true, then s must contain exactly n tokens; if false, n is merely a hint that the implementation can use for optimization
	* @throws IllegalArgumentException if s == null; delimiter is null or zero-length; n < 1; nIsExact is true and s fails to split into exactly n tokens
	*/
	public static List<String> splitByLiteral(String s, String delimiter, int n, boolean nIsExact) throws IllegalArgumentException {
		Check.arg().notNull(s);
		Check.arg().notNull(delimiter);
		if (delimiter.length() == 0) throw new IllegalArgumentException("delimiter.length() == 0");
		Check.arg().positive(n);
		
		List<String> list = new ArrayList<String>(n);
		if (s.length() == 0) {
			list.add("");
		}
		else {
			int start = 0;
			while (start < s.length()) {
				int i = s.indexOf(delimiter, start);
				if (i == -1) i = s.length();
				list.add( s.substring(start, i) );
				start = i + delimiter.length();
			}
			if (s.endsWith(delimiter)) {	// must add a final "" token if s ends with delimiter in order to perfectly mimic the behavior of String.split(delimiter, -1)
				list.add("");
			}
		}
		Check.state().notEmpty(list);
		if (nIsExact && (list.size() != n)) throw new IllegalArgumentException("s split into " + list.size() + " tokens, which is != the required n = " + n + " tokens");
		return list;
	}
	
	/**
	* Identical to {@link #splitByLiteral splitByLiteral} except that the token delimiter is restricted to a single char,
	* which allows an even more optimized algorithm to be used.
	* (It is 1.5-2 times faster than splitByLiteral; see {@link UnitTest#benchmark_splitByChar UnitTest.benchmark_splitByChar} for details.)
	* <p>
	* @param s the String to split into tokens
	* @param delimiter the token delimiting char
	* @param n the expected number of tokens
	* @param nIsExact if true, then s must contain exactly n tokens; if false, n is merely a hint that the implementation can use for optimization
	* @throws IllegalArgumentException if s == null; n < 1; nIsExact is true and s fails to split into exactly n tokens
	*/
	public static List<String> splitByChar(String s, char delimiter, int n, boolean nIsExact) throws IllegalArgumentException {
		Check.arg().notNull(s);
		Check.arg().positive(n);
		
		List<String> list = new ArrayList<String>(n);
		if (s.length() == 0) {
			list.add("");
		}
		else {
			int length = s.length();
			int start = 0;
			char c = 0;
			for (int i = 0; i < length; i++) {
				c = s.charAt(i);
				if (c == delimiter) {
					list.add( s.substring(start, i) );
					start = i + 1;
				}
			}
				// at this point, c is the last char of s, and always must add a token of some sort:
			if (c == delimiter) {	// if s ends with delimiter then must add a final "" token in order to perfectly mimic the behavior of String.split(delimiter, -1)
				list.add("");
			}
			else {	// else the final token must be all the remaining chars of s from start:
				list.add( s.substring(start, length) );
			}
		}
		Check.state().notEmpty(list);
		if (nIsExact && (list.size() != n)) throw new IllegalArgumentException("s split into " + list.size() + " tokens, which is != the required n = " + n + " tokens");
		return list;
	}
	
	// -------------------- quoteWhitespaceTokenize, removeQuotes --------------------
	
	/**
	* This method breaks up the supplied String into tokens.
	* Tokens are delimited either by a sequence of double quote (i.e. '"') chars,
	* or by whitespace chars.
	* <p>
	* The procedure followed is to find the next occurring double quote char.
	* The substring of all untokenized chars before that double quote char is then tokenized
	* using whitespace chars as delimiters. Then, the next token is the substring which
	* consists of all chars from the current double quote till the next double quote.
	* This procedure is repeated until the source String is exhausted.
	* <p>
	* It is an error if the source String has an odd number of double quote chars
	* (i.e. they must occur in pairs).
	* <p>
	* An example application of this method is to parse command lines.
	* Here, command line arguments are normally separated by spaces.
	* However, double quotes are used to enclose those args which have spaces inside them.
	* <p>
	* @param source the String to be tokenized
	* @param includeQuotes specifies whether or not to include double quote marks with those tokens that are delimted by them
	* @throws IllegalArgumentException if source is null or if it contains an odd number of double quote chars
	*/
	public static String[] quoteWhitespaceTokenize(String source, boolean includeQuotes) throws IllegalArgumentException {
		Check.arg().notNull(source);
		
		List<String> tokens = new ArrayList<String>( Math.max(source.length()/8, 16) );		// i.e. guesstimate that the average token is 8 chars in length, and insist that our minimum initial capacity be 16
		
		int index = 0;
		while (index < source.length()) {
			int startQuoteIndex = source.indexOf('"', index);
			
			if (startQuoteIndex == -1) {
				wsTokensize(source.substring(index), tokens);
				break;
			}
			else {
				wsTokensize( source.substring(index, startQuoteIndex), tokens );
				
				int endQuoteIndex = source.indexOf('"', startQuoteIndex + 1);
				if (endQuoteIndex == -1) throw new IllegalArgumentException("there is a double quote char at index = " + startQuoteIndex + " which does not have a subsequent matching quote in the source String = " + source);
				
				if (includeQuotes)
					tokens.add( source.substring(startQuoteIndex, endQuoteIndex + 1) );
				else
					tokens.add( source.substring(startQuoteIndex + 1, endQuoteIndex) );
					
				index = endQuoteIndex + 1;
			}
		}
		
		return tokens.toArray( new String[tokens.size()] );
	}
	
	private static void wsTokensize(String source, List<String> tokens) {
		int index = 0;
		while (true) {
				// skip over all whitespace chars; when done, index points to next non-whitespace char (or to source.length()):
			for ( ; index < source.length(); index++) {
				if ( !Character.isWhitespace( source.charAt(index) ) )
					break;
			}
			if (index == source.length()) return;
			
				// skip over all non-whitespace chars; when done, index points to next whitespace char (or to source.length()):
			int tokenStartIndex = index;
			for ( ; index < source.length(); index++) {
				if ( Character.isWhitespace( source.charAt(index) ) )
					break;
			}
			tokens.add( source.substring(tokenStartIndex, index) );
			if (index == source.length()) return;
			
				// may as well increment index so that do not retest the current char in the first for loop above:
			++index;
		}
	}
	
	/**
	* This utility method removes a <i>matching leading and trailing pair</i> of quote marks, if present,
	* from the supplied String and returns the substring inside the quotes.
	* If no such pair of leading or trailing quotes are present, then the original String is returned.
	* <p>
	* Any leading and trailing quote marks must be either both single or both double quote chars
	* in order to match.
	* <p>
	* @param lineNumber the line number where the String was found; used only if throw a ParseException
	* @throws IllegalArgumentException if s is null, or lineNumber < 0
	* @throws ParseException if a leading but no matching trailing quote mark is present, or vice versa
	*/
	public static String removeQuotes(String s, int lineNumber) throws IllegalArgumentException, ParseException {
		Check.arg().notNull(s);
		Check.arg().notNegative(lineNumber);
		
			// handle the zero-length case before proceed with remaining code (which assumes length > 0):
		if (s.length() == 0)
			return s;
			
		int lastIndex = s.length() - 1;
		
			// strip matching single quotes if present, bomb if starts with single quote but does not end with one:
		if (s.charAt(0) == '\'') {
			if (s.charAt(lastIndex) == '\'')
				return s.substring(1, lastIndex);	// Note: lastIndex is exclusive in substring, so it is not included
			else
				throw new ParseException("the following String starts with a single quote, but does not end with one: " + s, lineNumber);
		}
			// bomb if ends with a single quote mark but does not start with one:
		else if (s.charAt(lastIndex) == '\'')
			throw new ParseException("the following String ends with a single quote, but does not start with one: " + s, lineNumber);
			
			// strip matching double quotes if present, bomb if starts with double quote but does not end with one:
		else if (s.charAt(0) == '"') {
			if (s.charAt(lastIndex) == '"')
				return s.substring(1, lastIndex);	// Note: lastIndex is exclusive in substring, so it is not included
			else
				throw new ParseException("the following String starts with a double quote, but does not end with one: " + s, lineNumber);
		}
			// bomb if ends with a double quote mark but does not start with one:
		else if (s.charAt(lastIndex) == '"')
			throw new ParseException("the following String ends with a double quote, but does not start with one: " + s, lineNumber);
			
		else
			return s;
	}
	
	// -------------------- toString --------------------
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(boolean[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (boolean element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(byte[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (byte element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(char[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (char element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(double[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (double element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of each array[i] on their own line,
	* with these elements in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(double[][] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		int m = array.length;
		int n = (m > 0) ? array[0].length : 0;
		StringBuilder sb = new StringBuilder( Math.max(16 * m * n, 1 * 1024) );
		for (double[] arrayInner : array) {
			if (sb.length() > 0) sb.append("\n");
			for (int i = 0; i < arrayInner.length; i++) {
				if (i > 0) sb.append(separator);
				sb.append(arrayInner[i]);
			}
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(float[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (float element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(long[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (long element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(short[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (short element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static String toString(int[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (int element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append(element);
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of array.
	* <p>
	* The result consists of the elements of array in sequence, with separator between each element.
	* If any element == null, then it is represented by the text "null".
	* <p>
	* @throws IllegalArgumentException if array == null; separator == null
	*/
	public static <T> String toString(T[] array, String separator) throws IllegalArgumentException {
		Check.arg().notNull(array);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * array.length, 1 * 1024) );
		for (T element : array) {
			if (sb.length() > 0) sb.append(separator);
			sb.append( element );
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of collection.
	* <p>
	* The result consists of the elements of collection as returned by its Iterator, with separator between each element.
	* If any element == null, then it is represented by the text "null".
	* <p>
	* @throws IllegalArgumentException if collection == null; separator == null
	*/
	public static String toString(Collection<?> collection, String separator) throws IllegalArgumentException {
		Check.arg().notNull(collection);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(16 * collection.size(), 1 * 1024) );
		for (Object element : collection) {
			if (sb.length() > 0) sb.append(separator);
			sb.append( element );
		}
		return sb.toString();
	}
	
	/**
	* Returns a String representation of map.
	* <p>
	* The result consists of map's key/value pairs, with separator between each pair.
	* The keys are obtained by map's keySet Iterator, and the text " --> " appears between the key and its value.
	* If any key or value == null, then it is represented by the text "null".
	* <p>
	* @throws IllegalArgumentException if map == null; separator == null
	*/
	public static String toString(Map<?,?> map, String separator) throws IllegalArgumentException {
		Check.arg().notNull(map);
		Check.arg().notNull(separator);
		
		StringBuilder sb = new StringBuilder( Math.max(32 * map.size(), 1 * 1024) );
		for (Map.Entry<?,?> entry : map.entrySet()) {
			if (sb.length() > 0) sb.append(separator);
			sb.append( entry.getKey() ).append(" --> ").append( entry.getValue() );
		}
		return sb.toString();
	}
	
	// -------------------- toStringLiteral --------------------
	
	/**
	* Converts a String into a series of chars that constitute a Java String literal.
	* In particular, double quotes are placed at the beginning and end, and any special chars
	* inside are properly escaped.
	* <p>
	* One use is that you could take the result and directly paste it into a Java source file.
	* Another use for this method is to handle filepaths with spaces and '\' chars
	* (which commonly occur in DOS).
	* <p>
	* @throws IllegalArgumentException if s == null
	*/
	public static String toStringLiteral(String s) throws IllegalArgumentException {
		Check.arg().notNull(s);
		
		StringBuilder sb = new StringBuilder(s.length() + 16);
		
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if ( CharUtil.hasEscapeForLiteral( c ) )	// this case will handle quotes, line terminator chars, and the backslash char (among others) that must be escaped
				sb.append( CharUtil.getEscapeForLiteral( c ) );
			else
				sb.append( c );
		}
		sb.append('"');
		
		return sb.toString();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private StringUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
// +++ need to add more tests, such as for bytesToChars...
		
		@Test public void test_newString() throws Exception {
				// first prove that substring shares the same underlying char[] as the parent String:
			String s1 = "abc123def";
			String s2 = s1.substring(3, 6);
			char[] value1 = (char[]) ReflectUtil.get(s1, "value");
			char[] value2 = (char[]) ReflectUtil.get(s2, "value");
			Assert.assertSame(value1, value2);
			Assert.assertEquals(value2.length, s1.length());
			
				// second prove that newString trims garbage chars:
			String s3 = newString(s2);
			char[] value3 = (char[]) ReflectUtil.get(s3, "value");
			Assert.assertNotSame(value3, value2);
			Assert.assertTrue(value3.length < value2.length);
		}
		
		@Test public void test_ensureSuffix() {
			Assert.assertEquals( "abc/", ensureSuffix("abc", '/') );
			Assert.assertEquals( "000123", ensureSuffix("000", "123") );
		}
		
		@Test public void test_toLength() {
			Assert.assertEquals( "000123", toLength(123, 6) );
			Assert.assertEquals( "helloWorldxxxxxxxxxx", toLength("helloWorld", 20, false, 'x') );
		}
		
		@Test public void test_repeatChars() {
			Assert.assertEquals( "aaaaa", repeatChars('a', 5) );
		}
		
		@Test public void test_keepWithinLength() {
			Assert.assertEquals( "ab...i", keepWithinLength("abcdefghi", 6) );
		}
		
		@Test public void test_arraysToTextColumns() {
			String textExpected =
				"A" + '\t' + "B" + '\t' + "C" + '\n' +
				"1.0" + '\t' + "1.0" + '\t' + "1.0" + '\n' +
				'\t' + "2.0" + '\t' + "2.0" + '\n' +
				'\t' + '\t' + "3.0" + '\n';
			
			double[][] arrays = new double[][] {
				new double[] {1},
				new double[] {1, 2},
				new double[] {1, 2, 3}
			};
			String[] header = new String[] {"A", "B", "C"};
			
			Assert.assertEquals( textExpected, arraysToTextColumns(arrays, header) );
		}
		
		@Test public void test_toMatrix() {
			String[][] matrixExpected = new String[][] {
				new String[] {"A", "B", "C", "D"},
				new String[] {"1", "2", "3", "4"},
				new String[] {"a", "b", "c", "d"},
				new String[] {"5", "6", "7", "8"},
				new String[] {"M", "L", "N", "O"},
				new String[] {"9", "10", "11", "12"}
			};
			
			String s =
				"A" + " " + "B" + "\t" + "C" + "," + "D" + "\r\n" +
				"1" + " " + "2" + "\t" + "3" + "," + "4" + "\n" +
				"a" + " " + "b" + "\t" + "c" + "," + "d" + "\r" +
				"5" + " " + "6" + "\t" + "7" + "," + "8" + "\u0085" +
				"M" + " " + "L" + "\t" + "N" + "," + "O" + "\u2028" +
				"9" + " " + "10" + "\t" + "11" + "," + "12" + "\u2029";

//			Assert.assertArrayEquals( matrixExpected, toMatrix(s) );
// not using the line above, since not sure that it handles [][] correctly...
			String[][] matrix = toMatrix(s);
			Assert.assertEquals( matrixExpected.length, matrix.length );
			for (int i = 0; i < matrix.length; i++) {
				Assert.assertArrayEquals( matrixExpected[i], matrix[i] );
			}
		}
		
		@Test public void test_isAllAsciiChars() {
			StringBuilder sb = new StringBuilder(128);
			for (int i = 0; i < 128; i++) {
				sb.append( (char) i );
			}
			String s = sb.toString();
			Assert.assertTrue( isAllAsciiChars(s) );
			
			sb = new StringBuilder(128);
			for (int i = 128; i < 256; i++) {
				sb.append( (char) i );
			}
			s = sb.toString();
			Assert.assertFalse( isAllAsciiChars(s) );
		}
		
		@Test public void test_asciiBytesToChars() {
			byte[] bytes = new byte[128];
			for (int i = 0; i < 128; i++) {
				bytes[i] = (byte) i;
			}
			asciiBytesToChars(bytes);
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_asciiBytesToChars_fail() {
			byte[] bytes = new byte[128];
			for (int i = 0; i < 128; i++) {
				bytes[i] = (byte) (i - 128);
			}
			asciiBytesToChars(bytes);
		}
		
		@Test public void test_toStringAscii() {
			StringBuilder sb = new StringBuilder(128);
			for (int i = 0; i < 256; i++) {
				sb.append( (char) i );
			}
			String s = sb.toString();
			System.out.println("Result of calling toStringAscii on the first 256 char values:");
			System.out.println(toStringAscii(s));
		}
		
		@Test public void test_isTrimmable() {
			Assert.assertEquals( false, isTrimmable(null) );
			Assert.assertEquals( false, isTrimmable("") );
			Assert.assertEquals( false, isTrimmable("abc") );
			Assert.assertEquals( true, isTrimmable(" abc") );
			Assert.assertEquals( true, isTrimmable("abc ") );
		}
		
		@Test public void test_normalizeWhitespace() {
			String s = "\t\r\n  abc\n\t\r\n def \t\r\n";
			String normalizationExpected = "abc def";
			Assert.assertEquals( normalizationExpected, normalizeWhitespace(s) );
		}
		
		@Test public void test_indentLines() {
			String input = "";	// empty String
			String outputExpected = "\t";
			Assert.assertEquals( outputExpected, indentLines(input) );
			
			input = "a";	// no line end sequence
			outputExpected = "\t\t" + "a";
			Assert.assertEquals( outputExpected, indentLines(input, 2) );
			
			input = "a\n" + "b\r" + "c\r\n";	// normal lines of text, albeit all 3 different types of line end sequences
			outputExpected = "\t" + "a\n" + "\t" + "b\r" + "\t" + "c\r\n";
			Assert.assertEquals( outputExpected, indentLines(input) );
		}
		
		@Test public void test_parseLines() {
				// test mode 1 (drop line end sequences):
			String input = "";	// empty String
			String[] outputExpected = new String[] {""};
			Assert.assertArrayEquals( outputExpected, parseLines(input) );
			
			input = "a";	// no line end sequence
			outputExpected = new String[] {"a"};
			Assert.assertArrayEquals( outputExpected, parseLines(input) );
			
			input = "a\n" + "b\r" + "c\r\n";	// normal lines of text, albeit all 3 different types of line end sequences
			outputExpected = new String[] {"a", "b", "c"};
			Assert.assertArrayEquals( outputExpected, parseLines(input) );
			
			input = "a\n" + "b\r" + "c\r\n" + "d";	// save as before, but add a final line that does not end with a line end sequence
			outputExpected = new String[] {"a", "b", "c", "d"};
			Assert.assertArrayEquals( outputExpected, parseLines(input) );
			
			input = "\n" + "\n" + "\r" + "\r" + "\r\n" + "\r\n";	// all empty lines
			outputExpected = new String[] {"", "", "", "", "", ""};
			Assert.assertArrayEquals( outputExpected, parseLines(input) );
			
				// test mode 2 (retain line end sequences):
			input = "";	// empty String
			outputExpected = new String[] {""};
			Assert.assertArrayEquals( outputExpected, parseLines(input, true) );
			
			input = "a";	// no line end sequence
			outputExpected = new String[] {"a"};
			Assert.assertArrayEquals( outputExpected, parseLines(input, true) );
			
			input = "a\n" + "b\r" + "c\r\n";	// normal lines of text, albeit all 3 different types of line end sequences
			outputExpected = new String[] {"a\n", "b\r", "c\r\n"};
			Assert.assertArrayEquals( outputExpected, parseLines(input, true) );
			
			input = "a\n" + "b\r" + "c\r\n" + "d";	// save as before, but add a final line that does not end with a line end sequence
			outputExpected = new String[] {"a\n", "b\r", "c\r\n", "d"};
			Assert.assertArrayEquals( outputExpected, parseLines(input, true) );
			
			input = "\n" + "\n" + "\r" + "\r" + "\r\n" + "\r\n";	// all empty lines
			outputExpected = new String[] {"\n", "\n", "\r", "\r", "\r\n", "\r\n"};
			Assert.assertArrayEquals( outputExpected, parseLines(input, true) );
		}
		
		@Test public void test_splitByLiteral_pass() {
				// s is empty:
			String s = "";
			List<String> tokensExpected = Arrays.asList( "" );
			List<String> tokensPresent = splitByLiteral(s, "-", 1, true);
			List<String> tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s is only a delimiter (2 tokens which are both a ""):
			s = "-";
			tokensExpected = Arrays.asList( "", "" );
			tokensPresent = splitByLiteral(s, "-", 2, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s is normal:
			s = "a" + "-" + "b" + "-" + "c";
			tokensExpected = Arrays.asList( "a", "b", "c" );
			tokensPresent = splitByLiteral(s, "-", 3, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s starts on delimiter (first token should then be a ""):
			s = "-" + "a" + "-" + "b" + "-" + "c";
			tokensExpected = Arrays.asList( "", "a", "b", "c" );
			tokensPresent = splitByLiteral(s, "-", 4, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s ends on delimiter (final token should then be a ""):
			s = "a" + "-" + "b" + "-" + "c" + "-";
			tokensExpected = Arrays.asList( "a", "b", "c", "" );
			tokensPresent = splitByLiteral(s, "-", 4, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
		}
		
		@Test(expected=AssertionError.class) public void test_splitByLiteral_fail1() {
			String s = "a" + "-" + "b" + "-" + "c";
			List<String> tokensExpected = Arrays.asList( "a", "b", "c", "d" );
			List<String> tokensPresent = splitByLiteral(s, "-", 1, false);
			Assert.assertEquals( tokensExpected, tokensPresent ); // should fail (tokensExpected added a bogus token)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByLiteral_fail2() {
			String s = null;
			splitByLiteral(s, "-", 3, true);	// should fail (null supplied)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByLiteral_fail3() {
			String s = "a" + "-" + "b" + "-" + "c";
			splitByLiteral(s, "-", 2, true); // should fail (too few tokens specified when nIsExact = true)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByLiteral_fail4() {
			String s = "a" + "-" + "b" + "-" + "c";
			splitByLiteral(s, "-", 4, true); // should fail (too many tokens specified when nIsExact = true)
		}
		
		/**
		* Results on 2009-06-26 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_14 server jvm):
		* <pre><code>
			s contains 3 tokens:
				splitByLiteral: first = 107.094 us, mean = 192.368 ns (CI deltas: -180.591 ps, +167.583 ps), sd = 1.987 us (CI deltas: -305.803 ns, +542.034 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
				String.split: first = 136.978 us, mean = 784.090 ns (CI deltas: -402.093 ps, +412.352 ps), sd = 2.323 us (CI deltas: -338.734 ns, +437.863 ns) WARNING: SD VALUES MAY BE INACCURATE
				Pattern.split: first = 75.389 us, mean = 459.917 ns (CI deltas: -427.344 ps, +409.991 ps), sd = 3.392 us (CI deltas: -398.413 ns, +570.629 ns) WARNING: execution times may have serial correlation, SD VALUES MAY BE INACCURATE
				
			s contains 10 tokens:
				splitByLiteral: first = 79.09 us, mean = 510.290 ns (CI deltas: -1.048 ns, +1.142 ns), sd = 6.255 us (CI deltas: -1.026 us, +1.481 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
				String.split: first = 157.356 us, mean = 1.421 us (CI deltas: -2.105 ns, +2.217 ns), sd = 8.728 us (CI deltas: -1.131 us, +1.496 us) WARNING: SD VALUES MAY BE INACCURATE
				Pattern.split: first = 76.199 us, mean = 1.109 us (CI deltas: -2.013 ns, +1.994 ns), sd = 8.100 us (CI deltas: -1.190 us, +1.702 us) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_splitByLiteral() throws Exception {
			//final String s = "a" + "-" + "b" + "-" + "c";	// 3 token version
			final String s = "a" + "-" + "b" + "-" + "c" + "-" + "d" + "-" + "e" + "-" + "f" + "-" + "g" + "-" + "h" + "-" + "i" + "-" + "j";	// 10 token version
			
			Runnable task1 = new Runnable() {
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					List<String> tokens = splitByLiteral(s, "-", 10, true);
					state ^= tokens.size();
				}
			};
			System.out.println("splitByLiteral: " + new Benchmark(task1));
			
				// use String.split:
			Runnable task2 = new Runnable() {
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					String[] tokens = s.split("-", -1);
					state ^= tokens.length;
				}
			};
			System.out.println("String.split: " + new Benchmark(task2));
			
				// use Pattern.split:
			Runnable task3 = new Runnable() {
				private final Pattern pattern = Pattern.compile("-");
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					String[] tokens = pattern.split(s, -1);
					state ^= tokens.length;
				}
			};
			System.out.println("Pattern.split: " + new Benchmark(task3));
		}
		
		@Test public void test_splitByChar_pass() {
				// s is empty:
			String s = "";
			List<String> tokensExpected = Arrays.asList( "" );
			List<String> tokensPresent = splitByChar(s, '-', 1, true);
			List<String> tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s is only a delimiter (2 tokens which are both a ""):
			s = "-";
			tokensExpected = Arrays.asList( "", "" );
			tokensPresent = splitByChar(s, '-', 2, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s is normal:
			s = "a" + "-" + "b" + "-" + "c";
			tokensExpected = Arrays.asList( "a", "b", "c" );
			tokensPresent = splitByChar(s, '-', 3, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s starts on delimiter (first token should then be a ""):
			s = "-" + "a" + "-" + "b" + "-" + "c";
			tokensExpected = Arrays.asList( "", "a", "b", "c" );
			tokensPresent = splitByChar(s, '-', 4, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
			
				// s ends on delimiter (final token should then be a ""):
			s = "a" + "-" + "b" + "-" + "c" + "-";
			tokensExpected = Arrays.asList( "a", "b", "c", "" );
			tokensPresent = splitByChar(s, '-', 4, true);
			tokensString_split = String_split(s, "-");
			Assert.assertEquals( tokensExpected, tokensPresent );
			Assert.assertEquals( tokensExpected, tokensString_split );
		}
		
		@Test(expected=AssertionError.class) public void test_splitByChar_fail1() {
			String s = "a" + "-" + "b" + "-" + "c";
			List<String> tokensExpected = Arrays.asList( "a", "b", "c", "d" );
			List<String> tokensPresent = splitByChar(s, '-', 1, false);
			Assert.assertEquals( tokensExpected, tokensPresent ); // should fail (tokensExpected added a bogus token)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByChar_fail2() {
			String s = null;
			splitByChar(s, '-', 3, true);	// should fail (null supplied)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByChar_fail3() {
			String s = "a" + "-" + "b" + "-" + "c";
			splitByChar(s, '-', 2, true); // should fail (too few tokens specified when nIsExact = true)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_splitByChar_fail4() {
			String s = "a" + "-" + "b" + "-" + "c";
			splitByChar(s, '-', 4, true); // should fail (too many tokens specified when nIsExact = true)
		}
		
		/**
		* Results on 2009-06-26 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_14 server jvm):
		* <pre><code>
			s contains 3 tokens:
				splitByChar: first = 59.715 us, mean = 113.615 ns (CI deltas: -93.321 ps, +101.454 ps), sd = 1.571 us (CI deltas: -230.824 ns, +347.740 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
				String.split: first = 136.978 us, mean = 784.090 ns (CI deltas: -402.093 ps, +412.352 ps), sd = 2.323 us (CI deltas: -338.734 ns, +437.863 ns) WARNING: SD VALUES MAY BE INACCURATE
				Pattern.split: first = 75.389 us, mean = 459.917 ns (CI deltas: -427.344 ps, +409.991 ps), sd = 3.392 us (CI deltas: -398.413 ns, +570.629 ns) WARNING: execution times may have serial correlation, SD VALUES MAY BE INACCURATE
				
			s contains 10 tokens:
				splitByChar: first = 59.712 us, mean = 329.528 ns (CI deltas: -366.100 ps, +422.782 ps), sd = 3.201 us (CI deltas: -559.421 ns, +1.169 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
				String.split: first = 156.493 us, mean = 1.427 us (CI deltas: -2.007 ns, +2.222 ns), sd = 8.547 us (CI deltas: -1.506 us, +2.289 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
				Pattern.split: first = 77.697 us, mean = 1.106 us (CI deltas: -2.095 ns, +2.236 ns), sd = 8.796 us (CI deltas: -1.361 us, +1.883 us) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_splitByChar() throws Exception {
			//final String s = "a" + "-" + "b" + "-" + "c";	// 3 token version
			final String s = "a" + "-" + "b" + "-" + "c" + "-" + "d" + "-" + "e" + "-" + "f" + "-" + "g" + "-" + "h" + "-" + "i" + "-" + "j";	// 10 token version
			
			Runnable task1 = new Runnable() {
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					List<String> tokens = splitByChar(s, '-', 10, true);
					state ^= tokens.size();
				}
			};
			System.out.println("splitByChar: " + new Benchmark(task1));
			
				// use String.split:
			Runnable task2 = new Runnable() {
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					String[] tokens = s.split("-", -1);
					state ^= tokens.length;
				}
			};
			System.out.println("String.split: " + new Benchmark(task2));
			
				// use Pattern.split:
			Runnable task3 = new Runnable() {
				private final Pattern pattern = Pattern.compile("-");
				private int state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					String[] tokens = pattern.split(s, -1);
					state ^= tokens.length;
				}
			};
			System.out.println("Pattern.split: " + new Benchmark(task3));
		}
		
		private List<String> String_split(String s, String delimiter) {
			return Arrays.asList( s.split(delimiter, -1) );
		}
		
		@Test public void test_quoteWhitespaceTokenize() {
			String source = "abc d e f \"ghi\" \"j k l\"";
			boolean includeQuotes = false;
			String[] tokensExpected = new String[] {"abc", "d", "e", "f", "ghi", "j k l"};
			Assert.assertArrayEquals( tokensExpected, quoteWhitespaceTokenize(source, includeQuotes) );
			
			source = "123 4 5 6 \"789\" \"10 11 12\"";
			includeQuotes = true;
			tokensExpected = new String[] {"123", "4", "5", "6", "\"789\"", "\"10 11 12\""};
			Assert.assertArrayEquals( tokensExpected, quoteWhitespaceTokenize(source, includeQuotes) );
		}
		
		@Test public void test_diagnoseProblem() {
			byte[] bytes = new byte[] {(byte) 'a', (byte) -55, (byte) 'c'};
			System.out.println(diagnoseProblem(bytes, 1));
		}
		
		@Test public void investigate_bitFlip() {
			byte b = (byte) '9';
			System.out.println( describeAsciiCharsByHammingDistance(b) );
		}
// when last ran this program on 2006/5/11, confirmed that an Interval number read in as 5.88 was actually 5.89 in the file, and that bit flipping must have happened because 8 is 1 bit off from 9
		
		@Test public void test_toString() {
			double[] doubleArray = new double[] {1, 2, 3};
			String doubleArrayTextExpected = "1.0, 2.0, 3.0";
			Assert.assertEquals( doubleArrayTextExpected, StringUtil.toString(doubleArray, ", ") );
			
			double[][] doubleMatrix = new double[][] { {1, 2, 3}, {4, 5, 6}, {7, 8, 9} };
			String doubleMatrixTextExpected =
				"1.0, 2.0, 3.0" + '\n' +
				"4.0, 5.0, 6.0" + '\n' +
				"7.0, 8.0, 9.0";
			Assert.assertEquals( doubleMatrixTextExpected, StringUtil.toString(doubleMatrix, ", ") );
			
			String[] stringArray = new String[] {"a", "b", "c", null};
			String stringArrayTextExpected = "a;b;c;null";
			Assert.assertEquals( stringArrayTextExpected, StringUtil.toString(stringArray, ";") );
			
			Collection<String> collection = Arrays.asList("a", "b", "c", null);
			String collectionTextExpected = "a  b  c  null";
			Assert.assertEquals( collectionTextExpected, StringUtil.toString(collection, "  ") );
			
			Map<Integer,Character> map = new LinkedHashMap<Integer,Character>();	// use LinkedHashMap because HashMap permits nulls yet need guaranteed iteration order for the assertEquals to work
			map.put(1, 'a');
			map.put(2, 'b');
			map.put(3, 'c');
			map.put(null, null);
			String mapTextExpected = "1 --> a, 2 --> b, 3 --> c, null --> null";
			Assert.assertEquals( mapTextExpected, StringUtil.toString(map, ", ") );
		}
		
	}
	
}
