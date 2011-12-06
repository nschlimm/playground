/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer Notes:

--a given web application may want to be more sophisticated here and NOT turn off caching for things like images?

*/


package bb.servlet;


import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;


/**
* This filter implements a universal cache-disabling service for an entire web application
* via its {@link #doFilter doFilter} method.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class CacheDisablingFilter extends AbstractFilter {


	// -------------------- Filter API (and helper) methods --------------------


	/**
	* This method sets the response header for all incoming HTTP requests with values
	* that should disable all caching for HTTP 1.1, HTTP 1.0, and any proxy server.
	* Non-HTTP requests pass thru unmodified.
	* <p>
	* @param request The servlet request we are processing
	* @param response The servlet response we are creating
	* @param chain The filter chain we are processing
	* @throws IOException if an input/output error occurs
	* @throws ServletException if a servlet error occurs
	*/
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (response instanceof HttpServletResponse) {
			HttpServletResponse hresponse = (HttpServletResponse) response;
			hresponse.setHeader("Cache-Control", "no-cache");	// HTTP 1.1
			hresponse.setHeader("Pragma", "no-cache");			// HTTP 1.0
			hresponse.setDateHeader("Expires", 0);				// prevents caching at any proxy server
		}

		chain.doFilter(request, response);
	}


}
