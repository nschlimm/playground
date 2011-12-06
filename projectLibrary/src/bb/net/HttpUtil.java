/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.net;


import bb.util.Check;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;


/**
* Provides miscellaneous static utility methods for dealing with the http protocol.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @see HttpParameters
* @author Brent Boyer
*/
public final class HttpUtil {
	
	
	// -------------------- sendPostMessage --------------------
	
	
	/**
	* This method:
	* <ol>
	*  <li>sets the request method of the connection arg to "POST"</li>
	*  <li>adds the request property "Content-Type"/"application/x-www-form-urlencoded"</li>
	*  <li>writes <code>parameters.toPostParameterString()</code> to connection's OutputStream</li>
	* </ol>
	* <p>
	* Note: this method performs no cleanup actions (e.g. closing the OutputStream).
	* <p>
	* @throws IllegalArgumentException if connection or parameters is null
	* @throws IOException if an I/O problem occurs
	*/
	public static void sendPostMessage(HttpURLConnection connection, HttpParameters parameters) throws IllegalArgumentException, IOException {
		Check.arg().notNull(connection);
		Check.arg().notNull(parameters);
		
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		
		DataOutputStream dos = new DataOutputStream( connection.getOutputStream() );
		dos.writeBytes( parameters.toPostParameterString() );	// is safe to call writeBytes, which ignores high byte of each char -- see the HttpParameters.toFormUrlencodedString contract
		dos.flush();	// Note: FindBugs complains that dos is never closed, however, cannot close it because that would affect connection...
	}
	
	
	// -------------------- constructor --------------------
	
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private HttpUtil() {}


}
