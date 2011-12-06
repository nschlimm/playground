/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.net;

import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.Properties2;
import bb.util.StringUtil;
import bb.util.ThrowableUtil;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.TransportEvent;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
* Each instance stores a specific set of mail server resources (see the makeXXX factory method javadocs).
* It may then be used to send arbitrary emails using one of the send methods.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
* @see "The 2001-10-23 JDC newsletter for tips on JavaMail"
* @see <a href="http://java.sun.com/products/javamail/javadocs/index.html">javamail javadocs</a>
*/
public class Emailer implements Closeable {
	
	// -------------------- constants --------------------
	
	/** Default value for the "mail.debug" or "mail.smtp.debug" property. */
	private static final boolean mailDebug_default = false;
	
	/** Default value for {@link #parseStrictness}. */
	private static final boolean parseStrictness_default = true;
	
	/**
	* Set of all mandatory keys for the properties arg of {@link #makeForSmtpSsl}.
	* These keys are:
	* <pre><code>
		mail.transport.protocol
		mail.smtp.host
		mail.smtp.port
		username
		password
	* </code></pre>
	*/
	private static final Set<String> keysSmtpSslMandatory = new HashSet<String>( Arrays.asList(
		"mail.transport.protocol",
		"mail.smtp.host",
		"mail.smtp.port",
		"username",
		"password"
	) );
	
	/**
	* Set of all optional keys for the properties arg of {@link #makeForSmtpSsl}.
	* If not defined, default values will be supplied.
	* These keys are:
	* <pre><code>
		mail.smtp.debug
		mail.smtp.socketFactory.class
		mail.smtp.socketFactory.port
		mail.smtp.user
		parseStrictness
	* </code></pre>
	*/
	private static final Set<String> keysSmtpSslOptional = new HashSet<String>( Arrays.asList(
		"mail.smtp.debug",
		"mail.smtp.socketFactory.class",
		"mail.smtp.socketFactory.port",
		"mail.smtp.user",
		"parseStrictness"
	) );
	
	/** Default value for the "mail.smtp.socketFactory.class" property. */
	private static final String sslFactory_default = "javax.net.ssl.SSLSocketFactory";
	
	// -------------------- instance fields --------------------
	
	/**
	* Stores the from email address(es) which will be used for all emails sent by this instance.
	* <p>
	* Contract: is never blank.
	*/
	private final String from;
	
	/**
	* Specifies the boolean value that will be supplied to {@link InternetAddress#parse(String, boolean)}
	* when parsing address Strings (e.g. from, to, cc, and bcc).
	*/
	private final boolean parseStrictness;
	
	/**
	* {@link Session} used by this instance.
	* <p>
	* Contract: is never null.
	*/
	private final Session session;
	
	/**
	* {@link Transport} used by this instance.
	* <p>
	* Contract: is null until initialized by {@link #initTransport initTransport}.
	*/
	private Transport transport = null;
	
	/**
	* Set of all {@link ConnectionListener}s used by this instance.
	* <p>
	* Contract: is never null.
	*/
	private final Set<ConnectionListener> connectionListeners = new HashSet<ConnectionListener>();
	
	/**
	* Set of all {@link TransportListener}s used by this instance.
	* <p>
	* Contract: is never null.
	*/
	private final Set<TransportListener> transportListeners = new HashSet<TransportListener>();
	
	// -------------------- makeXXX factory methods and constructor --------------------
	
	/**
	* Returns <code>{@link #make(Properties2) make}( new {@link Properties2#Properties2(File[]) Properties2}(file) )</code>.
	* <p>
	* @throws RuntimeException if Properties2 has any problem parsing file
	* @throws IllegalArgumentException if the properties parsed from file is missing required keys/values, or contains illegal keys
	*/
	public static Emailer make(File file) throws RuntimeException, IllegalArgumentException {
		// file checked by Properties2 below
		
		return makeForSmtpSsl( new Properties2(file) );
	}
	
