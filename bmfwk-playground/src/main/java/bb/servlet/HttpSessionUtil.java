/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.servlet;


import bb.util.Check;
import javax.servlet.http.HttpSession;


/**
* Provides static utility methods for dealing with HttpSessions.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @see HttpSession
* @author Brent Boyer
*/
public final class HttpSessionUtil {


	// -------------------- toString, getCreationTime --------------------


	/**
	* Returns a String description of the supplied HttpSession.
	* <p>
	* @throws IllegalArgumentException if session is null
	*/
	public static String toString(HttpSession session) throws IllegalArgumentException {
		Check.arg().notNull(session);

		return
			"session.getId() = " + session.getId() + ", " +
			"session.getCreationTime() = " + HttpSessionUtil.getCreationTime(session) + ", " +
			"session.getLastAccessedTime() = " + session.getLastAccessedTime();
	}


	/**
	* Returns a String description of the supplied HttpSession's creation time.
	* If the HttpSession has already been invalidated, this method catches
	* the IllegalStateException which will be thrown and returns a message stating this.
	* <p>
	* @throws IllegalArgumentException if session is null
	*/
	public static String getCreationTime(HttpSession session) throws IllegalArgumentException {
		Check.arg().notNull(session);

		try {
			return String.valueOf( session.getCreationTime() );
		}
		catch (IllegalStateException ise) {
			return "<CREATION TIME UNAVAILABLE: session has already been invalidated>";
		}
	}


	// -------------------- constructor --------------------


	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private HttpSessionUtil() {}


}
