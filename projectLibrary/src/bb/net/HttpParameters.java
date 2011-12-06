/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer Notes:

--the current implementation simply takes string name/values and adds their encodings to a StringBuilder,
but is there ever a need for greater functionality, such as having a fundamental Parameter class, and having
this instance add Parameter instances to an internal List, and only doing the string conversion as an afterthought?

*/


package bb.net;


import bb.util.Check;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Properties;


/**
* This class provides a convenient way to store all the parameters associated with an http request
* in a form that is properly encoded for submission.
* <p>
* Many programmers use a {@link Properties} instance to do the same functionality as this class.
* Unfortunately, the Properties class has a fundamental defect: each key is always mapped to exactly one value.
* In contrast, each http parameter key is allowed to be mapped to many values; this class supports this feature.
* Furthermore, since this class is dedicated to supporting http parameters,
* it is more convenient to use for this purpose than the Properties class
* (e.g. the {@link #toGetParameterString toGetParameterString} and {@link #toPostParameterString toPostParameterString} methods).
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class HttpParameters {


	/**
	* Stores the parameters as they are added.
	* Is initialized in a constructor.
	*/
	protected StringBuilder buffer;


	// -------------------- constructor --------------------
	
	
	/** Simply calls <code>this(1)</code>. */
	public HttpParameters() {
		this(1);
	}
	
	
	/**
	* Constructs a new HttpParameters instance.
	* <p>
	* @param initialNumberOfParameters
	*/
	public HttpParameters(int initialNumberOfParameters) {
		buffer = new StringBuilder( initialNumberOfParameters * 2 * 32 );	// 2: each parameter has a name & value; 32: a guess at the size of a typical name or value
	}
	
	
	// -------------------- addProperties, addParameter --------------------
	
	
	/**
	* Simply calls <code>addProperties(properties, "UTF-8")</code>.
	* <p>
	* @throws IllegalArgumentException if properties == null;
	* any (name, value) pair of properties violates what
	* {@link #addParameter(String, String, String) addParameter} requires
	* @throws UnsupportedEncodingException if "UTF-8" is not a supported encoding (this should never happen)
	*/
	public void addProperties(Properties properties) throws IllegalArgumentException, UnsupportedEncodingException {
		addProperties(properties, "UTF-8");
	}
	
	
	/**
	* Adds all the name and value pairs in the properties arg as parameters to this instance.
	* This method is mainly provided for the convenience of old code which used Properties instances.
	* New code should only use instances of this class.
	* <p>
	* @throws IllegalArgumentException if properties == null;
	* any (name, value) pair of properties or encoding violates what
	* {@link #addParameter(String, String, String) addParameter} requires
	* @throws UnsupportedEncodingException if encoding is not supported
	*/
	public void addProperties(Properties properties, String encoding) throws IllegalArgumentException, UnsupportedEncodingException {
		Check.arg().notNull(properties);
		// all the elements of properties, as well as encoding, will be checked by the calls to addParameter below
		
		for (Enumeration names = properties.propertyNames(); names.hasMoreElements(); ) {
			String name = (String) names.nextElement();
			String value = properties.getProperty(name);
			addParameter(name, value, encoding);
		}
	}
	
	
	/**
	* Simply calls <code>addParameter(name, value, "UTF-8")</code>.
	* <p>
	* @throws IllegalArgumentException if either name is blank; value == null
	* @throws UnsupportedEncodingException if "UTF-8" is not a supported encoding (this should never happen)
	*/
	public void addParameter(String name, String value) throws IllegalArgumentException, UnsupportedEncodingException {
		addParameter(name, value, "UTF-8");
	}
	
	
	/**
	* Adds name and value as a new properly encoded parameter to {@link #buffer}.
	* Before adding them, an ampersand is added if buffer already has contents (i.e. previous parameters).
	* Also, both name and value are first converted into
	* <code>application/x-www-form-urlencoded</code> MIME format using {@link URLEncoder}
	* before being added.
	* <p>
	* @throws IllegalArgumentException if either name is blank; value == null; encoding is blank
	* @throws UnsupportedEncodingException if encoding is not supported
	*/
	public void addParameter(String name, String value, String encoding) throws IllegalArgumentException, UnsupportedEncodingException {
		Check.arg().notBlank(name);
		Check.arg().notNull(value);
		Check.arg().notBlank(encoding);
		
		if (buffer.length() > 0) buffer.append('&');	// precede with ampersand if already have paramter(s) present
		
		name = URLEncoder.encode(name, encoding);
		value = URLEncoder.encode(value, encoding);
		buffer.append( name ).append('=').append( value );
	}


	// -------------------- toFormUrlencodedString, toGetParameterString, toPostParameterString --------------------


	/**
	* Returns the parameters that have been added so far as a single String of name/value pairs that is properly encoded for
	* <a href="http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4">HTML form submission</a>
	* as MIME type
	* <a href="http://www.w3.org/TR/html401/interact/forms.html#h-17.13.4.1">application/x-www-form-urlencoded</a>.
	* <p>
	* Note: the charset for <a href="http://www.ietf.org/rfc/rfc1738.txt">URLs</a>
	* (which includes HTML form encoding) is "US-ASCII".
	* One consequence is that the high byte of each char in the result is unused.
	* <p>
	* @return a properly encoded String of the name/value pairs, or an empty String (i.e. "") if there are no name/value pairs
	*/
	public String toFormUrlencodedString() {
		return buffer.toString();
	}


	/**
	* Returns the parameters that have been added so far as a single String of name/value pairs
	* that is suitable for appending to a URL as part of an HTTP GET request.
	* <p>
	* @return the result of <code>"?" + toFormUrlencodedString()</code>
	* @see #toFormUrlencodedString
	*/
	public String toGetParameterString() {
		return "?" + toFormUrlencodedString();
	}


	/**
	* Returns the parameters that have been added so far as a single String of name/value pairs
	* that is suitable for writing on an OutputStream to a server as part of an HTTP POST request.
	* <p>
	* @return the result of <code>toFormUrlencodedString()</code>
	* @see #toFormUrlencodedString
	*/
	public String toPostParameterString() {
		return toFormUrlencodedString();
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
// +++ add one...


}
