/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import bb.io.FileUtil;
import bb.util.Check;
import bb.util.StringUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.junit.Assert;
import org.junit.Test;

/**
* Accepts any File which matches a set of regular expressions.
* <p>
* Instances are {@link #RegexFilter constructed} with a Set<Pattern> of regular expressions.
* A {@link File} is accepted if it matches at least one of the Patterns (i.e. the patterns are connected by an implicit OR).
* <p>
* This class is multithread safe: it is immutable.
* In particular, it has no listener (see the {@link BaseFilter ancestor class} javadocs for more discussion).
* <p>
* @author Brent Boyer
*/
public class RegexFilter extends BaseFilter {
	
	// -------------------- instance constants --------------------
	
	private final Set<Pattern> patterns;
	
	// -------------------- static methods --------------------
	
	/**
	* Converts strings into an equivalent {@link Set} of {@link String}s.
	* <p>
	* Contract: if this method returns normally, the result will be non-null, non-empty,
	* and the Set type will be one which perfectly preserves the original iteration order of strings.
	* <p>
	* @param strings String[] of arbitrary Strings
	* @throws IllegalArgumentException if strings is null or 0-length
	*/
	public static Set<String> toSet(String... strings) throws IllegalArgumentException {
		Check.arg().notEmpty(strings);
		
		return new LinkedHashSet<String>( Arrays.asList(strings) );	// CRITICAL: use a LinkedHashSet to preserve order
	}
// +++ should i move this to SetUtil, and generify it?  while it would thus be more generally useful, it would also be less convenient for subclasses to use
	
	/**
	* Converts a set of {@link String} regular expression specifications into a set of {@link Pattern}s.
	* <p>
	* Contract: if this method returns normally, the result will be non-null, non-empty,
	* and the Set type will be one which perfectly preserves the original iteration order of regexes.
	* <p>
	* @param regexes Set<String> of regular expressions (each in the syntax of {@link Pattern});
	*	for top performance, use a Set implementation with predictable iteration order,
	*	and ensure that the most frequently used regular expressions are iterated over first
	* @throws IllegalArgumentException if regexes is null or 0-length
	* @throws PatternSyntaxException if any element of regexes has an invalid regular expression syntax
	*/
	public static Set<Pattern> toPatterns(Set<String> regexes) throws IllegalArgumentException, PatternSyntaxException {
		Check.arg().notEmpty(regexes);
		
		Set<Pattern> patterns = new LinkedHashSet<Pattern>( regexes.size() );	// use LinkedHashSet for contract about preserving iteration order
		for (String regex : regexes) {
			patterns.add( Pattern.compile(regex) );
		}
		return patterns;
	}
	
	// -------------------- constructors --------------------
	
	/**
	* Calls <code>{@link #RegexFilter(Set, FileMode, DirectoryMode) this}(patterns, {@link FileMode#test}, {@link DirectoryMode#reject})</code>.
	* So, this filter will always test normal files (and only tests the name of them),
	* and always rejects directories and "other" file system elements.
	*/
	public RegexFilter(Set<Pattern> patterns) throws IllegalArgumentException {
		this(patterns, FileMode.test, DirectoryMode.reject);
	}
	
	/**
	* Calls <code>{@link #RegexFilter(Set, FileMode, DirectoryMode, OtherMode, PartMode) this}(patterns, fileMode, directoryMode, {@link OtherMode#reject}, {@link PartMode#name})</code>.
	* So, this filter will always reject "other" file system elements,
	* and for normal files/directories, only tests the name of each File (assuming {@link FileMode#test}/{@link DirectoryMode#test}).
	*/
	public RegexFilter(Set<Pattern> patterns, FileMode fileMode, DirectoryMode directoryMode) throws IllegalArgumentException {
		this(patterns, fileMode, directoryMode, OtherMode.reject, PartMode.name);
	}
	
	/**
	* Constructs a new RegexFilter instance.
	* <p>
	* Contract: this instance will preserve the iteration order of patterns.
	* <p>
	* @param patterns Set<Pattern> of regular expressions that if a File's name/path (depending on partMode) matches, result in the File being accepted;
	*	for top performance, use a Set implementation with predictable iteration order,
	*	and ensure that the most frequently used regular expressions are iterated over first
	* @param fileMode a {@link FileMode} instance that specifies how normal files will be handled
	* @param directoryMode a {@link DirectoryMode} instance that specifies how directories will be handled
	* @param otherMode a {@link OtherMode} instance that specifies how "other" file system elements will be handled
	* @param partMode a {@link PartMode} instance that specifies what part of a File's full path will be tested
	* @throws IllegalArgumentException if patterns is null or 0-length; fileMode, directoryMode, otherMode, or partMode are null
	*/
	public RegexFilter(Set<Pattern> patterns, FileMode fileMode, DirectoryMode directoryMode, OtherMode otherMode, PartMode partMode) throws IllegalArgumentException {
		super(fileMode, directoryMode, otherMode, partMode);
		
		Check.arg().notEmpty(patterns);
		
		this.patterns = new LinkedHashSet<Pattern>(patterns);	// need new Set to ensure encapsulation; use LinkedHashSet for contract about preserving iteration order
	}
	
	// -------------------- accessors --------------------
	
	/** Returns a read-only view of {@link #patterns}. */
	public Set<Pattern> getPatterns() { return Collections.unmodifiableSet(patterns); }
	
	// -------------------- passesTest --------------------
	
	@Override protected boolean passesTest(String part) {
		for (Pattern pattern : getPatterns()) {
			if ( pattern.matcher(part).matches() ) return true;
		}
		return false;
	}
	
	// -------------------- getDescription --------------------
	
	/**
	* {@inheritDoc}
	* <p>
	* The implementation here has some introductory text, followed by a comma separated list of all of the regexes present in {@link #patterns}.
	*/
	@Override public String getDescription() {
		return getDescriptionIntro() + StringUtil.toString(getPatterns(), ", ");
	}
	
	protected String getDescriptionIntro() {
		switch (getPartMode()) {
			case name: return "File names matching: ";
			case path: return "File paths matching: ";
			default: throw new IllegalStateException("getPartMode() = " + getPartMode() + " is unexpected");
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final String[] regexes = new String[] {
			"(?i).*\\.test1",	// (?i) means is case insensitive
			".*\\.TEST2"	// whereas this one is case sensitive
		};
		
		@Test public void test_accept() {
			RegexFilter regexFilter = new RegexFilter( toPatterns( toSet(regexes) ) );
			
				// Files that should match:
			Assert.assertTrue( regexFilter.accept( FileUtil.createTempLog(".test1") ) );	// pure extension is OK
			Assert.assertTrue( regexFilter.accept( FileUtil.createTempLog("abc.test1") ) );
			Assert.assertTrue( regexFilter.accept( FileUtil.createTempLog("def.TeSt1") ) );	// first element of regexes is case insensitive
			Assert.assertTrue( regexFilter.accept( FileUtil.createTempLog("ghi.TEST2") ) );
			
				// Files that should NOT match:
			Assert.assertFalse( regexFilter.accept( FileUtil.createTempLog("test1") ) );	// but a pure extension requires a '.'
			Assert.assertFalse( regexFilter.accept( FileUtil.createTempLog("jkl.test1a") ) );
			Assert.assertFalse( regexFilter.accept( FileUtil.createTempLog("mno.TeSt2") ) );	// second element of regexes is case sensitive
			Assert.assertFalse( regexFilter.accept( FileUtil.createTempLog("pqr.test2") ) );	// second element of regexes is case sensitive
		}
		
	}
	
}
