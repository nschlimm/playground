/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer Notes:

--this guy also stumbled across using reflection to handle command line interfaces:
	http://www-128.ibm.com/developerworks/java/library/j-dyn0715.html
Study his code when return to this class...

--using hash tables for command line interfaces:
	http://www.ibm.com/developerworks/linux/library/l-gperf.html?S_TACT=105AGX59&S_CMP=GR&ca=dgr-lnxw01GPERF

--see also this project:
	http://commons.apache.org/cli/

+++ if sun ever implements this RFE
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6351276
then could write much better CLIs?

--on using java for even small systems programming tasks:
	http://weblogs.java.net/blog/evanx/archive/2006/05/bin_bash_java_c.html
	http://weblogs.java.net/blog/evanx/archive/2006/05/a_fools_errand.html
	http://weblogs.java.net/blog/evanx/archive/2006/05/java_is_all_you.html
see also a quick way to execute java:
	http://www.martiansoftware.com/nailgun/index.html
*/


package bb.util;

import bb.io.StreamUtil;
import bb.util.logging.LogUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
* Illustrates one way to quickly implement a simple command line interface (CLI).
* <p>
* The CLI supported by this class consists of the following:
* <ol>
*  <li>upon startup, all the commands available to the user are printed on the console ({@link System#out})</li>
*  <li>
*		thereafter, the program waits for the user to type a command on the console ({@link System#in}).
*		Valid input is either the number or name of a command, followed by hitting the <code>Enter</code> key.
*  </li>
* </ol>
* There are obviously many major limitations with the above CLI (e.g. no options or arguments to commands are currently supported);
* many of these deficiencies could be addressed by extra coding...
* <p>
* What makes this class interesting is that it allows the programmer to add new commands extremely quickly:
* all you need to do is write a Java method (see {@link Commands} for details).
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class CommandLineInterface {
	
	// -------------------- main --------------------
	
	public static void main(final String[] args) {
		Execute.thenContinue( new Runnable() { public void run() {
			Check.arg().empty(args);
			
			new Thread( new StdinReader(), "StdinReader" ).start();
		} } );
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor ensures non-instantiability outside of this class. */
	private CommandLineInterface() {}
	
	// -------------------- StdinReader (static inner classes) --------------------
	
	private static class StdinReader implements Runnable {
		
		private final Commands commands = new Commands();
		
		private final Map<String,Method> textToMethod = new HashMap<String,Method>();
		{
			Method[] methods = commands.getClass().getDeclaredMethods();
			for (int i = 0; i < methods.length; i++) {
				textToMethod.put( String.valueOf(i).toLowerCase(), methods[i] );	// hmm, unlikely that a String representation of an int needs toLowerCase() called on it, but maybe some weird language has cases for numbers...
				textToMethod.put( methods[i].getName().toLowerCase(), methods[i] );
			}
		}
		
		private StdinReader() {}
		
		public void run() {
			BufferedReader br = null;
			try {
				commands.listCommands();
				
				br = new BufferedReader( new InputStreamReader(System.in) );
				while (!Thread.currentThread().isInterrupted()) {
					String cmd = br.readLine();
					if (cmd == null) {
						LogUtil.getLogger2().logp(Level.WARNING, "CommandLineInterface.StdinReader", "run", "System.in appears to have been closed (just read a null command); this must have been done by some other process; will now quit this program");
						executeCommand("quit");
					}
					else {
						executeCommand(cmd);
					}
				}
			}
			catch (Throwable t) {
				LogUtil.getLogger2().logp(Level.SEVERE, "CommandLineInterface.StdinReader", "run", "caught an unexpected Throwable", t);
			}
			finally {
				StreamUtil.close(br);
			}
		}
		
		private void executeCommand(String commandText) {
			try {
				Check.arg().notBlank(commandText);
				
				Method method = textToMethod.get( commandText.toLowerCase() );
				if (method == null) throw new IllegalArgumentException("commandText = " + commandText + " is invalid (does not correspond to any known command)");
				method.invoke(commands);
			}
			catch (Throwable t) {
				t.printStackTrace(System.err);
			}
		}
		
	}
	
	// -------------------- Commands (static inner classes) --------------------
	
	/**
	* Whenever you wish to add a command to the CommandLineInterface class,
	* <i>all</i> that you need to do is add it as a method of this class.
	* (Reflection in {@link #listCommands listCommands}
	* and {@link StdinReader} will figure out all the rest).
	* <p>
	* Every command method is subject to the following restrictions:
	* <ol>
	*  <li>it must take no arguments (the current command line interface is too crude to pass any in)</li>
			<!--
			Note that the above restriction is not fundamental.
			For example, each command method could take a String[] as arg
			by having StdinReader.executeCommand split what it reads into whitespace delimited tokens.
			-->
	*  <li>it must not have private access (recommendation: for simplicity, use default access)</li>
	* </ol>
	* Otherwise, it is completely arbitrary how you name each command method,
	* how it behaves, and whether or not it throws any type of Throwable.
	* <p>
	* Note: the order in which command methods are declared below
	* is <i>likely</i> to be the order in which they are listed by {@link #listCommands listCommands}.
	* (This comes from the current behavior of {@link Class#getDeclaredMethods Class.getDeclaredMethods}:
	* altho this behavior is not guaranteed to occur, it is what is currently observed.)
	*/
	private static class Commands {
		
		void listCommands() {
			System.out.println();
			System.out.println("--------------------------------------------------");
			System.out.println("List of all available commands:");
			
			Method[] methods = getClass().getDeclaredMethods();
			for (int i = 0; i < methods.length; i++) {
				System.out.println("\t" + i + ": " + methods[i].getName());
			}
			System.out.println("To execute an command above, type either the number of the command or its name, and then press Enter");
		}
		
		void printSystemProperties() {
			System.out.println();
			System.out.println("Here are all the properties currently defined in System:");
			System.out.println( "\t" + StringUtil.toString(System.getProperties(), "\n\t") );
		}
		
		void printTime() {
			System.out.println();
			System.out.println("Current time: " + DateUtil.getTimeStamp());
		}
		
		void quit() {
			System.exit(0);
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed--is tested by main
	
}
