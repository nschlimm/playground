/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io;

import bb.io.filefilter.DirectoryFilter;
import bb.io.filefilter.JavaFilter;
import bb.io.filefilter.NormalFileFilter;
import bb.io.filefilter.SuffixFilter;
import bb.util.Check;
import bb.util.OsUtil;
import bb.util.ThrowableUtil;
import bb.util.logging.LogUtil;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
* Provides static utility methods for dealing with directories.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see FileUtil
* @author Brent Boyer
*/
public final class DirUtil {
	
	// -------------------- ensureExists --------------------
	
	/**
	* Makes sure that directory exists, creating it and any parent directories if necessary.
	* <p>
	* The usefulness of this method (compared to simply calling {@link File#mkdirs File.mkdirs}) is:
	* <ol>
	*  <li>this method only calls File.mkdirs if directory fails to exist in the first place</li>
	*  <li>it checks that the call to File.mkdirs succeeded</li>
	*  <li>it checks that an actual directory was created, not some other type of file</li>
	* </ol>
	* <p>
	* @return directory, which enables method call chaining
	* @throws IllegalArgumentException if directory is null
	* @throws SecurityException if a security manager exists
	* and does not permit verification of the existence of directory and all necessary parent directories;
	* or if it does not permit directory and all necessary parent directories to be created
	* @throws IllegalStateException if directory failed to be created or is not an actual directory but is some other type of file
	*/
	public static File ensureExists(File directory) throws IllegalArgumentException, SecurityException, IllegalStateException {
		Check.arg().notNull(directory);
		
		if (!directory.exists()) {
			boolean created = directory.mkdirs();
			if (!created) throw new IllegalStateException("directory = " + directory.getPath() + " failed to exist and could not be created");
		}
		
		if (!directory.isDirectory()) throw new IllegalArgumentException("directory = " + directory.getPath() + " is not a directory");
		
		return directory;
	}
	
	// -------------------- contains --------------------
	
	/**
	* Determines whether or not directory contains file (which could be either a normal file or a directory).
	* <p>
	* For the purposes of this method, "contains" means "equals or is found inside".
	* Consequently, this method returns true if directory equals file.
	* <p>
	* This method works correctly regardless of whether directory or file
	* were initially specified as relative, absolute, or canonical paths
	* because it first internally converts them both to canonical paths.
	* <p>
	* <b>Warning:</b> directory must actually exist, since a call will be made to its {@link File#isDirectory isDirectory} method.
	* In contrast, file need not actually exist.
	* <p>
	* @param directory an existing filesystem directory
	* @param file an arbitrary filesystem path that need not exist nor and which can resolve to either a normal file or directory
	* @throws IllegalArgumentException if directory is {@link Check#validDirectory not valid}; file == null
	* @throws SecurityException if a security manager exists and denies read access to directory
	* @throws IOException if an I/O problem occurs
	*/
	public static boolean contains(File directory, File file) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validDirectory(directory);
		Check.arg().notNull(file);
		
