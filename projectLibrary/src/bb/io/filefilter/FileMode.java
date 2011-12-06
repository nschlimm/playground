/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

/**
* Enum of all the ways that normal files can be handled by a filter.
* <p>
* This enum is multithread safe: it is stateless (except for the enumeration of values, which are immutable).
* <p>
* Like all java enums, this enum is Comparable and Serializable.
* <p>
* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/language/enums.html">Enum documentation</a>
* <p>
* @author Brent Boyer
*/
public enum FileMode {
	
	/** Specifies that normal files will always be accepted. */
	accept,
	
	/** Specifies that normal files will be subjected to the filter's test. */
	test,
	
	/** Specifies that normal files will always be rejected. */
	reject;
	
}