	/**
	* Inspects the value of the "mail.transport.protocol" key in properties (which must be defined),
	* and then returns the result of calling the appropriate makeXXX method (e.g. {@link #makeForSmtpSsl(Properties2) makeForSmtpSsl}).
	* <p>
	* <i>It is highly recommended that users call this method instead of one of the protocol specific makeXXX methods,
	* because then they can change their email properties files to new protocols, if needed, and not have to change their code.</i>
	* <p>
	* @throws IllegalArgumentException if properties is null, is missing required keys/values, or contains illegal keys
	*/
	public static Emailer make(Properties2 properties) throws IllegalArgumentException {
		Check.arg().notNull(properties);
		Check.arg().isTrue( properties.containsKey("mail.transport.protocol") );
		
		if (properties.getProperty("mail.transport.protocol").equals("smtp")) {
			return makeForSmtpSsl(properties);
		}
		else {
			throw new IllegalArgumentException("cannot handle mail.transport.protocol = " + properties.getProperty("mail.transport.protocol"));
		}
	}
	
// +++ is there ever a need for a plain makeForSmtp method (i.e. one that does NOT use SSL)?
// The current code below enforces the use of SSL.
// It could fairly eqasily be modified to not require SSL, but have not done this because do not have access to a non-SSL email server which could test against.
	
	/**
	* Creates a new instance that is configured to use SMTP with SSL.
	* <p>
	* @throws IllegalArgumentException if properties is null,
	* fails to have keys for every element of {@link #keysSmtpSslMandatory},
	* contains illegal keys besides those in keysSmtpSslMandatory and {@link #keysSmtpSslOptional},
	* or its value for the key <code>mail.transport.protocol</code> is not <code>smtp</code>
	
	<!--
	Programmer notes:
	
	--javamail/smtp/ssl in general:
		http://java.sun.com/products/javamail/SSLNOTES.txt
		http://www.javaworld.com/javatips/jw-javatip115.html
	
	--javamail and gmail:
		http://www.jscape.com/articles/sending_email_smtp_ssl_gmail.html
		http://g4j.sourceforge.net/
		http://gavamail.sourceforge.net/
	
	--code examples that influenced this method:
		http://forums.sun.com/thread.jspa?threadID=5267916
		http://forums.java.sun.com/thread.jspa?threadID=591321&messageID=3486033
			see the post by gary_mcm
	
	--list of all properties that can set for javamail's SMTP:
		http://java.sun.com/products/javamail/javadocs/com/sun/mail/smtp/package-summary.html
		
	--concerning the starttls command:
		http://www.gordano.com/kb.htm?q=1450
		http://www.technoids.org/wwstarttls.html
		
		--TLS is an evolution of SSL (in fact, it is sometimes called SSL 3.1)
			http://mrcorp.infosecwriters.com/Final-Pape-published.htm
			http://www.intellireach.com/products/MP_Secure_Messaging.htm
	-->
	*/
	public static Emailer makeForSmtpSsl(Properties2 properties) throws IllegalArgumentException {
		Check.arg().notNull(properties);
			// confirm that all mandatory keys are present, and that any remaining keys are the optional ones:
		Set<String> keys = new HashSet<String>( properties.keySet() );	// CRITICAL: have to create a new Set to hold the keys, since will be mutating this below and do not want to affect properties
		Check.arg().isTrue( keys.containsAll(keysSmtpSslMandatory) );
		keys.removeAll(keysSmtpSslMandatory);
		keys.removeAll(keysSmtpSslOptional);
		if (keys.size() > 0) throw new IllegalArgumentException("properties contains the following illegal keys: " + StringUtil.toString(keys, ", "));
			// check the protocol:
		Check.arg().isTrue( properties.getProperty("mail.transport.protocol").equals("smtp") );
		
			// build the from email address:
		final String username = properties.getProperty("username");
		String mailHost = properties.getProperty("mail.smtp.host");
		String from = username + "@" + mailHost;
		
			// ensure values for optional keys:
		properties.putIfAbsent( "mail.smtp.debug", Boolean.toString(mailDebug_default) );
		properties.putIfAbsent( "mail.smtp.socketFactory.class", sslFactory_default );
		properties.putIfAbsent( "mail.smtp.socketFactory.port", properties.get("mail.smtp.port") );
		properties.putIfAbsent( "mail.smtp.user", from );
		
			// add certain key/values to properties that are never to be user defined but only defined here:
		properties.put( "mail.smtp.auth", "true" );
		properties.put( "mail.smtp.socketFactory.fallback", "false" );	// prevents JavaMail from ever fallback to a default (nonsecure) connection
		properties.put( "mail.smtp.starttls.enable", "true" );
		
			// extract parseStrictness:
		boolean parseStrictness = properties.getBoolean( "parseStrictness", parseStrictness_default );
		
			// create the Session:
		final String password = properties.getProperty("password");
		
		Authenticator authenticator = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication( username, password );
			}
		};
		
		Session session = Session.getInstance( properties.toProperties(), authenticator );
		
			// can finally construct the result:
		return new Emailer(from, parseStrictness, session);
	}
	