		return getFullPath(file).startsWith( getFullPath(directory) );
	}
	
	/**
	* Returns a String that always starts with {@link File#getCanonicalPath getCanonicalPath}.
	* If file is a directory, then further ensures that the result ends with {@link File#separatorChar}.
	* <p>
	* This method was written to support the path comparison logic inside {@link #contains}
	* because getCanonicalPath does not guarantee the above behavior for directories.
	* <p>
	* To see why this behavior is critical for the logic inside the current implementation of contains,
	* consider the case that on some platform getCanonicalPath returns "a/b/someName" for directory
	* and "a/b/someNamePlusMore" for file:
	* if simply used getCanonicalPath for the path determination, would wrongly return true
	* but if use this method, will correctly return false.
	*/
	private static String getFullPath(File file) throws IOException {
		if (file.isDirectory()) {
			String directoryPath = file.getCanonicalPath();
			if (directoryPath.endsWith(File.separator)) return directoryPath;
			else return directoryPath + File.separatorChar;
		}
		else {
			return file.getCanonicalPath();
		}
	}
	
	// -------------------- findRootWithSpaceFreeMax, getWorking --------------------
	
	/**
	* Returns that filesystem root directory which has the most free space.
	* <p>
	* Contract: the result is always a {@link Check#validDirectory valid directory}
	*/
	public static File findRootWithSpaceFreeMax() {
		long spaceFreeMax = 0;
		File rootMax = null;
		for (File root : File.listRoots()) {
			if (root.getFreeSpace() > spaceFreeMax) {
				spaceFreeMax = root.getFreeSpace();
				rootMax = root;
			}
		}
		Check.state().validDirectory(rootMax);
		return rootMax;
	}
	
	/**
	* Returns a new File representing the default temp directory
	* (that is, the path returned by a call to <code>System.getProperty("java.io.tmpdir")</code>).
	* <p>
	* @throws RuntimeException (or some subclass) if any problem occurs
	*/
	public static File getTemp() throws RuntimeException {
		try {
			File temp = new File( System.getProperty("java.io.tmpdir") );
			Check.state().validDirectory(temp);
			return temp;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	/**
	* Returns a new File representing the current working directory
	* (that is, the path returned by a call to <code>System.getProperty("user.dir")</code>).
	* <p>
	* @throws RuntimeException (or some subclass) if any problem occurs
	*/
	public static File getWorking() throws RuntimeException {
		try {
			File working = new File( System.getProperty("user.dir") );
			Check.state().validDirectory(working);
			return working;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- getContents, getTree, getFilesInTree --------------------
	
	/** Returns {@link #getContents(File, FileFilter) getContents(directory, null)}. */
	public static File[] getContents(File directory) throws IllegalArgumentException, SecurityException, IOException {
		return getContents(directory, null);
	}
	
	
	/** Returns {@link #getContents(File, FileFilter, boolean) getContents(directory, fileFilter, true)}. */
	public static File[] getContents(File directory, FileFilter fileFilter) throws IllegalArgumentException, SecurityException, IOException {
		return getContents(directory, fileFilter, true);
	}
	
	/**
	* Returns the contents of a directory.
	* <p>
	* The usefulness of this method, compared to the File.listFiles methods, is:
	* <ol>
	*  <li>first checks that directory is valid</li>
	*  <li>guarantees that the result returned is non-null (tho may be zero length)</li>
	*  <li>can specify that the result is to be sorted in ascending order</li>
	*  <li>handles some operating system specific bugs</li>
	* </ol>
	* <p>
	* @param directory a File for the directory whose contents are to be retrieved
	* @param fileFilter restricts what appears in the result; may be null, in which case no filtering is done
	* @param sortResult specifies whether or not the result should be sorted in ascending order (if false, the order is unspecified)
	* @throws IllegalArgumentException if directory is {@link Check#validDirectory not valid}
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static File[] getContents(File directory, FileFilter fileFilter, boolean sortResult) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validDirectory(directory);
		
		File[] files = directory.listFiles(fileFilter);
		if (files == null) {
if (OsUtil.isMicrosoft() && directory.getName().equals("System Volume Information")) return new File[0];
// +++ handles a bug with java on windoze: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4803836
			throw new IOException("listFiles returned null for " + directory.getPath());	// this can happen -- see javadocs on the listFiles method
		}
		
		if (sortResult) Arrays.sort(files);
		return files;
	}
	
	/** Returns {@link #getTree(File, FileFilter) getTree(directory, null)}. */
	public static File[] getTree(File directory) throws IllegalArgumentException, SecurityException, IOException {
		return getTree(directory, null);
	}
	
	/** Returns {@link #getTree(File, FileFilter, boolean) getTree(directory, fileFilter, true)}. */
	public static File[] getTree(File directory, FileFilter fileFilter) throws IllegalArgumentException, SecurityException, IOException {
		return getTree(directory, fileFilter, true);
	}
	
	/**
	* Explores the tree rooted at directory and returns all the Files
	* (normal files, subdirectories, and other file system elements) that are accepted by fileFilter.
	* <p>
	* Subdirectories are handled as follows:
	* <ol>
	*  <li>if a subdirectory is accepted by fileFilter, then not only is it present in the result, but its contents are recursively explored depth first for further elements in the result</li>
	*  <li>if a subdirectory is rejected by fileFilter, then not only is it absent from the result, but its contents are unexplored</li>
	* </ol>
	* <i>Consequently, be careful about supplying a FileFilter that always rejects directories (e.g. {@link JavaFilter}),
	* since that will prevent subdirectory exploration (unless that is a desird effect).</i>
	* <p>
	* Contract: the result is never null, and is guaranteed to be sorted in ascending order if sortResult is true.
	* <p>
	* @param directory a File for the directory whose tree files are to be retrieved
	* @param fileFilter a java.io.FileFilter instance for filtering the returned result; may be null (i.e. no filtering is done);
	* note that if fileFilter rejects a subdirectory, then that entire subtree is excluded
	* @param sortResult specifies whether or not the result should be sorted in ascending order (if false, the order returned is unspecified)
	* @throws IllegalArgumentException if directory is null, non-existent, or is not an actual directory
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static File[] getTree(File directory, FileFilter fileFilter, boolean sortResult) throws IllegalArgumentException, SecurityException, IOException {
		List<File> fileList = new ArrayList<File>(128);
		exploreTree(directory, fileFilter, fileList, sortResult, false);
		return fileList.toArray( new File[fileList.size()] );
	}
	
	/** Returns {@link #getFilesInTree(File, FileFilter) getFilesInTree(directory, null)}. */
	public static File[] getFilesInTree(File directory) throws IllegalArgumentException, SecurityException, IOException {
		return getFilesInTree(directory, null);
	}
	
	/** Returns {@link #getFilesInTree(File, FileFilter, boolean) getFilesInTree(directory, fileFilter, true)}. */
	public static File[] getFilesInTree(File directory, FileFilter fileFilter) throws IllegalArgumentException, SecurityException, IOException {
		return getFilesInTree(directory, fileFilter, true);
	}
	
	/**
	* Explores the tree rooted at directory and returns all the <i>normal files</i> that are accepted by fileFilter.
	* <p>
	* <i>Subdirectories are unaffected by fileFilter:</i>
	* they are always absent from the result, however, they are always recursively examined depth first.
	* <p>
	* Contract: the result is never null, and is guaranteed to be sorted in ascending order if sortResult is true.
	* <p>
	* @param directory a File for the directory whose tree files are to be retrieved
	* @param fileFilter a java.io.FileFilter instance for filtering the returned result; may be null (i.e. no filtering is done);
	* note that if fileFilter rejects a subdirectory, then that entire subtree is excluded
	* @param sortResult specifies whether or not the result should be sorted in ascending order (if false, the order returned is unspecified)
	* @throws IllegalArgumentException if directory is null, non-existent, or is not an actual directory
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static File[] getFilesInTree(File directory, FileFilter fileFilter, boolean sortResult) throws IllegalArgumentException, SecurityException, IOException {
		List<File> fileList = new ArrayList<File>(128);
		exploreTree(directory, fileFilter, fileList, sortResult, true);
		return fileList.toArray( new File[fileList.size()] );
	}
	
// +++ altho have not seen it in practice, there is the possibility that fileList grows so large that it exhausts memory.
// To cope with this, instead of storing all the stuff in fileList, could return a Iterator<File>
// which descends directory by directory depth first on an as requested basis (i.e. call to next would trigger another search).
// Thus, it need only store the contents of a given directory and its ancestors.
// This would greatly limit the memory used.
// Any problems with guaranteeing global sortedness?
	private static void exploreTree(File directory, FileFilter fileFilter, List<File> fileList, boolean sortResult, boolean restrictToNormalFiles) throws IllegalArgumentException, SecurityException, IOException {
		// directory checked by call to getContents below
		
		for (File path : getContents(directory, null, sortResult)) {	// CRITICAL: supply null, not fileFilter, to getContents because will apply fileFilter in a custom manner below
			if (path.isFile()) {
				if ((fileFilter == null) || fileFilter.accept(path)) {
					fileList.add(path);
				}
			}
			else if (path.isDirectory()) {
				if (restrictToNormalFiles) {
					exploreTree(path, fileFilter, fileList, sortResult, restrictToNormalFiles);
				}
				else if ((fileFilter == null) || fileFilter.accept(path)) {
					fileList.add(path);
					exploreTree(path, fileFilter, fileList, sortResult, restrictToNormalFiles);
				}
			}
			else {
				if (restrictToNormalFiles) {
					// do nothing: other file system elements are always rejected in this mode
				}
				else if ((fileFilter == null) || fileFilter.accept(path)) {
					fileList.add(path);
				}
			}
		}
	}
	
	// -------------------- areContentsSame --------------------
	
	// see also .../filePrograms/src/DirectoryComparer.java for a much more thorough comparison
	
	/**
	* Determines whether or not the contents of directory1 and directory2 are identical.
	* <p>
	* The sorted contents of both directory1 and directory2 are retrieved using {@link #getContents(File)}.
	* False is immediately returned if the sizes differ.
	* Otherwise, both SortedSets are simultaneously iterated thru and each corresponding element is compared for equality.
	* (This works, because even tho they have different roots, identical trees will nevertheless have the same paths relative to each root.)
	* False is returned if ever a pair have different names or types.
	* (The types are directories, normal files, and "other" file system elements.)
	* If both are directories, then this method is recursively called to see if they differ.
	* If both are normal files, they must be byte for byte identical, which may require a deep file examination to confirm.
	* <p>
	* @throws IllegalArgumentException if directory1 or directory2 are not {@link Check#validDirectory valid}
	* @throws SecurityException if a security manager exists and denies read access to either directory
	* @throws IOException if an I/O problem occurs
	*/
	public static boolean areContentsSame(File directory1, File directory2) throws IllegalArgumentException, SecurityException, IOException {
		// args checked by getContents below
		
		File[] contents1 = getContents(directory1);
		File[] contents2 = getContents(directory2);
		if (contents1.length != contents2.length) return false;
		for (int i = 0; i < contents1.length; i++) {
			File file1 = contents1[i];
			File file2 = contents2[i];
			
			if (!file1.getName().equals(file2.getName())) {
				return false;
			}
			
			if (file1.isDirectory()) {
				if (file2.isDirectory()) {
					boolean dirsSame = areContentsSame(file1, file2);	// if both are directories, recursively drill down
					if (!dirsSame) return false;
				}
				else {
					return false;
				}
			}
			
			// Note: the logic above and below is NOT if/else if in order to handle file systems in which elements can be both directories and normal files
			
			if (file1.isFile()) {
				if (file2.isFile()) {
					if (file1.length() != file2.length()) return false;	// performance optimization: it is really quick to check this before make an expensive call to FileUtil.compareContents below
					
					long indexDifferent = FileUtil.compareContents(file1, file2);	// if both are normal files, examine the contents of the files themselves
					if (indexDifferent != -1) return false;
				}
				else {
					return false;
				}
			}
			
			if (!file1.isDirectory() && !file1.isFile()) {
				if (!file2.isDirectory() && !file2.isFile()) {
// do what?  I am not sure if any comparison can be done with these "other" file system elements...
				}
				else {
					return false;
				}
			}
		}
		return true;
	}
// +++ is there any additional meta data that should also do a comparison on?  do NOT want modification times, but maybe permisions or something?
	
	// -------------------- delete, deleteIfExists, gut, erase --------------------
	
	/**
	* Deletes a directory along with all of its contents.
	* It is useful because the delete method of File fails if the directory has any content.
	* <p>
	* Note: {@link FileUtil#delete FileUtil.delete} should be used to delete a normal file.
	* <p>
	* @param directory a File for the directory that is to be deleted
	* @throws IllegalArgumentException if directory is {@link Check#validDirectory not valid}
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static void delete(File directory) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validDirectory(directory);
		erase(directory, true);
	}
	
	/**
	* Calls <code>{@link #delete delete}(directory)</code> if directory != null and directory exists.
	* <p>
	* Note: {@link FileUtil#deleteIfExists FileUtil.deleteIfExists} should be used to delete a normal file if it exists.
	* <p>
	* @param directory a File for the directory that is to be deleted; may be null or non-existent
	* @throws IllegalArgumentException if directory is not null but is {@link Check#validDirectory not valid}
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs; this includes failure of the directory to be deleted
	*/
	public static void deleteIfExists(File directory) throws IllegalArgumentException, SecurityException, IOException {
		if ( (directory != null) && directory.exists() ) delete(directory);
	}
	
	/**
	* Deletes <i>only the contents</i> of a directory; the directory itself will remain.
	* It is useful because the File class has no such method.
	* <p>
	* @param directory a File for the directory whose contents are to be deleted
	* @throws IllegalArgumentException if directory is {@link Check#validDirectory not valid}
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static void gut(File directory) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validDirectory(directory);
		erase(directory, false);
	}
	
	/**
	* Calls <code>{@link #gut gut}(directory)</code> if directory != null and directory exists.
	* <p>
	* Note: {@link FileUtil#deleteIfExists FileUtil.deleteIfExists} should be used to delete a normal file if it exists.
	* <p>
	* @param directory the directory that is to be gutted; may be null or non-existent
	* @throws IllegalArgumentException if directory is not null but is {@link Check#validDirectory not valid}
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	public static void gutIfExists(File directory) throws IllegalArgumentException, SecurityException, IOException {
		if ( (directory != null) && directory.exists() ) gut(directory);
	}
	
	/**
	* Always deletes the contents of directory;
	* the fate of the directory itself is specified by the deleteDirectory arg.
	* This method is useful because the File class has no such method.
	* <p>
	* This method marches down the directory tree depth first, deleting all normal files,
	* and then deleting directories as they become empty.
	* <p>
	* @param directory a File for the directory whose contents are to be deleted
	* @throws IllegalArgumentException if directory is null, non-existent, or is not an actual directory
	* @throws SecurityException if a security manager exists and denies read access to some file
	* @throws IOException if an I/O problem occurs
	*/
	protected static void erase(File directory, boolean deleteDirectory) throws IllegalArgumentException, SecurityException, IOException {
		// no need to arg check here, as the the public methods which calls this method should do so already, plus the call to getContents below will as well
		
			// first delete the contents of the directory:
		for (File file : getContents( directory, null )) {
			if ( file.isDirectory() )
				erase(file, true);
			else {
				FileUtil.delete( file );
			}
		}
		
			// and then delete the directory itself, if this is called for:
		if (deleteDirectory) {
			boolean deleted = directory.delete();
			if (!deleted) throw new IOException("Failed to delete " + directory.getPath());
		}
	}
	
	/**
	* Trys to rename directory1 to directory2, and throws an IOException if the rename failed.
	* It is useful because {@link File#renameTo File.renameTo} unfortunately merely returns a boolean indicating
	* the success of the operation, rather than throwing an Exception.
	* <p>
	* Note: {@link FileUtil#rename FileUtil.rename} should be used to rename normal files.
	* <p>
	* @param directory1 the currently existing directory
	* @param directory2 the directory that is to be renamed to
	* @throws IllegalArgumentException if directory1 is {@link Check#validDirectory not valid}; directory2 == null or already exists
	* @throws SecurityException if a security manager exists and denies write access to directory1 or directory2
	* @throws IOException if an I/O problem occurs; this includes failure of the directory to be renamed
	*/
	public static void rename(File directory1, File directory2) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validDirectory(directory1);
		Check.arg().notNull(directory2);
		if (directory2.exists()) throw new IllegalArgumentException("directory2 = " + directory2.getPath() + " already exists");
		
		boolean renamed = directory1.renameTo( directory2 );
		if (!renamed) throw new IOException("failed to rename directory1 = " + directory1.getPath() + " to directory2 = " + directory2.getPath());
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private DirUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static String dirTestPrefix = "DirUtil_test#";
		
		private static int dirTestId = 0;
		
		private static File dirTest;
		
		// -------------------- setupClass, teardownClass and helper methods --------------------
		
		@BeforeClass public static void setupClass() throws Exception {
			dirTest = makeTestDirectoryTree();
		}
		
		@AfterClass public static void teardownClass() throws Exception {
			for (File path : LogUtil.getLogDirectory().listFiles()) {
				if (path.getName().startsWith(dirTestPrefix)) {
					delete(path);
					Assert.assertFalse( path.exists() );
				}
			}
		}
		
		private static File makeTestDirectoryTree() throws Exception {
			File dir0 = new File(LogUtil.getLogDirectory(), dirTestPrefix + (dirTestId++));
			ensureExists(dir0);
			
			File dir1 = new File(dir0, "dir1");
			ensureExists(dir1);
			File file1 = new File(dir0, "file1.tmp");
			if ( !file1.createNewFile() ) throw new IOException("failed to create " + file1.getPath());
						
			File dir2 = new File(dir1, "dir2");
			ensureExists(dir2);
			File file2 = new File(dir1, "file2.tmp");
			if ( !file2.createNewFile() ) throw new IOException("failed to create " + file2.getPath());
			
			File file3 = new File(dir2, "file3.tmp");
			if ( !file3.createNewFile() ) throw new IOException("failed to create " + file3.getPath());
			
			return dir0;
		}
		
		// -------------------- test_XXX --------------------
		
		@Test public void test_getContents() throws Exception {
			Assert.assertEquals( 2, getContents(dirTest).length );
			Assert.assertEquals( 1, getContents(dirTest, new DirectoryFilter()).length );
			Assert.assertEquals( 1, getContents(dirTest, new SuffixFilter(".tmp")).length );
		}
		
		@Test public void test_getTree() throws Exception {
			Assert.assertEquals( 5, getTree(dirTest).length );
			Assert.assertEquals( 2, getTree(dirTest, new DirectoryFilter()).length );
			Assert.assertEquals( 1, getTree(dirTest, new NormalFileFilter()).length );
		}
		
		@Test public void test_getFilesInTree() throws Exception {
			Assert.assertEquals( 3, getFilesInTree(dirTest).length );
			Assert.assertEquals( 3, getFilesInTree(dirTest, new SuffixFilter(".tmp")).length );
			Assert.assertEquals( 0, getFilesInTree(dirTest, new SuffixFilter(".blah")).length );
		}
		
		@Test public void test_areContentsSame() throws Exception {
			File dirTestDup = makeTestDirectoryTree();
			Assert.assertTrue( areContentsSame(dirTest, dirTestDup) );
		}
		
// +++ need to test all methods...
		
	}
	
}
