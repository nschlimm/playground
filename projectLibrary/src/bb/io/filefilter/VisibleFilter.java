/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import java.io.File;

/**
* File filter that accepts all visible normal files and directories, and rejects all hidden ones as well as other file system elements.
* <p>
* This class is multithread safe: it is immutable.
* In particular, it has no listener (see the {@link BaseFilter ancestor class} javadocs for more discussion).
* <p>
* @author Brent Boyer
*/
public class VisibleFilter extends BaseFilter {
	
	/** Constructs a new VisibleFilter instance. */
	public VisibleFilter() {
		super(FileMode.test, DirectoryMode.test, OtherMode.reject, PartMode.path);
	}
	
	@Override protected boolean passesTest(File file) {
		return !file.isHidden();
	}
	
	@Override public String getDescription() { return "Visible Files Only"; }
	
}