/*
Where did I get this code from--one of the "demo" programs in the JavaMail download bundle?
Would this ever work, since it seems to have no authentication?
MAYBE it would if the email server was on a LAN that allowed local apps to send email to it without any authentication?

	public static Emailer makeFor???(String mailHost, String from) throws IllegalArgumentException {
		return makeFor???(mailTransportProtocol_default, mailHost, from, mailDebug_default, parseStrictness_default);
	}
	
	public static Emailer makeFor???(String from, boolean parseStrictness, String mailTransportProtocol, String mailHost, boolean mailDebug) throws IllegalArgumentException {
		// mailTransportProtocol and mailHost will be checked by properties.put below
		EmailUtil.checkHeaderFieldBody(from, "from");
		
		Properties2 properties = new Properties2();
		properties.put( "mail.transport.protocol", mailTransportProtocol );
		properties.put( "mail.host", mailHost );
		properties.put( "mail.debug", Boolean.toString(mailDebug) );
		session = Session.getInstance(properties.toProperties());
		
			// can finally construct the result:
		return new Emailer(from, parseStrictness, session);
	}
*/
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if from fails {@link EmailUtil#checkHeaderFieldBody EmailUtil.checkHeaderFieldBody};
	* session is null
	*/
	private Emailer(String from, boolean parseStrictness, Session session) throws IllegalArgumentException {
		EmailUtil.checkHeaderFieldBody(from, "from");
		Check.arg().notNull(session);
		
		this.from = from;
		this.parseStrictness = parseStrictness;
		this.session = session;
	}
	
	// -------------------- initTransport --------------------
	
	/**
	* Closes {@link #transport} if it currently exists.
	* Then recreates it from {@link #session}, adds all listeners ({@link #connectionListeners} and {@link #transportListeners}), and connects it.
	* <p>
	* This method is normally called by {@link #send(String, String, String, String, String, Map)} in order to lazy initialize transport.
	* However, it may be the case that transport has become invalid
	* (e.g. the mail server may have severed the connection after a while;
	* see <a href="http://forums.sun.com/thread.jspa?threadID=5414559&tstart=0">this forum posting</a>).
	* So, this method was made public to let users perform error recovery.
	* <p>
	* @throws MessagingException
	* @throws NoSuchProviderException
	*/
	public void initTransport() throws MessagingException, NoSuchProviderException {
		//StreamUtil.close( transport );	// decided NOT to use this, even tho more convenient than the code below, because it will log any Throwable at the SEVERE level whereas I want it at INFO since am seeing this error so often with gmail it is not worth drawing extra attention
		try {
			if (transport != null) transport.close();
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.INFO, "Emailer", "initTransport", "caught an unexpected Throwable", t);
		}
		
		transport = session.getTransport();
		
		for (ConnectionListener cListener : connectionListeners) {
			transport.addConnectionListener(cListener);
		}
		for (TransportListener tListener : transportListeners) {
			transport.addTransportListener(tListener);
		}
		
		transport.connect();
	}
	
	// -------------------- XXXListener --------------------
	
	/**
	* Attempts to add connectionListener to an internal Set of {@link ConnectionListener}s.
	* <p>
	* @return true if connectionListener was added by this call, false if was already present
	* @throws IllegalArgumentException if connectionListener == null
	*/
	public boolean addConnectionListener(ConnectionListener connectionListener) throws IllegalArgumentException {
		Check.arg().notNull(connectionListener);
		
		boolean added = connectionListeners.add(connectionListener);
		if (added && (transport != null)) transport.addConnectionListener(connectionListener);
		return added;
	}
	
	/**
	* Attempts to remove connectionListener from an internal Set of {@link ConnectionListener}s.
	* <p>
	* @return true if connectionListener was removed by this call, false if was not present
	* @throws IllegalArgumentException if connectionListener == null
	*/
	public boolean removeConnectionListener(ConnectionListener connectionListener) throws IllegalArgumentException {
		Check.arg().notNull(connectionListener);
		
		boolean removed = connectionListeners.remove(connectionListener);
		if (removed && (transport != null)) transport.removeConnectionListener(connectionListener);
		return removed;
	}
	
	/**
	* Attempts to add transportListener to an internal Set of {@link TransportListener}s.
	* <p>
	* @return true if transportListener was added by this call, false if was already present
	* @throws IllegalArgumentException if transportListener == null
	*/
	public boolean addTransportListener(TransportListener transportListener) throws IllegalArgumentException {
		Check.arg().notNull(transportListener);
		
		boolean added = transportListeners.add(transportListener);
		if (added && (transport != null)) transport.addTransportListener(transportListener);
		return added;
	}
	
	/**
	* Attempts to remove transportListener from an internal Set of {@link TransportListener}s.
	* <p>
	* @return true if transportListener was removed by this call, false if was not present
	* @throws IllegalArgumentException if transportListener == null
	*/
	public boolean removeTransportListener(TransportListener transportListener) throws IllegalArgumentException {
		Check.arg().notNull(transportListener);
		
		boolean removed = transportListeners.remove(transportListener);
		if (removed && (transport != null)) transport.removeTransportListener(transportListener);
		return removed;
	}
	
	// -------------------- send --------------------
	
	/**
	* Convenience method that calls the more general <code>send</code> method with null values for
	* the <code>cc</code> and <code>bcc</code> arguments.
	*/
	public final void send(String subject, String messageBody, String to) throws IllegalArgumentException, MessagingException {
		send(subject, messageBody, to, null, null, null);
	}
	
	/**
	* This method sends an email using the info in the arguments.
	* <p>
	* <b>Warning</b>: the mail server may override the <code>mailhost</code> value to prevent spoofing?
	* <p>
	* @throws IllegalArgumentException if some arg is illegal
	* @throws MessagingException if some messaging error occurs
	* @see "The program <JavaMail install directory>/demo/msgsend.java"
	* @see <a href="http://www.stopspam.org/email/headers.html">All About Email Headers</a>
	*/
	public final void send(String subject, String messageBody, String to, String cc, String bcc, Map<String, String> headers) throws IllegalArgumentException, MessagingException {
		if ( StringUtil.isBlank(subject) && StringUtil.isBlank(messageBody) ) throw new IllegalArgumentException("supplied blank values for both subject and messageBody");
		if ( StringUtil.isBlank(to) && StringUtil.isBlank(cc) && StringUtil.isBlank(bcc) ) throw new IllegalArgumentException("supplied blank values for all of to, cc, and bcc");
		EmailUtil.checkHeaderFieldBody(to, "to");
		EmailUtil.checkHeaderFieldBody(cc, "cc");
		EmailUtil.checkHeaderFieldBody(bcc, "bcc");
				
		MimeMessage message = new MimeMessage(session);
		message.setSubject(subject);
		message.setText(messageBody);
		if (from != null)
			message.setFrom( new InternetAddress(from, parseStrictness) );
		if (to != null)
			message.setRecipients( Message.RecipientType.TO, InternetAddress.parse(to, parseStrictness) );
		if (cc != null)
			message.setRecipients( Message.RecipientType.CC, InternetAddress.parse(cc, parseStrictness) );
		if (bcc != null)
			message.setRecipients( Message.RecipientType.BCC, InternetAddress.parse(bcc, parseStrictness) );
		if (headers != null) {
			for (Map.Entry<String,String> entry : headers.entrySet()) {
				message.setHeader( entry.getKey(), entry.getValue() );
			}
		}
		message.setSentDate( new Date() );
		
		if (transport == null) initTransport();
		transport.sendMessage(message, message.getAllRecipients());
	}
