/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import java.io.File;

/**
* Enum of all the ways that "other" file system elements can be handled by a filter.
* <p>
* There are three types of file system elements: normal files, directories, and "other".
* Here, a "other" file system element is one whose {@link File} returns false for both
* {@link File#isFile isFile} and {@link File#isDirectory isDirectory}.
* <p>
* Here are some ways that "other" file system elements can be encountered:
* <ol>
*  <li>
*		if a new File instance is created, and its path refers to a non-existing file system element,
*		then its isFile and isDirectory methods obviously must both return false
*  </li>
*  <li>
*		as actual existing entities on certain operating systems and/or file systems.
*		For example, on Windows/NTFS, every drive has a (usually hidden) folder called <code>System Volume Information</code>,
*		and a File which points to it returns false for both its isFile and isDirectory methods.
* 		Have also seen a File which points to <code>A:\</code> when a floppy drive is not present return false for both its isFile and isDirectory methods.
*  </li>
* </ol>
* <p>
* This enum is multithread safe: it is stateless (except for the enumeration of values, which are immutable).
* <p>
* Like all java enums, this enum is Comparable and Serializable.
* <p>
* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/language/enums.html">Enum documentation</a>
* <p>
* @author Brent Boyer
*/
public enum OtherMode {
	
	/** Specifies that "other" file system elements will always be accepted. */
	accept,
	
	/** Specifies that "other" file system elements will be subjected to the filter's test. */
	test,
	
	/** Specifies that "other" file system elements will always be rejected. */
	reject;
	
}
