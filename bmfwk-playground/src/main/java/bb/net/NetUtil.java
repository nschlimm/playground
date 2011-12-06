/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.net;

import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.NumberUtil;
import bb.util.logging.LogUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import org.junit.Ignore;
import org.junit.Test;

/**
* Provides static utility methods that deal with networking.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class NetUtil {
	
	// -------------------- isValidPort --------------------
	
	/**
	* Determines whether or not port is a valid TCP or UDP port number.
	* The requirement is that port be representable by an unsigned 2 byte integer
	* (i.e. 0 <= port <= {@link NumberUtil#unsigned2ByteMaxValue}).
	* See <a href="http://en.wikipedia.org/wiki/TCP_and_UDP_port">TCP and UDP port</a>.
	*/
	public static boolean isValidPort(int port) {
		return ( (0 <= port) && (port <= NumberUtil.unsigned2ByteMaxValue) );
	}
	
	// -------------------- getSocketState --------------------
	
	/**
	* Returns a String which lists <i>all</i> of socket's publicly accessible state.
	* <p>
	* @throws IllegalArgumentException if any socket == null
	* @throws SocketException if there is an error in the underlying protocol, such as a TCP error
	*/
	public static String getSocketState(Socket socket) throws IllegalArgumentException, SocketException {
		Check.arg().notNull(socket);
		
		StringBuilder sb = new StringBuilder();
		sb
			.append("Class: ").append(socket.getClass().getName()).append('\n')
			.append("hashCode: ").append(socket.hashCode()).append('\n')
			.append("Channel: ").append(socket.getChannel()).append('\n')
			.append("InetAddress: ").append(socket.getInetAddress()).append('\n')
			.append("KeepAlive: ").append(socket.getKeepAlive()).append('\n')
			.append("LocalAddress: ").append(socket.getLocalAddress()).append('\n')
			.append("LocalPort: ").append(socket.getLocalPort()).append('\n')
			.append("LocalSocketAddress: ").append(socket.getLocalSocketAddress()).append('\n')
			.append("OOBInline: ").append(socket.getOOBInline()).append('\n')
			.append("(Remote)Port: ").append(socket.getPort()).append('\n')
			.append("ReceiveBufferSize: ").append(socket.getReceiveBufferSize()).append('\n')
			.append("RemoteSocketAddress: ").append(socket.getRemoteSocketAddress()).append('\n')
			.append("ReuseAddress: ").append(socket.getReuseAddress()).append('\n')
			.append("SendBufferSize: ").append(socket.getSendBufferSize()).append('\n')
			.append("SoLinger: ").append(socket.getSoLinger()).append('\n')
			.append("SoTimeout: ").append(socket.getSoTimeout()).append('\n')
			.append("TcpNoDelay: ").append(socket.getTcpNoDelay()).append('\n')
			.append("TrafficClass: ").append(socket.getTrafficClass()).append('\n')
			.append("isBound: ").append(socket.isBound()).append('\n')
			.append("isClosed: ").append(socket.isClosed()).append('\n')
			.append("isConnected: ").append(socket.isConnected()).append('\n')
			.append("isInputShutdown: ").append(socket.isInputShutdown()).append('\n')
			.append("isOutputShutdown: ").append(socket.isOutputShutdown()).append('\n');
		return sb.toString();
	}
	
	// -------------------- getFtpUrl, writeFileToUrl --------------------
	
