/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.misc;

import bb.util.ReflectUtil;

/**
* Offers a public hook for classes outside this package (e.g. {@link ReflectUtil}) to create instances of the otherwise inacessible {@link ClassToReflectOn}.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class MiscFactory {
	
	public static Object createClassToReflectOn() {
		return new ClassToReflectOn();
	}
	
}
