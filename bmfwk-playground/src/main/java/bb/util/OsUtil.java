/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ for the execXXX below, may also want to bear in mind
	--accessing Windows DLL with pure Java (no JNI):
		https://jna.dev.java.net/

*/

package bb.util;

import bb.io.StreamDrainer;
import bb.io.StreamDrainerStoring;
import bb.util.logging.LogUtil;
import java.io.File;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods relating to the operating system that the JVM is running on.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class OsUtil {
	
	// -------------------- constants --------------------
	
	/** Official name of the Microsoft Windows 98 operating system. */
	private static final String windows98_osName = "Windows 98";
	
	/** Official name of the Microsoft Windows NT operating system. */
	private static final String windowsNt_osName = "Windows NT";
	
	/** Official name of the Microsoft Windows 2000 operating system. */
	private static final String windows2000_osName = "Windows 2000";
	
	/** Official name of the Microsoft Windows XP operating system. */
	private static final String windowsXp_osName = "Windows XP";
	
	/** Name of the command processor program to execute DOS commands under Windows 98 (and below). */
	private static final String windows98_commandProcessor = "command.com /c";	// Note: the /c makes it end after executing a single command; see http://www.microsoft.com/resources/documentation/windows/xp/all/proddocs/en-us/cmd.mspx?mfr=true
	
	/** Name of the command processor program to execute DOS commands under Windows NT (and above). */
	private static final String windowsNt_commandProcessor = "cmd /c";	// Note: the /c makes it end after executing a single command; see http://www.microsoft.com/resources/documentation/windows/xp/all/proddocs/en-us/cmd.mspx?mfr=true
	
	// -------------------- getXXX --------------------
	
	/**
	* Returns a known <i>invalid</i> command for the current operating system.
	* This method is used during testing.
	* <p>
	* @throws IllegalStateException if unable to determine an invalid command for this operating system
	*/
	public static String getCommandInvalid() throws IllegalStateException {
		if (isMicrosoft()) return getDosCommandProcessor() + " " + "sdvnjsddvjsj" + "\n";
		else if (isUnix()) return "sdvnjsddvjsj" + "\n";
		else throw new IllegalStateException(getOsName() + " is currently not a supported operating system");
	}
	
	/**
	* Returns a known valid command for the current operating system.
	* This method is used during testing.
	* <p>
	* @throws IllegalStateException if unable to determine a valid command for this operating system
	*/
	public static String getCommandValid() throws IllegalStateException {
		if (isMicrosoft()) return getDosCommandProcessor() + " " + "dir" + "\n";
		else if (isUnix()) return "ls -a" + "\n";
		else throw new IllegalStateException("unable to determine a valid command for this operating system, since it is of unknown type; its name is: " + getOsName());
	}
	
	/**
	* Returns the name of the command processor program used to execute DOS commands if this is a Microsoft operating system.
	* <p>
	* @throws IllegalStateException if unable to determine what DOS command processor to use for this operating system
	*/
	public static String getDosCommandProcessor() throws IllegalStateException {
		if (isWindows98()) return windows98_commandProcessor;
		else if (isWindowsNT() || isWindows2000() || isWindowsXP()) return windowsNt_commandProcessor;
		else throw new IllegalStateException("unable to determine what DOS command processor to use for this operating system, as it may not even be from Microsoft; its name is: " + getOsName());
	}
	
	/** Returns the current operating system name. */
	public static String getOsName() {
		return System.getProperties().getProperty("os.name");
	}
	
	/** Returns the current operating system version. */
	public static String getOsVersion() {
		return System.getProperties().getProperty("os.version");
	}
	
	/** Returns a concatenation (with a space char separator) of the current operating system name and version. */
	public static String getOsNameAndVersion() {
		return getOsName() + " " + getOsVersion();
	}
	
	// -------------------- isXXX --------------------
	
	/**
	* Determines whether or not the operating system is from Microsoft (e.g. DOS, Windows).
	* All of these tests must be passed:
	* <ol>
	*  <li>{@link File#separatorChar File.separatorChar} == '\\'</li>
	*  <li>{@link #getOsName getOsName} contains the text "windows" or "msdos" (case irrelevant)</li>
	*  <li>the dos command <code>ver</code> was sucessfully executed</li>
	* </ol>
	*/
	public static boolean isMicrosoft() {
		String name = getOsName().toLowerCase();
		return
			(File.separatorChar == '\\') &&
			(name.contains("windows")) || (name.contains("msdos")) &&
			execSynchSucceeded(getDosCommandProcessor() + " " + "ver" + "\n");	// CRITICAL: do not call getCommandValid here, since that would involve circular calls between these 2 methods
	}
	
	/**
	* Determines whether or not the operating system is Windows 98
	* by comparing {@link #windows98_osName} to the result of getOsName.
	*/
	public static boolean isWindows98() {
		return windows98_osName.equals(getOsName());
	}
	
	/**
	* Determines whether or not the operating system is Windows NT
	* by comparing {@link #windowsNt_osName} to the result of getOsName.
	*/
	public static boolean isWindowsNT() {
		return windowsNt_osName.equals(getOsName());
	}
	
	/**
	* Determines whether or not the operating system is Windows 2000
	* by comparing {@link #windows2000_osName} to the result of getOsName.
	*/
	public static boolean isWindows2000() {
		return windows2000_osName.equals(getOsName());
	}
	
	/**
	* Determines whether or not the operating system is Windows 2000
	* by comparing {@link #windowsXp_osName} to the result of getOsName.
	*/
	public static boolean isWindowsXP() {
		return windowsXp_osName.equals(getOsName());
	}
	
	/**
	* Determines whether or not the operating system is Unix (including Linux and OS/X).
	* All of these tests must be passed:
	* <ol>
	*  <li>{@link File#separatorChar File.separatorChar} == '/'</li>
	*  <li>the unix command <code>uname</code> was sucessfully executed</li>
	* </ol>
	*/
	public static boolean isUnix() {
		return
			(File.separatorChar == '/') &&
			execSynchSucceeded("uname" + "\n");	// CRITICAL: do not call getCommandValid here, since that would involve circular calls between these 2 methods
	}
	
	// -------------------- execSynch --------------------
	
	/**
	* Calls {@link #execSynch(String) execSynch}(command).
	* Returns true if no Exception of any type was thrown, false otherwise.
	* Used by this class to determine valid/invalid commands.
	*/
	private static boolean execSynchSucceeded(String command) {
		try {
			execSynch(command);
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}
	
	/**
	* Returns <code>{@link #execSynch(String, String[], File) execSynch}(command, null, null)</code>.
	* So, the process which executes command inherits the env vars and working directory of the current Java process.
	*/
	public static byte[] execSynch(String command) throws IllegalArgumentException, RuntimeException {
		return execSynch(command, null, null);
	}
	
	/**
	* Returns <code>{@link #execSynch(String, String[], File) execSynch}(command, null, null, new {@link StreamDrainerStoring}(), new StreamDrainerStoring())</code>.
	* In words:
	* <ol>
	*  <li>the process which executes command is synchronously executed</li>
	*  <li>that process uses the supplied env vars and working directory</li>
	*  <li>
	*		if that process terminates normally,
	*		then the bytes returned are all the bytes drained from its std out
	*  </li>
	*  <li>
	*		else if that process terminates abnormally,
	*		then the RuntimeException that is thrown will include all the bytes drained from both its std out and err
	*  </li>
	* </ol>
	* <p>
	* <b>Warning:</b> because StreamDrainerStoring instances handle the process's std streams,
	* the command must not generate excessive output over its lifetime
	* else the program may run out of memory.
	*/
	public static byte[] execSynch(String command, String[] envp, File dir) throws IllegalArgumentException, RuntimeException {
		return execSynch( command, envp, dir, new StreamDrainerStoring(), new StreamDrainerStoring() );
	}
	
	/**
	* Executes a native (operating system specific) command in a new child process.
	* This method executes <i>synchronously</i>: it does not return until the command has finished (the child process terminates).
	* See the execAsynch methods of this class for corresponding non-blocking versions.
	* <p>
	* <i>The command must not require any input, since this method does not know how to supply it with any;
	* the command will hang forever waiting for input if you mistakenly call this method with such a command.</i>
	* This will cause the calling thread to block indefinately.
	* <p>
	* The command may, however, produce arbitrary output on either stream (its std out or err streams).
	* The outDrainer and errDrainer params will be {@link StreamDrainer#init initialized}
	* with the child process's std out and err streams respectively,
	* and each will then be concurrently run in its own new thread.
	* This guarantees that those streams are properly drained.
	* <p>
	* @param command the operating system specific command to execute
	* @param envp array of strings, each element of which has environment variable settings in the format name=value,
	* or null if the subprocess should inherit the environment of the current process; see also
	* <a href="http://java.sun.com/developer/JDCTechTips/2001/tt1204.html#tip1">Accessing the Environment from Java Applications</a>
	* @param dir the working directory of the subprocess,
	* or null if the subprocess should inherit the working directory of the current process
	* @param outDrainer used to drain the child process's std out stream; its {@link StreamDrainer#init init} must not have been called
	* @param errDrainer used to drain the child process's std err stream; its {@link StreamDrainer#init init} must not have been called
	* @return a byte[] containing everything written to the child process's std out stream <i>assuming that outDrainer stored those bytes</i>
	* (see {@link StreamDrainer} for more discussion); if those bytes were not stored, then an empty (but never null) byte[] is returned
	* @throws IllegalArgumentException if command is blank;
	* envp is not null but has a blank element;
	* dir is not null but is {@link Check#validDirectory not valid};
	* outDrainer is null;
	* errDrainer is null
	* @throws RuntimeException if the native process terminates abnormally or wrote any data to its error stream,
	* or if some other error was detected (will be stored in the cause field of this RuntimeException)
	*/
	public static byte[] execSynch(String command, String[] envp, File dir, StreamDrainer outDrainer, StreamDrainer errDrainer) throws IllegalArgumentException, RuntimeException {
		// all args checked by ExecTask below
		
		ExecTask task = new ExecTask(command, envp, dir, outDrainer, errDrainer);
		task.run();	// CRITICAL: for synchronous mode, excute task in the calling thread; note that run throws a RuntimeException if command failed
		return task.getStdOut();	// return command's std out if it succeeded
	}
	
	// -------------------- execAsynch --------------------
	
	/**
	* Simply calls <code>{@link #execAsynch(String, String[], File) execAsynch}(command, null, null)</code>.
	* So, the process which executes command inherits the env vars and working directory of the current Java process.
	*/
	public static void execAsynch(String command) throws IllegalArgumentException, RuntimeException {
		execAsynch(command, null, null);
	}
	
	/**
	* Simply calls <code>{@link #execAsynch(String, String[], File) execAsynch}(command, null, null, new {@link StreamDrainerStoring}(), new StreamDrainerStoring())</code>.
	* In words:
	* <ol>
	*  <li>the process which executes command is asynchronously executed</li>
	*  <li>that process uses the supplied env vars and working directory</li>
	*  <li>that process's outcome is always logged to the {@link LogUtil#getLogger2 default Logger} when it terminates</li>
	* </ol>
	* <p>
	* <b>Warning:</b> because StreamDrainerStoring instances handle the process's std streams,
	* the command must not generate excessive output over its lifetime
	* else the program may run out of memory.
	*/
	public static void execAsynch(String command, String[] envp, File dir) throws IllegalArgumentException, RuntimeException {
		execAsynch( command, envp, dir, new StreamDrainerStoring(), new StreamDrainerStoring() );
	}
	
	/**
	* Executes a native (operating system specific) command in a new child process.
	* This method executes <i>asynchronously</i>: it simply establishes a background thread which handles the execution and then immediately returns.
	* See the execSynch methods of this class for corresponding blocking versions.
	* <p>
	* <i>The command must not require any input, since this method does not know how to supply it with any;
	* the command will hang forever waiting for input if you mistakenly call this method with such a command.</i>
	* This will cause the background thread to block indefinately.
	* <p>
	* The command may, however, produce arbitrary output on either stream (its std out or err streams).
	* The outDrainer and errDrainer params will be {@link StreamDrainer#init initialized}
	* with the child process's std out and err streams respectively,
	* and each will then be concurrently run in its own new thread.
	* This guarantees that those streams are properly drained.
	* <p>
	* The background thread ensures that process's outcome is always logged to the {@link LogUtil#getLogger2 default Logger} when it terminates.
	* <p>
	* @param command the operating system specific command to execute
	* @param envp array of strings, each element of which has environment variable settings in the format name=value,
	* or null if the subprocess should inherit the environment of the current process; see also
	* <a href="http://java.sun.com/developer/JDCTechTips/2001/tt1204.html#tip1">Accessing the Environment from Java Applications</a>
	* @param dir the working directory of the subprocess,
	* or null if the subprocess should inherit the working directory of the current process
	* @param outDrainer used to drain the child process's std out stream; its {@link StreamDrainer#init init} must not have been called
	* @param errDrainer used to drain the child process's std err stream; its {@link StreamDrainer#init init} must not have been called
	* @throws IllegalArgumentException if command is blank;
	* envp is not null but has a blank element;
	* dir is not null but is {@link Check#validDirectory not valid};
	* outDrainer is null;
	* errDrainer is null
	*/
	public static void execAsynch(String command, String[] envp, File dir, StreamDrainer outDrainer, StreamDrainer errDrainer) throws IllegalArgumentException {
		// all args checked by ExecTask below
		
		final ExecTask task = new ExecTask(command, envp, dir, outDrainer, errDrainer);
		
		Runnable backgroundTask = new Runnable() { public void run() {
			try {
				task.run();	// CRITICAL: for asynchronous mode, excute task in this background thread
				LogUtil.getLogger2().logp(Level.INFO, "OsUtil", "execAsynch", "Execution succeeded:" + "\n" + task.getState());
			}
			catch (Throwable t) {
				LogUtil.getLogger2().logp(Level.SEVERE, "OsUtil", "execAsynch", "Execution FAILED", t);	// Note that t should normally be the RuntimeException thrown by task.run, and so it should have the details from task.getState already embedded in it
			}
		} };
		
		Thread t = new Thread( backgroundTask, "OsUtil_execAsynch" );
		t.setPriority( Thread.NORM_PRIORITY );
		t.start();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private OsUtil() {}
	
	// -------------------- ExecTask (static inner class) --------------------
	
	/**
	* Encapsulates all the work carried out by both the execSynch and execAsynch methods.
	* <p>
	* This class is not multithread safe: it expects to be called by the same thread over its entire life.
	*/
	private static class ExecTask implements Runnable {
		
		private final String command;
		private final String[] envp;
		private final File dir;
		private final StreamDrainer outDrainer;
		private final StreamDrainer errDrainer;
		private byte[] stdOut;
		private String errors;
		
		private ExecTask(String command, String[] envp, File dir, StreamDrainer outDrainer, StreamDrainer errDrainer) throws IllegalArgumentException {
			Check.arg().notBlank(command);
			if (envp != null) {	// envp may be null
				for (int i = 0; i < envp.length; i++) if (StringUtil.isBlank(envp[i])) throw new IllegalArgumentException("envp[" + i + "] is blank");
			}
			if (dir != null) {	// dir may be null
				Check.arg().validDirectory(dir);
			}
			Check.arg().notNull(outDrainer);
			Check.arg().notNull(errDrainer);
			
			this.command = command;
			this.envp = envp;
			this.dir = dir;
			this.outDrainer = outDrainer;
			this.errDrainer = errDrainer;
		}
		
		public void run() {
			try {
					// execute command:
				Process p = Runtime.getRuntime().exec(command, envp, dir);
				
					// simultaneously and independently drain p's out and err outputs as it executes:
				outDrainer.init( p.getInputStream() );	// the Process class's InputStream is an InputStream for us, the user of the Process, but it holds the actual process's std ot
				Thread tOut = new Thread( outDrainer, "OsUtil_outDrainer");
				tOut.setPriority( Thread.NORM_PRIORITY );
				tOut.start();
				
				errDrainer.init( p.getErrorStream() );
				Thread tErr = new Thread( errDrainer, "OsUtil_errDrainer" );
				tErr.setPriority( Thread.NORM_PRIORITY );
				tErr.start();
				
					// wait for all processes/threads to finish:
				p.waitFor();
				tOut.join();
				tErr.join();
				
				stdOut = outDrainer.getBytes();	// CRITICAL: because getBytes clears all currently stored bytes, can only call it once at this point
				byte[] stdErr = errDrainer.getBytes();
				
					// throw a RuntimeException if some error occured:
				errors = "";	// CRITICAL: need to record ALL the issues that were encountered, not just the first one, to help debugging
				if (p.exitValue() != 0) errors += formatMsg("ERROR: abnormal termination", "exit code = " + p.exitValue());
				if (stdErr.length != 0) errors += formatMsg("ERROR: std err data", new String(stdErr));
				if (outDrainer.getThrowable() != null) errors += formatMsg("ERROR: outDrainer Throwable", ThrowableUtil.toString(outDrainer.getThrowable()));
				if (errDrainer.getThrowable() != null) errors += formatMsg("ERROR: errDrainer Throwable", ThrowableUtil.toString(errDrainer.getThrowable()));
				if (errors.length() > 0) throw new RuntimeException("errors detected");
			}
			catch (Throwable t) {
				throw new RuntimeException("unexpected Throwable caught (see cause); here is all available state:" + "\n" + getState(), t);
			}
		}
		
		private byte[] getStdOut() { return stdOut; }
		
		private String getState() {
			return
				formatMsg("command", command)
				+ formatMsg("stdout data", (stdOut.length > 0) ? new String(stdOut) : "<NONE: either the child process never started, no bytes were read, or none are accessible at this point>")
				+ errors;
		}
		
		private String formatMsg(String label, String msg) {
			return
				"----------" + label + "----------" + "\n"
				+ msg + needsLineEnd(msg);
		}
		
		/** Returns a newline if msg does not end in one, otherwise returns blank. */
		private String needsLineEnd(String msg) {
			char charLast = msg.charAt( msg.length() - 1 );
			return (charLast == '\n' || charLast == '\r') ? "" : "\n";
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getXXX() {
			System.out.println("getOsName() = " + getOsName());
			System.out.println("getOsVersion() = " + getOsVersion());
		}
		
		@Test public void test_execSynch_shouldPass() throws Exception {
			System.out.println("Output from executing the valid command \"" + getCommandValid().replace("\n", "") + "\":");
			byte[] output = execSynch( getCommandValid() );
			System.out.println( new String(output) );
			Assert.assertTrue( output.length > 0 );
		}
		
		@Test(expected=RuntimeException.class) public void test_execSynch_shouldFail() throws Exception {
			System.err.println("Below should be an Exception that occured from executing the invalid command \"" + getCommandInvalid().replace("\n", "") + "\":");
			try {
				execSynch(getCommandInvalid());
			}
			catch (RuntimeException re) {
				re.printStackTrace(System.err);	// can't simply throw e. must log it first, because the @Test(expected will eat it if matches the Exception type
				throw re;
			}
		}
		
		@Test public void test_execAsynch_shouldPass() throws Exception {
			execAsynch( getCommandValid() );
			Thread.sleep(1000);	// if this happens to be the last test method called, need to give the background thread/process time to execute before program termination
// +++ umm, how exactly should i test asynch mode?  seems like i would have to examine log output for expected contents, but this is a huge pain...
		}
		
	}
	
}