// for doing ftp from within java:
//	http://www.javaworld.com/javaworld/jw-03-2006/jw-0306-ftp_p.html
//	http://forum.java.sun.com/thread.jsp?thread=15701&forum=11&message=38409
//	http://www.javaworld.com/javaworld/jw-04-2003/jw-0404-ftp.html
//	http://forum.java.sun.com/thread.jsp?forum=54&thread=420907
	/**
	* Returns a new URL instance that specifies filenameRemote on the remote host.
	* The ftp protocol in binary mode is used, with username and password as login credentials.
	* In particular, the returned URL has the string value
	*	<blockquote><code>"ftp://" + username + ":" + password + "@" + host + "/" + filenameRemote + ";type=i"</code></blockquote>
	* Thus, the host value should not end with a '/' char, nor should filenameRemote begin with a '/' char.
	* <p>
	* <b>Warning: the ftp protocol is completely insecure, with the username and password along with all data being sent in the clear.</b>
	* <p>
	* @throws IllegalArgumentException if any arg is null
	* @throws RuntimeException if any arg causes the ftp URL to be malformed
	* @see <a href="http://www.cs.tut.fi/~jkorpela/ftpurl.html">ftp URLs</a>
	* @see <a href="http://www.squid-cache.org/mail-archive/squid-users/199707/0206.html">ftp passwords sent in clear</a>
	* @see <a href="http://www.unixtools.com/network-security.html">Network Security</a>
	*/
	public static URL getFtpUrl(String filenameRemote, String host, String username, String password) throws IllegalArgumentException, RuntimeException {
		Check.arg().notNull(filenameRemote);
		Check.arg().notNull(host);
		Check.arg().notNull(username);
		Check.arg().notNull(password);

		try {
			return new URL("ftp://" + username + ":" + password + "@" + host + "/" + filenameRemote + ";type=i");
		}
		catch (MalformedURLException e) {
			throw new RuntimeException("At least one of the arguments caused a MalformedURLException", e);
			// do not throw the MalformedURLException since that is a checked Exception, and I would like to use this function to assign fields at their definition line
		}
	}
	
	/**
	* Writes file to url.
	* Note that some protocols (e.g. FTP) will overwrite any previously existing remote file.
	* <p>
	* @param logger is passed to {@link StreamUtil#transfer(InputStream, OutputStream, PrintWriter) StreamUtil.transfer(in, out, logger)}
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}; url == null
	* @throws IOException if an I/O problem occurs
	*/
	public static void writeFileToUrl(File file, URL url, PrintWriter logger) throws IllegalArgumentException, IOException {
		Check.arg().validFile(file);
		Check.arg().notNull(url);
		
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(file);
			out = UrlUtil.getOutputStream(url);
			StreamUtil.transfer(in, out, logger);
		}
		finally {
			StreamUtil.close(in);
			StreamUtil.close(out);
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private NetUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getSocketState() throws Exception {
			ServerSocket serverSocket = null;
			ServerSocketConnector connector = null;
			Socket clientSocket = null;
			try {
				serverSocket = new ServerSocket(0);	// use 0 to get any free local port
				
				connector = new ServerSocketConnector(serverSocket);
				Thread thread = new Thread(connector, "NetUtil.UnitTest_ServerSocketConnector");
				thread.setPriority( Thread.NORM_PRIORITY );
				thread.start();
				
					// now from this main thread, the server thread, accept the connection attempt being made in the above thread:
				clientSocket = serverSocket.accept();
				System.out.println( "Good: a sucessful socket connection was made between client & server" );
				System.out.println( "Here are the details of the socket accepted by the server:" );
				System.out.print( getSocketState(clientSocket) );
			}
			finally {
				StreamUtil.close(clientSocket);
				StreamUtil.close(serverSocket);
				if (connector != null) connector.stopWaiting();
			}
		}
		
		@Ignore("Not running because a) do not want to hard code security sensitive info inside here b) not sure how to verify that it really worked...")
		@Test public void test_getFtpUrl_writeFileToUrl() throws Exception {
			File file = new File("./<changeMe to some big file>");
			
			String filenameRemote = file.getName();
			String host = "ftp.<changeMe to some frp url>";
			String username = "<changeMe>";
			String password = "<changeMe>";
			URL url = getFtpUrl(filenameRemote, host, username, password);
			
			PrintWriter ps = new PrintWriter( new FileOutputStream( LogUtil.makeLogFile("ftpProgress.txt") ), true );	// true to autoflush so can see intermediate results
			
			writeFileToUrl(file, url, ps);
		}
		
		/** Creates a normal client Socket to {@link #serverSocket}, and then waits until {@link #stopWaiting stopWaiting} is called. */
		private static class ServerSocketConnector implements Runnable {
			
			private final ServerSocket serverSocket;
			
			/** Condition predicate for this instance's condition queue (i.e. the wait/notifyAll calls below; see "Java Concurrency in Practice" by Goetz et al p. 296ff, especially p. 299). */
			private boolean shouldWait = true;
			
			ServerSocketConnector(ServerSocket serverSocket) {
				this.serverSocket = serverSocket;
			}
			
			public void run() {
				Socket socket = null;
				try {
					socket = new Socket( serverSocket.getInetAddress(), serverSocket.getLocalPort() );
					waitTillNotified();
				}
				catch (Throwable t) {
					t.printStackTrace(System.err);
				}
				finally {
					StreamUtil.close(socket);
				}
			}
			
			private synchronized void waitTillNotified() throws InterruptedException {
				while (shouldWait) {
					this.wait();
				}
			}
			
			private synchronized void stopWaiting() {
				shouldWait = false;
				this.notifyAll();
			}
			
		}
		
	}
	
}
