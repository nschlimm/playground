/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.io;

import bb.gui.DialogInputSecure;
import bb.util.Check;
import bb.util.DateUtil;
import bb.util.Execute;
import bb.util.Properties2;
import bb.util.StringUtil;
import bb.util.logging.LogUtil;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import org.junit.Assert;
import org.junit.Test;

/**
* This class provides static utility methods for encryption.
* A {@link #main main} allows this program to also be used as a standalone command line application,
* especially useful for batch encryption and decryption operations (e.g. as part of a nightly backup process).
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see <a href = "http://www.rsasecurity.com/products/bsafe/whitepapers/Article3-PBE.pdf">RSA paper on PBE</a>
* @see <a href = "http://developers.slashdot.org/developers/04/09/01/1517242.shtml?tid=172&tid=8&tid=218">Many hash functions like MD5 and SHA0 are now compromised</a>
* @see <a href = "http://www.ftponline.com/javapro/2004_03/online/security_kjones_03_31_04/default_pf.aspx">Java for Symmetric Cryptography</a>
* @see <a href="http://java.sun.com/j2se/1.4.2/docs/guide/security/jce/JCERefGuide.html">JCE documentation</a>
* @see <a href="http://www.bouncycastle.org/specifications.html">Bouncy Castle</a>
* @see <a href="http://forum.java.sun.com/thread.jsp?forum=9&thread=542790">What is best algorithm for PBE? Does 3DES still cut it? AES avail?</a>
* @see <a href="http://forums.sun.com/thread.jspa?threadID=490728">The DEFINITIVE thread on secure password entry (console, gui, code reviews)</a>
* @see "bbLibrary/doc/encryptionNotes.txt"
* @author Brent Boyer
*/
public final class EncryptUtil {
	
	// -------------------- encryption constants --------------------
	
	/**
	* Stores a suggested encryption algorithm which classes that do not want to think about details can simply use.
	* <p>
	* Contract: is never {@link StringUtil#isBlank blank}.
	*/
	public static final String encryptionAlgorithm_default = "PBEWithMD5AndTripleDES";	// NEED unlimited strength policy files installed in order to use this; see http://java.sun.com/products/jce/index-14.html#UnlimitedDownload
// +++ above is BAD: MD5 is now cracked (http://developers.slashdot.org/developers/04/09/01/1517242.shtml?tid=172&tid=8&tid=218), need sha-512, and TripleDES is very slow and less secure than AES
//	public static final String encryptionAlgorithm_default = "PBEWithMD5AndDES";	// this is really weak, but will work if only have the default "strong" policy files
	// Note: can use different algorithms for key & cipher generation?  See Reply 7 of 9 of http://forum.java.sun.com/thread.jsp?forum=9&thread=277910
	
	private static final String secureRandomAlgorithm_default = "SHA1PRNG";
	
	// -------------------- switch constants --------------------
	
		// high level:
	private static final String fileInput_key = "-fileInput";
	private static final String fileOutput_key = "-fileOutput";
	private static final String operation_key = "-operation";
	
		//algorithm related:
	private static final String algorithm_key = "-algorithm";
	private static final String fileSaltSource_key = "-fileSaltSource";
	private static final String fileSaltTarget_key = "-fileSaltTarget";
	private static final String fileIterationCount_key = "-fileIterationCount";
	
