/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import bb.io.DirUtil;
import bb.io.FileUtil;
import bb.util.Check;
import java.io.File;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

/**
* Abstract base class for other {@link File} filter implementations that defines some common functionality.
<p>
* The first area addressed by this class is how to limit what kinds of Files may be accepted.
* THe types of Files are normal files, directories, and "other" file system elements.
* Different filter implementations can have different policies regarding these types.
* This class offers constructors whose fileMode/directoryMode/otherMode args specify the policies.
* <p>
* The next area addressed by this class is specifying what part of a File's path will be tested.
* This class offers constructors whose partMode arg specifies the policy.
* <p>
* Concrete subclasses must implement the {@link #passesTest(String)} and {@link #getDescription} methods.
* <p>
* Note that this class is both a subclass of {@link javax.swing.filechooser.FileFilter javax.swing.filechooser.FileFilter}
* (so that it can be passed to {@link javax.swing.JFileChooser}),
* as well as an implementation of {@link java.io.FileFilter java.io.FileFilter}
* (so that it is suitable as an argument to {@link java.io.File#listFiles(java.io.FileFilter) File.listFiles}).
* <p>
* This class is multithread safe if its {@link #listener} field is multithread safe,
* since other than possible deep state in that field,
* it is <a href="http://www.ibm.com/developerworks/java/library/j-jtp02183.html">immutable</a>.
* In particular, it is always <a href="http://www.ibm.com/developerworks/java/library/j-jtp0618.html">properly constructed</a>,
* all of its fields are final,
* and none of their state can be changed after construction except possibly any deep state inside listener.
* See p. 53 of <a href="http://www.javaconcurrencyinpractice.com">Java Concurrency In Practice</a> for more discussion.
* One way that listener can be multithread safe is if it is null.
* <p>
* @author Brent Boyer
*/
public abstract class BaseFilter extends javax.swing.filechooser.FileFilter implements java.io.FileFilter {
	
	// -------------------- instance constants --------------------
	
	private final FileMode fileMode;
	
	private final DirectoryMode directoryMode;
	
	private final OtherMode otherMode;
	
	private final PartMode partMode;
	
	private final FileFilterListener listener;
	
	// -------------------- constructors --------------------
	
	/**
	* Calls <code>{@link #BaseFilter(FileFilterListener) this}(null)</code>.
	* So, this filter will always test normal files (and only tests the name of them),
	* and always rejects directories and "other" file system elements.
	* It also has no {@link #getListener FileFilterListener}.
	*/
	public BaseFilter() {
		this(null);
	}
	
	/**
	* Calls <code>{@link #BaseFilter(FileMode, DirectoryMode, FileFilterListener) this}({@link FileMode#test}, {@link DirectoryMode#reject}, listener)</code>.
	* So, this filter will always test normal files (and only tests the name of them),
	* and always rejects directories and "other" file system elements.
	* All filter events are sent to listener.
	*/
	public BaseFilter(FileFilterListener listener) {
		this(FileMode.test, DirectoryMode.reject, listener);
	}
	
	/**
	* Calls <code>{@link #BaseFilter(FileMode, DirectoryMode, FileFilterListener) this}(fileMode, directoryMode, null)</code>.
	* So, this filter will always reject "other" file system elements,
	* and for normal files/directories, only tests the name of each File (assuming {@link FileMode#test}/{@link DirectoryMode#test}).
	* It also has no {@link #getListener FileFilterListener}.
	*/
	public BaseFilter(FileMode fileMode, DirectoryMode directoryMode) throws IllegalArgumentException {
		this(fileMode, directoryMode, null);
	}
	
	/**
	* Calls <code>{@link #BaseFilter(FileMode, DirectoryMode, OtherMode, PartMode, FileFilterListener) this}(fileMode, directoryMode, {@link OtherMode#reject}, {@link PartMode#name}, listener)</code>.
	* So, this filter will always reject "other" file system elements,
	* and for normal files/directories, only tests the name of each File (assuming {@link FileMode#test}/{@link DirectoryMode#test}).
	* All filter events are sent to listener.
	*/
	public BaseFilter(FileMode fileMode, DirectoryMode directoryMode, FileFilterListener listener) throws IllegalArgumentException {
		this(fileMode, directoryMode, OtherMode.reject, PartMode.name, listener);
	}
	
	/**
	* Calls <code>{@link #BaseFilter(FileMode, DirectoryMode, OtherMode, PartMode, FileFilterListener) this}(fileMode, directoryMode, {@link OtherMode#reject}, {@link PartMode#name}, null)</code>.
	* So, this filter has no {@link #getListener FileFilterListener}.
	*/
	public BaseFilter(FileMode fileMode, DirectoryMode directoryMode, OtherMode otherMode, PartMode partMode) throws IllegalArgumentException {
		this(fileMode, directoryMode, otherMode, partMode, null);
	}
	
