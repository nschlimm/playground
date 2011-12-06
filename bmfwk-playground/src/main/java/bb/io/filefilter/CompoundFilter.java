/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import bb.util.Check;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
* Meta filter whose {@link #accept accept} method incorporates all the constraints of all the file filters that it was constructed with.
* <p>
* Note that {@link RegexFilter} and {@link SuffixFilter} can be viewed as specialized types of compound filters:
* each has a set of criteria that are connected by an implicit logical OR.
* Using/subclassing RegexFilter or SuffixFilter is therefore more convenient than this class if that is the desired behavior.
* <p>
* In contrast, this class offers much more flexibility: any type of filters can be used,
* and the filters can be connected by logical {@link LogicMode#and AND} as well logical {@link LogicMode#or OR}.
* <p>
* Note that this class is both a subclass of {@link javax.swing.filechooser.FileFilter javax.swing.filechooser.FileFilter}
* (so that it can be passed to {@link javax.swing.JFileChooser}),
* as well as an implementation of {@link java.io.FileFilter java.io.FileFilter}
* (so that it is suitable as an argument to {@link java.io.File#listFiles(java.io.FileFilter) File.listFiles}).
* <p>
* This class is multithread safe if all the elements in its {@link #filters} field are multithread safe,
* since other than possible deep state in those elements,
* it is <a href="http://www.ibm.com/developerworks/java/library/j-jtp02183.html">immutable</a>.
* In particular, it is always <a href="http://www.ibm.com/developerworks/java/library/j-jtp0618.html">properly constructed</a>,
* all of its fields are final,
* and none of their state can be changed after construction except possibly any deep state inside filters' elements.
* See p. 53 of <a href="http://www.javaconcurrencyinpractice.com">Java Concurrency In Practice</a> for more discussion.
* <p>
* @author Brent Boyer
*/
public class CompoundFilter extends javax.swing.filechooser.FileFilter implements java.io.FileFilter {
	
	// -------------------- instance constants --------------------
	
	private final LogicMode logicMode;
	
	/** FileFilters which will be used in {@link #accept accept} and {@link #getDescription getDescription}. */
	private final Set<java.io.FileFilter> filters = new LinkedHashSet<java.io.FileFilter>();
	
	// -------------------- factory methods and constructors --------------------
	
	/**
	* Returns a new CompoundFilter instance that connects the elements of filters via {@link LogicMode#and logical AND}.
	* <p>
	* @param filters assigned to the {@link #filters} field;
	*	<i>for top performance, place the most frequently encountered patterns earliest in the array</i>
	* @throws IllegalArgumentException if filters == null; filters.length == 0
	*/
	public static CompoundFilter makeAnd(java.io.FileFilter... filters) throws IllegalArgumentException {
		return new CompoundFilter(LogicMode.and, filters);
	}
	
	/**
	* Returns a new CompoundFilter instance that connects the elements of filters via {@link LogicMode#and logical OR}.
	* <p>
	* @param filters assigned to the {@link #filters} field;
	*	<i>for top performance, place the most frequently encountered patterns earliest in the array</i>
	* @throws IllegalArgumentException if filters == null; filters.length == 0
	*/
	public static CompoundFilter makeOr(java.io.FileFilter... filters) throws IllegalArgumentException {
		return new CompoundFilter(LogicMode.or, filters);
	}
	
	/**
	* Constructor.
	* <p>
	* @param logicMode assigned to the {@link #logicMode} field
	* @param filters assigned to the {@link #filters} field;
	*	<i>for top performance, place the most frequently encountered patterns earliest in the array</i>
	* @throws IllegalArgumentException if logicMode == null; filters == null; filters.length == 0
	*/
	protected CompoundFilter(LogicMode logicMode, java.io.FileFilter... filters) throws IllegalArgumentException {
		Check.arg().notNull(logicMode);
		Check.arg().notEmpty(filters);
		
		this.logicMode = logicMode;
		this.filters.addAll( Arrays.asList(filters) );
	}
	
	// -------------------- accept --------------------
	
	/**
	* Reports whether or not file is accepted by this instance.
	* Applies all the {@link #filters} according to {@link #logicMode}.
	* <p>
	* @throws IllegalArgumentException if file is null
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to the file
	* @throws IllegalStateException if the class finds itself in an unexpected state
	*/
	@Override public boolean accept(File file) throws IllegalArgumentException, SecurityException, IllegalStateException {
		Check.arg().notNull(file);
		
		switch (logicMode) {
			case and:
				for (java.io.FileFilter filter : filters) {
					if ( !filter.accept(file) ) return false;
				}
				return true;
				
			case or:
				for (java.io.FileFilter filter : filters) {
					if ( filter.accept(file) ) return true;
				}
				return false;
			
			default:
				throw new IllegalStateException("logicMode = " + logicMode + " is an illegal value");
		}
	}
	
	// -------------------- getDescription --------------------
	
	/** Returns a description of this filter. Is simply a concatenation of the descriptions of all the {@link #filters}. */
	@Override public String getDescription() {
		StringBuilder sb = new StringBuilder( filters.size() * 32 );
		sb.append( getLogicModeDescription() );
		int sizeInitial = sb.length();
		for (java.io.FileFilter filter : filters) {
			if (sb.length() > sizeInitial) sb.append(", ");
			sb.append( getDescription(filter) );
		}
		return sb.toString();
	}
	
	private String getLogicModeDescription() throws IllegalStateException {
		switch (logicMode) {
			case and: return "AND of: ";
			case or: return "OR of: ";
			default: throw new IllegalStateException("logicMode = " + logicMode + " is an illegal value");
		}
	}
	
	private String getDescription(java.io.FileFilter filter) {
		if (filter instanceof javax.swing.filechooser.FileFilter) {
			return ((javax.swing.filechooser.FileFilter) filter).getDescription();
		}
		else {
			return filter.getClass().getName();
		}
	}
	
	// -------------------- LogicMode (static inner enum) --------------------
	
	/**
	* Enum of all the supported modes for applying the individual filters.
	* <p>
	* This enum is multithread safe: it is stateless (except for the enumeration of values, which are immutable).
	* <p>
	* Like all java enums, this enum is Comparable and Serializable.
	* <p>
	* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/language/enums.html">Enum documentation</a>
	*/
	private static enum LogicMode {
		
		/** Specifies logical AND of all the individual filters (all must pass for the compound filter to pass). */
		and,
		
		/** Specifies logical OR of all the individual filters (at least one must pass for the compound filter to pass). */
		or;
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final String[] suffixes = new String[] {".test1", ".test2"};
		
		private static final File[] matchingFiles = new File[] {
			new File("abc.test1"),
			new File("abc.TEST1"),
			new File("def.test2"),
			new File("def.TEST2")
		};
		
		private static final File[] nonmatchingFiles = new File[] {
			new File("abc.test12"),
			new File("abc.TEST12"),
			new File("def.txt"),
			new File("def.jpg")
		};
		
		@Test public void test_getDescription() {
			TestFilter0 filter1 = new TestFilter0();
			TestFilter1 filter2 = new TestFilter1();
			CompoundFilter compoundFilter = CompoundFilter.makeOr(filter1, filter2);
			
			System.out.println("Here is the result from the getDescription method of a test filter:");
			System.out.println( "\t" + compoundFilter.getDescription() );
		}
		
		@Test public void test_filtering() throws Exception {
			TestFilter0 filter1 = new TestFilter0();
			TestFilter1 filter2 = new TestFilter1();
			CompoundFilter compoundFilter = CompoundFilter.makeOr(filter1, filter2);
			
			for (File matchingFile : matchingFiles) {
				String errMsg = "MISTAKENLY rejected " + matchingFile.getName();
				Assert.assertTrue( errMsg, compoundFilter.accept(matchingFile) );
			}
			for (File nonmatchingFile : nonmatchingFiles) {
				String errMsg = "FAILED to reject " + nonmatchingFile.getName();
				Assert.assertFalse( errMsg, compoundFilter.accept(nonmatchingFile) );
			}
		}
		
		private static abstract class TestFilter extends SuffixFilter {
			private TestFilter(String ... suffixes) {
				super( toSet(suffixes), FileMode.reject, DirectoryMode.reject, OtherMode.test, PartMode.name );	// reject all but "other" file system elements, which includes non-existing Files, like the ones that will be tested here
			}
		}
		
		private static class TestFilter0 extends TestFilter {
			private TestFilter0() {
				super(suffixes[0]);
			}
		}
		
		private static class TestFilter1 extends TestFilter {
			private TestFilter1() {
				super(suffixes[1]);
			}
		}
		
	}
	
}
