/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--code below currently uses apache's commons-compress project for TAR support
	--homepage:
		http://commons.apache.org/compress/
	--issues with this project:
		http://commons.apache.org/compress/issue-tracking.html
		http://issues.apache.org/jira/browse/COMPRESS
	--some developers:
		tcurdt@apache.org
		bodewig@apache.org

--if ever need another TAR library, look at this code:
		https://truezip.dev.java.net/

+++ idea: have a ArchiveUtil master class, and have it support zip and tar, since so much code between TarUtil and ZipUtil is shared?
	--would need inner classes like Tarhandler and ZipHandler for the specific functionality
*/

package bb.io;

import bb.util.Check;
import bb.util.Execute;
import bb.util.OsUtil;
import bb.util.Properties2;
import bb.util.StringUtil;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
* Provides static utility methods for dealing with TAR Files.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
* @see <a href="http://en.wikipedia.org/wiki/Tar_%28file_format%29">Wikipedia TAR file format</a>
* @see <a href="http://people.freebsd.org/~kientzle/libarchive/tar.5.txt">BSD TAR file format</a>
* @see <a href="http://www.mkssoftware.com/docs/man4/tar.4.asp">TAR file format</a>
* @see <a href="http://forum.java.sun.com/thread.jspa?threadID=757876">Forum posting on TAR and Java</a>
* @see <a href="http://jakarta.apache.org/commons/sandbox/compress/">jakarta compression library</a>
*/
public final class TarUtil {
	
	// -------------------- switch constants --------------------
	
	private static final String tarFile_key = "-tarFile";
	private static final String pathsToArchive_key = "-pathsToArchive";
	private static final String appendBackup_key = "-appendBackup";
	private static final String appendTimeStamp_key = "-appendTimeStamp";
	private static final String appendExtension_key = "-appendExtension";
	private static final String appendAll_key = "-appendAll";
	private static final String filter_key = "-filter";
	private static final String directoryExtraction_key = "-directoryExtraction";
	private static final String overwrite_key = "-overwrite";
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main} for an archive. */
	private static final List<String> keysLegal_archive = Arrays.asList(
		tarFile_key,
		pathsToArchive_key,
		appendBackup_key, appendTimeStamp_key, appendExtension_key, appendAll_key,
		filter_key
	);
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main} for an extract. */
	private static final List<String> keysLegal_extract = Arrays.asList(
		tarFile_key,
		directoryExtraction_key,
		overwrite_key
	);
	
	// -------------------- misc constants --------------------
	
	/** Maximum number of chars that can appear in a path. */
	//private static final long tarablePathLengthLimit = 100;
	/*
	The above restriction applies to ustar/POSIX.1-1988 compatible tar files:
		The maximum length of a file name is limited to 256 characters, provided that the file name can be split at a directory separator in two parts, first of them being at most 155 bytes long. So, in most cases the maximum file name length will be shorter than 256 characters.
		The maximum length of a symbolic link name is limited to 100 characters.
		http://www.gnu.org/software/tar/manual/html_section/Formats.html
	
	Furthermore, the org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_ERROR javadocs state that it bombs on names > 100 chars
	
	Commented out because this class is configured to always use
		org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU
	mode, which is presumably the POSIX.1-2001 specification:
		This is the most flexible and feature-rich format. It does not impose any restrictions on file sizes or file name lengths.
		http://www.gnu.org/software/tar/manual/html_section/Formats.html
		
	See also
		Torture-testing Backup and Archive Programs
		http://www.coredumps.de/doc/dump/zwicky/testdump.doc.html
		
		[extensive discussion of tar formats]
		http://leaf.dragonflybsd.org/cgi/web-man?command=tar&section=5
	*/
	
	/**
	* Maximum size of a file that can be put into a TAR archive file by this class.
	* The value here is for the classic TAR format, namely 8 GB = (2^33) - 1 = 8,589,934,591 bytes:
	* <blockquote>
	*	For historical reasons numerical values are encoded in octal with leading zeroes.
	*	The final character is either a null or a space.
	*	Thus although there are 12 bytes reserved for storing the file size, only 11 octal digits can be stored.
	*	This gives a maximum file size of 8 gigabytes on archived files.
	*	To overcome this limitation some versions of tar, including the GNU implementation,
	*	support an extension in which the file size is encoded in binary.<br>
	*	<a href="http://en.wikipedia.org/wiki/Tar_%28file_format%29">Tar (file format)</a>
	* </blockquote>
	*/
	private static final long tarableFileSizeLimit = (8L * 1024L * 1024L * 1024L) - 1L;
