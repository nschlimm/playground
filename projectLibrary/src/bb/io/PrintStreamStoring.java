/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Assert;
import org.junit.Test;

/**
* PrintStream subclass that writes all bytes to an internal byte buffer.
* This output data is stored indefinately until it is retrieved and cleared by calling either {@link #getString getString} or {@link #getBytes getBytes}.
* <p>
* Motivation: this class is typically used for debugging/test purposes when output from a PrintStream needs to be captured.
* <p>
* This class is obsolete in the same sense that PrintStream is obsolete:
* just as PrintStream should be replaced by PrintWriter whenever possible,
* so should this class be replaced by {@link PrintWriterStoring}.
* Unfortunately, certain APIs (e.g. {@link System#out}) have hardwired the use of PrintStream, and so this class must be retained.
* <p>
* This class is multithread safe: every method is synchronized.
* (Altho unmentioned in the javadocs, if you look at the source code, PrintStream synchronizes on the this reference, so this class continues that pattern.)
* <p>
* @author Brent Boyer
*/
public class PrintStreamStoring extends PrintStream {
	
	// -------------------- instance fields --------------------
	
	private final ByteArrayOutputStream baos;
	
	// -------------------- constructor --------------------
	
	public PrintStreamStoring() {
		super( new ByteArrayOutputStream() );
		this.baos = (ByteArrayOutputStream) out;	// out is a field of our ancestor FilterOutputStream+
	}
	
	// -------------------- new api: getString, getBytes --------------------
	
	/** Returns <code>new String( {@link #getBytes} )</code>. */
	public synchronized String getString() {
		return new String( getBytes() );
	}
	
	/**
	* Returns all the bytes that have been written so far to this instance as a new byte[].
	* <b>Side effect:</b> before return, clears the internal byte buffer.
	*/
	public synchronized byte[] getBytes() {
		flush();
		byte[] result = baos.toByteArray();
		baos.reset();
		return result;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_publish() throws Exception {
			PrintStreamStoring pss = null;
			try {
				pss = new PrintStreamStoring();
				
				String s = "Arbitrary text could be here...";
				pss.print(s);
				Assert.assertEquals(s, pss.getString());
				
				byte[] bytes = new byte[] { 1, 2, 3, 4, 5 };
				pss.write(bytes);
				Assert.assertArrayEquals(bytes, pss.getBytes());
			}
			finally {
				StreamUtil.close(pss);
			}
		}
		
	}
	
}
