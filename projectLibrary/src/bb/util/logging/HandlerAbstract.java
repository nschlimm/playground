/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--an earlier version of this class (when it was part of HandlerConsole) was been submitted to Sun:
	Your report has been assigned an internal review ID of 1484816, which is NOT visible on the Sun Developer Network (SDN).

--see also this bug report:
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4827381
*/

package bb.util.logging;

import bb.util.ThrowableUtil;
import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
* Abstract subclass of {@link Handler} which makes it easier to write further subclasses.
* <p>
* <i>The chief reason why this class was written is because Sun annoyingly hid key functionality for Handler subclasses.</i>
* First, from {@link ConsoleHandler}, this class copied and modified {@link #configure configure}.
* Second, from {@link LogManager}, this class copied and modified some code, which it exposes in the following methods:
* {@link #getLevelProperty getLevelProperty}, {@link #getFilterProperty getFilterProperty}, {@link #getFormatterProperty getFormatterProperty}, {@link #getStringProperty getStringProperty}.
* These methods are needed by configure.
* Also to support configure, this class introduces the methods
* {@link #getEncodingDefault getEncodingDefault}, {@link #getFilterDefault getFilterDefault},
* {@link #getFormatterDefault getFormatterDefault}, and {@link #getLevelDefault getLevelDefault}.
* <p>
* This class introduces a new field, {@link #alive}, along with accessor ({@link #isAlive isAlive}) and mutator ({@link #setAlive setAlive}) methods for it.
* This field is used by {@link #close close}, which this class overrides from its superclass (Handler).
* Also, this class overrides {@link #isLoggable isLoggable}, since its superclass method does not detect null record params.
* <p>
* <i>It is unclear whether or not this class is multithread safe.</i>
* This is because it is unclear whether or not its superclass, Handler, is multithread safe:
* its javadocs do not mention thread safety, and Sun's source code is bizarre
* (as of JDK 1.6.0_11, two of Handler's methods are synchronized, but many others are not even tho they involve shared mutable state).
* There exists a bug report with Sun on this matter
* (status as of 2009-03-23: "Your report has been assigned an internal review ID of 1484784, which is NOT visible on the Sun Developer Network (SDN).")
* Every method added by this class is synchronized.
* <p>
* @author Brent Boyer
*/
public abstract class HandlerAbstract extends Handler {
	
	// -------------------- instance fields --------------------
	
	/**
	* Records whether or not this instance is considered alive.
	* Is initially true, then should be set to false once and for all when {@link #close close} is first called.
	*/
	private boolean alive = true;
	
	// -------------------- constructor --------------------
	
	/** Constructor. */
	public HandlerAbstract() {
		//configure();	// CRITICAL: do NOT call this method from this constructor, since subclasses may need control over what order it is called or may need to override it in a way that this constructor should not call
	}
	
	// -------------------- configure --------------------
	
	// NOTE: CODE IN THE SECTION BELOW BELOW ADAPTED FROM ConsoleHandler
	
	/**
	* Initializes an instance using the following {@link LogManager} configuration properties:
	* <ol>
	*  <li><code><i>className</i>.encoding</code> the name of the character set encoding to use (default: {@link #getEncodingDefault getEncodingDefault})</li>
	*  <li><code><i>className</i>.filter</code> specifies the name of a Filter class to use (default: {@link #getFilterDefault getFilterDefault})</li>
	*  <li><code><i>className</i>.formatter</code> specifies the name of a Formatter class to use (default: {@link #getFormatterDefault getFormatterDefault})</li>
	*  <li><code><i>className</i>.level</code> specifies the default level for the Handler (default: {@link #getLevelDefault getLevelDefault})</li>
	* </ol>
	* If any property is not defined (or has an invalid value) then the specified default value is used.
	* <p>
	* @throws RuntimeException (or some subclass) if any problem occurs
	*/
	protected synchronized void configure() throws RuntimeException {
		try {
			String cname = getClass().getName();
			
			setEncoding( getStringProperty(cname + ".encoding", getEncodingDefault()) );
			
// +++ why no call to setErrorManager?  there ought to be a class that you can specify here, just like Filter or Formatter...
			
			setFilter( getFilterProperty(cname + ".filter", getFilterDefault()) );
			
			setFormatter( getFormatterProperty(cname + ".formatter", getFormatterDefault()) );
			
			setLevel( getLevelProperty(cname + ".level", getLevelDefault()) );
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- getXXXDefault --------------------
	
	/**
	* Returns the default value to use for the {@link #getEncoding encoding}.
	* This method is called by {@link #configure}.
	* Implementation here returns null (which means use the platform default).
	*/
	protected synchronized String getEncodingDefault() {
		return null;
	}
	
	/**
	* Returns the default value to use for the {@link #getFilter filter}.
	* This method is called by {@link #configure}.
	* Implementation here returns null (which means use no Filter).
	*/
	protected synchronized Filter getFilterDefault() {
		return null;
	}
	
	/**
	* Returns the default value to use for the {@link #getFormatter formatter}.
	* This method is called by {@link #configure}.
	* Implementation here returns a new {@link FormatterShort} instance.
	*/
	protected synchronized Formatter getFormatterDefault() {
		return new FormatterShort();
	}
	
	/**
	* Returns the default value to use for the {@link #getLevel level}.
	* This method is called by {@link #configure}.
	* Implementation here returns {@link Level#INFO}.
	*/
	protected synchronized Level getLevelDefault() {
		return Level.INFO;
	}
	
	// -------------------- getXXXProperty --------------------
	
	// NOTE: CODE IN THE SECTION BELOW ADAPTED FROM LogManager
	
// +++ there is an RFE relevant to eliminating this section; Sun feedback: "Your report has been assigned an internal review ID of 1484807, which is NOT visible on the Sun Developer Network (SDN)."
	
	protected synchronized Level getLevelProperty(String name, Level defaultValue) {
		String val = LogManager.getLogManager().getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		try {
			return Level.parse(val.trim());
		}
		catch (Exception ex) {
			return defaultValue;
		}
	}
	
	protected synchronized Filter getFilterProperty(String name, Filter defaultValue) {
		String val = LogManager.getLogManager().getProperty(name);
		try {
			if (val != null) {
			Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Filter) clz.newInstance();
			}
		}
		catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception.  Return the defaultValue.
		return defaultValue;
	}
	
	protected synchronized Formatter getFormatterProperty(String name, Formatter defaultValue) {
		String val = LogManager.getLogManager().getProperty(name);
		try {
			if (val != null) {
			Class clz = ClassLoader.getSystemClassLoader().loadClass(val);
				return (Formatter) clz.newInstance();
			}
		}
		catch (Exception ex) {
			// We got one of a variety of exceptions in creating the
			// class or creating an instance.
			// Drop through.
		}
		// We got an exception.  Return the defaultValue.
		return defaultValue;
	}
	
	protected synchronized String getStringProperty(String name, String defaultValue) {
		String val = LogManager.getLogManager().getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		return val.trim();
	}
	
	// Note: the code below I wrote; is modeled after the above
	
	protected synchronized int getIntProperty(String name, int defaultValue) {
		String val = LogManager.getLogManager().getProperty(name);
		if (val == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(val.trim());
		}
		catch (Exception ex) {
			return defaultValue;
		}
	}
	
	// -------------------- isAlive, setAlive --------------------
	
	/** Accessor for {@link #alive}. */
	protected synchronized boolean isAlive() { return alive; }
	
	/** Mutator for {@link #alive}. */
	protected synchronized void setAlive(boolean alive) { this.alive = alive; }
	
	// -------------------- overriden Handler methods --------------------
	
	/**
	* If {@link #isAlive isAlive} returns true,
	* then calls {@link Handler#close super.close} before calling <code>{@link #setAlive setAlive}(false)</code>.
	*/
	@Override public synchronized void close() {
		if (isAlive()) {
			//super.close();	// NO: is abstract
			setAlive(false);
		}
	}
	
	/**
	* If record is null, immediately returns false.,
	* Otherwise, returns <code>{@link Handler#isLoggable super.isLoggable}(record)</code>.
	*/
	@Override public synchronized boolean isLoggable(LogRecord record) {
		if (record == null) return false;
		return super.isLoggable(record);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// NOTE: THE CLASS BELOW ACTUALLY DOES NO TESTS.
	// Instead, it merely defines some useful functionality for the UnitTest classes of subclasses.
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public abstract static class UnitTest {
		
		// NOTE: see comment above LogGenerator.selectLevel if wonder why see delays in new data arrival...
		protected void test_all_makeLogsSeriallySlowly(int numToGenerate, Handler handler) throws Exception {
			LogGenerator generator = new LogGenerator(numToGenerate, handler, 1000);
			String tName = handler.getClass().getSimpleName() + ".UnitTest_LogGenerator";
			Thread thread = new Thread( generator, tName );
			thread.setPriority( Thread.NORM_PRIORITY );
			thread.start();
		}
		
		protected void test_all_makeLogsConcurrentlyRapidly(int numToGenerate, Handler handler) throws Exception {
			Thread[] threads = new Thread[10];
			for (int i = 0; i < threads.length; i++) {
				LogGenerator generator = new LogGenerator(numToGenerate, handler, 1);
				String tName = handler.getClass().getSimpleName() + ".UnitTest_LogGenerator#" + i;
				threads[i] = new Thread( generator, tName );
				threads[i].setPriority( Thread.NORM_PRIORITY );
				threads[i].start();
			}
		}
		
		private static class LogGenerator implements Runnable {
			
			private final int numToGenerate;
			private final Handler handler;
			private final long timeSleep;
			
			private LogGenerator(int numToGenerate, Handler handler, long timeSleep) {
				this.numToGenerate = numToGenerate;
				this.handler = handler;
				this.timeSleep = timeSleep;
			}
			
			public void run() {
				try {
					for (int i = 0; i < numToGenerate; i++) {
						Level level = selectLevel(i);
						String msg = "#" + i + ": log message from " + Thread.currentThread().getName();
						handler.publish( new LogRecord(level, msg) );
						Thread.sleep(timeSleep);
					}
					handler.close();
				}
				catch (Throwable t) {
					LogUtil.getLogger2().logp(Level.SEVERE, "Handler.UnitTest.LogGenerator", "run", "unexpected error caught", t);
				}
			}
			
			// WARNING: there is ONE problem with this code: if handler only accepts, say, INFO and above,
			// then can encounter long periods when execute test_all_makeLogsSeriallySlowly wherein nothing is published.
			// The user may freak out if they do not know that it is because Level filtering is causing stuff not to appear.
			private Level selectLevel(int i) {
				switch (i % 9) {
					case 0: return Level.OFF;
					case 1: return Level.SEVERE;
					case 2: return Level.WARNING;
					case 3: return Level.INFO;
					case 4: return Level.CONFIG;
					case 5: return Level.FINE;
					case 6: return Level.FINER;
					case 7: return Level.FINEST;
					case 8: return Level.ALL;
					default: throw new IllegalStateException("reached an illegal program point");
				}
			}
			
		}
		
	}
	
}