	/**
	* Constructs a new BaseFilter instance.
	* <p>
	* @param fileMode a {@link FileMode} instance that specifies how normal files will be handled
	* @param directoryMode a {@link DirectoryMode} instance that specifies how directories will be handled
	* @param otherMode a {@link OtherMode} instance that specifies how "other" file system elements will be handled
	* @param partMode a {@link PartMode} instance that specifies what part of a File's full path will be tested
	* @param listener a {@link FileFilterListener} instance that can be supplied to receive filter events; may be null
	* @throws IllegalArgumentException if fileMode, directoryMode, otherMode, or partMode is null
	*/
	public BaseFilter(FileMode fileMode, DirectoryMode directoryMode, OtherMode otherMode, PartMode partMode, FileFilterListener listener) throws IllegalArgumentException {
		Check.arg().notNull(fileMode);
		Check.arg().notNull(directoryMode);
		Check.arg().notNull(otherMode);
		Check.arg().notNull(partMode);
		// no checks on listener
		
		this.fileMode = fileMode;
		this.directoryMode = directoryMode;
		this.otherMode = otherMode;
		this.partMode = partMode;
		this.listener = listener;
	}
	
	// -------------------- accessors --------------------
	
	/** Returns {@link #fileMode}. */
	public FileMode getFileMode() { return fileMode; }
	
	/** Returns {@link #directoryMode}. */
	public DirectoryMode getDirectoryMode() { return directoryMode; }
	
	/** Returns {@link #otherMode}. */
	public OtherMode getOtherMode() { return otherMode; }
	
	/** Returns {@link #partMode}. */
	public PartMode getPartMode() { return partMode; }
	
	/** Returns {@link #listener}. */
	public FileFilterListener getListener() { return listener; }
	
	// -------------------- accept and helper methods --------------------
	
	/**
	* Reports whether or not file is accepted by this filter
	* and fires the event to {@link #getListener the FileFilterListener} if it is non-null.
	* <p>
	* Implementation here merely wraps a call to {@link #acceptImpl acceptImpl}.
	* <p>
	* @throws IllegalArgumentException if file is null
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to the file
	*/
	@Override public boolean accept(File file) throws IllegalArgumentException, SecurityException {
		Check.arg().notNull(file);
		
		boolean accepted = acceptImpl(file);
		if (getListener() != null) {
			if (accepted) getListener().accepted(file, "accepted by " + getClass().getSimpleName());
			else getListener().rejected(file, "REJECTED by " + getClass().getSimpleName());
		}
		return accepted;
	}
	
	/**
	* Reports whether or not file is accepted by this filter.
	* <p>
	* Implementation here sees if file is a normal file/directory/"other" file system element
	* and applies {@link #getFileMode}/{@link #getDirectoryMode}/{@link #getOtherMode} as appropriate.
	* If the mode calls for the filter's test to be applied to file,
	* then returns <code>{@link #passesTest(File) passesTest}(file)</code>.
	* <p>
	* @throws IllegalArgumentException if file is null
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to the file
	*/
	protected boolean acceptImpl(File file) throws IllegalArgumentException, SecurityException {		
		if (file.isFile()) {
			switch (getFileMode()) {
				case accept: return true;
				case test: return passesTest(file);
				case reject: return false;
				default: throw new IllegalStateException("getFileMode() = " + getFileMode() + " is unexpected");
			}
		}
		else if (file.isDirectory()) {
			switch (getDirectoryMode()) {
				case accept: return true;
				case test: return passesTest(file);
				case reject: return false;
				default: throw new IllegalStateException("getDirectoryMode() = " + getDirectoryMode() + " is unexpected");
			}
		}
		else {
			switch (getOtherMode()) {
				case accept: return true;
				case test: return passesTest(file);
				case reject: return false;
				default: throw new IllegalStateException("getOtherMode() = " + getOtherMode() + " is unexpected");
			}
		}
	}
	
	/**
	* Applies this filter's test to file.
	* <p>
	* Implementation here returns <code>{@link #passesTest(String) passesTest}( {@link #extractPart extractPart}(file) )</code>.
	*/
	protected boolean passesTest(File file) {
		return passesTest( extractPart(file) );
	}
	
	/** Extracts and returns the part of file's full path that is specified by {@link #getPartMode}. */
	protected String extractPart(File file) {
		switch (getPartMode()) {
			case name: return file.getName();
			case path: return file.getPath();
			case other: throw new IllegalStateException("getPartMode() = " + getPartMode() + " is incompatible with the extractPart method; probable error: forgot to override one of passesTest(File), acceptImpl, or accept");
			default: throw new IllegalStateException("getPartMode() = " + getPartMode() + " is unexpected");
		}
	}
	
