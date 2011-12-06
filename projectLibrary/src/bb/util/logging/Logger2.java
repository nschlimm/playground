/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ have a map from LEVEL --> count, so that could query the logger and see if encountered issues
	the methods would be
		getCount	returns total number of calls to log
		getCountAt(LEVEL)	returns total number of calls to log at exactly LEVEL
		getCountAtLeast(LEVEL)	returns total number of calls to log at or above LEVEL (e.g. supply WARNING and get count of SEVERE as well)
*/

package bb.util.logging;

import bb.util.Check;
import bb.util.ThrowableUtil;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
* Subclass of Logger which adds this additional functionality:
* <ol>
*  <li>all logging methods <i>log robustly</i> (see {@link #log(LogRecord)} log)</li>
*  <li>the {@link #logIfNew(Level, String, String, String, Object[]) logIfNew} methods</li>
* </ol>
* <p>
* This class is multithread safe: every method which uses mutable state is synchronized.
* <p>
* @author Brent Boyer
*/
public class Logger2 extends Logger {
	
	// -------------------- instance fields --------------------
	
	/**
	* This class actually delegates all of its low-level logging work to this internal Logger instance.
	* <p>
	* This is wasteful of cpu and memory, and forces this class to override many Logger methods just to do this delegation.
	* Nevertheless, it was done because the Logger javadocs (as of JDK 1.6 at least) specify that subclasses must do this:
	* <blockquote>
	*	Subclassing Information: ... Therefore, any subclasses of Logger ...
	*	should take care to obtain a Logger instance from the LogManager class
	*	and should delegate operations ... to that instance
	* </blockquote>
	* Another reason to do this delegation is because Sun has not exposed the private Logger.anonymous field.
	* There is a RFE about this:
	*	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6830679
	*/
	private final Logger logger;
	
	/**
	* Used by {@link #log(LogRecord) log} in its error handling logic if need to resort to outputting to the console.
	* For this purpose, a concrete subclass of Formatter which prints out all the useful LogRecord information must be used.
	* <p>
	* Used by {@link #logIfNew(Level, String, String, String, Object[]) logIfNew} in order to perform localization and param substitution
	* via the {@link Formatter#formatMessage Formatter.formatMessage} method.
	* For this purpose, any concrete subclass of Formatter which does not override formatMessage in an unexpected way may be assigned here.
	* (That subclass's {@link Formatter#format Formatter.format} method is never used, so it is irrelevant what its output looks like.)
	*/
	private final Formatter formatter = new FormatterFull();
	
	/** Solely used (and lazy initialized) inside logIfNew to store messages known by this instance. */
	private Set<String> loggedMessages;
// +++ should actually be a Map, in which the key is the String but a Long (which tracks the count) is the value; then should offer a way to query this count...
	
	// -------------------- analogs to Logger static methods --------------------
	
	/** Returns <code>{@link #getAnonymousLogger2(String) getAnonymousLogger2}(null)</code>. */
	public static Logger2 getAnonymousLogger2() { return getAnonymousLogger2(null); }
	
	/**
	* Constructs a new Logger2
	* whose underlying {@link #logger} is anonymous (i.e. unregistered with {@link LogManager}) and uses resourceBundleName.
	*/
	public static Logger2 getAnonymousLogger2(String resourceBundleName) {
		return new Logger2( Logger.getAnonymousLogger(resourceBundleName) );
	}
	
	/**
	* Returns <code>{@link #getLogger2(String, String) getLogger2}(name, null)</code>.
	* <p>
	* @throws IllegalArgumentException if name is blank
	* @throws IllegalStateException if a Logger with name already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 getLogger2(String name) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		return getLogger2(name, null);
	}
	
	/**
	* Constructs a new Logger2
	* whose underlying {@link #logger} is registered with {@link LogManager} and uses name and resourceBundleName.
	* <p>
	* No other Logger with name may currently exist:
	* this method's first action is to check this using {@link LogManager}.
	* Only if this is the first Logger with name is it created.
	* <p>
	* @throws IllegalArgumentException if name is {@link Check#notBlank blank}; consiequently, this method cannot be used to create the root Logger (the one named "")
	* @throws IllegalStateException if a Logger with name already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 getLogger2(String name, String resourceBundleName) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		Check.arg().notBlank(name);
		// resourceBundleName may be anything, even null or blank?
		
		try {
			LogManager manager = LogManager.getLogManager();
			synchronized (manager) {	// must synchronize on this because, in principle, other classes could be calling it too; also, inspecting the LogManager source code reveals that the LogManager instance itself is used as the lock
				Logger logger = manager.getLogger(name);	// see if one already exists (this call does NOT create it if it does not exist)
				if (logger != null) throw new IllegalStateException("a Logger with name = " + name + " already exists");	// immediately crash if already exists
				logger = Logger.getLogger(name, resourceBundleName);	// this call DOES create it if it does not exist, which it shoudl not at this point
				return new Logger2(logger);
			}
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- constructor --------------------
	
	/**
	* Constructs a new Logger2 which ultimately defers to {@link #logger} for all of its work.
	* <p>
	* @throws NullPointerException if logger == null
	*/
	protected Logger2(Logger logger) throws NullPointerException {
		super(logger.getName(), logger.getResourceBundleName() );
		
		this.logger = logger;
	}
	
	// -------------------- overridden Logger instance methods --------------------
	
	@Override public void addHandler(Handler handler) { logger.addHandler(handler); }
	
	@Override public Filter getFilter() { return logger.getFilter(); }
	
	@Override public Handler[] getHandlers() { return logger.getHandlers(); }
	
	@Override public Level getLevel() { return logger.getLevel(); }
	
	@Override public String getName() { return logger.getName(); }
	
	@Override public Logger getParent() { return logger.getParent(); }
	
	@Override public ResourceBundle getResourceBundle() { return logger.getResourceBundle(); }
	
	@Override public String getResourceBundleName() { return logger.getResourceBundleName(); }
	
	@Override public boolean getUseParentHandlers() { return logger.getUseParentHandlers(); }
	
	@Override public boolean isLoggable(Level level) { return logger.isLoggable(level); }
	
	/**
	* {@inheritDoc}
	* <p>
	* Contract: should never throw any Throwable.
	* Instead, this implementation <i>logs robustly</i>.
	* It first calls {@link #logger}.log, and if that succeeds, then it returns with no further action.
	* However, if a Throwable is raised, then it tries to log record as well as the new Throwable to {@link System#err}.
	* Similarly, if the System.err code raises a Throwable, then it tries to log record and both Throwables to {@link System#out}).
	* Finally, if the System.out code raises a Throwable, then it is silently ignored.
	* <p>
	* This robust logging behavior was chosen because some users may want to log inside <code>finally</code> blocks.
	* In this case, logging should not throw a Throwable,
	* because that might skip other actions inside that <code>finally</code> block.
	*/
	@Override public void log(LogRecord record) {
		try {
			logger.log(record);
		}
		catch (Throwable t) {
			String msg = "";
			try {
				msg =
					"\n"
					+ "A Logger2 instance was asked to log this LogRecord:" + "\n"
					+ getRecordDescription(record) + "\n"
					+ "\n"
					+ "HOWEVER, when tried to log the above, this Throwable occurred:" + "\n"
					+ getThrowableDescription(t) + "\n";
				System.err.println(msg);
			}
			catch (Throwable t2) {
				try {
					msg +=
						"\n"
						+ "AND THEN, yet another Throwable occurred:" + "\n"
						+ getThrowableDescription(t2) + "\n";
					System.out.println(msg);
				}
				catch (Throwable t3) {
					// can do nothing if reach here...
				}
			}
		}
	}
		// NOTE: no need to override the remaining logging methods below: as per Logger javadocs, only need to override the fundamental log(LogRecord) method above, which the rest eventually call
	//public void config(String msg)
	//public void entering(...)
	//public void exiting(...)
	//public fine(String msg)
	//public finer(String msg)
	//public finest(String msg)
	//public void info(String msg)
	//public void log(...)
	//public void logp(...)
	//public void logrb(...)
	//public void severe(String msg)
	//public void throwing(String sourceClass, String sourceMethod, Throwable thrown)
	//public void warning(String msg)
	
	@Override public void removeHandler(Handler handler) { logger.removeHandler(handler); }
	
	@Override public void setFilter(Filter newFilter) { logger.setFilter(newFilter); }
	
	@Override public void setLevel(Level newLevel) {
		super.setLevel(newLevel);	// CRITICAL: must do this too in order to set the Logger.level field, since all the convenience logging methods directly access it instead of using the accessor Logger.getLevel
		logger.setLevel(newLevel);
	}
	
	@Override public void setParent(Logger parent) { logger.setParent(parent); }
	
	@Override public void setUseParentHandlers(boolean useParentHandlers) { logger.setUseParentHandlers(useParentHandlers); }
	
	// -------------------- getXXXDescription helper methods --------------------
	
	/** Contract: should never throw any Throwable. */
	private String getRecordDescription(LogRecord record) {
		try {
			return formatter.formatMessage(record);
		}
		catch (Throwable t) {
			return "ERROR: NO INFORMATION about the LogRecord is available, because the following Throwable was raised:" + "\n" + getThrowableDescription(t);
		}
	}
	
	/** Contract: should never throw any Throwable. */
	private String getThrowableDescription(Throwable t) {
		try {
			return ThrowableUtil.toString(t);
		}
		catch (Throwable t2) {
			return "ERROR: NO INFORMATION about the supplied Throwable is available, because a new Throwable was raised while processing the original";
		}
	}
	
	// -------------------- logIfNew --------------------
	
	/**
	* Simply calls <code>{@link #logIfNew(Level, String, String, String, Object[]) logIfNew}(level, sourceClass, sourceMethod, message, (Object[]) null)</code>.
	* <p>
	* @throws IllegalArgumentException if level == null; sourceClass is blank; sourceMethod is blank; message is blank;
	*/
	public void logIfNew(Level level, String sourceClass, String sourceMethod, String message) throws IllegalArgumentException {
		logIfNew(level, sourceClass, sourceMethod, message, (Object[]) null);
	}
	
	/**
	* Simply calls <code>{@link #logIfNew(Level, String, String, String, Object[]) logIfNew}(level, sourceClass, sourceMethod, message, new Object[] {parameter})</code>.
	* <p>
	* @throws IllegalArgumentException if level == null; sourceClass is blank; sourceMethod is blank; message is blank;
	*/
	public void logIfNew(Level level, String sourceClass, String sourceMethod, String message, Object parameter) throws IllegalArgumentException {
		logIfNew(level, sourceClass, sourceMethod, message, new Object[] {parameter});
	}
	
	/**
	* This method only logs the information if it produces a message which is unknown by this class.
	* It is useful when you wish to make just one log entry for an event which may occur many times,
	* in order to avoid clogging the logs with redundant information.
	* <p>
	* For optimal performance, this method immediately returns
	* if level is such that {@link #logger} would never even log it, since this is a quick test.
	* <p>
	* Otherwise, a new LogRecord is constructed from the params.
	* Its message then has localization and param substitution performed on it
	* in order to produce the actual message that might be logged.
	* This actual message is then compared against the messages know by this instance
	* <i>from a previous call to one of these logIfNew methods</i>.
	* If it is new, then the LogRecord is logged.
	* <p>
	* Side effect: if it is new, the actual message will be stored in an internal data structure of this class.
	* <i>This storage is permanent, which makes memory exhaustion possible.
	* So, use this method with great care in long running processes.</i>
<!--
+++ one cure: a Timer task that executes periodically (say every midnight)
to clear loggerToLoggedMessages; this should limit memory buildup
-->
	* <p>
	* @throws IllegalArgumentException if level == null; sourceClass is blank; sourceMethod is blank; message is blank
	*/
	public synchronized void logIfNew(Level level, String sourceClass, String sourceMethod, String message, Object[] parameters) throws IllegalArgumentException {
		Check.arg().notNull(level);
		Check.arg().notBlank(sourceClass);
		Check.arg().notBlank(sourceMethod);
		Check.arg().notBlank(message);
		// parameters may be anything, even null or empty
		
		if (!logger.isLoggable(level)) return;
		
		LogRecord record = new LogRecord(level, message);
		//record.setLevel(...);	// no need to call: value passed to the constructor will always be correct
		record.setLoggerName(logger.getName());
		//record.setMessage(...);	// no need to call: value passed to the constructor is currently correct
		//record.setMillis(...);	// no need to call: value set in the constructor is good
		record.setParameters(parameters);
		record.setResourceBundle( logger.getResourceBundle() );
		record.setResourceBundleName( logger.getResourceBundleName() );
		//record.setSequenceNumber(...);	// no need to call: value set in the constructor is good
		record.setSourceClassName(sourceClass);
		record.setSourceMethodName(sourceMethod);
		//record.setThreadID(...);	// no need to call: value set in the constructor is good
		//record.setThrown(...);	// currently this method does not take a Throwable; see comment after method
		
			// performation localization and param substitution to get the actual message:
		String messageActual = formatter.formatMessage(record);
		
		if (loggedMessages == null) loggedMessages = new HashSet<String>();
		if (!loggedMessages.contains(messageActual)) {
			loggedMessages.add(messageActual);
			
				// for optimal performance, update LogRecord so that the Handlers will not recompute localization and param substitution:
			record.setMessage(messageActual);
			record.setParameters(null);
			record.setResourceBundle(null);
			
			log(record);
		}
	}
/*
+++ possible future work:
	--add a Throwable param that is also to be logged
		--would need to decide if that param affects the newness or not...

	--add a String bundleName for a ResourceBundle just like the logrb method
		--reason why did not do this now is because the code to convert that String into a ResourceBundle
		(the Logger.findResourceBundle method) is currently private...
*/
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_log() throws Exception {
			Logger2 logger2 = getLogger2("testLogger");
			logger2.log( new LogRecordCrashes(Level.INFO, "dummy log message") );
			// should see out on System.err regarding the RuntimeException thrown by LogRecordCrashes.getLevel
		}
		
		@Test public void test_logIfNew() throws Exception {
			Logger2 logger2 = null;
			try {
				final AtomicInteger count = new AtomicInteger(0);
				Handler duplicateDetector = new Handler() {
					public void close() {}
					public void flush() {}
					public void publish(LogRecord record) { count.incrementAndGet(); }
				};
				
				logger2 = Logger2.getLogger2( "Logger2.UnitTest.test_logIfNew" );
				logger2.addHandler(duplicateDetector);
				
				logger2.logIfNew(Level.INFO, "LogUtil.UnitTest", "main", "If see this message more than once, then logIfNew failed");
				logger2.logIfNew(Level.INFO, "LogUtil.UnitTest", "main", "If see {0} more than once, then logIfNew failed", "this message");
				logger2.logIfNew(Level.INFO, "LogUtil.UnitTest", "main", "If see {0} more than {1}, then logIfNew failed", new String[] {"this message", "once"});
				
				String errMsg = "logIfNew failed: count.get() = " + count.get() + " != 1";
				Assert.assertTrue( errMsg, count.get() == 1 );
			}
			finally {
				LogUtil.close( logger2 );
			}
		}
		
		private static class LogRecordCrashes extends LogRecord {
			
			private static final long serialVersionUID = 1;
			
			private LogRecordCrashes(Level level, String msg) {
				super(level, msg);
			}
			
			public Level getLevel() {
				throw new RuntimeException("deliberately generated RuntimeException");
			}
			
		}
		
	}
	
}
