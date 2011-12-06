/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import java.io.File;

/**
* Subclass of {@link VisibleFilter} that additionally accepts Files which are system roots.
* This filter was introduced because it has been observed that Windows (XP, on NTFS disks, at least) marks roots as hidden.
* <p>
* This class is multithread safe: it is <a href="http://www.ibm.com/developerworks/java/library/j-jtp02183.html">immutable</a>
* (all of its fields are final, none of their state can be changed after construction,
* and it is always <a href="http://www.ibm.com/developerworks/java/library/j-jtp0618.html">properly constructed</a>;
* see p. 53 of <a href="http://www.javaconcurrencyinpractice.com">Java Concurrency In Practice</a> for more discussion).
* <p>
* @author Brent Boyer
*/
public class VisibleOrRootFilter extends VisibleFilter {
	
	/** Constructs a new VisibleOrRootFilter instance. */
	public VisibleOrRootFilter() {}
	
	@Override protected boolean passesTest(File file) {
		return super.passesTest(file) || isRootFile(file);
	}
	
	private boolean isRootFile(File file) {
		//File[] rootFiles = File.listRoots();
		//return (Arrays.binarySearch(rootFiles, file) >= 0);
			// One problem with the code above is performance: there is a cost to calling File.listRoots.
			// This problem can be solved by caching the result into a field.
			// Unfortunately, there is a more serious bug: removable media can invalidate the result at any time due to insertions/removals.
			// So, had to go with the code below, which ought to always work.
		return file.exists() && (file.getParentFile() == null);
	}
	
	@Override public String getDescription() { return "Visible and Root Files Only"; }
	
}
