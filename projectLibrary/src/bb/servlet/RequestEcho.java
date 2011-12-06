/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.servlet;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
* This servlet echoes back information about the request that was sent to it.
* This is useful in debugging.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see #doPost doPost
* @see #doGet doGet
* @author Brent Boyer
*/
public class RequestEcho extends HttpServlet {


	// -------------------- constants --------------------


	private static final long serialVersionUID = 1;


	// -------------------- doGet, doPost --------------------


	/** This method simply calls {@link #doPost doPost}. */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}


	/**
	* This method echoes back information about the request's metadata, headers, URL info, parameters, and content.
	* The response is a plain text format (i.e. "text/plain") description.
	*/
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/plain");
		PrintWriter out = response.getWriter();

		printNetworkInfo(request, out);
		out.println();
		printMetadata(request, out);
		out.println();
		printUrlInfo(request, out);
		out.println();
		printHeaders(request, out);
		out.println();
			// NOTE: the request's headers should include Cookies & Locales (albeit in raw format, instead of parsed), which is why comment out code below
		//printCookies(request, out);
		//out.println();
		//printLocales(request, out);
		//out.println();
		printParameters(request, out);
		out.println();
		printContentInfo(request, out);

/*
+++ here are other things that may wish to print out in the future:
	--authentication & user info
		--getAuthType(), getRemoteUser()
*/
	}


	// -------------------- printXXX --------------------


	// Note: in the methods below, everything is appropriate only to HttpServletRequests unless otherwise noted


	/** Prints to out the network information (local and remote) involved in the request. */
	protected void printNetworkInfo(HttpServletRequest request, PrintWriter out) {
		out.println("--------------------------------------------------");
		out.println("Network Information");
		out.println("--------------------------------------------------");
			// generic servlet:
		out.println("Remote IP Address: " + request.getRemoteAddr());
		out.println("Remote Host: " + request.getRemoteHost());
		out.println("Server Host: " + request.getServerName());
		out.println("Server Port: " + request.getServerPort());
	}


	/** Prints to out metadata about the request. */
	protected void printMetadata(HttpServletRequest request, PrintWriter out) {
		out.println("--------------------------------------------------");
		out.println("Request Metadata");
		out.println("--------------------------------------------------");
			// generic servlet:
		out.println("Protocol: " + request.getProtocol());
		out.println("Scheme: " + request.getScheme());
		out.println("Is Secure: " + request.isSecure());
			// http servlet:
		out.println("Method: " + request.getMethod());
	}


	/** Prints to out information about the URL used by the request. */
	protected void printUrlInfo(HttpServletRequest request, PrintWriter out) {
		out.println("--------------------------------------------------");
		out.println("Request URL Info");
		out.println("--------------------------------------------------");
		out.println( "Request URL: " + request.getRequestURL() );
		out.println( "Request URI: " + request.getRequestURI() );
		out.println( "Context Path: " + request.getContextPath() );
		out.println( "Servlet Path: " + request.getServletPath() );
		out.println( "Path Info: " + request.getPathInfo() );
		out.println( "Path Translated: " + request.getPathTranslated() );
		out.println( "Query String: " + request.getQueryString() );
	}


	/** Prints to out any name/value header pairs in the request. */
	protected void printHeaders(HttpServletRequest request, PrintWriter out) {
		Enumeration names = request.getHeaderNames();

		if ( !names.hasMoreElements() ) {
			out.println("--------------------------------------------------");
			out.println("<Request HAS NO Headers>");
			out.println("--------------------------------------------------");
			return;
		}

		out.println("--------------------------------------------------");
		out.println("Request Headers");
		out.println("[Name : Value]");
		out.println("--------------------------------------------------");
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			String value = request.getHeader(name);
			out.println(name + " : " + value);
		}
	}


	/** Prints to out any cookies in the request. */
/*
the code below is commented out for now, since the info is essentially already in the headers;
only reason to uncomment is if you really need to see how the info is parsed

	protected void printCookies(HttpServletRequest request, PrintWriter out) {
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			out.println("--------------------------------------------------");
			out.println("<Request HAS NO Cookies>");
			out.println("--------------------------------------------------");
			return;
		}

		out.println("--------------------------------------------------");
		out.println("Request Cookies");
		out.println("[Name  Value  Comment  Domain  Path  MaxAge  Secure  Version]");
		out.println("--------------------------------------------------");
		for (Cookie c : cookies) {
			out.println(
				c.getName() + "  " + c.getValue() + "  " + c.getComment() + "  " +
				c.getDomain() + "  " + c.getPath() + "  " + c.getMaxAge() + "  " +
				c.getSecure() + "  " + c.getVersion() );
		}
	}


	protected void printLocales(HttpServletRequest request, PrintWriter out) {
		<to be implemented...>
	}
*/

	/** Prints to out any name/value parameter pairs in the request. */
	protected void printParameters(HttpServletRequest request, PrintWriter out) {
		Enumeration names = request.getParameterNames();

		if ( !names.hasMoreElements() ) {
			out.println("--------------------------------------------------");
			out.println("<Request HAS NO Parameters>");
			out.println("--------------------------------------------------");
			return;
		}

		out.println("--------------------------------------------------");
		out.println("Request Parameters");
		out.println("[Name (value sequence number) : Value]");
		out.println("--------------------------------------------------");
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			String[] values = request.getParameterValues(name);
			for (int i = 0; i < values.length; i++) {
				out.println(name + " (" + i + ") : " + values[i]);
			}
		}
	}


	/** Prints to out information about the request's content. */
	protected void printContentInfo(HttpServletRequest request, PrintWriter out) {
		out.println("--------------------------------------------------");
		out.println("Request Content");
		out.println("--------------------------------------------------");
			// generic servlet:
		out.println("Content Type: " + request.getContentType());
		out.println("Character Encoding: " + request.getCharacterEncoding());
		out.println("Content Length: " + request.getContentLength());
	}


}
