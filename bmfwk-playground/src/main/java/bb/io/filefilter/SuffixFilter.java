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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.junit.Assert;
import org.junit.Test;

/**
* Subclass of {@link RegexFilter} that makes it convenient to match {@link File}s based on their endings.
* <p>
* Instances are {@link #SuffixFilter constructed} with a Set<String> of desired suffixes.
* A {@link File} is accepted if it ends in at least one of the suffixes (i.e. the suffixes are connected by an implicit OR).
* <i>The suffix matching is always case insensitive</i> but is otherwise a literal match against the suffixes;
* these qualities are what make this class more convenient than RegexFilter
* (users need not know regular expressions).
* <p>
* In general, only a File's name is examined, however,
* {@link #SuffixFilter(Set, FileMode, DirectoryMode, OtherMode, PartMode) one of the constructors}
* takes a {@link PartMode} arg that allows other possibilities (e.g. examine a File's path).
* Furthermore, that constructor also allows precise control over how
* normal files, directories, and "other" file system elements are handled.
* These features, along with the fact that the suffix is arbitrary (can be more or less than a file's extension)
* make this class much more powerful than the JDK's {@link FileNameExtensionFilter}.
* <p>
* This class is multithread safe: it is immutable.
* In particular, it maintains its {@link RegexFilter ancestor class}'s immutability.
* <p>
* @author Brent Boyer
*/
public class SuffixFilter extends RegexFilter {
	
	// -------------------- instance constants --------------------
	
	private final Set<String> suffixes;
	
	// -------------------- static methods --------------------
	
	/**
	* Converts suffixes into a set of {@link Pattern}s that match any String that ends in a literal occurrence (case insensitive) of an element of suffixes.
	* <p>
	* Contract: if this method returns normally, the result will be non-null, non-empty,
	* and the Set type will be one which perfectly preserves the original iteration order of suffixes.
	* <p>
	* @param suffixes Set<String> of arbitrary Strings;
	*	for top performance, use a Set implementation with predictable iteration order,
	*	and ensure that the most frequently used suffixes are iterated over first
	* @throws IllegalArgumentException if suffixes is null or 0-length; any element of suffixes is {@link Check#notBlank blank}
	*/
	public static Set<Pattern> toPatternLiterals(Set<String> suffixes) throws IllegalArgumentException {
		Check.arg().notEmpty(suffixes);
		
		Set<Pattern> patterns = new LinkedHashSet<Pattern>( suffixes.size() );	// use LinkedHashSet for contract about preserving iteration order
		for (String suffix : suffixes) {
			Check.arg().notBlank(suffix);
			patterns.add( Pattern.compile( "(?i).*" + Pattern.quote(suffix) ) );
		}
		return patterns;
	}
	
	// -------------------- constructors --------------------
	
	/**
	* Calls <code>{@link #SuffixFilter(Set) this}( {@link #toSet}(suffixes) )</code>.
	* So, this filter will always test normal files (and only tests the name of them),
	* and always rejects directories and "other" file system elements.
	*/
	public SuffixFilter(String... suffixes) throws IllegalArgumentException {
		this( toSet(suffixes) );
	}
	
	/**
	* Calls <code>{@link #SuffixFilter(Set, FileMode, DirectoryMode) this}(suffixes, {@link FileMode#test}, {@link DirectoryMode#reject})</code>.
	* So, this filter will always test normal files (and only tests the name of them),
	* and always rejects directories and "other" file system elements.
	*/
	public SuffixFilter(Set<String> suffixes) throws IllegalArgumentException {
		this(suffixes, FileMode.test, DirectoryMode.reject);
	}
	
	/**
	* Calls <code>{@link #SuffixFilter(Set, FileMode, DirectoryMode, OtherMode, PartMode) this}(suffixes, fileMode, directoryMode, {@link OtherMode#reject}, {@link PartMode#name})</code>.
	* So, this filter will always reject "other" file system elements,
	* and for normal files/directories, only tests the name of each File (assuming {@link FileMode#test}/{@link DirectoryMode#test}).
	*/
	public SuffixFilter(Set<String> suffixes, FileMode fileMode, DirectoryMode directoryMode) throws IllegalArgumentException {
		this(suffixes, fileMode, directoryMode, OtherMode.reject, PartMode.name);
	}
	
	/**
	* Constructs a new SuffixFilter instance.
	* <p>
	* Contract: this instance will preserve the iteration order of suffixes.
	* <p>
	* @param suffixes Set<String> that if a File's name/path (depending on partMode) ends with, result in the File being accepted;
	*	for top performance, use a Set implementation with predictable iteration order,
	*	and ensure that the most frequently used suffixes are iterated over first
	* @param fileMode a {@link FileMode} instance that specifies how normal files will be handled
	* @param directoryMode a {@link DirectoryMode} instance that specifies how directories will be handled
	* @param otherMode a {@link OtherMode} instance that specifies how "other" file system elements will be handled
	* @param partMode a {@link PartMode} instance that specifies what part of a File's full path will be tested
	* @throws IllegalArgumentException if suffixes is null or 0-length; fileMode, directoryMode, otherMode, or partMode are null
	*/
	public SuffixFilter(Set<String> suffixes, FileMode fileMode, DirectoryMode directoryMode, OtherMode otherMode, PartMode partMode) throws IllegalArgumentException {
		super( toPatternLiterals(suffixes), fileMode, directoryMode, otherMode, partMode );
		
		this.suffixes = new LinkedHashSet<String>(suffixes);	// need new Set to ensure encapsulation; use LinkedHashSet for contract about preserving iteration order
	}
	
	// -------------------- accessors --------------------
	
	/** Returns a read-only view of {@link #suffixes}. */
	public Set<String> getSuffixes() { return Collections.unmodifiableSet(suffixes); }
	
	// -------------------- getDescription and helper methods --------------------
	
	/**
	* {@inheritDoc}
	* <p>
	* The implementation here has some introductory text, followed by a comma separated list of {@link #getSuffixes the suffixes}.
	*/
	@Override public String getDescription() {
		return getDescriptionIntro() + StringUtil.toString(getSuffixes(), ", ");
	}
	
	protected String getDescriptionIntro() {
		switch (getPartMode()) {
			case name: return "File name suffixes: ";
			case path: return "File path suffixes: ";
			default: throw new IllegalStateException("getPartMode() = " + getPartMode() + " is unexpected");
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_accept() {
			SuffixFilter filenameSuffixFilter = new SuffixFilter(".test1", ".TEST2");
			
				// Files that should match:
			Assert.assertTrue( filenameSuffixFilter.accept( FileUtil.createTempLog(".test1") ) );	// pure extension is OK
			Assert.assertTrue( filenameSuffixFilter.accept( FileUtil.createTempLog("abc.test1") ) );
			Assert.assertTrue( filenameSuffixFilter.accept( FileUtil.createTempLog("def.TEST2") ) );
			Assert.assertTrue( filenameSuffixFilter.accept( FileUtil.createTempLog("ghi.TeSt1") ) );	// suffixes are always case insensitive
			Assert.assertTrue( filenameSuffixFilter.accept( FileUtil.createTempLog("jkl.test2") ) );	// ditto
			
				// Files that should NOT match:
			Assert.assertFalse( filenameSuffixFilter.accept( FileUtil.createTempLog("test1") ) );	// but a pure extension requires a '.'
			Assert.assertFalse( filenameSuffixFilter.accept( FileUtil.createTempLog("mno.test1a") ) );
		}
		
	}
	
}
