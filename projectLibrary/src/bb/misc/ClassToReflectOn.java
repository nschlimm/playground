/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.misc;

import bb.util.ReflectUtil;

/**
* Class with multiple fields and methods that use various modifiers.
* <p>
* This class is package-private for two reasons.
* First, it is solely meant for internal testing purposes.
* Second, some of the classes which use it for testing (e.g. {@link ReflectUtil}) needs a non-public class like this
* to test how well they can overcome JVM access restrictions.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
class ClassToReflectOn {
	
	// -------------------- fields --------------------
	
	public static Object public_static_field;
	
	protected Object protected_field;
	
	Object default_field;
	
	private final Object private_final_field = new Object();
	
	// -------------------- constructor --------------------
	
	/** Package-private constructor too. */
	ClassToReflectOn() {}
	
	// -------------------- methods --------------------
	
	private String echoMethodName() { return "echoMethodName"; }
	
}
