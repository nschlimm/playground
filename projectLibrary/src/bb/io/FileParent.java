/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.io;

import bb.util.Check;
import bb.util.StringUtil;
import java.io.File;

/**
* This package-private class supports by classes like {@link TarUtil} and {@link ZipUtil}.
* The {@link #getRelativePath getRelativePath} method defines the essential functionality.
* <p>
* This class is multithread safe: its static state is guarded by synchronization,
* while its instance state is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
class FileParent {
	
	// -------------------- fields --------------------
	
	private static int count = 0;
	
	/**
	* File that this instance is for.
	* Every File passed to the {@link #getRelativePath getRelativePath} method
	* must equal or be contained inside this value.
	* <p>
	* Contract: is never null.
	*/
	private final File file;
	
	/**
	* The parent directory of {@link #file}.
	* <p>
	* Contract: is null if and only if file is a root directory.
	*/
	private final File parent;
	
	/**
	* If {@link #parent} is null,
	* then this field holds a made up name for an imaginary root directory containing {@link #file}.
	* <p>
	* Contract: is null if file is a filesystem root directory.
	* Otherwise, is non-null and the value is unique across all instances of this class in a given JVM session.
	*/
	private final String rootName;
	
	// -------------------- static methods --------------------
	
	private static synchronized String nextRootName(File parent) {
		if (parent == null) return "root" + (++count);
		else return null;
	}
	
	// -------------------- constructor --------------------
	
	FileParent(File file) throws IllegalArgumentException {
		Check.arg().notNull(file);
		
		this.file = file;
		this.parent = file.getParentFile();
		this.rootName = nextRootName(this.parent);
	}
	
	// -------------------- getRelativePath --------------------
	
	/**
	* Returns descendant's relative path.
	* If {@link #parent} is non-null, then the result is relative to parent.
	* Otherwise, the result begins with {@link #rootName},
	* followed by descendant's path relative to {@link #file}.
	* <p>
	* The result always uses separator to distinguish path elements.
	* <p>
	* Furthermore, if descendant is a directory, then the result is guaranteed to end with separator
	* (since classes like ZipEntry identify directories based on this).
	* <p>
	* To understand the motivation for this behavior,
	* see, for example, {@link ZipUtil#archive(File, FileFilter, File[])}.
	* <p>
	* @throws IllegalArgumentException if descendant == null
	*/
	String getRelativePath(File descendant, char separator) throws IllegalArgumentException {
		Check.arg().notNull(descendant);
		
		String path;
		if (parent != null) {
			path = FileUtil.getPathRelativeTo(descendant, parent);
		}
		else {
			path = rootName + separator + FileUtil.getPathRelativeTo(descendant, file);
		}
		
		if (File.separatorChar != separator) path = path.replace(File.separatorChar, separator);
		
		if (descendant.isDirectory()) path = StringUtil.ensureSuffix(path, separator);
		
		return path;
	}
	
}
