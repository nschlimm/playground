/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer's notes:

+++ this class works perfectly fine except for ONE major defect: the line width had to be hard coded (see the lineLength constant below)
	--it had to be hard coded because I know of no way to discover that information programmatically in java
		http://forum.java.sun.com/thread.jspa?forumID=31&threadID=646822
		http://forum.java.sun.com/thread.jspa?forumID=52&threadID=536954
	--jdk 1.6 introduced the Console class.  Unfortunately, it offers no methods which look like they will solve this problem
	+++ to partially solve this, at a minimum, maybe could at least allow the console line width to be passed in as some type of property...

--Gerry Seidman pointed out to me that if do the
	mem /d
command in dos, will see all the devices; of particular interest (under the IO section) is
	con
the console device; can read and write to it as if it were the file con:
See also
	http://www.tcs.org/ioport/jul98/driver-3.htm

*/


package bb.io;


import bb.util.Check;
import bb.util.Execute;
import bb.util.StringUtil;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;


/**
* Provides static utility methods for dealing with consoles (i.e. the default System.in and System.out).
* <p>
* One problem that must be dealt with is if output requests occur very rapidly:
* there is no way that the human eye can meaningfully keep up with the changes,
* but to insist that every line be output drains cpu resources for no human benefit.
* To deal with this, the output method {@link #overwriteLine overwriteLine}
* merely notes output requests as its only immediate action.
* An internal thread will take care of periodically writing the last requested line,
* and will disregard any other requested lines.
* Thus, the output will appear to be changing as rapidly as is meaningful to the human eye,
* yet the cpu usage will be limited.
* In contrast, the {@link #eraseLine eraseLine} method always immediately executes.
* <p>
* This class is multithread safe: every method is synchronized on this class.
* <p>
* @author Brent Boyer
*/
public final class ConsoleUtil {
	
	
	// -------------------- constants --------------------
	
	
	private static final int lineLength = 120;
	
	/**
	* Maximum number of chars that will appear on a line of console output.
	* <i>The value here is a hard coded guess, and very well could be wrong for many screen size/font size combinations.</i>
	* <p>
	* Contract: is > 0.
	*/
	public static final int maxCharsPerLine = lineLength - 1;	// subtract 1 from lineLength because DOS, at least, will automaticly append an end of line the instant you touch the end
	
	//private static final String backspacesLine = StringUtil.repeatChars('\b', maxCharsPerLine);	// use backspaces, as \r has OS dependent meaning
	private static final String backspacesLine = "\r";	// decided to go back with this, since is not only shorter, but it works on unix whereas the above frequently backs up into the line above (this is a difference between windows and unix consoles)
	private static final String blankLine = StringUtil.repeatChars(' ', maxCharsPerLine);
	
	/**
	* A piece of text that when printed clears the current line on the console.
	* <p>
	* Contract: is never null.
	*/
	public static final String clearLine = backspacesLine + blankLine + backspacesLine;
	
	
	private static final long delay = 0;	// in ms
	private static final long period = 100;	// in ms, so 100 ms <--> 0.1 s <--> 10 times a second
	private static final Timer timer;
	static {
		timer = new Timer( "ConsoleUtil_LineWriter", true );
		timer.schedule( new LineWriterTask(), delay, period ) ;
	}
	
	
	// -------------------- static fields --------------------
	
	
	private static String line;
	
	
	// -------------------- output methods: overwriteLine, eraseLine, writeLine --------------------
	
	
	/**
	* Merely records a request to replace the current line with s.
	* Note: if s == null, it is replaced with the empty String before being assigned to line.
	*/
	public static synchronized void overwriteLine(String s) {
		if (s == null) s = "";
		line = s;
	}
	
	
	/** Erases the console's current line. */
	public static synchronized void eraseLine() {
		line = "";	// the empty String, when supplied to writeLine below, will cause nothing to be present on the line, just as desired
		writeLine();
	}
	
	
	/**
	* Writes {@link #line}.
	* If line is longer than {@link #maxCharsPerLine maxCharsPerLine},
	* it will be truncated by a call to {@link StringUtil#keepWithinLength StringUtil.keepWithinLength}.
	* Should only be called by {@link #timer}.
	*/
	private static synchronized void writeLine() {
		if (line != null) {
			System.out.print(clearLine);
			System.out.print( StringUtil.keepWithinLength(line, maxCharsPerLine) );
			System.out.flush();
			line = null;	// CRITICAL: must reset to null after each write
		}
	}
	
	
	// -------------------- constructor --------------------
	
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ConsoleUtil() {}
	
	
	// -------------------- LineWriterTask (static inner class) --------------------
	
	
	private static class LineWriterTask extends TimerTask {
		
		private LineWriterTask() {}
		
		public void run() {
			writeLine();
		}
		
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Tests the parent class.
		* <p>
		* If this method is this Java process's entry point (i.e. first <code>main</code> method),
		* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
		* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
		* Otherwise, this method returns and leaves the JVM running.
		*/
		public static void main(final String[] args) {
			Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
					// test: request output at random times, but verify that the actual console output is smooth
				//for (int i = 0; i < 1*100; i++) {
				for (int i = 1*100; i > 0; i--) {	// its better to count down, so that can verify that line is completely cleared
					ConsoleUtil.overwriteLine( String.valueOf(i) );
					Thread.sleep( (long) (200 * Math.random()) );
				}
				ConsoleUtil.eraseLine();
				
				return null;
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
	
}
