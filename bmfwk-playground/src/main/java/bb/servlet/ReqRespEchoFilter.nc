// Copyright (c) 2003 Brent Boyer.  All Rights Reserved.


/*

Programmer Notes:

+++ FINISH THE CODE BELOW!

*/


package bb.servlet;


import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.*;


/**
* This filter reads the input client request and the output servlet chain response
* and writes all the data to a PrintWriter for logging purposes.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class ReqRespEchoFilter extends AbstractFilter {
	
	
	/** Contract: is never null. */
	protected final PrintWriter pw;
	
	
	// -------------------- constructor --------------------
	
	
	/**
	* Constructor.
	* <p>
	* @param pw the PrintWriter that will echo to
	* @throws IllegalArgumentException if pw == null
	*/
	public ReqRespEchoFilter(PrintWriter pw) throws IllegalArgumentException {
		Check.arg().notNull(pw);
		
		this.pw = pw;
	}
	
	
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
+++ do something here to read the data on request, then write it to pw

+++ will probably need to wrap response in a class that buffers the output data so that we can retrieve it below
	--for example, see the CharResponseWrapper class here:
		http://java.sun.com/products/servlet/Filters.html
		

		chain.doFilter(request, response);


+++ do something here to read the data on response, then write it to pw
	}
	
	
}
