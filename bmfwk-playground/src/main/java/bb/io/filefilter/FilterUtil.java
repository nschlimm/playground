/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import bb.util.Check;
import java.io.File;

/**
* Provides static utility methods for dealing with file filters.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class FilterUtil {
	
	/**
	* Returns a new {@link java.io.FileFilter java.io.FileFilter} which delegates all filtering to filter.
	* All of filter's results are detected and forwarded to listener.
	* <p>
	* While some FileFilter classes in this package directly support FileFilterListeners
	* (e.g. see the {@link BaseFilter#BaseFilter(FileMode, DirectoryMode, OtherMode, PartMode, FileFilterListener) full-arg BaseFilter constructor}),
	* generic FileFilters do not.
	* So, this method was written to conveniently add listener support to arbitrary FileFilters.
	* <p>
	* @param filter FileFilter that will delegate all filtering to
	* @param listener FileFilterListener that will forward all events to
	* @throws IllegalArgumentException if filter or listener is null
	*/
	public static java.io.FileFilter makeFilterWithListener(final java.io.FileFilter filter, final FileFilterListener listener) throws IllegalArgumentException {
		Check.arg().notNull(filter);
		Check.arg().notNull(listener);
		
		return new java.io.FileFilter() {
			@Override public boolean accept(File file) {
				boolean accepted = filter.accept(file);
				if (accepted) listener.accepted(file, "accepted by " + filter.getClass().getName());
				else listener.rejected(file, "REJECTED by " + filter.getClass().getName());
				return accepted;
			}
		};
	}
	
	/**
	* Same as {@link #makeFilterWithListener(java.io.FileFilter, FileFilterListener)}
	* except that uses {@link javax.swing.filechooser.FileFilter javax.swing.filechooser.FileFilter}.
	*/
	public static javax.swing.filechooser.FileFilter makeFilterWithListener(final javax.swing.filechooser.FileFilter filter, final FileFilterListener listener) {
		Check.arg().notNull(filter);
		Check.arg().notNull(listener);
		
		return new javax.swing.filechooser.FileFilter() {
			@Override public boolean accept(File file) {
				boolean accepted = filter.accept(file);
				if (accepted) listener.accepted(file, "accepted by " + filter.getClass().getName());
				else listener.rejected(file, "REJECTED by " + filter.getClass().getName());
				return accepted;
			}
			
			@Override public String getDescription() {
				return filter.getDescription();
			}
		};
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private FilterUtil() {}
	
}