// +++ add support for sending file attachments; see http://www-106.ibm.com/developerworks/forums/dw_thread.jsp?forum=262&thread=63790&cat=11
// +++ further action that could do: save a record of the email sent; see the msgsend.java sample code from Sun
	
	// -------------------- close --------------------
	
	/**
	* Closes all messaging resources currently being used by this instance.
	* <p>
	* @throws RuntimeException (or some subclass) if any problem occurs
	*/
	@Override public final void close() throws RuntimeException {
		try {
			if (transport != null) transport.close();
			
			try {
				Thread.sleep(10);	// must sleep for a short amount of time if want a better chance of any ConnectionListeners receiving the close event
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();	// never swallow this; see http://www-128.ibm.com/developerworks/java/library/j-jtp05236.html
			}
			
			for (ConnectionListener cListener : connectionListeners) {
				if (cListener instanceof Listener) ((Listener) cListener).close();
			}
			for (TransportListener tListener : transportListeners) {
				if (tListener instanceof Listener) ((Listener) tListener).close();
			}
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- Listener (static inner class) --------------------
	
	private static class Listener implements Closeable, ConnectionListener, TransportListener {
		
		private final Logger logger;
		
		public Listener(Logger logger) throws IllegalArgumentException {
			Check.arg().notNull(logger);
			
			this.logger = logger;
		}
		
		public void opened(ConnectionEvent ce) { handleConnectionEvent(ce); }
		public void disconnected(ConnectionEvent ce) { handleConnectionEvent(ce); }
		public void closed(ConnectionEvent ce) { handleConnectionEvent(ce); }
		private void handleConnectionEvent(ConnectionEvent ce) {
			logger.log( Level.INFO, EmailUtil.eventToString(ce) );
		}
		
		public void messageDelivered(TransportEvent te) { handleTransportEvent(te); }
		public void messageNotDelivered(TransportEvent te) { handleTransportEvent(te); }
		public void messagePartiallyDelivered(TransportEvent te) { handleTransportEvent(te); }
		private void handleTransportEvent(TransportEvent te) {
			try {
				logger.log( Level.FINER, EmailUtil.eventToString(te) );
			}
			catch (Throwable t) {
				logger.logp(Level.SEVERE, "Emailer.Listener", "handleTransportEvent", "Caught an unexpected Throwable", t);
			}
		}
		
		@Override public void close() {
			LogUtil.close( logger );
// +++ when sun gets back to me on my bug report, let them know that i solved it if use close instead of flush; but why does logger not have this method?
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final File fileProperties = new File("../../../../../ellipticGroup/KEEP_PRIVATE_SENSITIVE_INFO/gmail.txt");
		
		private static final String validAddresses = "ellipticgroupinc@gmail.com";
		private static final String imaginaryAddresses = "32423t44t@1gi9erg389.com, 32jksad8957h@fljhjsnk.com";
		private static final String malformedAddresses = "\u234389.com, \u73542@fljhjsnk.com";
		private static final String addressesGoodAndBad = validAddresses + ", " + imaginaryAddresses + ", " + malformedAddresses;
		
		private Emailer emailer;
		
		@Before public void setUp() throws Exception {
			emailer = make(fileProperties);
		}
		
		@After public void tearDown() throws Exception {
			StreamUtil.close(emailer);
		}
		
		@Test public void test_send_shouldWork() throws Exception {
			Logger2 logger = LogUtil.makeLogger2( Emailer.UnitTest.class, "test_send_shouldWork" );
			Listener listener = new Listener(logger);
			emailer.addConnectionListener( listener );
			emailer.addTransportListener( listener );
			emailer.send("UnitTest message#1", "Sent by test_send_shouldWork ...", validAddresses);
			Thread.sleep( 30 * 1000 );	// pause for a bit to ensure that the email system has enought time to send those emails (this is usually only needed if are executing just this class, since if are eecuting all the tests classes that should suffice)
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_send_shouldFail() throws Exception {
			Logger2 logger = LogUtil.makeLogger2( Emailer.UnitTest.class, "test_send_shouldFail" );
			Listener listener = new Listener(logger);
			emailer.addConnectionListener( listener );
			emailer.addTransportListener( listener );
			emailer.send("UnitTest message#2", "Sent by test_send_shouldFail ...", addressesGoodAndBad);
		}
		
	}
	
}
