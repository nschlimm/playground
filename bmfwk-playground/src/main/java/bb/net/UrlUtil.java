/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.net;

import bb.io.FileUtil;
import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.StringUtil;
import bb.util.ThrowableUtil;
import bb.util.TimeLength;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.text.ParseException;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods for dealing with {@link URL}s.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class UrlUtil {
	
	// -------------------- constants --------------------
	
	private static final String fileProtocol = "file";
//	private static final String ftpProtocol = "ftp";
//	private static final String gopherProtocol = "gopher";
	private static final String httpProtocol = "http";
	private static final String httpsProtocol = "https";
	private static final String mailtoProtocol = "mailto";
//	private static final String newsProtocol = "news";
	
	private static final int connectTimeout_default = (int) (5 * TimeLength.minute);
	private static final int readTimeout_default = (int) (5 * TimeLength.minute);
	
	// -------------------- getFileUrl --------------------
	
	/**
	* Returns a new URL that points to file.
	* <p>
	* @throws IllegalArgumentException if file == null
	* @throws MalformedURLException if file's path cannot be parsed into a proper URL specification
	*/
	public static URL getFileUrl(File file) throws IllegalArgumentException, MalformedURLException {
		Check.arg().notNull(file);
		
		return new URL( fileProtocol + ":" + file.getPath() );
	}
	
	// -------------------- extractUrl --------------------
	
	/**
	* First strips any quote marks from urlString via a call to removeQuotes,
	* and then returns a new URL instance based off of urlString and context.
	* <p>
	* @param urlString a String which should specify a URL
	* @param lineNumber the line number where urlString is found; used only if throw a ParseException
	* @param context the context URL; used to resolve relative URLs; may be null if is not to be specified
	* @throws IllegalArgumentException if urlString is null, or lineNumber < 0
	* @throws ParseException if a leading but no matching trailing quote mark is present, or vice versa, on urlString
	* @throws MalformedURLException if urlString cannot be parsed into a proper URL specification
	*/
	public static URL extractUrl(String urlString, int lineNumber, URL context) throws IllegalArgumentException, ParseException, MalformedURLException {
		// args checked by StringUtil.removeQuotes below
		
		urlString = StringUtil.removeQuotes( urlString, lineNumber );
		return new URL(context, urlString);
	}
	
	// -------------------- drainXXX --------------------
	
	/**
	* Returns <code>{@link StreamUtil#drain StreamUtil.drain}( {@link #getInputStream getInputStream}(url) )</code>.
	* <p>
	* @throws IllegalArgumentException if url == null
	* @throws IllegalStateException if url's inputStream turns out to hold more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	* @throws UnknownServiceException if the protocol does not support input
	*/
	public static byte[] drain(URL url) throws IllegalArgumentException, IllegalStateException, IOException, UnknownServiceException {
		return StreamUtil.drain( getInputStream(url) );	// Note: StreamUtil.drain closes the InputStream
	}
	
	/**
	* Returns <code>{@link StreamUtil#drainIntoString StreamUtil.drainIntoString}( {@link #getInputStream getInputStream}(url) )</code>.
	* <p>
	* @throws IllegalArgumentException if url == null
	* @throws IllegalStateException if url's inputStream turns out to hold more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws IOException if an I/O problem occurs
	* @throws UnknownServiceException if the protocol does not support input
	*/
	public static String drainIntoString(URL url) throws IllegalArgumentException, IllegalStateException, IOException, UnknownServiceException {
		return StreamUtil.drainIntoString( getInputStream(url) );	// Note: StreamUtil.drainIntoString (via StreamUtil.drain) closes the InputStream
	}
	
	// -------------------- getInputStream --------------------
	
	/** Returns <code>{@link #getInputStream(URL, int, int) getInputStream}( url, connectTimeout_default, readTimeout_default )</code>. */
	public static InputStream getInputStream(URL url) throws IllegalArgumentException, IOException, UnknownServiceException {
		return getInputStream( url, connectTimeout_default, readTimeout_default );
	}
	
	/**
	* Creates a URLConnection for url with the specified connect and read timeouts,
	* then opens and returns an InputStream from it.
	* <p>
	* @throws IllegalArgumentException if url == null; connectTimeout or readTimeout is < 0
	* @throws IOException if an I/O problem occurs
	* @throws UnknownServiceException if the protocol does not support input
	*/
	public static InputStream getInputStream(URL url, int connectTimeout, int readTimeout) throws IllegalArgumentException, IOException, UnknownServiceException {
		Check.arg().notNull(url);
		Check.arg().notNegative(connectTimeout);
		Check.arg().notNegative(readTimeout);
		
		URLConnection conn = url.openConnection();
		conn.setConnectTimeout( connectTimeout );	// CRITICAL: if fail to establish finite timeout here and on next line, run a grave danger out being unable to close the InputStream because the underlying socket is sometimes seen to wait forever for a remote response
		conn.setReadTimeout( readTimeout );
		conn.connect();	// hmm, this may not really be necessary, as conn.getInputStream the call below will connect it if not done already?
		return conn.getInputStream();
	}
	
	/** Returns <code>{@link #getOutputStream(URL, int, int) getOutputStream}( url, connectTimeout_default, readTimeout_default )</code>. */
	public static OutputStream getOutputStream(URL url) throws IllegalArgumentException, IOException, UnknownServiceException {
		return getOutputStream( url, connectTimeout_default, readTimeout_default );
	}
	
	/**
	* Creates a URLConnection for url with the specified connect and read timeouts,
	* then opens and returns an OutputStream from it.
	* <p>
	* @throws IllegalArgumentException if url == null; connectTimeout or readTimeout is < 0
	* @throws IOException if an I/O problem occurs
	* @throws UnknownServiceException if the protocol does not support input
	*/
	public static OutputStream getOutputStream(URL url, int connectTimeout, int readTimeout) throws IllegalArgumentException, IOException, UnknownServiceException {
		Check.arg().notNull(url);
		Check.arg().notNegative(connectTimeout);
		Check.arg().notNegative(readTimeout);
		
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		conn.setConnectTimeout( connectTimeout );	// CRITICAL: if fail to establish finite timeout here and on next line, run a grave danger out being unable to close the OutputStream because the underlying socket is sometimes seen to wait forever for a remote response
		conn.setReadTimeout( readTimeout );
		conn.connect();	// hmm, this may not really be necessary, as conn.getOutputStream the call below will connect it if not done already?
		return conn.getOutputStream();
	}
	
	// -------------------- analyseUrl and helper methods --------------------
	
	/**
	* Analyses the supplied URL. The exact analysis performed is completely
	* protocol dependent, but it typically involves trying to follow the link and see
	* if valid data is on the other end.

<!--
Note that most analyses typically begin with either "+Analysis outcome: "
or "-Analysis outcome: ". The "+" sign indicates success, while the "-" sign
indicates some kind of warning or problem. The reason why I do this is so
that you can do a search thru the output file for all the "-A" occurrences,
since those are generally the ones that you are concerned about.

In the future, need a better way of telling the user what are good and bad links.
-->

	* <p>
	* @param url the URL to analyse
	* @throws IllegalArgumentException if url == null
	* @see <a href="http://lava.net/support/urls.html">List of some protocols</a>
	* @see <a href="http://www.faqs.org/rfcs/rfc2396.html">RFC2396 Uniform Resource Identifiers (URI): Generic Syntax</a>
	*/
	public static String analyseUrl(URL url) throws IllegalArgumentException {
		Check.arg().notNull(url);
		
		String protocol = url.getProtocol();	// NOTE: hostnames and protocols of URLs are not case sensitive (http://webtips.dan.info/url.html), so the code below needs to handle this
		
		if ( fileProtocol.equalsIgnoreCase(protocol) )
			return analyseFileUrl(url);
		
		else if ( httpProtocol.equalsIgnoreCase(protocol) || httpsProtocol.equalsIgnoreCase(protocol) )
			return analyseHttpUrl(url);
// +++ is it right to handle https in the same way as http?
		
		else if ( mailtoProtocol.equalsIgnoreCase(protocol) )
			return analyseMailtoUrl(url);
		
		else
			return analyseUnsupportedUrl(url);
	}
	
	private static String analyseFileUrl(URL url) {
		try {
			URLConnection conn = url.openConnection();
			conn.connect();
			conn.getInputStream();
			//conn.getOutputStream();	// WRONG!  the file protocol does not support writing
			
			return	"+Analysis outcome: SUCCESS -- was able to open a URL connection, " +
					"and access an InputStream from the file";
		}
		catch (Throwable t) {
			return	"-Analysis outcome: INTERRUPTED -- the following Throwable was thrown during analysis " +
					"(will need to manually analyse):" + "\n" +
					ThrowableUtil.toString(t);
		}
	}
	
	private static String analyseHttpUrl(URL url) {
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			
//conn.setInstanceFollowRedirects(false);
// +++ uncomment the above line if want a stricter check in which redirects are NOT to be followed
			
			conn.connect();
			int statusCode = conn.getResponseCode();
			
			if ( (100 <= statusCode) && (statusCode <= 199) )
				return	"-Analysis outcome: INDETERMINATE -- was able to open an HTTP connection, " +
						"but received a continuation notification from the server " +
						"(http status code = " + statusCode + ")";
						
			else if ( (200 <= statusCode) && (statusCode <= 299) )
				return	"+Analysis outcome: SUCCESS -- was able to open an HTTP connection, " +
						"and received a success notification from the server " +
						"(http status code = " + statusCode + ", http response message = " + conn.getResponseMessage() + ")";
// +++ if want to see the actual data sent back, use the drainIntoString method from above
			
			else if ( (300 <= statusCode) && (statusCode <= 399) )
				return	"-Analysis outcome: WARNING -- was able to open an HTTP connection, " +
						"but received a warning notification from the server " +
						"(http status code = " + statusCode + ", http response message = " + conn.getResponseMessage() + ")";
			
			else
				return	"-Analysis outcome: FAILURE -- was able to open an HTTP connection, " +
						"but received an error notification from the server " +
						"(http status code = " + statusCode + ", http response message = " + conn.getResponseMessage() + ")";
		}
		catch (Throwable t) {
			return	"-Analysis outcome: INTERRUPTED -- the following Throwable was thrown during analysis " +
					"(will need to manually analyse):" + "\n" +
					ThrowableUtil.toString(t);
		}
	}
	
	private static String analyseMailtoUrl(URL url) {
		return	"-Analysis outcome: UNKNOWN -- no analysis of the mailto protocol is performed";
	}
	
