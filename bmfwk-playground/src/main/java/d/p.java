/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--should i add ability to stop the program just like the g class has in its s methods?
	--the user would need to hit something on the keyboard to continue execution...

--in my current code, Strings are always treated as primitives in that they just get simply printed out,
as opposed to having their complete internal state printed out
	--any need to ever print out a String's internal state?
	--if ever wanted this functionality, would need to define a separate s(String) method to handle the simple case,
	and reserve the s(String, Object) & s(Object) methods to always print out the state of the Object arg.
*/

package d;

import bb.util.Check;
import bb.util.ObjectState;
import bb.util.logging.LogUtil;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import org.junit.Test;

/**
* This class provides static methods which <i>send</i> output to a {@link PrintWriter}.
* You can use the various s methods to simply print out primitive types or Strings, or print out complete Object states.
* <p>
* The {@link #getPrintWriter}/{@link #setPrintWriter} methods are the accessor/mutator for the PrintWriter used.
* The default PrintWriter wraps {@link System#out}.
* <p>
* <i>This class was designed to require as little typing as possible to use</i>.
* In particular, all public names (package, class, and method) are lower case and have the minmal length (one char each).
* So, you can avoid import statements and easily invoke all methods using their fully qualified form.
* Here are some typical uses:
* <pre><code>
*	d.p.s();	// prints a blank line
*	...
*	d.p.s("reached point #1");	// use to note that your program reached a certain point
*	...
*	d.p.s(i);	// i is an int, previously defined and assigned, that you now want to print
*	...
*	d.p.s("l = " + l);	// print out some descriptive text followed by some long value
*	...
*	OneOfMyTypes myObject = new OneOfMyTypes();
*	d.p.s("Complete state of OneOfMyTypes", myObject);	// print out an object's fields
* </code></pre>
* <p>
* Mnemonic: the package name d stands for debug; the class name p stands for PrintWriter; the methods named s stand for send.
* <p>
* This class is multithread safe.
* <p>
* @author Brent Boyer
*/
public final class p {
	
	// -------------------- static fields --------------------
	
	/** The <code>PrintWriter</code> used by the <code>s</code> methods. */
	private static PrintWriter pw = null;	// always initialize this field in the static initializer method instead
	
	// -------------------- static initializer --------------------
	
	/**
	* Calls <code>setPrintWriter( new PrintWriter(System.out) )</code>
	* (do this instead of directly assign the field above in order to detect any error state in System.out).
	* Then registers a shutdown hook that calls {@link #onShutdown onShutdown}.
	* <p>
	* @throws IllegalArgumentException if setPrintWriter finds a problem with System.out
	*/
	static {
		setPrintWriter( new PrintWriter(System.out, true) );	// CRITICAL: use true for autoflush, see setPrintWriter javadocs below
		
		Thread shutdownHook = new Thread(
			new Runnable() {
				public void run() {
					onShutdown();
				}
			},
			"p_ShutdownHook"
		);
		shutdownHook.setPriority( Thread.NORM_PRIORITY );
		Runtime.getRuntime().addShutdownHook( shutdownHook );
	}
	
	// -------------------- getPrintWriter and setPrintWriter --------------------
	
	/** Accessor for {@link #pw}. */
	public static synchronized PrintWriter getPrintWriter() { return pw; }
	
	/**
	* Mutator for {@link #pw}.
	* Before the existing <code>PrintWriter</code> is swapped out, it is first flushed.
	* <p>
	* This class is typically used for debugging.
	* In this context, the I/O performance of pw is less important than its reliability.
	* Therefore, it is highly recommended that the pw arg be set to autoflush,
	* since otherwise have seen problems with output not appearing in a timely manner or at all.
	* <p>
	* @throws IllegalArgumentException if pw == null; pw is in the error state
	*/
	public static synchronized void setPrintWriter(PrintWriter pw) throws IllegalArgumentException {
		Check.arg().notNull(pw);
		if (pw.checkError()) throw new IllegalArgumentException("pw is in the error state");
		
		if (p.pw != null) p.pw.flush();
		p.pw = pw;
	}
	
