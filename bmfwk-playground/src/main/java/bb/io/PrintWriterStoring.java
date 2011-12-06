/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.io;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.io.Writer;
import org.junit.Assert;
import org.junit.Test;

/**
* PrintWriter subclass that writes all chars to an internal char buffer.
* This data is stored indefinately until it is retrieved and cleared by calling {@link #getString getString}.
* <p>
* Motivation: this class is typically used for debugging/test purposes when output from a PrintWriter needs to be captured.
* <p>
* This class is multithread safe: every method is synchronized on {@link Writer#lock}.
* <p>
* @author Brent Boyer
*/
public class PrintWriterStoring extends PrintWriter {
	
	// -------------------- instance fields --------------------
	
	private final CharArrayWriter caw;
	
	// -------------------- constructor --------------------
	
	public PrintWriterStoring() {
		super( new CharArrayWriter() );
		this.caw = (CharArrayWriter) out;	// out is a field of PrintWriter
	}
	
	// -------------------- new api: getString --------------------
	
	/**
	* Returns all the chars that have been written so far to this instance as a new String.
	* <b>Side effect:</b> before return, clears the internal char buffer.
	*/
	public String getString() {
		synchronized (lock) {	// lock is a field of Writer
			flush();
			String result = caw.toString();
			caw.reset();
			return result;
		}
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_publish() throws Exception {
			PrintWriterStoring pws = null;
			try {
				pws = new PrintWriterStoring();
				
				String s = "Arbitrary text could be here...";
				pws.print(s);
				Assert.assertEquals(s, pws.getString());
			}
			finally {
				StreamUtil.close(pws);
			}
		}
		
	}
	
}
