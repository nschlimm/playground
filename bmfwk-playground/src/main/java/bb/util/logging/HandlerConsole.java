/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.io.PrintStreamStoring;
import bb.util.Check;
import bb.util.Execute;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;
import java.util.logging.ConsoleHandler;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;
import org.junit.Assert;
import org.junit.Test;

/**
* Same as {@link ConsoleHandler} except that instead of always logging to {@link System#err},
* this class only logs serious issues to <code>System.err</code> while non-serious issues get logged to {@link System#out}.
* The problem with <code>ConsoleHandler</code> always logging to <code>System.err</code> is that there may be monitoring code
* which looks at <code>System.err</code> and concludes that if anything appears, then it must signal a problem.
* This is obviously a mistake for many log records (typically {@link Level#INFO} and below logs are not problems).
* <p>
* It can be useful to attach an instance to the root Logger, and have it publish all logs.
* <p>
* <i>It is unclear whether or not this class is multithread safe.</i>
* There may be issues with its superclass; see {@link HandlerAbstract} for more discussion.
* Concerning the state added by this class, every method is synchronized.
* <p>
* @author Brent Boyer
*/
public class HandlerConsole extends HandlerAbstract {
	
	// -------------------- instance fields --------------------
	
	/**
	* All LogRecords with this or a higher Level are considered to be "serious" log events by {@link #isSerious isSerious}.
	* <p>
	* If this field has the value {@link Level#ALL} (which is the lowest valued Level),
	* then all LogRecords will be logged to <code>System.err</code>.
	* Else if this field has the value {@link Level#OFF} (which is the highest valued Level),
	* then all LogRecords will be logged to <code>System.out</code>.
	* Else if this field has some other value,
	* then the "high value" LogRecords will be logged to <code>System.err</code>
	* and the "low value" LogRecords will be logged to <code>System.out</code>.
	* For example, if this value is {@link Level#WARNING},
	* then (ignoring OFF LogRecords) only {@link Level#SEVERE} and WARNING LogRecords will be logged to <code>System.err</code>,
	* while all others go to <code>System.out</code>).
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private Level levelSerious;
	
	/**
	* Used to write non-serious logs.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private StreamHandler outHandler = new StreamHandler( System.out, new FormatterShort() );
	
	/**
	* Used to write serious logs.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private StreamHandler errHandler = new StreamHandler( System.err, new FormatterShort() );
	
	// -------------------- constructors and helper methods --------------------
	
	/**
	* Convenience constructor.
	* <p>
	* All this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure},
	* except for the {@link #levelSerious} field which is assigned from calling
	* <code>{@link #getLevelProperty getLevelProperty}(getClass().getName() + ".levelSerious", Level.SEVERE)</code>.
	* In other words, {@link #levelSerious} is set to whatever is defined for it in the logging properties file,
	* else it defaults to Level.SEVERE if not defined there.
	*/
	public HandlerConsole() {
		synchronized (this) {	// needed to safely publish levelSerious, at a minimum (and maybe some of the state mutated by configure)
			this.levelSerious = getLevelProperty(HandlerConsole.class.getName() + ".levelSerious", Level.SEVERE);
			configure();
		}
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* All this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure},
	* except for the {@link #levelSerious} field which is assigned from the param.
	* <p>
	* @throws IllegalArgumentException if levelSerious is null
	*/
	public HandlerConsole(Level levelSerious) throws IllegalArgumentException {
		Check.arg().notNull(levelSerious);
		
		synchronized (this) {	// needed to safely publish levelSerious, at a minimum (and maybe some of the state mutated by configure)
			this.levelSerious = levelSerious;
			configure();
		}
	}
	
	// -------------------- overriden Handler methods --------------------
	
	@Override public synchronized void close() throws SecurityException {
		if (!isAlive()) return;
		
		super.close();
		
		levelSerious = null;
		
		outHandler.close();
		outHandler = null;
		
		errHandler.close();
		errHandler = null;
	}
	
	@Override public synchronized void flush() {
		if (!isAlive()) return;
		
		outHandler.flush();
		errHandler.flush();
	}
	
	/**
	* If {@link #isAlive isAlive} or {@link #isLoggable isLoggable}(record) returns false, then immediately returns.
	* Else publishes record to <code>System.err</code> if <code>{@link #isSerious isSerious}(record)</code> returns true,
	* else publishes record to <code>System.out</code>.
	*/
	@Override public synchronized void publish(LogRecord record) {
		try {
			if (!isAlive()) return;
			if (!isLoggable(record)) return;	// Note: isLoggable checks if a) record is null b) record's Level passes this instance's Level, and c) record passes this instance's Filter
			
			if (isSerious(record)) {
				errHandler.publish(record);
				errHandler.flush();	// serious logs always autoflush to warn the user ASAP
			}
			else {
				outHandler.publish(record);
				outHandler.flush();	// non-serious logs are autoflushed only because want the entire console output to be listed in actual time of occurrence, and since the same console is usually used for both stdout and stderr, and stderr is being autoflushed above, then we must autoflush stdout as well
			}
		}
		catch (Exception e) {
			reportError(null, e, ErrorManager.GENERIC_FAILURE);	// report the exception to any registered ErrorManager
		}
	}
	
	// Note: all the mutators need to be overridden to both:
	// 1) store the state in our superclass (which allows us to not have to override the accessors)
	// 2) store the state in both our StreamHandler fields (since these are what actually do the work)
	
	@Override public synchronized void setFormatter(Formatter formatter) throws SecurityException {
		super.setFormatter(formatter);
		outHandler.setFormatter(formatter);
		errHandler.setFormatter(formatter);
	}
	
	@Override public synchronized void setEncoding(String encoding) throws SecurityException, UnsupportedEncodingException {
		super.setEncoding(encoding);
		outHandler.setEncoding(encoding);
		errHandler.setEncoding(encoding);
	}
	
	@Override public synchronized void setFilter(Filter filter) throws SecurityException {
		super.setFilter(filter);
		outHandler.setFilter(filter);
		errHandler.setFilter(filter);
	}
	
	@Override public synchronized void setErrorManager(ErrorManager errorManager) {
		super.setErrorManager(errorManager);
		outHandler.setErrorManager(errorManager);
		errHandler.setErrorManager(errorManager);
	}
	
	@Override public synchronized void setLevel(Level level) throws SecurityException {
		super.setLevel(level);
		outHandler.setLevel(level);
		errHandler.setLevel(level);
	}
	
	// -------------------- new api: isSerious --------------------
	
	/** Returns true if record's Level has at the same or higher {@link Level#intValue value} than {@link #levelSerious}. */
	public synchronized boolean isSerious(LogRecord record) {
		if (record == null) return false;	// Handler.publish's javadocs state that nulls should be silently ignored
		
		return (record.getLevel().intValue() >= levelSerious.intValue());	// because Sun screwed up and has not yet made Level implement Comparable, need to call the intValue method, furthermore, must look at Level's source code to confirm that this works correctly
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// NOTE: the class below has both a main that requires manual execution, as well as a JUnit test method that can be autoexecuted by JUnitExecutor
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest extends HandlerAbstract.UnitTest {
		
		private static final String token = "thisTextShouldBeSomethingDistinctive";
		
		public static void main(final String[] args) throws Exception {
			Execute.thenContinue( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				UnitTest unitTest = new UnitTest();
				unitTest.test_all_makeLogsSeriallySlowly( 20, new HandlerConsole() );
				//unitTest.test_all_makeLogsConcurrentlyRapidly( Integer.MAX_VALUE, new HandlerConsole() );
				
				return null;
			} } );
		}
		
		@Test public void test_publish() throws Exception {
			PrintStream stdoutOrig = System.out;
			PrintStream stderrOrig = System.err;
			try {
				PrintStreamStoring pssOut = new PrintStreamStoring();
				System.setOut(pssOut);
				PrintStreamStoring pssErr = new PrintStreamStoring();
				System.setErr(pssErr);
				
				HandlerConsole handlerConsole = new HandlerConsole(Level.SEVERE);	// CRITICAL: asserts below will fail if not set to this
				handlerConsole.setLevel( Level.ALL );	// CRITICAL: asserts below will fail if not set to this
				
					// now write all normal (non-All/OFF) Levels and confirm that each std stream got what it should:
				handlerConsole.publish( new LogRecord(Level.OFF, token) );
				handlerConsole.publish( new LogRecord(Level.SEVERE, token) );
				handlerConsole.publish( new LogRecord(Level.WARNING, token) );
				handlerConsole.publish( new LogRecord(Level.INFO, token) );
				handlerConsole.publish( new LogRecord(Level.CONFIG, token) );
				handlerConsole.publish( new LogRecord(Level.FINE, token) );
				handlerConsole.publish( new LogRecord(Level.FINER, token) );
				handlerConsole.publish( new LogRecord(Level.FINEST, token) );
				handlerConsole.publish( new LogRecord(Level.ALL, token) );
				Assert.assertEquals( 7, getLogCount(pssOut) );
				Assert.assertEquals( 2, getLogCount(pssErr) );
			}
			finally {
				System.setOut(stdoutOrig);
				System.setErr(stderrOrig);
			}
		}
		
		private int getLogCount(PrintStreamStoring pss) {
			String s = pss.getString();
			return (s.length() - s.replaceAll(token, "").length()) / token.length();	// inspired by http://forums.sun.com/thread.jspa?messageID=10653292
		}
		
	}
	
}
