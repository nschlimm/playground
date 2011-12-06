/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ see this discussion
	http://forums.sun.com/thread.jspa?messageID=10735917
In particular, at the end, a mention is made of the new Inputs and Outputs classes.
I like their api better, for the most part, than, say, my writeString method below.
SO UPDATE MY CODE BELOW.

+++ see also this guy's work:
"Utility Class Fills in Java's Missing Functionality" by Vlad Patryshev
	http://www.devx.com/Java/Article/27367/1954?pf=true
*/

package bb.io;

import bb.science.FormatUtil;
import bb.util.Check;
import bb.util.StringUtil;
import bb.util.ThrowableUtil;
import bb.util.logging.LogUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods for dealing with normal Files.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* This class checks for thread interruption only before major blocking operations
* in order to achieve a reasonable balance between cancellation responsiveness and program performance
* (see Doug Lea <cite>Concurrent Programming in Java Second Edition</cite> p. 170).
* <p>
* @see DirUtil
* @author Brent Boyer
*/
public final class FileUtil {
	
	// -------------------- getNameMinusExtension, getExtension, changeExtension --------------------
	
	/**
	* Returns the file's name minus any extension, that is, the part of its name up to (but not including) the last '.' char.
	* For example, if presented with a file named "helloWorld.old.txt" then "helloWorld.old" is returned.
	* If no extension exists (either because there is nothing after the last '.' char or a '.' char never occurs)
	* then the complete file name (up to but not including any final '.' char) is returned.
	* Note that the File need not actually exist nor be a normal file.
	* <p>
	* @param file the File whose name is will be returned
	* @throws IllegalArgumentException if file == null
	*/
	public static String getNameMinusExtension(File file) throws IllegalArgumentException {
		Check.arg().notNull(file);
		
		String name = file.getName();
		int indexPeriod = name.lastIndexOf('.');
		if (indexPeriod == -1) return name;
		else return name.substring(0, indexPeriod);
	}
	
// +++ could add a getNameMinusExtension(File file, boolean useFirstPeriod, boolean includePeriod) version
// that is analagous to getExtension(File file, boolean useFirstPeriod, boolean includePeriod)
	
	/**
	* Returns the file's extension, defined here as the part of its name after the last '.' char.
	* <p>
	* This is a convenience method which simply returns
	* <code>{@link #getExtension(File, boolean, boolean) getExtension}(file, false, false)</code>.
	* So, all the rules discussed for that method apply here.
	* For example, if presented with a file named "helloWorld.old.txt" then "txt" is returned.
	* If no extension exists (either because there is nothing after the last '.' char or a '.' char never occurs)
	* then the blank string "" is returned.
	* Note that the File need not actually exist nor be a normal file.
	* <p>
	* @param file the File whose extension will be returned
	* @throws IllegalArgumentException if file is null
	*/
	public static String getExtension(File file) throws IllegalArgumentException {
		return getExtension(file, false, false);
	}
	
	/**
	* Returns the file's extension.
	* <p>
	* If useFirstPeriod is true, then the extension starts at the <i>first</i> occurence of a period (i.e. '.' char) in file's name,
	* otherwise the extension starts at the <i>last</i> occurence of a period in file's name.
	* If includePeriod is true, then the period is included in the result, otherwise it is excluded.
	* For example, if presented with a file named "helloWorld.old.txt", then the following table shows what is returned
	* for the various combinations of useFirstPeriod and includePeriod:
			<!-- http://www.w3.org/TR/REC-html40/struct/tables.html -->
		<table border="1">
			<tr>
				<th rowspan="2" colspan="2"></th>
				<th colspan="2">includePeriod</th>
			</tr>
			<tr>
				<th><i>true</i></th>
				<th><i>false</i></th>
			</tr>
			<tr>
				<th rowspan="2">useFirstPeriod</th>
				<th><i>true</i></th>
				<td><tt>.old.txt</tt></td>
				<td><tt>old.txt</tt></td>
			</tr>
			<tr>
				<th><i>false</i></th>
				<td><tt>.txt</tt></td>
				<td><tt>txt</tt></td>
			</tr>
		</table>
	* <p>
	* If no extension exists (i.e. there is no '.' char) then the blank string "" is returned.
	* Note that the File need not actually exist nor be a normal file.
	* <p>
	* @param file the File whose extension will be returned
	* @param useFirstPeriod if true then the extension starts at the <i>first</i> occurence of a period (i.e. '.' char) in file's name,
	* otherwise the extension starts at the <i>last</i> occurence of a period in file's name
	* @param includePeriod if true, then the period is included in the result, otherwise it is excluded
	* @throws IllegalArgumentException if file is null
	*/
	public static String getExtension(File file, boolean useFirstPeriod, boolean includePeriod) throws IllegalArgumentException {
		Check.arg().notNull(file);
		
		String name = file.getName();
		
		int indexPeriod = useFirstPeriod ? name.indexOf('.') : name.lastIndexOf('.');
		if (indexPeriod == -1) return "";
		
		if (!includePeriod) indexPeriod += 1;
		
		return name.substring(indexPeriod);
	}
	