		// password:
	private static final String passwordInstruction_key = "-passwordInstruction";
	private static final String password_key = "-password";
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main}. */
	private static final List<String> keysLegal = Arrays.asList(
		fileInput_key, fileOutput_key, operation_key,
		algorithm_key, fileSaltSource_key, fileSaltTarget_key, fileIterationCount_key,
		passwordInstruction_key, password_key
	);
	
		// values for the property corresponding to operation_key:
	/**
	* Specifies encryption mode.
	* <p>
	* Contract: is never {@link StringUtil#isBlank blank}.
	*/
	public static final String encryptOperation = "encrypt";
	
	/**
	* Specifies decryption mode.
	* <p>
	* Contract: is never {@link StringUtil#isBlank blank}.
	*/
	public static final String decryptOperation = "decrypt";
	
	// -------------------- main --------------------
	
	/**
	* Encrypts or decrypts a target File into a destination file.
	* <p>
	* The File to be encrypted/decrypted must have its path specified on the command line in the usual switch format
	* as <code>-fileInput <i>insertPathHere</i></code>.
	* The destination file must have its path specified on the command line in the usual switch format
	* as <code>-fileOutput <i>insertPathHere</i></code>.
	* <p>
	* The operation to perform (encryption or decryption) must be specified on the command line in the usual switch format
	* as <code>-operation encrypt</code> or <code>-operation decrypt</code>.
	* <p>
	* The Password Based Encryption algorithm name must be specified on the command line in the usual switch format
	* as <code>-algorithm <i>insertAlgorithmNameHere</i></code>.
	* <p>
	* The salt (a random byte[] which is used as part of Password Based Encryption) must be specified in one of the following ways:
	* <ol>
	*  <li>
	*		the command line switch <code>-fileSaltSource <i>insertPathHere</i></code> means that the random salt bytes
	*		have previously been generated and stored in the specified file; they are to be read in and used here;
	*		usually this option is used for decryption, with the salt file having been generated as part of encryption
	*  </li>
	*  <li>
	*		the command line switch <code>-fileSaltTarget <i>insertPathHere</i></code> means that the random salt bytes
	*		need to be generated and then stored in the specified file before being used here;
	*		usually this option is used as part of encryption--the salt must be saved in a file for future decryption
	*  </li>
	* </ol>
	* <i>Note that there is no default salt stored in this class; this is absolutely required to thwart dictionary attacks.
	* Instead, the above options strongly encourage new salt to be used for each round of encryption.</i>
	* <p>
	* The iteration count (also part of Password Based Encryption) must be stored in a file whose path
	* is specified on the command line in the usual switch format as <code>-fileIterationCount <i>insertPathHere</i></code>.
	* This file should be a text file which <i>only</i> contains the digits of the iteration count
	* (e.g. "1000000" is valid contents; "1000000\n" is not as it contains a line end).
	* Unlike the salt, it is not critical that the iteration count change, just that it be a sufficiently high value (e.g. > 1,000).
	* <p>
	* A password may optionally be specified on the command line in the usual switch format
	* as <code>-password <i>insertPasswordHere</i></code>.
	* <p>
	* If the password is not specified on the command line, then the user will be prompted to enter one on the console or in a GUI dialog.
	* In this case, there should be some prompt text given to the user.
	* This is specified on the command line in the usual switch format
	* as <code>-passwordInstruction <i>insertPasswordInstructionsHere</i></code>.
	* <p>
	* <b>Warning:</b> if supply the password via the command line in this manner, there is a loss in security
	* (the password will be stored at one point in a String, not a char[], and so can never be blanked out after use).
	* Supplying the password via command line is only advised when doing batch encryptions from a trusted machine
	* (e.g. as part of an automated backed process).
	* <p>
	* If this method is this Java process's entry point (i.e. first <code>main</code> method),
	* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
	* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
	* Otherwise, this method returns and leaves the JVM running.
	*/
	public static void main(final String[] args) {
		Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
			Check.arg().notEmpty(args);
			
			Properties2 switches = new Properties2(args).checkKeys(keysLegal);
			
			PBEKeySpec pbeKeySpec = null;
			InputStream in = null;
			OutputStream out = null;
			try {
				File fileInput = switches.getFile(fileInput_key);
				File fileOutput = switches.getFile(fileOutput_key);
				int operation = extractOperation(switches);
				String algorithm = switches.getProperty(algorithm_key);
				PBEParameterSpec pbeParamSpec = extractPbeParameterSpec(switches);
				
				LogUtil.getLogger2().logp(Level.INFO, "EncryptUtil", "main", "Key & Cipher generation start: " + DateUtil.getTimeStamp());
				pbeKeySpec = extractPbeKeySpec(switches, pbeParamSpec);
				Cipher cipher = getPbeCipher(algorithm, pbeKeySpec, pbeParamSpec, operation);
				LogUtil.getLogger2().logp(Level.INFO, "EncryptUtil", "main", "Key & Cipher generation end: " + DateUtil.getTimeStamp());
				
				in = new FileInputStream(fileInput);
				out = new CipherOutputStream( new FileOutputStream(fileOutput), cipher );
				
				LogUtil.getLogger2().logp(Level.INFO, "EncryptUtil", "main", "Crypto start: " + DateUtil.getTimeStamp());
				StreamUtil.transfer(in, out);
				LogUtil.getLogger2().logp(Level.INFO, "EncryptUtil", "main", "Crypto end: " + DateUtil.getTimeStamp());
			}
			finally {
				if (pbeKeySpec != null) pbeKeySpec.clearPassword();
				StreamUtil.close(in);
				StreamUtil.close(out);
			}
			
			return null;
		} } );
	}
	
	private static int extractOperation(Properties2 switches) throws IllegalArgumentException {
		String operation = switches.getProperty(operation_key);
		if (encryptOperation.equals(operation)) return Cipher.ENCRYPT_MODE;
		else if (decryptOperation.equals(operation)) return Cipher.DECRYPT_MODE;
		else throw new IllegalArgumentException("the " + operation_key + " value " + operation + " is unsupported");
	}
	
	private static PBEParameterSpec extractPbeParameterSpec(Properties2 switches) throws IllegalArgumentException, InterruptedException, IllegalStateException, IOException, NoSuchAlgorithmException {
		byte[] salt;
		if (switches.containsKey(fileSaltSource_key)) {
			File file = new File( switches.getProperty(fileSaltSource_key) );
			salt = FileUtil.readBytes(file);
		}
		else if (switches.containsKey(fileSaltTarget_key)) {
			salt = randomBytes(8);	// Sun's JCE seems to require exactly 8 bytes, at least for the PBEWithMD5AndTripleDES algorithm...
			File file = new File( switches.getProperty(fileSaltTarget_key) );
			FileUtil.writeBytes(salt, file, false);	// false means we overwrite the file
		}
		else
			throw new IllegalArgumentException("failed to specify the salt file (\"" + fileSaltSource_key + "\" or \"" + fileSaltTarget_key + "\")");
		
		File file = switches.getFile(fileIterationCount_key);
		int iterationCount = Integer.parseInt( FileUtil.readString(file) );
// +++ add a check that iterationCount is sufficiently large?  need a minimum of 1000 (see http://www.rsasecurity.com/products/bsafe/whitepapers/Article3-PBE.pdf)
		
		return new PBEParameterSpec(salt, iterationCount);
	}
	
	private static PBEKeySpec extractPbeKeySpec(Properties2 switches, PBEParameterSpec pbeParamSpec) throws IOException, InterruptedException, InvocationTargetException {
		char[] password = null;
		try {
			if (switches.containsKey(password_key)) {
				password = switches.getProperty(password_key).toCharArray();	// WARNING: there is no way to null out the original password String here, so this is a bit of a security loss (see main javadoc)
				if (!isValidPassword(password)) throw new IllegalArgumentException("the supplied password (value suppressed for security) is invalid");
			}
			else {
				String passwordInstruction = switches.get(passwordInstruction_key);
				if (passwordInstruction == null) passwordInstruction = "Enter some piece of sensitive information (note: character echo is suppressed)";
				passwordInstruction += ": ";
				
				try {	// use the Console first, which should be available on more systems:
					password = readConsoleSecure(passwordInstruction);
				}
				catch (Exception e) {	// if the Console fails (e.g. if there is no shell window, like if you use javaw), then try a gui
					password = DialogInputSecure.getInputSecure(null, "Password entry", true, "Dialog for entering information securely.", passwordInstruction, 32);
				}
				
				if (!isValidPassword(password)) throw new IllegalStateException("the entered password (value suppressed for security) is invalid");
			}
			
			return new PBEKeySpec(password, pbeParamSpec.getSalt(), pbeParamSpec.getIterationCount());
		}
		finally {
			eraseChars(password);	// PBEKeySpec will store a clone of password internally, so must clear our local version before returning
		}
	}
	
	/**
	* Checks if the supplied password is valid.
* Current implementation (subject to change) is that only null or zero-length passwords are invalid.
* Should add stronger checks in the future (e.g. R5),
* but problem is that want this program to be able to decrypt files from any source,
* and the encrypting party may have different password policies in place.
* @see <a href="http://www.cl.cam.ac.uk/techreports/UCAM-CL-TR-500.pdf">JX Yan, A Blackwell, RJ Anderson, A Grant, The Memorability and Security of Passwords—Some Empirical Results</a>
	*/
	private static boolean isValidPassword(char[] password) {
		if ((password == null) || (password.length == 0)) return false;
		return true;
	}
	
	// -------------------- getCipher --------------------
	
	/**
	* Returns a Password Based Encryption (PBE) Cipher.
	* <p>
	* The Cipher is initialized to algorithm and mode before being returned.
	* <p>
	* This method never returns null.
	* <p>
	* @throws Exception (many varieties) if any problem occurs
	* @see <a href="http://java.sun.com/j2se/1.4.2/docs/guide/security/jce/JCERefGuide.html#PBEEx">Password based encryption code examples from JCE documentation</a>
	*/
	public static Cipher getPbeCipher(String algorithm, PBEKeySpec pbeKeySpec, PBEParameterSpec pbeParamSpec, int mode) throws Exception {
		Check.arg().notNull(algorithm);
		Check.arg().notNull(pbeKeySpec);
		Check.arg().notNull(pbeParamSpec);
		
		SecretKey pbeKey = SecretKeyFactory.getInstance(algorithm).generateSecret(pbeKeySpec);
		Cipher pbeCipher = Cipher.getInstance(algorithm);
		pbeCipher.init(mode, pbeKey, pbeParamSpec);
		return pbeCipher;
	}
	
	// -------------------- readConsoleSecure, eraseChars --------------------
	
	/**
	* Reads and returns some sensitive piece of information (e.g. a password)
	* from the {@link Console} in a secure manner (the user's typing will not be echoed).
	* <p>
	* Contract: this method never returns null.
	* <p>
	* @param format a format string as described in <a href="http://java.sun.com/javase/6/docs/api/java/util/Formatter.html#syntax">Format String Syntax</a>
	* for the prompt text
	* @param args arguments referenced by the format specifiers in the format string.
	* If there are more arguments than format specifiers, the extra arguments are ignored.
	* The maximum number of arguments is limited by the maximum dimension of a Java array as defined by the Java Virtual Machine Specification.
	* @throws IllegalStateException if no Console is associated with this JVM; read no chars from the Console
	* @throws IllegalFormatException if format contains an illegal syntax,
	* a format specifier that is incompatible with the given arguments, insufficient arguments given the format string,
	* or other illegal conditions.
	* For specification of all possible formatting errors, see <a href="http://java.sun.com/javase/6/docs/api/java/util/Formatter.html#detail">this documentation</a>.
	* @throws IOError if an I/O problem occurs
	* @see <a href="http://blogs.sun.com/roller/page/alanb/20051021">java.io.Console is finally here!</a>
	* @see <a href="http://blogs.sun.com/DaveB/entry/new_improved_in_java_se1">Reading passwords without echoing their text</a>
	*/
	public static char[] readConsoleSecure(String format, Object... args) throws IllegalStateException, IllegalFormatException, IOError {
		Console console = System.console();
		if (console == null) throw new IllegalStateException("no Console is associated with this JVM");
		
		char[] chars = console.readPassword(format, args);
		if (chars == null) throw new IllegalStateException("read no chars from the Console (it reached end of stream); must mean that the Console was terminated");
		
		return chars;
	}
	
	/** If buffer is not null, fills buffer with space (' ') chars. */
	public static void eraseChars(char[] buffer) {
		if (buffer != null)  Arrays.fill(buffer, ' ');
	}
	
	// -------------------- randomBytes --------------------
	
	/**
	* Returns a cryptographically strong pseudo-random sequence of bytes as an array.
	* A new {@link SecureRandom} instance, whose internal state is completely randomized, is used for each call.
	* <p>
	* @throws IllegalArgumentException if length < 0
	* @throws NoSuchAlgorithmException if {@link #secureRandomAlgorithm_default} is unavailable
	*/
	public static byte[] randomBytes(int length) throws IllegalArgumentException, NoSuchAlgorithmException {
		Check.arg().notNegative(length);
		
		return SecureRandom.getInstance(secureRandomAlgorithm_default).generateSeed(length);
	}
	
	// -------------------- getAlgorithmsAvailable --------------------
	
	/** Returns a new String which describes all the available algorithm names for seviceName. */
	public static String getAlgorithmsAvailable(String serviceName) throws IllegalArgumentException {
		Check.arg().notNull(serviceName);
		
		StringBuilder sb = new StringBuilder();
		sb.append("Available ").append(serviceName).append(" algorithms:").append('\n');
		SortedSet<String> algorithmNameSet = new TreeSet<String>( Security.getAlgorithms(serviceName) );
		for (String algorithmName : algorithmNameSet) {
			sb.append('\t').append(algorithmName).append('\n');
		}
		return sb.toString();
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private EncryptUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_printAvailableAlgorithms() {
			System.out.println( getAlgorithmsAvailable("Cipher") );
			System.out.println( getAlgorithmsAvailable("KeyStore") );
			System.out.println( getAlgorithmsAvailable("Mac") );
			System.out.println( getAlgorithmsAvailable("MessageDigest") );
			System.out.println( getAlgorithmsAvailable("SecretKeyFactory") );
			System.out.println( getAlgorithmsAvailable("Signature") );
		}
		
		@Test public void test_main() throws Exception {
			File filePlaintext = FileUtil.createTemp( LogUtil.makeLogFile("filePlainText.txt") );
			File fileCiphertext = FileUtil.createTemp( LogUtil.makeLogFile("fileCiphertext.bytes") );
			File fileDecryptedtext = FileUtil.createTemp( LogUtil.makeLogFile("fileDecryptedtext.txt") );
			File fileSalt = FileUtil.createTemp( LogUtil.makeLogFile("fileSalt.bytes") );
			File fileIterationCount = FileUtil.createTemp( LogUtil.makeLogFile("fileIterationCount.txt") );
			
				// write sample plain text to filePlaintext
			FileUtil.writeString("sample plaintext...", filePlaintext, false);	// overwrite any existing contents
			
				// write sample iteration count to fileIterationCount
			FileUtil.writeString("1000", fileIterationCount, false);	// note that there is no line end; overwrite any existing contents
			// WARNING: in real life, use a iteration count value bigger than 1,000
			
				// encrypt filePlaintext to fileCiphertext
			System.out.println();
			main( new String[] {
				"-fileInput", filePlaintext.getPath(),
				"-fileOutput", fileCiphertext.getPath(),
				"-operation", encryptOperation,
				"-algorithm", encryptionAlgorithm_default,
				"-fileSaltTarget", fileSalt.getPath(),	// generate and store salt here
				"-fileIterationCount",  fileIterationCount.getPath(),
				"-passwordInstruction",  "Enter some password (note: character echo is suppressed)",
				"-password", "abracadabra"	// CRITICAL: must supply the password here, else user interaction will be needed, which will ruin the JUnit auto test
			} );
			
				// decrypt fileCiphertext to fileDecryptedtext
			System.out.println();
			main( new String[] {
				"-fileInput", fileCiphertext.getPath(),
				"-fileOutput", fileDecryptedtext.getPath(),
				"-operation", decryptOperation,
				"-algorithm", encryptionAlgorithm_default,
				"-fileSaltSource", fileSalt.getPath(),	// use the same salt file used in encryption
				"-fileIterationCount",  fileIterationCount.getPath(),
				"-passwordInstruction",  "Enter the SAME PASSWORD here as entered for encryption",
				"-password", "abracadabra",	// CRITICAL: must supply the password here, else user interaction will be needed, which will ruin the JUnit auto test
			} );
			
			Assert.assertTrue( FileUtil.compareContents(filePlaintext, fileDecryptedtext) == -1 );
			
// +++ confirm that can decrypt the file using a 3rd party program, especially if it is written in another language and on another platform
		}
	}
	
}
