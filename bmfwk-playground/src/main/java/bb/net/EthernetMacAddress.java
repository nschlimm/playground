/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.net;


import bb.util.Check;
import org.junit.Assert;
import org.junit.Test;


/**
* This class stores an ethernet MAC (hardware) address as a byte[].
* <p>
* To get a machine's Ethernet MAC (Hardware) Address using DOS (is unix similar?):
* <pre>
	1) from a command line, ping the target machine; for example:
		ping 192.168.1.101
	(So, you need to know the target machine's hostname or ip address before can do this.)

	2) then execute
		arp -a
	on the command line to list the ip-->mac address table; for example, should see output like
		Interface: 192.168.1.100 --- 0x2
		  Internet Address      Physical Address      Type
		  192.168.1.101         00-03-47-73-dd-a5     dynamic
* </pre>
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
* @see <a href="http://www.garykessler.net/library/tcpip.html#netint">TCP/IP tutorial</a>
* @see <a href="http://www.dslreports.com/faq/wol/1.+General+Questions#4122">How do I find my MAC Address?</a>
*/
public class EthernetMacAddress {
	
	
	/**
	* <i>Preferred</i> char used to separate the bytes in an ethernet MAC address's String representation.
	* <p>
	* There appear to be 2 conventions: a dash (i.e. '-') or a colon (i.e. ':') char.
	* The value of this constant is a dash (i.e. '-'), because that is what Windows uses.
	*/
	public static final char byteSeparatorChar = '-';


	/** Array of 6 bytes which stores the 48 bit ethernet MAC address. */
	private final byte[] bytes = new byte[6];
	
	
	/**
	* Constructor which parse the bytes from a String valued address.
	* Each byte in address must be in hexadecimal format, and must be separated by either a '-' or ':' char.
	* <p>
	* @throws IllegalArgumentException if address is blank; address.length() != 17; address does not split into 6 tokens (bytes)
	* @throws NumberFormatException if one of the byte tokens is not a byte in hexadecimal format
	*/
	public EthernetMacAddress(String address) throws IllegalArgumentException, NumberFormatException {
		Check.arg().notBlank(address);
		if (address.length() != 17) throw new IllegalArgumentException("address.length() = " + address.length() + " != 17");
			
		String[] tokens = address.split("-|:");
		Check.arg().hasSize(tokens, bytes.length);
		for (int i = 0; i < bytes.length; i++) {
		 	bytes[i] = (byte) Integer.parseInt( tokens[i], 16 );
		}
	}
	

	/** Returns a clone of the internal byte[] (to maintain encapsulation). */
	public byte[] getBytes() { return bytes.clone(); }

	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder(17);
		for (int i = 0; i < bytes.length; i++ ) {
			if (i > 0) sb.append(byteSeparatorChar);
			sb.append( byteToString( bytes[i] ) );
		}
		return sb.toString();
	}
	
	
	public static String byteToString(byte b) {
		int unsigned = (b & 0xff);	// convert the (signed) byte integer value into its unsigned int value
		String stringHexadecimal = Integer.toHexString( unsigned );
		if (stringHexadecimal.length() == 2) return stringHexadecimal;
		else return "0" + stringHexadecimal;
	}


	// -------------------- UnitTest (static inner class) --------------------


	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_all() throws Exception {
			String address = "00-03-47-73-dd-a5";
			EthernetMacAddress ethernetMacAddress = new EthernetMacAddress(address);
			Assert.assertEquals( address, ethernetMacAddress.toString() );
		}
		
	}


}
