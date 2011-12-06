/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;

import static bb.util.StringUtil.newline;
import java.text.ParseException;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods for Throwables.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class ThrowableUtil {
		
	// -------------------- constants --------------------
	
	private static final int typicalTypeAndMessageSize = 64;	// guesstimate of a typical total number of chars in an Throwable's type plus its message
	
	// -------------------- toRuntimeException --------------------
	
	/**
	* Always returns a RuntimeException using this sequential logic:
	* <ol>
	*  <li>if t == null, returns a new RuntimeException which has a null cause</li>
	*  <li>else if t is an instanceof Error, returns a new RuntimeException which has t as its cause</li>
	*  <li>else if t is an instanceof RuntimeException, returns t <i>itself</i></li>
	*  <li>
	*		else if t is an instanceof Exception, then it must be a checked Exception,
	*		so it returns a new RuntimeException which has t as its cause
	*  </li>
	*  <li>
	*		else t is an actual Throwable instance (or some unknown subclass),
	*		so it returns a new RuntimeException which has t as its cause
	*  </li>
	* </ol>
	* <p>
	* This method is usually called to convert checked Exceptions into unchecked ones.
	* <p>
	* One example where this is useful is if you want to initialize a field by calling a method:
	* it turns out that such a method cannot throw a checked Exception,
	* so it must have a try-catch block,
	* and it may be convenient for the catch to simply pass whatever it catches to this method and rethrow the result.
	* <p>
	* @param t the original cause; may be null
	*/
	public static RuntimeException toRuntimeException(Throwable t) {
		if (t == null) return new RuntimeException("This RuntimeException wraps a null cause");
		else if (t instanceof Error) return new RuntimeException("This RuntimeException wraps an underlying Error (see cause)", t);
		else if (t instanceof RuntimeException) return (RuntimeException) t;
		else if (t instanceof Exception) return new RuntimeException("This RuntimeException wraps an underlying checked Exception (see cause)", t);
		else return new RuntimeException("This RuntimeException wraps an underlying Throwable (see cause)", t);
	}
	
	// -------------------- String methods: toString, getTypeAndMessage, getStackTraceString --------------------
	
	/**
	* Returns a String that represents t in its entirety.
	* The first line in the result is the result of calling {@link #getTypeAndMessage getTypeAndMessage}(t).
	* The next lines come from calling {@link #getStackTraceString getStackTraceString}(t).
	* Finally, if t has a cause,
	* then that Throwable's information is appended onto the result in a recursive call to this method.
	* <p>
	* @throws IllegalArgumentException if t == null
	*/
	public static String toString(Throwable t) throws IllegalArgumentException {
		Check.arg().notNull(t);
		
		StringBuilder sb = new StringBuilder(512);
		sb.append( getTypeAndMessage(t) ).append(newline);
		sb.append( getStackTraceString(t) );
		
		Throwable cause = t.getCause();
		if (cause != null) {
			sb.append( "Caused by: " ).append( toString(cause) );
		}
		
		return sb.toString();
	}
	
	/**
	* Returns a String that concatenates t's type (i.e. classname) and message into a single String.
	* <p>
	* If t is an instance of {@link ParseException}, then it also appends the error offset.
	* <p>
	* @throws IllegalArgumentException if t is null
	*/
	public static String getTypeAndMessage(Throwable t) throws IllegalArgumentException {
		Check.arg().notNull(t);
		
		StringBuilder sb = new StringBuilder(typicalTypeAndMessageSize);
		sb.append( t.getClass().getName() );
		String message = t.getMessage();
		if (message != null) {
			sb.append(": ").append(message);
		}
		if (t instanceof ParseException) {
			ParseException pe = (ParseException) t;
			sb.append("; error offset = ").append( pe.getErrorOffset() );	// have to manually do this, since by default ParseExceptions don't print this
		}
		return sb.toString();
	}
	
	/**
	* Returns a String that represents t's stack trace.
	* <p>
	* @throws IllegalArgumentException if t == null
	*/
	public static String getStackTraceString(Throwable t) throws IllegalArgumentException {
		Check.arg().notNull(t);
		
		return getStackTraceString( t.getStackTrace() );
	}
	
	/**
	* Returns a <code>String</code> that represent's <code>stackTrace</code>'s elements.
	* <p>
	* @throws IllegalArgumentException if stackTrace == null
	*/
	public static String getStackTraceString(StackTraceElement[] stackTrace) throws IllegalArgumentException {
		Check.arg().notNull(stackTrace);
		
		StringBuilder sb = new StringBuilder( stackTrace.length * 64 );
		for (StackTraceElement element : stackTrace) {
			sb.append('\t').append("at ").append( element.toString() ).append(newline);
		}
		return sb.toString();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private ThrowableUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_toRuntimeException_toString() {
			System.out.println();
			System.out.println("toRuntimeException(null):");
			RuntimeException re = toRuntimeException(null);
			Assert.assertNull(re.getCause());
			System.out.println( ThrowableUtil.toString(re) );
			
			System.out.println();
			System.out.println("toRuntimeException(Error):");
			re = toRuntimeException( new Error("deliberately generated test Error") );
			Assert.assertNotNull(re.getCause());
			System.out.println( ThrowableUtil.toString(re) );
			
			System.out.println();
			System.out.println("toRuntimeException(RuntimeException):");
			re = toRuntimeException( new RuntimeException("deliberately generated test RuntimeException") );
			Assert.assertNull(re.getCause());
			System.out.println( ThrowableUtil.toString(re) );
			
			System.out.println();
			System.out.println("toRuntimeException(Exception):");
			re = toRuntimeException( new Exception("deliberately generated test Exception") );
			Assert.assertNotNull(re.getCause());
			System.out.println( ThrowableUtil.toString(re) );
			
			System.out.println();
			System.out.println("toRuntimeException(Throwable):");
			re = toRuntimeException( new Throwable("deliberately generated test Throwable") );
			Assert.assertNotNull(re.getCause());
			System.out.println( ThrowableUtil.toString(re) );
		}
		
		@Test public void test_getTypeAndMessage() {
			System.out.println();
			System.out.println("getTypeAndMessage(Exception):");
			System.out.println( getTypeAndMessage( new Exception("deliberately generated test Exception") ) );
			
			System.out.println();
			System.out.println("getTypeAndMessage(ParseException):");
			System.out.println( getTypeAndMessage( new ParseException("deliberately generated test ParseException", 0) ) );
		}
		
		@Test public void test_getStackTraceString() {
			System.out.println();
			System.out.println("getStackTraceString(Exception):");
			System.out.println( getStackTraceString( new Exception("deliberately generated test Exception") ) );
		}
		
	}
	
}