	/**
	* Trys to change file's extension to extensionNew.
	* If file currently has an extension, it is replaced by extensionNew,
	* and if file lacks an extension, extensionNew is appended to its name.
	* The new file must not currently exist (to be safest, this method never overwrites).
	* <p>
	* @param file the currently existing file
	* @param extensionNew the new extension for file; must not contain a '.' char in it
	* @return a new File instance that has extensionNew
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}; extensionNew is blank or contains a '.' char
	* @throws SecurityException if a security manager exists and write access to file
	* @throws IOException if an I/O problem occurs; this includes failure of the file to be renamed
	*/
	public static File changeExtension(File file, String extensionNew) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file);
		Check.arg().notBlank(extensionNew);
		if (extensionNew.contains(".")) throw new IllegalArgumentException("extensionNew = " + extensionNew + " contains a '.' char");
		
		File parent = file.getParentFile();
		String nameNew = getNameMinusExtension(file) + "." + extensionNew;
		File fileNew = new File(parent, nameNew);
		return rename(file, fileNew);
	}
	
	// -------------------- getRoot --------------------
	
	/**
	* Returns the filesystem root for file.
	* Specifically, this method traces back parent Files until there are no more, and returns the last one found.
	* <p>
	* <i>Note: unlike almost all other methods in this class, file need not be a normal file;</i>
	* it could be a directory as well.
	* Corollary: the result is file itself if file is a filesystem root.
	* <p>
	* @throws IllegalArgumentException if file is null, or non-existent
	* @throws SecurityException if a security manager exists and denies read access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static File getRoot(File file) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(file);
		if (!file.exists()) throw new IllegalArgumentException("file = " + file.getPath() + " is a non-existent path");
		
		file = file.getCanonicalFile();	// CRITICAL: if file is a relative path like ./ then the getParentFile call below will return null prematurely; cure this by putting file into canonical form
		for (File parent = file.getParentFile(); parent != null; parent = file.getParentFile()) {
			file = parent;
		}
		return file;	// file is now the last non-null File in the chain explored by the loop above
	}
	
	// -------------------- getPathRelativeTo, getParentRelativeTo --------------------
	
	/**
	* Returns that portion of the path of fileChild which is relative to the path of fileRoot.
	* For example, if fileChild.getPath() = "/dirA/dirB/dirC/file1" and fileRoot.getPath() = "/dirA/dirB"
	* then the result is "dirC/file1".
	* Note that the result never begins with a path separator char.
	* Neither File need actually exist; this method simply does String operations on their paths.
	* If fileChild equals fileRoot, then a blank String is returned.
	* <p>
	* @throws IllegalArgumentException if fileChild is null; fileRoot is null; fileChild is not contained under fileRoot
	*/
	public static String getPathRelativeTo(File fileChild, File fileRoot) throws IllegalArgumentException {
		Check.arg().notNull(fileChild);
		Check.arg().notNull(fileRoot);
		if (fileChild.equals(fileRoot)) return "";
		
		String pathChild = fileChild.getPath();
		String pathRoot = fileRoot.getPath();
		if ( !pathChild.startsWith(pathRoot) ) throw new IllegalArgumentException("fileChild = " + pathChild + " is not contained under fileRoot = " + pathRoot);
		
		int skipDirChar = pathRoot.endsWith( File.separator ) ? 0 : 1;	// handles the directory separator char being present or absent
		int indexChild = pathRoot.length() + skipDirChar;
		return pathChild.substring(indexChild);
	}
	
	/**
	* Returns a String that represents that part of fileChild's parent directory path which is contained inside fileRoot
	* (that is, fileChild's parent's path relative to fileRoot).
	* For example, if fileChild.getPath() = "/dirA/dirB/dirC/file1" and fileRoot.getPath() = "/dirA/dirB"
	* then the result is "dirC".
	* Note that the result never begins with a path separator char.
	* Neither File need actually exist; this method simply does String operations on their paths.
	* <p>
	* @throws IllegalArgumentException if file == null; if root == null; file has no parent; if root does not contain file's parent
	*/
	public static String getParentRelativeTo(File fileChild, File fileRoot) throws IllegalArgumentException {
		Check.arg().notNull(fileChild);
		Check.arg().notNull(fileRoot);
		
		File parent = fileChild.getParentFile();
		Check.arg().notNull(parent);
		
		String pathParent = parent.getPath();
		String pathRoot = fileRoot.getPath();
		if (!pathParent.startsWith(pathRoot)) throw new IllegalArgumentException("fileChild's parent = " + pathParent + " is not contained inside fileRoot = " + pathRoot);
		
		return pathParent.substring( pathRoot.length() + 1 );	// the + 1 skips over the directory separator char
	}
	
	// -------------------- createTemp, createTempLog --------------------
	
	/**
	* Returns <code>{@link #createTemp(File) createTemp}( new File(path) )</code>.
	* <p>
	* @return a new File constructed from path
	* @throws IllegalArgumentException if path == null
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static File createTemp(String path) throws IllegalArgumentException, SecurityException, RuntimeException {
		Check.arg().notNull(path);
		
		return createTemp( new File(path) );
	}
	
	/**
	* Creates a temporary normal file.
	* <p>
	* Does the following actions:
	* <ol>
	*  <li>attempts to delete file if it currently exists</li>
	*  <li>
	*		attempts to create a new empty version of file
	*		(<b>Warning:</b> does not create any parent directories that do not currently exist,
	*		because those directories cannot be automatically deleted upon exit.
	*		Therefore, this method fails if the parent directories do not all exist.)
	*  </li>
	*  <li>calls deleteOnExit on file, ensuring that file is temporary</li>
	* </ol>
	* Note that unlike {@link File#createTempFile File.createTempFile},
	* which adds random numbers inside the file names that it creates,
	* this method does no filename change, which allows you to work with files of a known name.
	* Furthermore, this method catches all checked Exceptions and converts them to a RuntimeException before rethrowing them,
	* so it may be used to assign fields.
	* <p>
	* @return the supplied file arg
	* @throws IllegalArgumentException if file == null
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	* @see <a href="http://www.devx.com/Java/Article/22018/1954?pf=true">Bugs in temp file deletion</a>
	*/
	public static File createTemp(File file) throws IllegalArgumentException, SecurityException, RuntimeException {
		try {
			Check.arg().notNull(file);
			
			if (file.exists()) {
				boolean deleted = file.delete();
				if (!deleted) throw new RuntimeException("file = " + file.getPath() + " was previously existing and failed to be deleted");
			}
			
			if (file.getParentFile() != null) {	// must check, since is null if file is directly in the root directory
				if (!file.getParentFile().exists()) throw new IllegalStateException("file.getParentFile() = " + file.getParentFile().getPath() + " does not currently exist");
			}
			
			boolean created = file.createNewFile();
			if (!created) throw new RuntimeException("file = " + file.getPath() + " failed to be created");
			
			file.deleteOnExit();
// +++ warning: see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4171239
			
			return file;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	/**
	* Creates a temp file inside the {@link LogUtil#getLogDirectory log directory}.
	* <p>
	* This method behaves as if it returns <code>{@link #createTemp(File) createTemp}( {@link LogUtil#makeLogFile LogUtil.makeLogFile}(childPath) )</code>.
	* However, it is implemented slightly differently:
	* it first ensures that the parent directories exist of the file that is returned.
	* Because of the LogUtil.makeLogFile contract,
	* it is guaranteed that any such newly created directories will be subdirectories of the log directory.
	* Therefore, this is a safe operation.
	* <p>
	* @return a new File constructed from childPath
	* @throws IllegalArgumentException if childPath is {@link Check#notBlank blank}; childPath resolves to a path that falls outside the log directory
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static File createTempLog(String childPath) throws IllegalArgumentException, SecurityException, RuntimeException {
		try {
			// childPath checked by makeLogFile below
			
			File file = LogUtil.makeLogFile(childPath);
			DirUtil.ensureExists( file.getParentFile() );
			return createTemp(file);
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- copy, delete, deleteIfExists, move, rename --------------------
	
	/**
	* Copies source to target.
	* <p>
	* @param source the File that will read bytes from
	* @param target the File that will write bytes to
	* @param append only relevant if target already exists: if true, then adds source's bytes to end of target,
	* if false, then overwrites target
	* @throws IllegalArgumentException if source is {@link Check#validFile not valid};
	* target == null; source and target resolve to the same path
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies access to source or target
	* @throws IOException if an I/O problem occurs
	*/
	public static void copy(File source, File target, boolean append) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
		Check.arg().validFile(source);
		Check.arg().notNull(target);
		if (source.getCanonicalPath().equals(target.getCanonicalPath())) throw new IllegalArgumentException("source and target resolve to the same path = " + source.getCanonicalPath());
		
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(target, append);
			StreamUtil.transfer( in, out );
		}
		finally {
			StreamUtil.close(in);
			StreamUtil.close(out);
		}
	}
	
	/**
	* Trys to delete file, and throws an IOException if the delete failed.
	* It is useful because {@link File#delete File.delete} unfortunately merely returns a boolean indicating
	* the success of the operation, rather than throwing an Exception.
	* <p>
	* Note: {@link DirUtil#delete DirUtil.delete} should be used to delete directories.
	* <p>
	* @param file a normal file that is to be deleted
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws IOException if an I/O problem occurs; this includes failure of the file to be deleted
	*/
	public static void delete(File file) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file);
		
		boolean deleted = file.delete();
		if (!deleted) throw new IOException("Failed to delete " + file.getPath());
	}
	
	/**
	* Calls <code>{@link #delete delete}(file)</code> if file != null and file exists.
	* <p>
	* Note: delete will do some additional tests of file (that it is a normal file that can be read by this application).
	* <p>
	* Note: {@link DirUtil#deleteIfExists DirUtil.deleteIfExists} should be used to delete directories if they exist.
	* <p>
	* @param file a normal file that is to be deleted; may be null or non-existent
	* @throws IllegalArgumentException if file is not a normal file
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws IOException if an I/O problem occurs; this includes failure of the file to be deleted
	*/
	public static void deleteIfExists(File file) throws IllegalArgumentException, SecurityException, IOException {
		if ( (file != null) && file.exists() ) delete(file);
	}
	
	/**
	* Moves file into directory.
	* <p>
	* This is a convenience method that simply returns <code>{@link #rename rename}(file, new File(directory, file.getName())</code>.
	* <p>
	* @param file the file to move
	* @param directory the directory to move file into
	* @return a new File instance whose path is for the new location of file after the move
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid};
	* directory is {@link Check#validDirectory not valid}; rename finds an issue
	* @throws SecurityException if a security manager exists and denies write access to file or directory
	* @throws IOException if an I/O problem occurs; this includes failure of the file to be renamed
	*/
	public static File move(File file, File directory) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file);
		Check.arg().validDirectory(directory);
		
		return rename( file, new File(directory, file.getName()) );
	}
	
	/**
	* Trys to rename file1 to file2.
	* Since file2 may be in a different directory and/or have a different name,
	* this is really a combined move/rename method.
	* <p>
	* This method was written because {@link File#renameTo File.renameTo} unfortunately
	* merely returns a boolean indicating the success of the operation, which forces the user to check.
	* In contrast, this method corrects that defect and throws an Exception instead.
	* <p>
	* Furthermore, for maximum safety, this method will never overwrite an existing but different file.
	* Therefore, it insists that file2 must not currently exist unless it is equal to file1.
	* (One reason why the user may wish to supply file2 equal to file1 is if their
	* operating system has case insensitive file names:
	* perhaps they are simply trying to change file1's name to a standard case.)
	* <p>
	* Note: {@link DirUtil#rename DirUtil.rename} should be used to rename directories.
	* <p>
	* @param file1 the currently existing file
	* @param file2 the file that is to be renamed to
	* @return file2
	* @throws IllegalArgumentException if file1 is {@link Check#validFile not valid};
	* file2 == null; file2 already exists and is not equal to file1
	* @throws SecurityException if a security manager exists and denies write access to file1 or file2
	* @throws IOException if an I/O problem occurs; this includes failure of the file to be renamed
	*/
	public static File rename(File file1, File file2) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file1);
		Check.arg().notNull(file2);
		if (file2.exists() && !file1.equals(file2)) throw new IllegalArgumentException("file2 = " + file2.getPath() + " already exists and is not equal to file1 = " + file1.getPath());
		
		boolean renamed = file1.renameTo( file2 );
		if (!renamed) throw new IOException("failed to rename file1 = " + file1.getPath() + " to file2 = " + file2.getPath());
		return file2;
	}
	
	// -------------------- readBytes, readBytesEnd, readAsciiToChars, readChars, readString --------------------
	
	/**
	* Reads file's contents into a byte[] which is returned.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}
	* @throws IllegalStateException if file holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static byte[] readBytes(File file) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
		Check.arg().validFile(file);
		
		return StreamUtil.drain( new FileInputStream(file) );	// Note: StreamUtil.drain closes the FileInputStream
		
/*
--the above line of code should be the fastest for smaller files, say < ~30 KB,
but should be able to get better performance by memory mapping large files using NIO

	+++ write the mem map code and benchmark it
	using a public method that anyone can call to determine the crossover on their platform
		--in order to avoid disk caching, file sector location on the disk, and other difficult issues,
		maybe the best measurement technique to compare the above versus mem map
		is to create a File[][] called fileMatrix as follows:
			fileMatrix[i] is always a File[] that has an even length and consists of files created with equal size;
			half of the files will be measure under one algorithm, half under the other,
			HOWEVER, for a given i, the algorithms are always shuffled among the various elements elements of the File[];
			a given File is only read once,
			and for a given algorithm, the read times are averaged across all the files that it read
		--may need a fair bit of free space on the drive in order to do this; if have 1.6, measure the free space before run
	--assuming that there is a crossover point, then change the above method to use the optimal algorithm acording to file size

See:
	http://mindprod.com/jgloss/nio.html
	http://www.jroller.com/page/cpurdy/20040406 (2004-04-06 15:53:30.0 entry)
	http://forum.java.sun.com/thread.jspa?threadID=757896&tstart=0
*/
	}
	
	/**
	* Reads the <i>final</i> part of file's contents into a byte[] which is returned.
	* The number of bytes in the result is the minimum of n or file's length.
	* The byte order of the result is the same as the order in the file.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static byte[] readBytesEnd(File file, int n) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file);
		Check.arg().notNegative(n);
		
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
			n = (int) Math.min( n, raf.length() );
			raf.seek( raf.length() - n );
			byte[] bytes = new byte[n];
			raf.readFully(bytes);
			return bytes;
		}
		finally {
			StreamUtil.close(raf);
		}
	}
	
	/**
	* Reads file's contents into a byte[] (using the {@link #readBytes readBytes} method),
	* converts that into a char[] (using the {@link StringUtil#asciiBytesToChars StringUtil.asciiBytesToChars} method)
	* returns the char[].
	* So, the data inside file must consist only of US-ASCII bytes.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid};
	* file cannot be read by this application; a non-ascii byte (i.e. a negative value) is encountered
	* @throws IllegalStateException if file holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static char[] readAsciiToChars(File file) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
		// file checked by readBytes below
		
		return StringUtil.asciiBytesToChars( readBytes(file) );
	}
	
	/**
	* Reads file's contents into a byte[] (using {@link #readBytes readBytes}),
	* converts that into a char[] (using {@link StringUtil#bytesToChars StringUtil.bytesToChars}),
	* and then returns the char[].
	* So, the data inside file must be encoded using this platform's default encoding.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid};
	* file cannot be read by this application
	* @throws IllegalStateException if file holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException (or a subclass, like MalformedInputException or UnmappableCharacterException) if an I/O problem occurs
	*/
	public static char[] readChars(File file) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
		// file checked by readBytes below
		
		return StringUtil.bytesToChars( readBytes(file) );
	}
	
	/**
	* Returns <code>new String( readBytes(file) )</code>.
	* Note: the platform's default char encoding is implicitly used to convert the bytes into a String.
	* So, the data inside file must be encoded using this platform's default encoding.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid};
	* file cannot be read by this application
	* @throws IllegalStateException if file holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static String readString(File file) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
		// file checked by readBytes below
		
		return new String( readBytes(file) );
	}
	
	// -------------------- writeBytes, writeString --------------------
	
	/**
	* Writes bytes into file.
	* All resources (e.g. OutputStreams) that are created as part of this process will be closed upon method return.
	* <p>
	* @param append specifies whether to append to an existing file or to overwrite its contents
	* @throws IllegalArgumentException if bytes is null; file is null; file is a directory
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies write access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static void writeBytes(byte[] bytes, File file, boolean append) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(bytes);
		Check.arg().notNull(file);
		if (file.isDirectory()) throw new IllegalArgumentException("file = " + file.getPath() + " refers to directory, not a normal text file");
		//if (!file.canWrite()) throw new IllegalArgumentException("file = " + file.getPath() + " refers to a file that cannot be read by this application");	// DO NOT UNCOMMENT: it will fail if the file does not exist, which could be a common situation; instead let the write below fail
		
		OutputStream out = null;
		try {
			out = new FileOutputStream(file, append);
			out.write(bytes);
		}
		finally {
			StreamUtil.close(out);
		}
	}
	
	/**
	* Simply executes <code>{@link #writeBytes writeBytes}( string.getBytes(), file, append )</code>.
	* Note: that call to <code>string.{@link String#getBytes() getBytes}</code>
	* implicitly uses the platform's default char encoding to convert the String into a byte[].
	* <p>
	* @param append specifies whether to append to an existing file or to overwrite its contents
	* @throws IllegalArgumentException if string is null; file is null; file is a directory
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies write access to file
	* @throws IOException if an I/O problem occurs
	*/
	public static void writeString(String string, File file, boolean append) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().notNull(string);
		
		writeBytes( string.getBytes(), file, append );
	}
	
	// -------------------- compareContents --------------------
	
	// Note that my .../filePrograms/src/FileComparer.java class does a more thorough check (looks at all the bytes of the 2 files, as opposed to stopping on the first discrepancy like below)
	
	/**
	* Compares the contents of the two supplied normal files, byte by byte.
	* Returns the (zero-based) index of the first byte position where they differ, else returns -1 if they are identical.
	* If one file is smaller than the other, but the bytes in the 2 files are identical up to where the smaller ends,
	* then the value returned is the length of the smaller file (i.e. the index of the first extra byte in the larger file).
	* <p>
	* Contract: the result is -1 if and only the files are identical, else is >= 0.
	* <p>
	* @throws IllegalArgumentException if file1 or file2 is {@link Check#validFile not valid};
	* @throws SecurityException if a security manager exists and denies read access to either file
	* @throws IOException if an I/O problem occurs
	*/
	public static long compareContents(File file1, File file2) throws IllegalArgumentException, SecurityException, IOException {
		Check.arg().validFile(file1);
		Check.arg().validFile(file2);
		
		InputStream in1 = null;
		InputStream in2 = null;
		try {
			in1 = new FileInputStream(file1);
			in2 = new FileInputStream(file2);
			
			int bufferSize = calcBufferSize(file1, file2);
			byte[] bytes1 = new byte[bufferSize];
			byte[] bytes2 = new byte[bufferSize];
			
			long index = 0;
			while (true) {
					// attempt a fast bulk read into byte arrays:
				int nRead1 = in1.read(bytes1);
				int nRead2 = in2.read(bytes2);
				
					// see if any ran out of data:
				int minRead = Math.min(nRead1, nRead2);
				if (minRead == -1) {	// at least one ran out of data
					if (nRead1 == nRead2) return -1;	// both ran out of data, and so the files are identical
					else return index;	// only 1 ran out of data, and so the files are NOT identical
				}
				
					// compare those parts of the byte[] which are both populated with data:
				int offset = findWhereDiffer(bytes1, bytes2, minRead);
				if (offset > -1) return index + offset;	// a difference was found, return the index where first one occurs
				else index += minRead;	// no differences found so far, so increment index and continue search
				
					// NASTY CORNER CASE: one of the bulk reads failed to pull in as much data as the other; resolve by doing a slow byte by byte read and compare:
				if (nRead1 != nRead2) {
					LogUtil.getLogger2().logp(Level.WARNING, "FileUtil", "compareContents", "nRead1 = " + nRead1 + " != nRead2 = " + nRead2 + "; while need to resolve by doing a slow byte by byte read and compare");
					for ( ; nRead1 < nRead2; nRead1++) {
						int read1 = in1.read();
						if ( (read1 == -1) || (read1 != bytes2[nRead1]) ) return index;
						else ++index;
					}
					for ( ; nRead2 < nRead1; nRead2++) {
						int read2 = in2.read();
						if ( (read2 == -1) || (read2 != bytes1[nRead2]) ) return index;
						else ++index;
					}
				}
			}
		}
		finally {
			StreamUtil.close(in1);
			StreamUtil.close(in2);
		}
	}
	
	private static int calcBufferSize(File file1, File file2) {
		int lowerBound = 16;
		int upperBound = 1024 * 1024;
		long minLength = Math.min( file1.length(), file2.length() );
		if (minLength < lowerBound) return lowerBound;
		else if (minLength > upperBound) return upperBound;
		else return (int) minLength;
	}
	
	private static int findWhereDiffer(byte[] bytes1, byte[] bytes2, int limit) {
		for (int i = 0; i < limit; i++) {
			if (bytes1[i] != bytes2[i]) return i;
		}
		return -1;
	}
	
	// -------------------- canEncode --------------------
	
	/**
	* Determines whether or not this platform's {@link Charset#defaultCharset default Charset})
	* can encode c and then decode it back to the exact same value.
	* <p>
	* The issue with some platforms is that they cannot handle certain chars correctly.
	* For example, windoze is notorious in that its default CharSet, Cp1252, has the following strange behavior:
	* <blockquote>
	*	[Cp1252 is] Microsoft Windows variant of Latin-1, NT default. Beware.
	*	Some unexpected translations occur when you read with this default encoding,
	*	e.g. codes 128..159 are translated to 16 bit chars with bits in the high order byte on.
	*	It does not just truncate the high byte on write and pad with 0 on read.
	*	For true Latin-1 see 8859-1.
	* </blockquote>
	* See the <a href="http://mindprod.com/jgloss/encoding.html">Java glossary article on encoding</a> for more details.
	* <p>
	* This method is fairly expensive to call: you should never use it on every char that you wish to encode.
	* It is currently only used in the UnitTest class, and is private.
	*/
	private static boolean canEncode(char c) {
		try {
			char[] charArray = new char[] { c };
			ByteBuffer byteBuffer = Charset.defaultCharset().newEncoder().encode( java.nio.CharBuffer.wrap( charArray ) );
			char[] charArrayRestored = Charset.defaultCharset().decode( byteBuffer ).array();
			return (charArray[0] == charArrayRestored[0]);
		}
		catch (CharacterCodingException cce) {
			//d.p.s("bad char = " + ((int) c));
			return false;
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private FileUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// Note: many of the tests below create their Files in the log directory (i.e. call createTempLog instead of createTemp).
	// This is done even if they are temp files, since have seen temp files fail to be deleted on exit (e.g. if the JVM crashes),
	// which leaves a painful cleanup task if they are located all over the file system.
	// In contrast, putting them all in the log directory is safer, because I typically gut it after all tests have finished.
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getXXXExtension() {
			File file = new File("helloWorld.old.txt");
			
			String nameMinusExtension = getNameMinusExtension(file);
			Assert.assertEquals( "helloWorld.old", nameMinusExtension );
			
			String extension = getExtension(file);
			Assert.assertEquals( "txt", extension );
			
			String extensionTT = getExtension(file, true, true);
			Assert.assertEquals( ".old.txt", extensionTT );
			
			String extensionTF = getExtension(file, true, false);
			Assert.assertEquals( "old.txt", extensionTF );
			
			String extensionFT = getExtension(file, false, true);
			Assert.assertEquals( ".txt", extensionFT );
			
			String extensionFF = getExtension(file, false, false);
			Assert.assertEquals( "txt", extensionFF );
		}
		
		@Test public void test_changeExtension() throws Exception {
			File fileNew = null;
			try {
				String namePrefix = "abc";
				String extensionOriginal = "def";
				File fileOriginal = createTempLog( namePrefix + "." + extensionOriginal );
				
				String extensionNew = "xyz";
				fileNew = changeExtension(fileOriginal, extensionNew);
				
				Assert.assertFalse( fileOriginal.exists() );
				Assert.assertTrue( fileNew.exists() );
			}
			finally {
				if (fileNew != null) delete(fileNew);
			}
		}
		
		@Test public void test_getRelativePath() {
			File fileChild = LogUtil.makeLogFile("dirA/dirB/dirC/file1");
			File fileRoot = LogUtil.makeLogFile("dirA/dirB");
			String resultExpected = "dirC" + File.separatorChar + "file1";
			Assert.assertEquals( resultExpected, getPathRelativeTo(fileChild, fileRoot) );
		}
		
		@Test public void test_getParentRelativeTo() {
			File fileChild = LogUtil.makeLogFile("dirA/dirB/dirC/file1");
			File fileRoot = LogUtil.makeLogFile("dirA/dirB");
			Assert.assertEquals( "dirC", getParentRelativeTo(fileChild, fileRoot) );
		}
		
		@Test public void test_delete() throws Exception {
			String shouldBeUnusedName = "test_delete_shouldBeUnusedName.txt";
			File file = createTempLog(shouldBeUnusedName);
//			Assert.assertTrue( file.createNewFile() );
// +++ line above OUGHT to work, and USED to work, but starting on 2010-05-05 I saw it fail and had to do the lines below; file bug report with Sun...
file.createNewFile();
Assert.assertTrue( file.exists() );
			
			delete(file);
			Assert.assertFalse( file.exists() );
		}
		
		@Test public void test_move() throws Exception {
			File f1 = null;
			File dir = null;
			File f2 = null;
			try {
				f1 = LogUtil.makeLogFile("test_move_shouldBeUnusedName1.txt");
				Assert.assertTrue( f1.createNewFile() );
				dir = DirUtil.ensureExists( LogUtil.makeLogFile("test_move") );
				f2 = new File(dir, f1.getName());
				move(f1, dir);
				Assert.assertFalse( f1.exists() );
				Assert.assertTrue( f2.exists() );
			}
			finally {
				deleteIfExists(f1);
				DirUtil.deleteIfExists(dir);
				deleteIfExists(f2);
			}
		}
		
		@Test public void test_rename() throws Exception {
			File f1 = null;
			File f2 = null;
			try {
				f1 = LogUtil.makeLogFile("test_rename_shouldBeUnusedName1.txt");
				Assert.assertTrue( f1.createNewFile() );
				f2 = LogUtil.makeLogFile("test_rename_shouldBeUnusedName2.txt");
				rename( f1, f2 );
				Assert.assertFalse( f1.exists() );
				Assert.assertTrue( f2.exists() );
			}
			finally {
				deleteIfExists(f1);
				deleteIfExists(f2);
			}
		}
		
		@Test public void test_copy_compareContents() throws Exception {
			File source = createTempLog("sourceTest.txt");
			File target = createTempLog("targetTest.txt");
			
				// special case: test that two empty files compare as identical
			writeString("", source, false);
			copy(source, target, false);
			Assert.assertTrue( compareContents(source, target) == -1 );
			
			byte[] bytesRandom = new byte[16 * 1024 * 1024];
			Random random = new Random();
			
				// normal case: test that two files with the same random data (because copy is used) compare as identical:
			random.nextBytes(bytesRandom);
			writeBytes(bytesRandom, source, false);
			copy(source, target, false);
			Assert.assertTrue( compareContents(source, target) == -1 );
				// then append a single byte to target and confirm that the files differ where expected:
			writeBytes(new byte[] {1}, target, true);
			Assert.assertTrue( compareContents(source, target) == bytesRandom.length );
			
				// normal case: test that two files with (highly likely) different random data compare as non-identical:
			random.nextBytes(bytesRandom);
			writeBytes(bytesRandom, source, false);
			random.nextBytes(bytesRandom);
			writeBytes(bytesRandom, target, false);
			Assert.assertFalse( compareContents(source, target) == -1 );
		}
		
		/**
		* Measures how long it takes to read up to 100 MB.
		* <p>
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Read 2045 files, 105620961 bytes
			Time = 2.952956956 s
			Rate = 3.576786339042051E7 bytes/second
		* </code></pre>
		*/
		@Test public void benchmark_readBytes() throws Exception {
			File root = getRoot( LogUtil.getLogDirectory() );
			File[] files = DirUtil.getFilesInTree(root);
			int numberFilesRead = 0;
			long numberBytesRead = 0;
			long start = System.nanoTime();
			for (File file : files) {
				++numberFilesRead;
				numberBytesRead += readBytes(file).length;
				if (numberBytesRead >= 100*1000*1000) break;	// break as soon as read at least 100 MB
			}
			long stop = System.nanoTime();
			
			System.out.println("Read " + numberFilesRead + " files, " + numberBytesRead + " bytes");
			double diff = (stop - start) * 1e-9;
			System.out.println("Time = " + FormatUtil.toEngineeringTime(diff));
			System.out.println("Rate = " + (numberBytesRead / diff) + " bytes/second");
		}
		
		/**
		* Measures how long it takes to read up to 1 MB of data that occurs at the end of files.
		* <p>
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Read 1119 files, 1000699 bytes
			Time = 113.08593300000001 ms
			Rate = 8849013.961798413 bytes/second
		* </code></pre>
		*/
		@Test public void benchmark_readBytesEnd() throws Exception {
			File root = getRoot( LogUtil.getLogDirectory() );
			File[] files = DirUtil.getFilesInTree(root);
			int numberFilesRead = 0;
			long numberBytesRead = 0;
			long start = System.nanoTime();
			for (File file : files) {
				++numberFilesRead;
				numberBytesRead += readBytesEnd(file, 1024).length;
				if (numberBytesRead >= 1*1000*1000) break;	// break as soon as read at least 10 MB
			}
			long stop = System.nanoTime();
			
			System.out.println("Read " + numberFilesRead + " files, " + numberBytesRead + " bytes");
			double diff = (stop - start) * 1e-9;
			System.out.println("Time = " + FormatUtil.toEngineeringTime(diff));
			System.out.println("Rate = " + (numberBytesRead / diff) + " bytes/second");
			
			byte[] bytes1 = readBytesEnd(files[1], 1024);
			char[] chars1 = new char[bytes1.length];
			for (int i = 0; i < chars1.length; i++) {
				chars1[i] = (char) bytes1[i];
			}
			System.out.println("Below are the final 1024 (ascii) chars from file " + files[1].getPath() + ":");
			System.out.println( new String(chars1) );
		}
		
		@Test public void test_readChars() throws Exception {
			StringBuilder sb = new StringBuilder(256);
			for (int i = 0; i < 256; i++) {
				char c = (char) i;
				if (canEncode(c)) sb.append(c);	// CRITICAL: see the canEncode javadocs for why need to test this, especially on windoze
			}
			String s = sb.toString();
			
			File file = createTempLog("fileWithNonAsciiChars");
			writeString(s, file, false);
			
			char[] chars = readChars(file);
			String sRestored = new String(chars);
			
			Assert.assertEquals( s, (sRestored) );
		}
		
// +++ really need to benchmark the performance of readChars versus readSciiToChars...
		
/*
+++ fill in...
		@Test public void test_compareContents() throws Exception {
			File f1 = new File("D:/finance.zip");
			File f2 = new File("D:/finance2.zip");
			Assert.assertTrue( compareContents(f1, f2) == -1 );
		}
*/
		
		// NOTE: see also my class .../projectsMine/filePrograms/src/BenchmarkFileWriting.java which has some really interesting benchmarks in it
	}
	
}
