/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ in JDK 7, 64-bit ZIP archives will be supported, so need to change this class to support them; see
	http://blogs.sun.com/CoreJavaTechTips/entry/superduper_slow_jar
		look in the comments for "Posted by Xueming Shen  on May 26, 2009 at 11:55 AM PDT #"

--note that the Java ZIP api is such crap that it does not even support appending to an existing Zip file:
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4129445
*/

package bb.io;

import bb.util.Check;
import bb.util.DateUtil;
import bb.util.Execute;
import bb.util.OsUtil;
import bb.util.Properties2;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
* Provides static utility methods for dealing with ZIP paths.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see <a href="http://www.pkware.com/support/appnote.html">The ZIP file specification</a>
* @see <a href="http://www.ddj.com/articles/1997/9712/9712e/9712e.htm?topic=compression">DDJ article on Java and ZIP</a>
* @see <a href="http://dogma.net/markn/articles/JavaZip/ZipCreate.java">The ZipCreate program</a>
* @see <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip120.html">Self-extracting JAR files</a>
* @see "The O'Reilly Java I/O book"
* @author Brent Boyer
*/
public final class ZipUtil {
	
	// -------------------- switch constants --------------------
	
	private static final String zipFile_key = "-zipFile";
	private static final String listContents_key = "-listContents";
	private static final String pathsToArchive_key = "-pathsToArchive";
	private static final String appendBackup_key = "-appendBackup";
	private static final String appendTimeStamp_key = "-appendTimeStamp";
	private static final String appendExtension_key = "-appendExtension";
	private static final String appendAll_key = "-appendAll";
	private static final String filter_key = "-filter";
	private static final String directoryExtraction_key = "-directoryExtraction";
	private static final String overwrite_key = "-overwrite";
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main} for a content list operation. */
	private static final List<String> keysLegal_listContents = Arrays.asList(
		zipFile_key,
		listContents_key
	);
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main} for an archive operation. */
	private static final List<String> keysLegal_archive = Arrays.asList(
		zipFile_key,
		pathsToArchive_key,
		appendBackup_key, appendTimeStamp_key, appendExtension_key, appendAll_key,
		filter_key
	);
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main} for an extract operation. */
	private static final List<String> keysLegal_extract = Arrays.asList(
		zipFile_key,
		directoryExtraction_key,
		overwrite_key
	);
	
	// -------------------- misc constants --------------------
	
	/** Is 4 * 2^30 = 2^32 = . */
	private static final long fourGibi = 4L * 1024L * 1024L * 1024L;
	
	/**
	* Maximum size of a file that can be put into a ZIP archive file by Java.
	* This value is valid as of 2005/1/16 under JDK 1.5.0.
	* <p>
	* @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4681995">bug report #1</a>
	* @see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4418997">bug report #2</a>
	* @see "The file D:\software\java\proposalsQuestionsPostingsEtc\zipFileSupportInJava.txt"
	*/
	private static final long zipableFileSizeLimit = fourGibi - 1L;
	
	/**
	* Maximum size of any ZIP archive file that can be read by Java.
	* This value is valid as of 2005/1/16 under JDK 1.5.0.
	* <p>
	* @see <a href="http://www.backupforall.com/slicing.php">Discussion #1 of the official ZIP standard</a>
	* @see <a href="http://www.miclasificado.com.ar/cgi/News/i14/Zip_files_in_windows_xp.php">Discussion #2 of the official ZIP standard</a>
	*/
	private static final long zipArchiveSizeLimit = fourGibi - 1L;
	
	private static final boolean giveUserFeedback = true;
	
	// -------------------- main --------------------
	
	/**
	* May be used either to list the contents of, archive to, or extract from a ZIP file.
	* The action to perform and all of its specifications are embedded as command line switches in args.
	* <p>
	* If listing archive contents,
	* the action to perform (listing) is specified as the (key-only) command line switch <code>-listContents</code>.
	* The target ZIP archive file is the (key/value) command line switch <code>-zipFile <i>insertPathHere</i></code>.
	* For example, here is a complete command line that lists the contents of a zip file located in the working directory:
	* <pre><code>
		java  bb.io.ZipUtil  -zipFile ./backup.zip  -listContents
	* </code></pre>
	* <p>
	* If archiving,
	* the source path(s) to be archived (which can be either normal files or directories)
	* are specified as the (key/value) command line switch <code>-pathsToArchive <i>commaSeparatedListOfPaths</i></code>.
	* The target ZIP archive file is the same <code>-zipFile</code> command line switch mentioned before.
	* The following optional switches may also be supplied:
	* <ol>
	*  <li><code>-appendBackup</code> appends <code>_backup</code> to the ZIP file's name</li>
	*  <li><code>-appendTimeStamp</code> appends <code>_</code> followed by a timestamp to the ZIP file's name</li>
	*  <li><code>-appendExtension</code> appends <code>.zip</code> to the ZIP file's name</li>
	*  <li><code>-appendAll</code> is equivalent to supplying all the above append options</li>
	*  <li>
	*		<code>-filter <i>fullyQualifiedClassName</i></code>
	*		specifies the name of a FileFilter which limits what gets archived.
	*		Since this FileFilter class will be instantiated by a call to Class.forName,
	*		it must have a no-arg constructor.
	*  </li>
	* </ol>
	* For example, here is a complete command line that archives just the class files found under two different class directories:
	* <pre><code>
		java  bb.io.ZipUtil  -zipFile ../log/test.zip  -pathsToArchive ../class1,../class2  -filter bb.io.filefilter.ClassFilter
	* </code></pre>
	* <p>
	* If extracting,
	* the target directory to extract into is always specified as the command line switch <code>-directoryExtraction <i>insertPathHere</i></code>.
	* The source ZIP archive file is the same <code>-zipFile</code> command line switch mentioned before.
	* An optional switch <code>-overwrite <i>true/false</i></code> may also be supplied
	* to control if overwriting of existing normal files is allowed or not.
	* By default overwriting is not allowed (an Exception will be thrown if extraction needs to overwrite an existing file).
	* For example, here is a complete command line that extracts a ZIP file to a specific directory, overwriting any existing files:
	* <pre><code>
		java  bb.io.ZipUtil  -zipFile ../log/test.zip  -directoryExtraction ../log/zipExtractOutput  -overwrite true
	* </code></pre>
	* <p>
	* Note that the switches may appear in any order on the command line.
	* <p>
	* If this method is this Java process's entry point (i.e. first <code>main</code> method),
	* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
	* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
	* Otherwise, this method returns and leaves the JVM running.
	*/
	public static void main(final String[] args) {
		Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
			Check.arg().notEmpty(args);
			
			Properties2 switches = new Properties2(args);
			if (switches.containsKey(listContents_key)) {
				switches.checkKeys(keysLegal_listContents);
				
				File zipFile = getArchiveFile(switches, zipFile_key, "zip");
				
				ZipEntry[] entries = getEntries(zipFile, true);
				for (ZipEntry entry : entries) {
					System.out.println( entry );
				}
			}
			else if (switches.containsKey(pathsToArchive_key)) {
				switches.checkKeys(keysLegal_archive);
				
				File zipFile = getArchiveFile(switches, zipFile_key, "zip");
				File[] pathsToArchive = getPathsToArchive(switches);
				FileFilter filter = getFileFilter(switches);
				
				archive( zipFile, filter, pathsToArchive );
			}
			else if (switches.containsKey(directoryExtraction_key)) {
				switches.checkKeys(keysLegal_extract);
				
				File zipFile = getArchiveFile(switches, zipFile_key, "zip");
				File directoryExtraction = switches.getFile(directoryExtraction_key).getCanonicalFile();
				boolean overwrite = switches.getBoolean(overwrite_key, false);
				
				extract( zipFile, directoryExtraction, overwrite );
			}
			
			return null;
		} } );
	}
	
	static File getArchiveFile(Properties2 switches, String key, String extension) throws Exception {	// default access so that TarUtil can call
		String path = switches.getProperty(key);
		if (switches.containsKey(appendBackup_key) || switches.containsKey(appendAll_key))
			path += "_backup";
		if (switches.containsKey(appendTimeStamp_key) || switches.containsKey(appendAll_key))
			path += "_" + DateUtil.getTimeStampForFile();
		if (switches.containsKey(appendExtension_key) || switches.containsKey(appendAll_key))
			path += "." + extension;
		
		return new File(path).getCanonicalFile();
	}
	
	static FileFilter getFileFilter(Properties2 switches) throws Exception {	// default access so that TarUtil can call
		if (switches.containsKey(filter_key))
			return (FileFilter) Class.forName(switches.getProperty(filter_key)).newInstance();
		else
			return null;
	}
	
	static File[] getPathsToArchive(Properties2 switches) throws Exception {	// default access so that TarUtil can call
		String property = switches.getProperty(pathsToArchive_key);
		String[] tokens = property.split(",", -1);	// -1 to detect an erroneous final ','
		File[] paths = new File[ tokens.length ];
		for (int i = 0; i < paths.length; i++) {
			Check.state().notBlank(tokens[i]);
			paths[i] = new File(tokens[i]).getCanonicalFile();
		}
		return paths;
	}
	
	// -------------------- isZipable --------------------
	
	/**
	* If path is a directory, then returns true.
	* Else if path is a normal file, then returns true if path's length (in bytes) is <= {@link #zipableFileSizeLimit}, false otherwise.
	* Else returns false.
	* <p>
	* @throws IllegalArgumentException if path == null or path does not exist
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to path
	*/
	public static boolean isZipable(File path) throws IllegalArgumentException, SecurityException {
		Check.arg().notNull(path);
		if (!path.exists()) throw new IllegalArgumentException("path = " + path.getPath() + " does not exist");
		
		if (path.isDirectory()) {
			return true;
		}
		else if (path.isFile()) {
			return (path.length() <= zipableFileSizeLimit);
		}
		else {
			return false;
		}
	}
	
	// -------------------- archive and helper methods --------------------
	
	/**
	* Writes each element of pathsToArchive to a new ZIP format archive file specified by zipFile.
	* If any element is a directory, the entire contents of its directory tree will be archived (as limited by filter).
	* Paths that would otherwise be archived may be screened out by supplying a non null value for filter.
	* <p>
	* Altho this method does not use {@link DirUtil#getTree DirUtil.getTree},
	* it uses filter to control subdirectory exploration in a similar manner.
	* <p>
	* In general, the path stored in the archive
	* is the path relative to the <i>parent</i> of the relevant element of pathsToArchive.
	* For example, suppose that some element of pathsToArchive corresponds to <code>D:/someDirectory</code>,
	* and suppose that that directory contains the subdirectory and child file <code>D:/someDirectory/anotherDirectory/childFile</code>.
	* Then the paths stored in the archive are <code>anotherDirectory</code> and <code>anotherDirectory/childFile</code> respectively.
	* <p>
	* One complication with the above scheme is paths which are file system roots: they have no parents.
	* Examples include the windows path <code>C:</code> or the unix path <code>/</code>.
	* In cases like these, this method uses an imaginary parent name of the form <code>rootXXX</code> (where XXX is an integer).
	* For example, on a windows machine, if pathsToArchive contains the paths <code>C:</code> and <code>D:</code>,
	* then the contents of <code>C:</code> might be stored in the archive
	* with a path that starts with <code>root1</code>, and the contents of <code>D:</code>
	* may have an archive path that starts with <code>root2</code>.
	* This behavior ensures that the archive preserves the separate origins of the 2 sources,
	* which is necessary so that they do not get mixed when extracted.
	* <p>
	* @param zipFile the ZIP File that will write the archive data to
	* @param filter a FileFilter that can use to screen out certain paths from being written to the archive;
	* may be null (so everything specified by pathsToArchive gets archived);
	* if not null, see warnings in {@link DirUtil#getTree DirUtil.getTree} on directory acceptance
	* @param pathsToArchive array of all the paths to archive
	* @throws Exception if any Throwable is caught; the Throwable is stored as the cause, and the message stores the path of zipFile;
	* here are some of the possible causes:
	* <ol>
	*  <li>
	*		IllegalArgumentException if pathsToArchive == null; pathsToArchive.length == 0;
	*		zipFile == null;
	*		zipFile already exists and either is not a normal file or is but already has data inside it;
	*		zipFile has an invalid extension;
	*		any element of pathsToArchive is null, does not exist, cannot be read, is equal to zipFile, its path contains zipFile,
	*		or it fails {@link #isZipable isZipable}
	*  </li>
	*  <li>SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to some path</li>
	*  <li>IOException if an I/O problem occurs</li>
	* </ol>
	*/
	public static void archive(File zipFile, FileFilter filter, File... pathsToArchive) throws Exception {
		try {
			Check.arg().notNull(zipFile);
			if (zipFile.exists()) {
				if (!zipFile.isFile()) throw new IllegalArgumentException("zipFile = " + zipFile.getPath() + " is not a normal file");
				if (zipFile.length() != 0) throw new IllegalArgumentException("zipFile = " + zipFile.getPath() + " already exists and already has data inside it");
			}
			if (!zipFile.getName().toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("zipFile = " + zipFile.getPath() + " has an invalid extension for a zip file");
			Check.arg().notEmpty(pathsToArchive);
			for (int i = 0; i < pathsToArchive.length; i++) {
				Check.arg().notNull(pathsToArchive[i]);
				if ( !pathsToArchive[i].exists() ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " is a non-existent path");
				if ( !pathsToArchive[i].canRead() ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " cannot be read by this application");
				if ( pathsToArchive[i].equals(zipFile) ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " is the same File as arg zipFile = " + zipFile.getPath());
				if ( pathsToArchive[i].isDirectory() && DirUtil.contains(pathsToArchive[i], zipFile) ) throw new IllegalArgumentException("the directory corresponding to arg pathsToArchive[" + i + "] = " + pathsToArchive[i].getCanonicalPath() + " will contain the path corresponding to arg zipFile = " + zipFile.getCanonicalPath() );
			}
			
			ZipOutputStream zos = null;
			try {
				zos = new ZipOutputStream( new BufferedOutputStream( new FileOutputStream(zipFile) ) );
				for (File path : pathsToArchive) {
					archive( path, new FileParent(path), zos, filter );
				}
				zos.flush();	// CRITICAL: flush before do the file size test below, since data may be buffered
				if (zipFile.length() > zipArchiveSizeLimit) throw new ZipException("this call to archive wrote " + zipFile.length() + " bytes to zipFile = " + zipFile.getPath() + " however the maximum valid ZIP file size = " + zipArchiveSizeLimit);
			}
			finally {
				if (giveUserFeedback) ConsoleUtil.eraseLine();
				StreamUtil.close(zos);
			}
		}
		catch (Throwable t) {
			throw new Exception("See cause for the underlying Throwable; happened for zipFile = " + (zipFile != null ? zipFile.getPath() : "<null>"), t);
		}
	}
	
	/**
	* Writes path as a new ZipEntry to the ZipOutputStream.
	* If path is a normal file, then next writes path's data to zipOutputStream.
	* This ZipEntry is always written compressed, and at the highest compression level.
	* <p>
	* If path is a directory, then this method additionally calls itself on the contents
	* (thus recursing thru the entire directory tree).
	* <p>
	* <b>Warning</b>: several popular programs (e.g. winzip) fail to display mere directory entries.
	* Furthermore, if just a directory entry is present (i.e. it is empty), they also may fail
	* to create a new empty directoy when extracting the ZIP file's contents.
	* These are bugs in their behavior.
	* <p>
	* An optional FileFilter can be supplied to screen out paths that would otherwise be archived.
	* <p>
	* <i>This method does not close zipOutputStream:</i> that is the responsibility of the caller.
	* <p>
	* The caller also must take on the responsibility to not do anything stupid, like write
	* path more than once, or have the path be the same File that zipOutputStream is writing to.
	* <p>
	* @param path the File to archive
	* @param fileParent the FileParent for path
	* @param zipOutputStream the ZipOutputStream that will write the archive data to
	* @param filter a FileFilter that can use to screen out certain files from being written to the archive; may be null (so everything specified by path gets archived)
	* @throws Exception if any Throwable is caught; the Throwable is stored as the cause, and the message stores path's information;
	* here are some of the possible causes:
	* <ol>
	*  <li>IllegalArgumentException if path fails {@link #isZipable isZipable}</li>
	*  <li>SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to path</li>
	*  <li>IOException if an I/O problem occurs</li>
	* </ol>
	*/
	private static void archive(File path, FileParent fileParent, ZipOutputStream zipOutputStream, FileFilter filter) throws Exception {
		try {
			if ((filter != null) && !filter.accept(path)) return;
			if (!isZipable(path)) throw new IllegalArgumentException("path = " + path.getPath() + " has a length (" + path.length() + " bytes) which exceeds the ZIP format file size limit = " + zipableFileSizeLimit);
			if (giveUserFeedback) ConsoleUtil.overwriteLine(path.getPath());
			
			ZipEntry entry = new ZipEntry( fileParent.getRelativePath(path, '/') );	// Note: getRelativePath will ensure that '/' is the path separator and that directories end with '/'
				// Note: no need to call any of the setXXX methods of ZipEntry -- the ones I would care about will all get called automaticly when write to the stream
			zipOutputStream.setMethod( ZipOutputStream.DEFLATED );
			zipOutputStream.setLevel( Deflater.BEST_COMPRESSION );
// +++ may be faster to use Deflater.NO_COMPRESSION on known compressed files (e.g. .zip, .jpg, etc);
// on the other hand, I am still sometimes seeing a tiny bit of compression (say 1-5%) even on already compressed types like zip and jpg
			zipOutputStream.putNextEntry( entry );
			if (path.isFile()) readInFile(path, zipOutputStream);
			zipOutputStream.closeEntry();
			
			if (path.isDirectory()) {
				for (File fileChild : DirUtil.getContents(path, null)) {	// supply null, since we test at beginning of this method (supplying filter here will just add a redundant test)
					archive( fileChild, fileParent, zipOutputStream, filter );
				}
			}
		}
		catch (Throwable t) {
			String preface = "See cause for the underlying Throwable; happened for path = ";
			if ( (t instanceof Exception) && (t.getMessage().startsWith(preface)) ) throw (Exception) t;	// CRITICAL: see if t was actually the wrapping Exception generated by a subsequent recursive call to this method, and if so simply rethrow it unmodified to avoid excessive exception chaining
			throw new Exception(preface + (path != null ? path.getPath() : "<null>"), t);
		}
	}
	
	/**
	* Reads all the bytes from path and writes them to out.
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	private static void readInFile(File path, OutputStream out) throws IOException {
		InputStream in = null;
		try {
			in = new FileInputStream(path);	// no buffering, because StreamUtil.transfer below will do that itself
			StreamUtil.transfer(in, out);
		}
		finally {
			StreamUtil.close(in);
		}
	}
	
	// -------------------- getEntries --------------------
	
	/**
	* Creates a {@link ZipFile} out of zipFile named zipApiFile,
	* and then returns <code>{@link #getEntries(ZipFile, boolean) getEntries}( zipApiFile, sortResult )</code>.
	* Final action is to {@link StreamUtil#close(Object) close} zipApiFile.
	* <p>
	* @param zipFile the ZIP format file to be read
	* @param sortResult if true, then the result is first sorted by each entry's name before return;
	* otherwise the order is the sequence read from zipFile
	* @throws IllegalArgumentException if zipFile fails {@link Check#validFile Check.validFile}
	* @throws IOException if an I/O problem occurs
	*/
	public static ZipEntry[] getEntries(File zipFile, boolean sortResult) throws IllegalArgumentException, IOException {
		Check.arg().validFile(zipFile);
		
		ZipFile zipApiFile = null;
		try {
			zipApiFile = new ZipFile(zipFile);
			return getEntries(zipApiFile, sortResult);
		}
		finally {
			StreamUtil.close(zipApiFile);
		}
	}
	
	/**
	* Returns all the {@link ZipEntry}s in zipApiFile.
	* <p>
	* <b>Warning:</b> zipApiFile is not closed by this method.
	* <p>
	* @param zipApiFile the {@link ZipFile} to get the entries from
	* @param sortResult if true, then the result ZipEntry[] is first sorted by each entry's name before return;
	* otherwise the order is the sequence read from zipApiFile
	* @throws IllegalArgumentException if zipApiFile == null
	* @throws IllegalStateException if zipApiFile has been closed
	*/
	public static ZipEntry[] getEntries(ZipFile zipApiFile, boolean sortResult) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(zipApiFile);
		
		ZipEntry[] result = new ZipEntry[ zipApiFile.size() ];
		int i = 0;
		for (Enumeration<? extends ZipEntry> e = zipApiFile.entries(); e.hasMoreElements(); ) {
			result[i++] = e.nextElement();
		}
		
		if (sortResult) {
			Arrays.sort(result, new Comparator<ZipEntry>() {
				public int compare(ZipEntry ze1, ZipEntry ze2) {
					return ze1.getName().compareTo( ze2.getName() );
				}
			} );
		}
		
		return result;
	}
	
	// -------------------- extract --------------------
	
	/**
	* Creates a {@link ZipFile} out of zipFile named zipApiFile,
	* and then calls <code>{@link #extract(ZipFile, File, boolean) extract}( zipApiFile, directoryExtraction, overwrite )</code>.
	* Final action is to {@link StreamUtil#close(Object) close} zipApiFile.
	* <p>
	* @param zipFile the ZIP format file to be read
	* @param directoryExtraction the directory that will extract the contents of zipApiFile into
	* @param overwrite specifies whether or not extraction is allowed to overwrite an existing normal file inside directoryExtraction
	* @throws IllegalArgumentException if zipFile fails {@link Check#validFile Check.validFile};
	* zipApiFile has an invalid extension;
	* directoryExtraction fails {@link DirUtil#ensureExists DirUtil.ensureExists}
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead method
	* denies read access to zipFile or directoryExtraction
	* @throws IllegalStateException if directoryExtraction failed to be created or is not an actual directory but is some other type of file
	* @throws IOException if an I/O problem occurs
	*/
	public static void extract(File zipFile, File directoryExtraction, boolean overwrite) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
		Check.arg().validFile(zipFile);
		
		ZipFile zipApiFile = null;
		try {
			zipApiFile = new ZipFile(zipFile);
			extract( zipApiFile, directoryExtraction, overwrite);
		}
		finally {
			StreamUtil.close(zipApiFile);
		}
	}
	
	/**
	* Extracts the contents of zipApiFile to directoryExtraction.
	* <p>
	* <b>Warning:</b> zipApiFile is not closed by this method.
	* <p>
	* It is an error if zipApiFile does not exist, is not a normal file, or is not in the proper ZIP format.
	* In contrast, directoryExtraction need not exist, since it (and any parent directories) will be created if necessary.
	* <p>
	* @param zipApiFile the {@link ZipFile} to get the entries from
	* @param directoryExtraction the directory that will extract the contents of zipApiFile into
	* @param overwrite specifies whether or not extraction is allowed to overwrite an existing normal file inside directoryExtraction
	* @throws IllegalArgumentException if zipApiFile is null;
	* zipApiFile has an invalid extension;
	* directoryExtraction fails {@link DirUtil#ensureExists DirUtil.ensureExists}
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead method
	* denies read access to zipApiFile or directoryExtraction
	* @throws IllegalStateException if directoryExtraction failed to be created or is not an actual directory but is some other type of file
	* @throws IOException if an I/O problem occurs
	*/
	public static void extract(ZipFile zipApiFile, File directoryExtraction, boolean overwrite) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
		Check.arg().notNull(zipApiFile);
		if (!zipApiFile.getName().toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("zipApiFile = " + zipApiFile.getName() + " has an invalid extension for a zip file");
		DirUtil.ensureExists(directoryExtraction);
		
		for (ZipEntry entry : getEntries(zipApiFile, false)) {
			File path = new File( directoryExtraction, entry.getName() );
			if (path.exists() && path.isFile() && !overwrite) throw new IllegalStateException(path.getPath() + " is an existing normal file, but overwrite == false");
			
			if (entry.isDirectory()) {
				DirUtil.ensureExists(path);
			}
			else {
				DirUtil.ensureExists(path.getParentFile());
				writeOutFile(zipApiFile, entry, path);
			}
		}
	}
	
	/**
	* Writes all the bytes from zipApiFile's current entry to path.
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	private static void writeOutFile(ZipFile zipApiFile, ZipEntry entry, File path) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = zipApiFile.getInputStream(entry);	// no buffering, because StreamUtil.transfer below will do that itself
			out = new FileOutputStream(path, false);	// no buffering, because StreamUtil.transfer below will do that itself
			StreamUtil.transfer(in, out);
		}
		finally {
			StreamUtil.close(in);
			StreamUtil.close(out);
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ZipUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final File root = DirUtil.findRootWithSpaceFreeMax();	// using the filesystem root with the most free space will be the best bet for getting the tests below to pass
		
		private static final AtomicLong id = new AtomicLong();
		
		// -------------------- utility methods (default access, because are also used by TarUtil.UnitTest) --------------------
		
		/**
		* Result is always a temporary file of the specified length (its data consists of the Latin alphabet continuously repeated).
		* Its name includes an ID number as well as its length in bytes, which makes it unique (for this JVM session).
		*/
		static File makeDataFile(File parent, long length) throws IllegalArgumentException, IOException {
			Check.arg().notNegative(length);
			
			OutputStream out = null;
			try {
				DirUtil.ensureExists(parent);	// this is NOT done by FileUtil.createTemp below
				File file = FileUtil.createTemp( new File(parent, "dataFile#" + id.incrementAndGet() + "_length" + length + ".txt") );
				
				int bufferSize = (int) Math.min( length, 1 * 1024 * 1024 );
				byte[] bytes = new byte[bufferSize];
					// repeat the ascii values a thru z--do this if want to look at the contents by eye and verify:
				for (int i = 0; i < bytes.length; i++) {
					bytes[i] = (byte) (97 + (i % 26));
				}
				
				out = new FileOutputStream(file, false);
				for (long lengthWritten = 0; lengthWritten < length; ) {
					int lengthToWrite = (int) Math.min( length - lengthWritten, bytes.length );
					out.write(bytes, 0, lengthToWrite);
					lengthWritten += lengthToWrite;
				}
				assert (file.length() == length) : "file.length() = " + file.length() + " != length = " + length;
				
				return file;
			}
			finally {
				StreamUtil.close(out);
			}
		}
		
		/**
		* Result is always a temporary file that initially contains no data.
		* Its name includes an ID number, which makes it unique (for this JVM session).
		*/
		static File makeArchiveFile(String type) {
			File file = new File(root, type + "File#" + id.incrementAndGet() + "." + type);
			return FileUtil.createTemp(file);
		}
		
		/**
		* Result is always a directory located inside {@link #root}, with the specified name,
		* that does not currently exist (hence, contains no data).
		* Its name starts with namePrefix, and is then followed by "Dir#"
		* and then an ID number, which makes it unique (for this JVM session).
		*/
		static File makeDirectory(String namePrefix) {
			File directory = new File(root, namePrefix + "_dir#" + id.incrementAndGet());
			if (directory.exists()) throw new IllegalStateException(directory.getPath() + " already exists");
			return directory;
		}
		
		// -------------------- JUnit methods --------------------
		
		@Test public void test_archive_extract() throws Exception {
			archive_extract( new File("../class"), "zip" );
//			archive_extract( new File("../script"), "zip" );
//			archive_extract( new File("../src"), "zip" );
// +++ cannot reliably use the above lines with the current version of jzip, 1.3, because it bombs if a file is in use by another program; see http://forum.jzip.com/showthread.php?t=929
		}
		
		private void archive_extract(File dirToArchive, String type) throws Exception {
			Check.arg().validDirectory(dirToArchive);
			Check.arg().notBlank(type);
			
			File zipFile = null;
			File directoryExtraction = null;
			try {
				zipFile = makeArchiveFile(type);
				directoryExtraction = makeDirectory(type + "_extraction");
				
					// phase 1: archive with this class:
				FileUtil.deleteIfExists(zipFile);
				archive( zipFile, null, dirToArchive );
				printEntries(zipFile);
						// confirm extraction worked using this class:
				DirUtil.gutIfExists(directoryExtraction);
				extract(zipFile, directoryExtraction, false);
				confirmExtraction(dirToArchive, directoryExtraction);
/*
code below commented out because jzip has bugs; sometimes the program hangs...
				
						// confirm extraction worked using a 3rd party program:
				DirUtil.gutIfExists(directoryExtraction);
				extractWithDifferentProgram(zipFile, directoryExtraction);
				confirmExtraction(dirToArchive, directoryExtraction);
					// phase 2: archive using a 3rd party program:
				FileUtil.deleteIfExists(zipFile);
				archiveWithDifferentProgram( dirToArchive, zipFile );
				printEntries(zipFile);
						// confirm extraction worked using this class:
				DirUtil.gutIfExists(directoryExtraction);
				extract(zipFile, directoryExtraction, false);
				confirmExtraction(dirToArchive, directoryExtraction);
						// confirm extraction worked using a 3rd party program:
				DirUtil.gutIfExists(directoryExtraction);
				extractWithDifferentProgram(zipFile, directoryExtraction);
				confirmExtraction(dirToArchive, directoryExtraction);
*/
			}
			finally {
				FileUtil.deleteIfExists(zipFile);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				DirUtil.deleteIfExists(directoryExtraction);
			}
		}
		
		private void confirmExtraction(File dirToArchive, File directoryExtraction) throws Exception {
			File directoryToUse = directoryExtraction.listFiles()[0];	// need to drill down one level in order to get the proper directory in which to compare with dirToArchive
			Assert.assertTrue( DirUtil.areContentsSame(dirToArchive, directoryToUse) );	// DirUtil.areContentsSame ONLY returns true if 2 directories have exactly equally contents in all their elements (both normal files and subdirectories)
		}
		
		/*
			WARNING: THE NEXT 4 METHODS BELOW ONLY WORK ON A WINDOWS MACHINE WHICH HAS THE jzip PROGRAM INSTALLED.
			For more discussions on command line usage of jzip, see
				http://www.jzip.com/command_line.php
				http://forum.jzip.com/showthread.php?t=334&highlight=command+line+extract
		*/
		
		private void extractWithDifferentProgram(File zipFile, File directoryExtraction) throws Exception {
			DirUtil.ensureExists(directoryExtraction);	// as of 2009-02-04, jzip 1.3 is defective in that it will not create an extraction directory
try {
			OsUtil.execSynch( unZipCommand(zipFile), null, directoryExtraction );
}
catch (Exception e) {
	e.printStackTrace();
}
// +++ it looks like jzip returns a non-zero exit code even tho the extraction is working fine: http://forum.jzip.com/showthread.php?p=1800#post1800
		}
		
		private String unZipCommand(File zipFile) throws Exception {
			return "jzip -ed " + zipFile.getCanonicalPath();
		}
		
		private void archiveWithDifferentProgram(File dirToArchive, File zipFile) throws Exception {
try {
			OsUtil.execSynch( zipCommand(dirToArchive, zipFile) );
}
catch (Exception e) {
	e.printStackTrace();
}
// +++ it looks like jzip returns a non-zero exit code even tho the extraction is working fine: http://forum.jzip.com/showthread.php?p=1800#post1800
		}
		
		private String zipCommand(File dirToArchive, File zipFile) throws Exception {
			return "jzip -a -r -P " + zipFile.getCanonicalPath() + " " + dirToArchive.getCanonicalPath();
		}
		
		@Ignore("Not running because takes too long (when last sucessfully ran this method on 2009-02-09, it took ~26 min to run)")
		@Test public void test_archive_extract_fileSizeLimit_shouldPass() throws Exception {
			File dirToArchive = null;
			File zipFile = null;
			File directoryExtraction = null;
			try {
				dirToArchive = makeDirectory("test_archive_extract_fileSizeLimit_shouldPass");
				File fileMax = makeDataFile(dirToArchive, zipableFileSizeLimit);
				
				zipFile = makeArchiveFile("zip");
				archive( zipFile, null, dirToArchive );
				
				directoryExtraction = makeDirectory("zip" + "_extraction");
				extract( zipFile, directoryExtraction, false );
				confirmExtraction(dirToArchive, directoryExtraction);
			}
			finally {
				DirUtil.deleteIfExists(dirToArchive);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				FileUtil.deleteIfExists(zipFile);
				DirUtil.deleteIfExists(directoryExtraction);
			}
		}
		
		@Ignore("Not running because takes too long (when last sucessfully ran this method on 2009-02-09, it took ~116 s to run)")
		@Test(expected=Exception.class) public void test_archive_extract_fileSizeLimit_shouldFail() throws Exception {
			File dirToArchive = null;
			File zipFile = null;
			try {
				dirToArchive = makeDirectory("test_archive_extract_fileSizeLimit_shouldFail");
				File fileTooLarge = makeDataFile(dirToArchive, zipableFileSizeLimit + 1L);
				
				zipFile = makeArchiveFile("zip");
				archive( zipFile, null, fileTooLarge );
			}
			finally {
				DirUtil.deleteIfExists(dirToArchive);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				FileUtil.deleteIfExists(zipFile);
			}
		}
		
		private static void printEntries(File zipFile) throws Exception {
			System.out.println("Below are the entries of the ZIP file " + zipFile.getPath() + ":");
			for (ZipEntry entry : getEntries(zipFile, true)) {
				System.out.println( '\t' + entry.toString() );
			}
		}
		
	}
	
}
