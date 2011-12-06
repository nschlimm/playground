/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import org.junit.Test;

/**
* Provides static utility methods for Threads.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class ThreadUtil {
	
	// -------------------- getStackTraceString --------------------
	
	/** Returns <code>{@link #getStackTraceString(Thread) getStackTraceString}( Thread.currentThread() )</code>. */
	public static String getStackTraceString() {
		return getStackTraceString( Thread.currentThread() );
	}
	
	/**
	* Returns a <code>String</code> that represent's <code>thread</code>'s stack trace.
	* <p>
	* @throws IllegalArgumentException if thread == null
	*/
	public static String getStackTraceString(Thread thread) throws IllegalArgumentException {
		Check.arg().notNull(thread);
		
		return ThrowableUtil.getStackTraceString( thread.getStackTrace() );
	}
	
	// -------------------- toString --------------------
	
	public static String toString(Thread thread) {
		Check.arg().notNull(thread);
		
		StringBuilder sb = new StringBuilder(512);
		sb.append( thread.toString() ).append('\n');
		sb.append( getStackTraceString(thread) );
		return sb.toString();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private ThreadUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getStackTraceString() {
			System.out.println("Output of getStackTraceString:");
			System.out.println( ThreadUtil.getStackTraceString( Thread.currentThread() ) );
		}
		
		@Test public void test_toString() {
			System.out.println("Output of toString:");
			System.out.println( ThreadUtil.toString( Thread.currentThread() ) );
		}
		
	}
	
}