/*
+++ I PROBABLY can remove this IF are using a TRUE IMPLEMENTATION of GNU tar:

	This gives a maximum file size of 8 gigabytes on archived files. To overcome this limitation, some versions of tar, including the GNU implementation, support an extension in which the high-order bit of the leftmost byte of a numeric field indicates that the number is encoded in big-endian binary.
	http://en.wikipedia.org/wiki/Tar_(file_format)#File_header
		
	GNU tar as no file size limit.
	http://www.codecomments.com/archive309-2005-11-691598.html

Unfortunately, commons-compress TarArchiveInputStream seems to crash when asked to read from a 10 GB TAR file:
	java.io.IOException: unexpected EOF with 24064 bytes unread
		at org.apache.commons.compress.archivers.tar.TarArchiveInputStream.read(TarArchiveInputStream.java:339)
		at org.apache.commons.compress.archivers.tar.TarArchiveInputStream.copyEntryContents(TarArchiveInputStream.java:379)
Oddly enough, TarArchiveInputStream did not seem to complain when used to write the 10 GB TAR file in the first place...
*/
	
	private static final boolean giveUserFeedback = true;
	
	// -------------------- main --------------------
	
	/**
	* May be used either to archive to or extract from a TAR file.
	* The action to perform and all of its specifications are embedded as command line switches in args.
	* <p>
	* If archiving,
	* the source path(s) to be archived (which can be either normal files or directories)
	* are specified as the (key/value) command line switch <code>-pathsToArchive <i>commaSeparatedListOfPaths</i></code>.
	* The target TAR archive file is the (key/value) command line switch <code>-tarFile <i>insertPathHere</i></code>.
	* The following optional switches may also be supplied:
	* <ol>
	*  <li><code>-appendBackup</code> appends <code>_backup</code> to the TAR file's name</li>
	*  <li><code>-appendTimeStamp</code> appends <code>_</code> followed by a timestamp to the TAR file's name</li>
	*  <li><code>-appendExtension</code> appends <code>.tar</code> to the TAR file's name</li>
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
		java  bb.io.TarUtil  -tarFile ../log/test.tar  -pathsToArchive ../class1,../class2  -filter bb.io.filefilter.ClassFilter
	* </code></pre>
	* <p>
	* If extracting,
	* the target directory to extract into is always specified as the command line switch <code>-directoryExtraction <i>insertPathHere</i></code>.
	* The source TAR archive file is the same <code>-tarFile</code> command line switch mentioned before.
	* An optional switch <code>-overwrite <i>true/false</i></code> may also be supplied
	* to control if overwriting of existing normal files is allowed or not.
	* By default overwriting is not allowed (an Exception will be thrown if extraction needs to overwrite an existing file).
	* For example, here is a complete command line that extracts a TAR file to a specific directory, overwriting any existing files:
	* <pre><code>
		java  bb.io.TarUtil  -tarFile ../log/test.tar  -directoryExtraction ../log/tarExtractOutput  -overwrite true
	* </code></pre>
	* <p>
	* Optional GZIP compression/decompression may also be done when archiving/extracting a TAR file.
	* Normally, the value for the <code>-tarFile</code> switch must be a path
	* which ends in a ".tar" (case insensitive) extension.
	* However, this program will also accept either ".tar.gz" or ".tgz" extensions,
	* in which case it will automatically perform GZIP compression/decompression on the TAR file.
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
			if (switches.containsKey(pathsToArchive_key)) {
				switches.checkKeys(keysLegal_archive);
				
				File tarFile = ZipUtil.getArchiveFile(switches, tarFile_key, "tar");
				File[] pathsToArchive = ZipUtil.getPathsToArchive(switches);
				FileFilter filter = ZipUtil.getFileFilter(switches);
				
				archive( tarFile, filter, pathsToArchive );
			}
			else if (switches.containsKey(directoryExtraction_key)) {
				switches.checkKeys(keysLegal_extract);
				
				File tarFile = ZipUtil.getArchiveFile(switches, tarFile_key, "tar");
				File directoryExtraction = switches.getFile(directoryExtraction_key).getCanonicalFile();
				boolean overwrite = switches.getBoolean(overwrite_key, false);
				
				extract( tarFile, directoryExtraction, overwrite );
			}
			else {
				throw new IllegalArgumentException("args failed to specify a legitimate operation to perform");
			}
			
			return null;
		} } );
	}
	
	// -------------------- isTarable --------------------
	
	/**
	* If path is a directory, then returns true.
	* Else if path is a normal file, then returns true if path's length (in bytes) is <= {@link #tarableFileSizeLimit}, false otherwise.
	* Else returns false.
	* <p>
	* @throws IllegalArgumentException if path == null or path does not exist
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to path
	*/
	public static boolean isTarable(File path) throws IllegalArgumentException, SecurityException {
		Check.arg().notNull(path);
		if (!path.exists()) throw new IllegalArgumentException("path = " + path.getPath() + " does not exist");
		
		if (path.isDirectory()) {
			return true;
		}
		else if (path.isFile()) {
			return (path.length() <= tarableFileSizeLimit);
		}
		else {
			return false;
		}
	}
	
	// -------------------- archive and helper methods --------------------
	
	/**
	* Writes each element of pathsToArchive to a new TAR format archive file specified by tarFile.
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
	* The TAR archive witten by this method will use GNU TAR rules for the entry headers if long path names are encountered.
	* <i>This means that standard POSIX compliant programs that do not support the GNU TAR extension
	* will be unable to extract the contents.</i>
	* <p>
	* Optional GZIP compression may also be done.
	* Normally, tarFile must be a path which ends in a ".tar" (case insensitive) extension.
	* However, this method will also accept either ".tar.gz" or ".tgz" extensions,
	* in which case it will perform GZIP compression on tarFile as part of archiving.
	* <p>
	* @param tarFile the TAR File that will write the archive data to
	* @param filter a FileFilter that can use to screen out paths from being written to the archive;
	* may be null, which means everything inside pathsToArchive gets archived;
	* if not null, see warnings in {@link DirUtil#getTree DirUtil.getTree} on directory acceptance
	* @param pathsToArchive array of all the paths to archive
	* @throws Exception if any Throwable is caught; the Throwable is stored as the cause, and the message stores the path of tarFile;
	* here are some of the possible causes:
	* <ol>
	*  <li>
	*		IllegalArgumentException if pathsToArchive == null; pathsToArchive.length == 0;
	*		tarFile == null;
	*		tarFile already exists and either is not a normal file or is but already has data inside it;
	*		tarFile has an invalid extension;
	*		any element of pathsToArchive is null, does not exist, cannot be read, is equal to tarFile, its path contains tarFile,
	*		or it fails {@link #isTarable isTarable}
	*  </li>
	*  <li>SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to some path</li>
	*  <li>IOException if an I/O problem occurs</li>
	* </ol>
	*/
	public static void archive(File tarFile, FileFilter filter, File... pathsToArchive) throws Exception {
		try {
			Check.arg().notNull(tarFile);
			if (tarFile.exists()) {
				if (!tarFile.isFile()) throw new IllegalArgumentException("tarFile = " + tarFile.getPath() + " exists but is not a normal file");
				if (tarFile.length() != 0) throw new IllegalArgumentException("tarFile = " + tarFile.getPath() + " already exists and already has data inside it; this method will not overwrite it");
			}
			Check.arg().notEmpty(pathsToArchive);
			for (int i = 0; i < pathsToArchive.length; i++) {
				Check.arg().notNull(pathsToArchive[i]);
				if ( !pathsToArchive[i].exists() ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " is a non-existent path");
				if ( !pathsToArchive[i].canRead() ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " cannot be read by this application");
				if ( pathsToArchive[i].equals(tarFile) ) throw new IllegalArgumentException("pathsToArchive[" + i + "] = " + pathsToArchive[i].getPath() + " is the same path as tarFile = " + tarFile.getPath());
				if ( pathsToArchive[i].isDirectory() && DirUtil.contains(pathsToArchive[i], tarFile) ) throw new IllegalArgumentException("the directory corresponding to pathsToArchive[" + i + "] = " + pathsToArchive[i].getCanonicalPath() + " will contain the path of tarFile = " + tarFile.getCanonicalPath() );
			}
			
			TarArchiveOutputStream taos = null;
			try {
				taos = new TarArchiveOutputStream( getOutputStream(tarFile) );
				taos.setLongFileMode( TarArchiveOutputStream.LONGFILE_GNU );
				for (File path : pathsToArchive) {
					archive( path, new FileParent(path), taos, filter );
				}
			}
			finally {
				if (giveUserFeedback) ConsoleUtil.eraseLine();
				StreamUtil.close(taos);
			}
		}
		catch (Throwable t) {
			throw new Exception("See cause for the underlying Throwable; happened for tarFile = " + (tarFile != null ? tarFile.getPath() : "<null>"), t);
		}
	}
	
	/**
	* If tarFile's extension is simply tar, then returns a new FileOutputStream.
	* Else if tarFile's extension is tar.gz or tgz, then returns a new GZIPOutputStream wrapping a new FileOutputStream.
	* <p>
	* Note: the result is never buffered, since the TarArchiveOutputStream which will use the result always has an internal buffer.
	* <p>
	* @throws IllegalArgumentException if tarFile has an unrecognized extension
	* @throws IOException if an I/O problem occurs
	* @see <a href="http://www.gzip.org/">gzip home page</a>
	* @see <a href="http://www.brouhaha.com/~eric/tgz.html">.tar.gz file format FAQ</a>
	*/
	private static OutputStream getOutputStream(File tarFile) throws IllegalArgumentException, IOException {
		String name = tarFile.getName().toLowerCase();
		if (name.endsWith(".tar")) {
			return new FileOutputStream(tarFile);
		}
		else if (name.endsWith(".tar.gz")  || name.endsWith(".tgz")) {
			return new GZIPOutputStream( new FileOutputStream(tarFile) );
		}
		else {
			throw new IllegalArgumentException("tarFile = " + tarFile.getPath() + " has an invalid extension for a tar or tar/gzip file");
		}
	}
	
	/**
	* Writes path as a new TarArchiveEntry to tarArchiveOutputStream.
	* If path is a normal file, then next writes path's data to tarArchiveOutputStream.
	* <p>
	* If path is a directory, then this method additionally calls itself on the contents
	* (thus recursing thru the entire directory tree).
	* <p>
	* <b>Warning</b>: several popular programs (e.g. winzip) fail to display mere directory entries.
	* Furthermore, if just a directory entry is present (i.e. it is empty), they also may fail
	* to create a new empty directoy when extracting the TAR file's contents.
	* These are bugs in their behavior.
	* <p>
	* An optional FileFilter can be supplied to screen out paths that would otherwise be archived.
	* <p>
	* <i>This method does not close tarArchiveOutputStream:</i> that is the responsibility of the caller.
	* <p>
	* The caller also must take on the responsibility to not do anything stupid, like write
	* path more than once, or have the path be the same File that tarArchiveOutputStream is writing to.
	* <p>
	* @param path the File to archive
	* @param fileParent the FileParent for path
	* @param tarArchiveOutputStream the TarArchiveOutputStream that will write the archive data to
	* @param filter a FileFilter that can use to screen out certain files from being written to the archive; may be null (so everything specified by path gets archived)
	* @throws Exception if any Throwable is caught; the Throwable is stored as the cause, and the message stores path's information;
	* here are some of the possible causes:
	* <ol>
	*  <li>IllegalArgumentException if path fails {@link #isTarable isTarable}</li>
	*  <li>SecurityException if a security manager exists and its SecurityManager.checkRead method denies read access to path</li>
	*  <li>IOException if an I/O problem occurs</li>
	* </ol>
	*/
	private static void archive(File path, FileParent fileParent, TarArchiveOutputStream tarArchiveOutputStream, FileFilter filter) throws Exception {
		try {
			if ((filter != null) && !filter.accept(path)) return;
			if (!isTarable(path)) throw new IllegalArgumentException("path = " + path.getPath() + " failed isTarable");
			if (giveUserFeedback) ConsoleUtil.overwriteLine("TarUtil.archive: " + path.getPath());
			
			TarArchiveEntry entry = new TarArchiveEntry(path);
			entry.setName( fileParent.getRelativePath(path, '/') );	// Note: getRelativePath will ensure that '/' is the path separator and that directories end with '/'
			tarArchiveOutputStream.putNextEntry( entry );
			if (path.isFile()) readInFile(path, tarArchiveOutputStream);
			tarArchiveOutputStream.closeEntry();
			
			if (path.isDirectory()) {
				for (File fileChild : DirUtil.getContents(path, null)) {	// supply null, since we test at beginning of this method (supplying filter here will just add a redundant test)
					archive( fileChild, fileParent, tarArchiveOutputStream, filter );
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
	* Returns all the {@link TarArchiveEntry}s inside tarFile.
	* <p>
	* @param tarFile the TAR format file to be read
	* @param sortResult if true, then the result is first sorted by each entry's name before return;
	* otherwise the order is the sequence read from tarFile
	* @throws IllegalArgumentException if tarFile fails {@link Check#validFile Check.validFile}
	* @throws IOException if an I/O problem occurs
	*/
	public static TarArchiveEntry[] getEntries(File tarFile, boolean sortResult) throws IllegalArgumentException, IOException {
		Check.arg().validFile(tarFile);
		
		TarArchiveInputStream tais = new TarArchiveInputStream( getInputStream(tarFile) );	// closed by getEntries below
		return getEntries(tais, sortResult);
	}
	
	/**
	* Returns all the {@link TarArchiveEntry}s that can next be read by tarArchiveInputStream.
	* <p>
	* Nothing should have been previously read from tarArchiveInputStream if the full result is desired.
	* Nothing more can be read from tarArchiveInputStream when this method returns,
	* since the final action will be to close tarArchiveInputStream.
	* <p>
	* @param tarArchiveInputStream the TarArchiveInputStream to get the entries from
	* @param sortResult if true, then the result is first sorted by each entry's name before return;
	* otherwise the order is the sequence read from tarArchiveInputStream
	* @throws IllegalArgumentException if tarArchiveInputStream == null
	* @throws IOException if an I/O problem occurs
	*/
	public static TarArchiveEntry[] getEntries(TarArchiveInputStream tarArchiveInputStream, boolean sortResult) throws IllegalArgumentException, IOException {
		Check.arg().notNull(tarArchiveInputStream);
		
		try {
			List<TarArchiveEntry> entries = new LinkedList<TarArchiveEntry>();
			while (true) {
				TarArchiveEntry entry = (TarArchiveEntry) tarArchiveInputStream.getNextEntry();
				if (entry == null) break;
				else entries.add(entry);
			}
			
			if (sortResult) {
				Collections.sort(entries, new Comparator<TarArchiveEntry>() {
					public int compare(TarArchiveEntry entry1, TarArchiveEntry entry2) throws IllegalArgumentException {
						return entry1.getName().compareTo( entry2.getName() );
					}
				} );
			}
			
			return entries.toArray( new TarArchiveEntry[entries.size()] );
		}
		finally {
			StreamUtil.close(tarArchiveInputStream);
		}
	}
	
	// -------------------- extract --------------------
	
	/**
	* Extracts the contents of tarFile to directoryExtraction.
	* <p>
	* It is an error if tarFile does not exist, is not a normal file, or is not in the proper TAR format.
	* In contrast, directoryExtraction need not exist, since it (and any parent directories) will be created if necessary.
	* <p>
	* Optional GZIP decompression may also be done.
	* Normally, tarFile must be a path which ends in a ".tar" (case insensitive) extension.
	* However, this method will also accept either ".tar.gz" or ".tgz" extensions,
	* in which case it will perform GZIP decompression on tarFile as part of extracting.
	* <p>
	* @param tarFile the TAR archive file
	* @param directoryExtraction the directory that will extract the contents of tarFile into
	* @param overwrite specifies whether or not extraction is allowed to overwrite an existing normal file inside directoryExtraction
	* @throws IllegalArgumentException if tarFile is {@link Check#validFile not valid};
	* if directoryExtraction fails {@link DirUtil#ensureExists DirUtil.ensureExists};
	* tarFile has an invalid extension
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead method
	* denies read access to tarFile or directoryExtraction
	* @throws IllegalStateException if directoryExtraction failed to be created or is not an actual directory but is some other type of file
	* @throws IOException if an I/O problem occurs
	*/
	public static void extract(File tarFile, File directoryExtraction, boolean overwrite) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
		Check.arg().validFile(tarFile);
		DirUtil.ensureExists(directoryExtraction);
		
		TarArchiveInputStream tais = null;
		try {
			tais = new TarArchiveInputStream( getInputStream(tarFile) );
			
			for (TarArchiveEntry entry = (TarArchiveEntry) tais.getNextEntry(); entry != null; entry = (TarArchiveEntry) tais.getNextEntry()) {
				File path = new File( directoryExtraction, entry.getName() );
				if (path.exists() && path.isFile() && !overwrite) throw new IllegalStateException(path.getPath() + " is an existing normal file, but overwrite == false");
				
				if (entry.isDirectory()) {
					DirUtil.ensureExists(path);
				}
				else {
					DirUtil.ensureExists( path.getParentFile() );	// CRITICAL: the TAR format does not necessarily store all the directories as entries, so must make sure that they are created 	http://forum.java.sun.com/thread.jspa?threadID=573800&messageID=3115774
					writeOutFile(tais, path);
				}
			}
		}
		finally {
			StreamUtil.close(tais);
		}
	}
	
	/**
	* If tarFile's extension is simply tar, then returns a new FileInputStream.
	* Else if tarFile's extension is tar.gz or tgz, then returns a new GZIPnputStream wrapping a new FileInputStream.
	* <p>
	* Note: the result is never buffered, since the TarArchiveInputStream which will use the result always has an internal buffer.
	* <p>
	* @throws IllegalArgumentException if tarFile has an unrecognized extension
	* @throws IOException if an I/O problem occurs
	* @see <a href="http://www.gzip.org/">gzip home page</a>
	* @see <a href="http://www.brouhaha.com/~eric/tgz.html">.tar.gz file format FAQ</a>
	*/
	private static InputStream getInputStream(File tarFile) throws IllegalArgumentException, IOException {
		String name = tarFile.getName().toLowerCase();
		if (name.endsWith(".tar")) {
			return new FileInputStream(tarFile);
		}
		else if (name.endsWith(".tar.gz")  || name.endsWith(".tgz")) {
			return new GZIPInputStream( new FileInputStream(tarFile) );
		}
		else {
			throw new IllegalArgumentException("tarFile = " + tarFile.getPath() + " has an invalid extension for a tar or tar/gzip file");
		}
	}
	
	/**
	* Writes all the bytes from tarArchiveInputStream's current TarArchiveEntry to path.
	* <p>
	* @throws IOException if an I/O problem occurs
	*/
	private static void writeOutFile(TarArchiveInputStream tarArchiveInputStream, File path) throws IOException {
		OutputStream out = null;
		try {
			out = new FileOutputStream(path);
			tarArchiveInputStream.copyEntryContents(out);
		}
		finally {
			StreamUtil.close(out);
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private TarUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_archive_extract() throws Exception {
			archive_extract( new File("../class"), "tar" );
			archive_extract( new File("../script"), "tar.gz" );
			archive_extract( new File("../src"), "tgz" );
		}
		
		private void archive_extract(File dirToArchive, String type) throws Exception {
			Check.arg().validDirectory(dirToArchive);
			Check.arg().notBlank(type);
			
			File tarFile = null;
			File directoryExtraction = null;
			try {
				tarFile = ZipUtil.UnitTest.makeArchiveFile(type);
				directoryExtraction = ZipUtil.UnitTest.makeDirectory(type + "_extraction");
				
					// phase 1: archive with this class:
				FileUtil.deleteIfExists(tarFile);
				archive( tarFile, null, dirToArchive );
				printEntries(tarFile);
						// confirm extraction worked using this class:
				DirUtil.gutIfExists(directoryExtraction);
				extract(tarFile, directoryExtraction, false);
				confirmExtraction(dirToArchive, directoryExtraction);
/*
code below commented out because jzip has bugs; see further comments below...

						// confirm extraction worked using a 3rd party program:
				DirUtil.gutIfExists(directoryExtraction);
				extractWithDifferentProgram(tarFile, directoryExtraction);
				confirmExtraction(dirToArchive, directoryExtraction);
				
					// phase 2: archive using a 3rd party program:
				FileUtil.deleteIfExists(tarFile);
				archiveWithDifferentProgram( dirToArchive, tarFile );
				printEntries(tarFile);
						// confirm extraction worked using this class:
				DirUtil.gutIfExists(directoryExtraction);
				extract(tarFile, directoryExtraction, false);
				confirmExtraction(dirToArchive, directoryExtraction);
						// confirm extraction worked using a 3rd party program:
				DirUtil.gutIfExists(directoryExtraction);
				extractWithDifferentProgram(tarFile, directoryExtraction);
				confirmExtraction(dirToArchive, directoryExtraction);
*/
			}
			finally {
				FileUtil.deleteIfExists(tarFile);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
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
		
		private void extractWithDifferentProgram(File tarFile, File directoryExtraction) throws Exception {
			DirUtil.ensureExists(directoryExtraction);	// as of 2009-02-04, jzip 1.3 is defective in that it will not create an extraction directory
try {
			OsUtil.execSynch( unTarCommand(tarFile), null, directoryExtraction );
}
catch (Exception e) {
	e.printStackTrace();
}
// +++ it looks like jzip returns a non-zero exit code even tho the extraction is working fine: http://forum.jzip.com/showthread.php?p=1800#post1800

			File fileExtracted = directoryExtraction.listFiles()[0];
			if (fileExtracted.isFile()) {	// with gzipped files, jzip only does the first level of extraction, so need to do another level to get the real contents
try {
				OsUtil.execSynch( unTarCommand(fileExtracted), null, directoryExtraction );
}
catch (Exception e) {
	e.printStackTrace();
}
// +++ it looks like jzip returns a non-zero exit code even tho the extraction is working fine: http://forum.jzip.com/showthread.php?p=1800#post1800
				FileUtil.delete(fileExtracted);
			}
		}
		
		private String unTarCommand(File tarFile) throws Exception {
			return "jzip -ed " + tarFile.getCanonicalPath();
		}
		
		private void archiveWithDifferentProgram(File dirToArchive, File tarFile) throws Exception {
			OsUtil.execSynch( tarCommand(dirToArchive, tarFile) );
// +++ uhh, the above may not work for tar.gz and .tgz?  since cannot even get jzip to create tar files at the moment, this is all a moot point...
		}
		
		private String tarCommand(File dirToArchive, File tarFile) throws Exception {
			return "jzip -a " + tarFile.getCanonicalPath() + " " + dirToArchive.getCanonicalPath();
		}
// +++ above fails; not sure why, may be a jzip bug: http://forum.jzip.com/showthread.php?t=965
		
		@Ignore("Not running because takes too long (when last sucessfully ran this method on 2009-02-04, it took ~1 hour to run)")
		@Test public void test_archive_extract_fileSizeLimit_shouldPass() throws Exception {
			File dirToArchive = null;
			File tarFile = null;
			File directoryExtraction = null;
			try {
				dirToArchive = ZipUtil.UnitTest.makeDirectory("test_archive_extract_fileSizeLimit_shouldPass");
				File fileMax = ZipUtil.UnitTest.makeDataFile(dirToArchive, tarableFileSizeLimit);
				
				tarFile = ZipUtil.UnitTest.makeArchiveFile("tar");
				archive( tarFile, null, dirToArchive );
				
				directoryExtraction = ZipUtil.UnitTest.makeDirectory("tar" + "_extraction");
				extract( tarFile, directoryExtraction, false );
				confirmExtraction(dirToArchive, directoryExtraction);
			}
			finally {
				DirUtil.deleteIfExists(dirToArchive);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				FileUtil.deleteIfExists(tarFile);
				DirUtil.deleteIfExists(directoryExtraction);
			}
		}
		
		@Ignore("Not running because takes too long (when last sucessfully ran this method on 2009-02-04, it took ~235 s to run)")
		@Test(expected=Exception.class) public void test_archive_extract_fileSizeLimit_shouldFail() throws Exception {
			File dirToArchive = null;
			File tarFile = null;
			try {
				dirToArchive = ZipUtil.UnitTest.makeDirectory("test_archive_extract_fileSizeLimit_shouldFail");
				File fileTooLarge = ZipUtil.UnitTest.makeDataFile(dirToArchive, tarableFileSizeLimit + 1L);
				
				tarFile = ZipUtil.UnitTest.makeArchiveFile("tar");
				archive( tarFile, null, fileTooLarge );
			}
			finally {
				DirUtil.deleteIfExists(dirToArchive);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				FileUtil.deleteIfExists(tarFile);
			}
		}
		
		@Test public void test_archive_extract_pathLengthLimit() throws Exception {
			File dirToArchive = null;
			File tarFile = null;
			File directoryExtraction = null;
			try {
				dirToArchive = ZipUtil.UnitTest.makeDirectory("test_archive_extract_pathLengthLimit");
				File subdirLongName = new File( StringUtil.toLength(dirToArchive.getPath() + "/X", 200, false, 'X') );
				File fileData = ZipUtil.UnitTest.makeDataFile(subdirLongName, 2048);
				
				tarFile = ZipUtil.UnitTest.makeArchiveFile("tar");
				archive( tarFile, null, dirToArchive );
				
				directoryExtraction = ZipUtil.UnitTest.makeDirectory("tar" + "_extraction");
				extract( tarFile, directoryExtraction, false );
				confirmExtraction(dirToArchive, directoryExtraction);
			}
			finally {
				DirUtil.deleteIfExists(dirToArchive);	// do full cleanup immediately, even tho some/all of the files involved are temp, in order to save disk space for other tests
				FileUtil.deleteIfExists(tarFile);
				DirUtil.deleteIfExists(directoryExtraction);
			}
		}
		
		private void printEntries(File tarFile) throws Exception {
			System.out.println( "Below are the entries of the TAR file " + tarFile.getPath() + ":" );
			for (TarArchiveEntry entry : getEntries(tarFile, false)) {
				System.out.println( '\t' + entry.getName() );
			}
		}
		
	}
	
}
