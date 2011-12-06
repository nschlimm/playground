/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.net;

import bb.util.Check;
import bb.util.StringUtil;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.TransportEvent;

/**
* Provides static email utility methods.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
* @see "The 2001/October/23 JDC newsletter for tips on JavaMail"
* @see <a href="http://java.sun.com/products/javamail/javadocs/">http://java.sun.com/products/javamail/javadocs/</a>
*/
public final class EmailUtil {
	
	// -------------------- api --------------------
	
	/**
	* Checks whether or not fieldBody is a valid email field body
	* as specified by <a href="http://www.ietf.org/rfc/rfc822.txt">RFC #822</a> (especially section 3.1.2. STRUCTURE OF HEADER FIELDS).
	* <p>
	* Special case: if fieldBody is null, the method immediately returns, <i>so null is an acceptable value</i>.
	* <p>
	* <b>Warning:</b> unfolding of whitespace (see the RFC #822 spec) is not performed by this method;
	* the caller should do this before calling calling this method.
	* <p>
	* @throws IllegalArgumentException if any syntax error is found in fieldBody
	*/
	public static void checkHeaderFieldBody(String fieldBody, String fieldBodyLabel) throws IllegalArgumentException {
		if (fieldBody == null) return;
		
		if (!StringUtil.isAllAsciiChars(fieldBody)) throw new IllegalArgumentException(fieldBodyLabel + " = " + fieldBody + " contains at least 1 non-US ASCII char");
		if (fieldBody.contains("\n")) throw new IllegalArgumentException(fieldBodyLabel + " = " + fieldBody + " contains the new line ('\\n') char");
		if (fieldBody.contains("\r")) throw new IllegalArgumentException(fieldBodyLabel + " = " + fieldBody + " contains the carriage return ('\\r') char");
	}
	
	/**
	* Converts the information in a <code>MessagingException</code> to a more readable <code>String</code> form.
	* <p>
	* @throws IllegalArgumentException if messagingException == null
	* @see "The program <JavaMail install directory>/demo/msgsendsample.java"
	*/
	public static String getExceptionInfo(MessagingException messagingException) throws IllegalArgumentException {
		Check.arg().notNull(messagingException);
		
		CharArrayWriter caw = new CharArrayWriter(256);
		PrintWriter out = new PrintWriter(caw);
		
		messagingException.printStackTrace(out);
			// Note: printStackTrace above will print out all the nested Exceptions as well,
			// but now we go thru them to find any SendFailedExceptions and print out more detailed info:
		int count = 0;
		for (Exception e = messagingException; e instanceof MessagingException; e = ((MessagingException) e).getNextException()) {
			if (e instanceof SendFailedException) {
				out.println();
				out.println("Address status for SendFailedException #" + (count++) + " present in the above stack trace:");
				out.println( getAddressesStatus( (SendFailedException) e ) );
			}
		}
		
		out.flush();
		return caw.toString();
	}
	
	/**
	* Returns a <code>String</code> which contains the invalid, valid unsent, and valid sent
	* addresses that are contained inside the sendFailedException argument.
	* <p>
	* @throws IllegalArgumentException if sendFailedException == null
	* @see "The program <JavaMail install directory>/demo/msgsendsample.java"
	*/
	public static String getAddressesStatus(SendFailedException sendFailedException) throws IllegalArgumentException {
		Check.arg().notNull(sendFailedException);
		
		StringBuilder sb = new StringBuilder(256);
		appendAddresses("Invalid Address(es)", sendFailedException.getInvalidAddresses(), sb);
		appendAddresses("ValidUnsent Address(es)", sendFailedException.getValidUnsentAddresses(), sb);
		appendAddresses("ValidSent Address(es)", sendFailedException.getValidSentAddresses(), sb);
		return sb.toString();
	}
	
	/**
	* Returns a <code>String</code> representation of the information inside connectionEvent.
	* The result does <i>not</i> end in a new line ('\n') char, so if the result is printed, a printls instead of print call should be made.
	* <p>
	* @throws IllegalArgumentException if connectionEvent == null
	* @throws IllegalStateException if an unsupported event type is encountered
	*/
	public static String eventToString(ConnectionEvent connectionEvent) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(connectionEvent);
		
		StringBuilder sb = new StringBuilder(256);
		sb.append( "ConnectionEvent: " );
		switch (connectionEvent.getType()) {
			case ConnectionEvent.CLOSED:
				sb.append( "Connection closed" );
				break;
			case ConnectionEvent.DISCONNECTED:
				sb.append( "Connection disconnected" );
				break;
			case ConnectionEvent.OPENED:
				sb.append( "Connection opened" );
				break;
			default:
				throw new IllegalStateException("connectionEvent.getType() = " + connectionEvent.getType() + " is an unsupported value");
		}
		return sb.toString();
	}
	
	/**
	* Returns a <code>String</code> representation of the information inside transportEvent.
	* The result does <i>not</i> end in a new line ('\n') char, so if the result is printed, a printls instead of print call should be made.
	* <p>
	* @throws IllegalArgumentException if transportEvent == null
	* @throws IllegalStateException if an unsupported event type is encountered
	* @throws MessagingException if some error in processing transportEvent occurs
	*/
	public static String eventToString(TransportEvent transportEvent) throws IllegalArgumentException, IllegalStateException, MessagingException {
		Check.arg().notNull(transportEvent);
		
		StringBuilder sb = new StringBuilder(256);
		sb.append( "TransportEvent: " );
		switch (transportEvent.getType()) {
			case TransportEvent.MESSAGE_DELIVERED:
				sb.append( "Message delivered\n" );
				break;
			case TransportEvent.MESSAGE_NOT_DELIVERED:
				sb.append( "Message not delivered\n" );
				break;
			case TransportEvent.MESSAGE_PARTIALLY_DELIVERED:
				sb.append( "Message partially delivered\n" );
				break;
			default:
				throw new IllegalStateException("transportEvent.getType() = " + transportEvent.getType() + " is an unsupported value");
		}
		appendAddresses("From Address(es)", transportEvent.getMessage().getFrom(), sb);
		appendAddresses("Invalid Address(es)", transportEvent.getInvalidAddresses(), sb);
		appendAddresses("ValidUnsent Address(es)", transportEvent.getValidUnsentAddresses(), sb);
		appendAddresses("ValidSent Address(es)", transportEvent.getValidSentAddresses(), sb);
		sb.append( "Subject: " ).append( transportEvent.getMessage().getSubject() );
		return sb.toString();
	}
	
	private static void appendAddresses(String label, Address[] addresses, StringBuilder sb) {
		if ( (addresses != null) && (addresses.length > 0) ) {
			sb.append(label).append(": ");
			for (int i = 0; i < addresses.length; i++) {
				if (i > 0) sb.append(", ");
				sb.append( addresses[i] );
			}
			sb.append('\n');
		}
		else {
			//sb.append("[There were no ").append( label ).append("]\n");
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private EmailUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None--see Emailer.java
	
}