	// -------------------- s --------------------
	
	/**
	* Prints a blank line.
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s() throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println();
	}
	
	/**
	* Prints b on a line.
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(boolean b) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println(b);
	}
	
	/**
	* Prints c on a line.
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(char c) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println(c);
	}
	
	/**
	* Prints l on a line.
	* It should be used to print out any integral-type primitive (i.e. byte, short, int, long).
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(long l) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println(l);
	}
	
	/**
	* Prints d on a line.
	* It should be used to print out any floating point-type primitive (i.e. float, double).
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(double d) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println(d);
	}
	
	/**
	* Prints charSequence on a line.
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(CharSequence charSequence) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		pw.println(charSequence);
	}
	
	/**
	* Prints out label followed by obj.
	* Both args are optional (either may be null).
	* <p>
	* If label is non-null, it is printed followed by ": " on one line.
	* Next, the complete state of obj is printed using {@link ObjectState}.
	* <p>
	* @throws IllegalStateException if {@link #pw} is in the error state
	*/
	public static synchronized void s(String label, Object obj) throws IllegalStateException {
		if (pw.checkError()) throw new IllegalStateException("pw is in the error state");
		
		if (label != null) pw.println(label + ": ");
		pw.println( new ObjectState(obj).toStringLabeled() );
	}
	
	// -------------------- onShutdown --------------------
	
	/** Performs actions that should only be done before the JVM shuts down, such as flushing pw. */
	private static synchronized void onShutdown() {
		//pw.close();
		//pw = null;
			// do NOT do the above, since other Threads during shutdown could conceivably still call methods of this class (see the javadocs on Runtime.addShutdownHook)
			// so just flush pw if it still exists:
		if (pw != null) pw.flush();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private p() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static final class UnitTest {
		
		@Test public void test_setps() throws IllegalArgumentException, FileNotFoundException, SecurityException {
			try {
				File file = LogUtil.makeLogFile("testoutput.txt");
				d.p.setPrintWriter( new PrintWriter(file) );
				d.p.s("If the setPrintWriter(String) method is working, then this text will be written to the file " + file.getPath());
			}
			finally {
				d.p.setPrintWriter( new PrintWriter(System.out) );
			}
		}
		
		@Test public void test_s_boolean() throws IllegalStateException {
			d.p.s(true);
			d.p.s(false);
		}
		
		@Test public void test_s_char() throws IllegalStateException {
			d.p.s('a');
			d.p.s('b');
			d.p.s('c');
		}
		
		@Test public void test_s_long() throws IllegalStateException {
			d.p.s();
			d.p.s("byte:");
			byte b = 1;
			d.p.s(b);
			
			d.p.s();
			d.p.s("short:");
			short sh = 12;
			d.p.s(sh);
			
			d.p.s();
			d.p.s("int:");
			int i = 123;
			d.p.s(i);
			
			d.p.s();
			d.p.s("long:");
			long l = 1234;
			d.p.s(l);
		}
		
		@Test public void test_s_double() throws IllegalStateException {
			d.p.s();
			d.p.s("float:");
			float f = 1234.5f;
			d.p.s(f);
			
			d.p.s();
			d.p.s("double:");
			double dbl = 12345.56;
			d.p.s(dbl);
		}
		
		@Test public void test_s_CharSequence() throws IllegalStateException {
			d.p.s();
			d.p.s("Pass a String to the s(CharSequence) method");
			
			d.p.s();
			d.p.s( new StringBuffer("Pass a StringBuffer to the s(CharSequence) method") );
			
			d.p.s();
			d.p.s( new StringBuilder("Pass a StringBuilder to the s(CharSequence) method") );
		}
		
		@Test public void test_s_StringObject() throws IllegalStateException {
			d.p.s();
			d.p.s("Pass a null reference as the 2nd arg of the s(String, Object) method", null);
			d.p.s();
			d.p.s("Pass a String as the 2nd arg of the s(String, Object) method", "Mary had a little lamb...");
			d.p.s();
			d.p.s("Pass an int[] as the 2nd arg of the s(String, Object) method", new int[0]);
		}
		
	}
	
}
