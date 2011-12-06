/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io.filefilter;

import java.io.File;

/**
* Defines callback methods for a listener class interested in File accept/reject events.
* <p>
* @author Brent Boyer
*/
public interface FileFilterListener {
	
	/**
	* Called when file has just been accepted by a filter.
	* <p>
	* @param file the File which was just accepted
	* @param msg optional message concerning how or why file was accepted; may be null
	*/
	void accepted(File file, String msg);
	
	/**
	* Called when file has just been rejected by a filter.
	* <p>
	* @param file the File which was just rejected
	* @param msg optional message concerning how or why file was rejected; may be null
	*/
	void rejected(File file, String msg);
	
}
