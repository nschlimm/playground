/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import bb.io.ZipUtil;
import java.io.File;

/**
* Filter that accepts everything which passes {@link ZipUtil#isZipable ZipUtil.isZipable}.
* <p>
* This class is multithread safe: it is immutable.
* In particular, it has no listener (see the {@link BaseFilter ancestor class} javadocs for more discussion).
* <p>
* @author Brent Boyer
*/
public class ZippableFilter extends BaseFilter {
	
	/**
	* Constructor.
	* <p>
	* @param listener an optional {@link FileFilterListener} to receive File accept/reject events; may be null
	*/
	public ZippableFilter(FileFilterListener listener) {
		super(FileMode.test, DirectoryMode.test, OtherMode.test, PartMode.path, listener);
	}
	
	@Override protected boolean passesTest(File file) {
		return ZipUtil.isZipable(file);
	}
	
	@Override public String getDescription() { return "Files ZIPable by Java"; }
	
}
