/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

/**
* Provides static utility methods for dealing with <code>char</code>s.
* <p>
* Note that the argument type for many of the methods is of <code>int</code> type, as opposed to <code>char</code> type.
* This was deliberately chosen for these reasons:
* <ol>
*  <li>
*		as of JDK 1.5, Java now supports <a href="http://en.wikipedia.org/wiki/Unicode">Unicode 4.0</a>
*		which has 1,114,112 <a href="http://en.wikipedia.org/wiki/Code_points">code points</a>,
*		which requires an int to represent (Java's char type has insufficient range)
*  </li>
*  <li>
*		all <code>Reader</code>s return <code>int</code> values when do single character reads, in order to indicate EOF by returning -1.
*		So, using <code>int</code> type arguments allows this class's methods to handle EOF values, freeing the programmer from having to first check.
*  </li>
*  <li>
*		the internal comparison operations done in many of the methods here would promote a
*		<code>char</code> argument to an <code>int</code> anyways,
*		so it is never slower to do a single cast at the start
*  </li>
* </ol>
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class CharUtil {
	
	// -------------------- constants --------------------
	
	/** The minimum (int) value that a valid <a href="http://en.wikipedia.org/wiki/Ascii">US-ASCII</a> (i.e. 7 bit) char can have. */
	private static final int minAscii = 0;
	
	/** The maximum (int) value that a valid <a href="http://en.wikipedia.org/wiki/Ascii">US-ASCII</a> (i.e. 7 bit) char can have. */
	private static final int maxAscii = 127;
	
	/** The minimum (int) value that a valid Java char can have. */
	private static final int minChar = (int) Character.MIN_VALUE;
	
	/** The maximum (int) value that a valid Java char can have. */
	private static final int maxChar = (int) Character.MAX_VALUE;
	
/*
		// chars in this array act as word separators and will also be skipped over (i.e. not counted as part of words):
	public static final char[] WORD_SEPARATOR_CHARS = {
			// whitespace chars:
		' ', '\t',
		
			// text formatting chars:
		'\b', '\f', '\n', '\r',
		
			// punctuation chars (going left to right, bottom to top of keyboard):
		',', '.', '/', '?', ';', ':', '\'', '\"', '[', '{', ']', '}', '\\', '|',
		'`', '~', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+'
	};
*/
	
	// -------------------- isXXX methods --------------------
	
	/** Determines whether or not c has a value that a Java <code>char</code> can represent. */
	public static boolean isAscii(int c) {
		return (minAscii <= c) && (c <= maxAscii);
	}
	
	/** Determines whether or not c has a value that a Java <code>char</code> can represent. */
	public static boolean isChar(int c) {
		return (minChar <= c) && (c <= maxChar);
	}
	
	/** Determines whether or not c represents a decimal digit (i.e. '0', '1', ..., '9'). */
	public static boolean isDecimalDigit(int c) {
		return (48 <= c) && (c <= 57);
	}
	
	/** Determines whether or not c represents a (lower or upper case) Roman Letter (i.e. 'a', 'b', ..., 'z', 'A', 'B', ..., 'Z' ). */
	public static boolean isLineEnd(int c) {
		return (c == '\n') || (c == '\r');
	}
	
	/** Determines whether or not c represents a (lower or upper case) Roman Letter (i.e. 'a', 'b', ..., 'z', 'A', 'B', ..., 'Z' ). */
	public static boolean isRomanLetter(int c) {
		return isRomanLetterLowerCase(c) ||	isRomanLetterUpperCase(c);	// speed heuristic: do the lower case test first, as it should be the most common
	}
	
	/** Determines whether or not c represents a lower case Roman Letter (i.e. 'a', 'b', ..., 'z'). */
	public static boolean isRomanLetterLowerCase(int c) {
		return (97 <= c) && (c <= 122);
	}
	
	/** Determines whether or not c represents an upper case Roman Letter (i.e. 'A', 'B', ..., 'Z'). */
	public static boolean isRomanLetterUpperCase(int c) {
		return (65 <= c) && (c <= 90);
	}
	
	// -------------------- Escape methods --------------------
	
	/**
	* Reports whether or not the supplied char has a "simple" escape sequence that may appear in a Java char or String Literal.
	* (These chars are defined in section 3.10.6 of
	* <a href="http://java.sun.com/docs/books/jls/third_edition/html/lexical.html#3.10.6">The Java Language Specification, Third Edition</a>.
	* They are the ones which have 2-char escape sequences which begin with a backslash.)
	*/
	public static boolean hasEscapeForLiteral(char c) {
		switch (c) {
			case '\b':
			case '\t':
			case '\n':
			case '\f':
			case '\r':
			case '\"':
			case '\'':
			case '\\':
				return true;
			default:
				return false;
		}
	}
	
	/**
	* Returns the "simple" (i.e. 2 char) escape sequence for the supplied char,
	* which is suitable for appearing in a Java char or String Literal.
	* <p>
	* @throws IllegalArgumentException if the char has no "simple" escape
	* (i.e. if CharUtil.hasEscapeForLiteral(c) returns false)
	*/
	public static String getEscapeForLiteral(char c) throws IllegalArgumentException {
		switch (c) {
			case '\b':
				return "\\b";
			
			case '\t':
				return "\\t";
			
			case '\n':
				return "\\n";
				
			case '\f':
				return "\\f";
				
			case '\r':
				return "\\r";
				
			case '\"':
				return "\\\"";
				
			case '\'':
				return "\\'";
				
			case '\\':
				return "\\\\";
				
			default:
				throw new IllegalArgumentException("arg c = " + c + "has no simple escape sequence");
		}
	}
	
	// -------------------- matches --------------------
	
	/**
	* Determines whether or not c1 and c2 are matching char values.
	* Immediately returns true if <code>c1 == c2</code>.
	* Else if <code>isCaseSensitive == false</code>, then c1 and c2 are recompared on a case insensitive basis.
	* Only if that too fails is false returned.
	*/
	public static boolean matches(char c1, char c2, boolean isCaseSensitive) {
		if (c1 == c2) return true;
		
		if (!isCaseSensitive) {
				// see source code of String.regionMatches(boolean, int, String, int, int) for why this has to be so complicated:
			char up1 = Character.toUpperCase(c1);
			char up2 = Character.toUpperCase(c2);
			if (up1 == up2) return true;
			
			char low1 = Character.toLowerCase(c1);
			char low2 = Character.toLowerCase(c2);
			if (low1 == low2) return true;
		}
		
		return false;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private CharUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
// +++ need to add tests!!!
		
	}
	
}