// +++ could also report back what the getContentLength() method says?
	private static String analyseUnsupportedUrl(URL url) {
		try {
			URLConnection conn = url.openConnection();
			conn.connect();
			
			return	"-Analysis outcome: UNCLEAR -- was to open a URL connection, " +
					"but no further analysis could be done because this URL's protocol " +
					"is not currently supported";
		}
		catch (Throwable t) {
			return	"-Analysis outcome: INTERRUPTED -- the following Throwable was thrown during analysis " +
					"(will need to manually analyse):" + "\n" +
					ThrowableUtil.toString(t);
		}
	}
	
	// -------------------- description --------------------
	
	/**
	* Returns a string that describes every aspect of url.
	* <p>
	* @param url a URL
	* @throws IllegalArgumentException if url == null
	*/
	public static String description(URL url) throws IllegalArgumentException {
		Check.arg().notNull(url);
		
		return
			"Authority: " + url.getAuthority() + "\n" +
			"File: " + url.getFile() + "\n" +
			"Host: " + url.getHost() + "\n" +
			"Path: " + url.getPath() + "\n" +
			"Port: " + url.getPort() + "\n" +
			"Protocol: " + url.getProtocol() + "\n" +
			"Query: " + url.getQuery() + "\n" +
			"Ref: " + url.getRef() + "\n" +
			"UserInfo: " + url.getUserInfo() + "\n" +
			"url.toExternalForm: " + url.toExternalForm() + "\n" +
			"url.toString: " + url.toString();
//			"url.toURI: " + url.toURI();
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private UrlUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_drainXXX_getInputStream_getOutputStream() throws Exception {
				// get String from this source file using conventional techniques:
			File fileIn = new File("../src/bb/net/UrlUtil.java");
			String sFileIn = FileUtil.readString( fileIn );
			
				// confirm that the drainXXX methods read in the same data:
			URL urlIn = extractUrl("file:" + fileIn.getPath(), 0, null);	// this choice yields repeatable results
			
			String sDrain = new String( drain(urlIn) );
			Assert.assertEquals(sFileIn, sDrain);
			
			String sDrainIntoString = drainIntoString(urlIn);
			Assert.assertEquals(sFileIn, sDrainIntoString);
			
/*
this test fails because the file protocol "protocol doesn't support output"
Need a better test...

				// confirm that getOutputStream writes the same data:
			File fileOut = FileUtil.createTemp( LogUtil.makeLogFile("copyOf_UrlUtil.java") );
			
			InputStream in = null;
			OutputStream out = null;
			try {
				in = new FileInputStream(fileIn);
				URL urlOut = extractUrl("file:" + fileOut.getPath(), 0, null);	// this choice yields repeatable results
				out = getOutputStream(urlOut);
				StreamUtil.transfer(in, out);
			}
			finally {
				StreamUtil.close(in);
				StreamUtil.close(out);
			}
			
			String sFileOut = FileUtil.readString(fileOut);
			Assert.assertEquals(sFileIn, sFileOut);
*/
		}
		
		@Test public void test_extractUrl_analyseUrl() throws Exception {
			System.out.println();
			System.out.println("----------Valid URLS----------");
			
			URL url = extractUrl("http://www.apple.com", 0, null);
			doAnalysis(url, "+Analysis outcome: ");
			
			System.out.println();
			File fileRelative = new File("../src/bb/net/UrlUtil.java");
			url = extractUrl("file:" + fileRelative.getPath(), 0, null);	// Note: if want to use relative paths for the file protocol, do NOT specify the // as then would need to also specify the host and port followed by an absolute path (see example below)
			doAnalysis(url, "+Analysis outcome: ");
			
			System.out.println();
			File fileCanonical = fileRelative.getCanonicalFile();
			url = extractUrl("file:///" + fileCanonical.getPath(), 0, null );	// Note: there is a third / immediately after the first 2 /s -- this means local host and default port; we must follow this with the start of an absolute path
			doAnalysis(url, "+Analysis outcome: ");
			
			System.out.println();
			System.out.println("----------INVALID URLS----------");
			
			System.out.println();
			url = new URL("http://www.ibm.com/kjdnssdvksdvsdvsdkkkj.html");
			doAnalysis(url, "-Analysis outcome: ");
			
			System.out.println();
			url = getFileUrl( new File("./dvdsvsvdvsdvsdvsdvsd") );
			doAnalysis(url, "-Analysis outcome: ");
		}
		
		private void doAnalysis(URL url, String prefixExpected) throws Exception {
			System.out.println();
			System.out.println(url);
			String analysis = analyseUrl(url);
			System.out.println(analysis);
			Assert.assertTrue( analysis.startsWith(prefixExpected) );
		}
		
	}
	
}