	/**
	* Determines whether or not part (which is some part of a File's full path) passes this filter's test.
	* <p>
	* <i>The implementation here always throws an IllegalStateException.</i>
	* <p>
	* Subclasses must either override this method or ensure that it is never called.
	* There are two ways that they can achieve the latter.
	* First, they could make FileMode/DirectoryMode/OtherMode choices that cover all cases before any other detail of the file is considered.
	* This is the approach taken by {@link DirectoryFilter} and {@link NormalFileFilter}.
	* Second, they could override one or more of the methods in this method's call chain
	* (i.e. override {@link #passesTest(File)}, {@link #acceptImpl acceptImpl}, {@link #accept accept}).
	* in a manner that does not call the implementation here.
	* This is the approach taken by many classes, such as {@link TarableFilter} and {@link VisibleFilter}.
	* <p>
	* This method is not abstract to avoid forcing subclasses which do not call it (as described above) to implement it.
	*/
	protected boolean passesTest(String part) {
		throw new IllegalStateException("this method should never be called unless overridden--see the BaseFilter.passesTest(String) javadocs");
	}
	
	// -------------------- getDescription (from javax.swing.filechooser.FileFilter) --------------------
	
	/** {@inheritDoc} */
	public abstract String getDescription();
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_fileHandling() {
			File file = FileUtil.createTempLog("BaseFilter_UnitTest_file");
			
			TestFilter acceptFilter = new TestFilter(FileMode.accept);
			Assert.assertTrue( acceptFilter.accept(file) );
			
			TestFilter rejectFilter = new TestFilter(FileMode.reject);
			Assert.assertFalse( rejectFilter.accept(file) );
		}
		
		@Test public void test_directoryHandling() {
			File directory = new File("./");
			
			TestFilter acceptFilter = new TestFilter(DirectoryMode.accept);
			Assert.assertTrue( acceptFilter.accept(directory) );
			
			TestFilter rejectFilter = new TestFilter(DirectoryMode.reject);
			Assert.assertFalse( rejectFilter.accept(directory) );
		}
		
		@Test public void test_otherHandling() throws IOException {
			File other = findOther();
			if (other == null) {
				System.out.println("UNABLE to find a \"other\" file system element, so test_otherHandling could not be executed");
			}
			else {
				System.out.println("found this \"other\" file system element, which will use in this test: " + other.getPath());
				
				TestFilter acceptFilter = new TestFilter(OtherMode.accept);
				Assert.assertTrue( acceptFilter.accept(other) );
				
				TestFilter rejectFilter = new TestFilter(OtherMode.reject);
				Assert.assertFalse( rejectFilter.accept(other) );
			}
		}
		
		private File findOther() throws IOException {
			for (File root : File.listRoots()) {
				File other = findOther(root);
				if (other != null) return other;
			}
			return null;
		}
		
		private File findOther(File file) throws IOException {
			if (file.isFile()) {
				return null;
			}
			else if (file.isDirectory()) {
				for (File child : DirUtil.getContents(file)) {
					File other = findOther(child);
					if (other != null) return other;
				}
				return null;
			}
			else {
				return file;
			}
		}
		
		@Test public void test_partHandling() throws IOException {
			File file = FileUtil.createTempLog("BaseFilter_UnitTest_part");
			
			TestFilter acceptFilterName = new TestFilter(PartMode.name, file.getName());
			Assert.assertTrue( acceptFilterName.accept(file) );
			
			TestFilter rejectFilterName = new TestFilter(PartMode.name, file.getName() + "sdfsdf");
			Assert.assertFalse( rejectFilterName.accept(file) );
			
			TestFilter acceptFilterPath = new TestFilter(PartMode.path, file.getPath());
			Assert.assertTrue( acceptFilterPath.accept(file) );
			
			TestFilter rejectFilterPath = new TestFilter(PartMode.path, file.getPath() + "vnbvnv");
			Assert.assertFalse( rejectFilterPath.accept(file) );
		}
		
		private static class TestFilter extends BaseFilter {
			
			private final String textExpected;
			
			private TestFilter(FileMode fileMode) throws IllegalArgumentException {
				this(fileMode, DirectoryMode.reject, OtherMode.reject, PartMode.name, null);
			}
			
			private TestFilter(DirectoryMode directoryMode) throws IllegalArgumentException {
				this(FileMode.reject, directoryMode, OtherMode.reject, PartMode.name, null);
			}
			
			private TestFilter(OtherMode otherMode) throws IllegalArgumentException {
				this(FileMode.reject, DirectoryMode.reject, otherMode, PartMode.name, null);
			}
			
			private TestFilter(PartMode partMode, String textExpected) throws IllegalArgumentException {
				this(FileMode.test, DirectoryMode.test, OtherMode.test, partMode, textExpected);
			}
			
			private TestFilter(FileMode fileMode, DirectoryMode directoryMode, OtherMode otherMode, PartMode partMode, String textExpected) throws IllegalArgumentException {
				super(fileMode, directoryMode, otherMode, partMode);
				
				this.textExpected = textExpected;
			}
			
			protected boolean passesTest(String part) {
				return part.equals(textExpected);
			}
			
			@Override public String getDescription() {
				return "Only used for unit tests...";
			}
			
		}
		
	}
	
}
