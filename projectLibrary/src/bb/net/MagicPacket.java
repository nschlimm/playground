/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.net;


import bb.io.StreamUtil;
import bb.util.Check;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
* This class can create and send a "magic packet" to wake up another computer over the network (aka WOL).
* <p>
* To learn more about remote wake up, consult these links:
* <pre>
	http://support.intel.com/support/network/sb/CS-008459.htm
	http://www.madge.com/_assets/downloads/lsshelp8.0/LSSHelp/AdvFeat/WonLAN/WonLAN2.htm
	http://searchnetworking.techtarget.com/sDefinition/0,,sid7_gci214609,00.html
	http://www.dslreports.com/faq/wol
	http://gsd.di.uminho.pt/jpo/software/wakeonlan/mini-howto/wol-mini-howto-3.html#ss3.2
* </pre>
* Note that you will likely have to configure the target computer to allow its NIC to wake up the computer.
* On 2004/4/2 on Lily's Win2k laptop, here is the configuration that was required:
* <ol>
*  <li>open the Network control panel</li>
*  <li>right click on Local Area Connection and select Properties</li>
*  <li>click on the Configure button for the network adapter</li>
*  <li>In the Advanced tab, select the "Enable PME" item and set its value to Enabled</li>
*  <li>In the Power Management tab, click on both options (allow device to bring computer out of standby as well as be shut down)</li>
* </ol>
* <p>
* This class was inspired by the following magic packet programs:
* <pre>
	C: http://www.rom-o-matic.net/5.0.4/contrib/wakeonlan/wol.c
	Java: http://www.java-internals.com/code/
	Python: http://gsd.di.uminho.pt/jpo/software/wakeonlan/mini-howto/wolpython.txt
* </pre>
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class MagicPacket {
	
	
	// -------------------- constants --------------------
	
	
	/**
	* Default port value to send the magic packet to.
	* <p>
	* @see <a href="http://www.dslreports.com/faq/wol/1.1+DSLR+Wake+on+Lan#9388">Port 9, or what?</a>
	* @see <a href="http://www.dslreports.com/faq/wol/1.1+DSLR+Wake+on+Lan#9389">How do I set up my computer for WOL?</a>
	*/
	public static final int port_default = 9;
	
	
	// -------------------- send and helper methods --------------------
	
	
	/** Simply calls <code>{@link #send(EthernetMacAddress, InetAddress, int) send}(ethernetMacAddress, inetAddress, port_default)</code>. */
	public static void send(EthernetMacAddress ethernetMacAddress, InetAddress inetAddress) throws IllegalArgumentException, SocketException, SecurityException, IOException {
		send(ethernetMacAddress, inetAddress, port_default);
	}
	
	
	/**
	* Sends the "magic packet" data (constructed from ethernetMacAddress) to inetAddress at port.
	* This should wake up that computer.
	* <p>
	* @throws IllegalArgumentException if ethernetMacAddress == null; inetAddress == null; port is an invalid value
	* @throws SocketException if a DatagramSocket could not be opened, or the socket could not bind to the specified local port
	* @throws SecurityException if a security manager exists and its checkListen method doesn't allow the operation
	* @throws IOException if an I/O problem occurs
	*/
	public static void send(EthernetMacAddress ethernetMacAddress, InetAddress inetAddress, int port) throws IllegalArgumentException, SocketException, SecurityException, IOException {
		Check.arg().notNull(ethernetMacAddress);
		Check.arg().notNull(inetAddress);
		Check.arg().validPort(port);
		
		DatagramSocket socket = null;
		try {
			byte[] data = magicPacketData(ethernetMacAddress);
			DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
			socket = new DatagramSocket();
			socket.send(packet);
		}
		finally {
			StreamUtil.close(socket);
		}
	}
	
	
	private static byte[] magicPacketData(EthernetMacAddress ethernetMacAddress) {
		byte[] data = new byte[6 + (16*6)];
		
		int index = 0;
		
			// first 6 bytes are each 255 (0xff):
		for (int i = 0; i < 6; i++) {
			data[index++] = (byte) 255;
		}
		
			// remaining bytes are the bytes of ethernetMacAddress repeated 16 times:
		byte[] macAddress = ethernetMacAddress.getBytes();
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 6; j++ ) {
				data[index++] = macAddress[j];
			}
		}
		
		return data;
	}
	
	
	// -------------------- constructor --------------------
	
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private MagicPacket() {}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Ignore("This test requires known hardware MAC addresses which is bad, plus it requires manual inspection of the machione to be woken up")
		@Test public void test_send() throws Exception {
//			String macAddress = "B2-5B-F1-7A-73-5E";	// desktop
			String macAddress = "00-03-47-73-dd-a5";	// laptop
			EthernetMacAddress ethernetMacAddress = new EthernetMacAddress(macAddress);
			
//			String ipAddress = "192.168.1.100";	// desktop
			String ipAddress = "192.168.1.110";	// laptop
			InetAddress inetAddress = InetAddress.getByName(ipAddress);
			
			MagicPacket.send(ethernetMacAddress, inetAddress);
			System.out.println("Magic packet was sent to IP = " + ipAddress + " (MAC = " + macAddress + ")");
			System.out.println("INSPECT TARGET MACHINE TO SEE IF IT WOKE UP");
		}
		
		/** This test is taken from http://users.pandora.be/jbosman/poweroff/poweroff.htm (see "3.2.8 Wake-On-LAN"). */
		@Test public void test_magicPacketData() throws Exception {
			String address = "01:02:03:04:05:06";
			String knownValidMagicPacket = "FFFFFFFFFFFF010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506010203040506";
			
			EthernetMacAddress ethernetMacAddress = new EthernetMacAddress(address);
			StringBuilder sb = new StringBuilder(64);
			for (byte b : MagicPacket.magicPacketData(ethernetMacAddress)) {
				sb.append( EthernetMacAddress.byteToString(b) );
			}
			String ourMagicPacket = sb.toString();
			
			System.out.println("EthernetMacAddress: " + address);
			System.out.println("Known valid magic packet: " + knownValidMagicPacket);
			System.out.println("This class's magic packet: " + ourMagicPacket);
			Assert.assertEquals(knownValidMagicPacket, ourMagicPacket.toUpperCase());
		}
		
	}
	
	
}
