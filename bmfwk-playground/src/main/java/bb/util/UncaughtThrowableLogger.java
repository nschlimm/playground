/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import bb.io.FileUtil;
import static bb.util.StringUtil.newline;
import bb.util.logging.LogUtil;
import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
* Logs all otherwise uncaught Throwables to a Logger.
* <p>
* This class is multithread safe: the types of its state are individually multithread safe, and are used such that only their individual multithread safety matters.
* <p>
* @author Brent Boyer
* @see <a href="http://java.sun.com/developer/JDCTechTips/2006/tt0211.html#2">Catching Uncaught Exceptions</a>
*/
public class UncaughtThrowableLogger implements Thread.UncaughtExceptionHandler {
	
	// -------------------- fields --------------------
	
	/**
	* Logger where all Throwables will get logged to.
	* <p>
	* Contract: this field is never null.
	*/
	private final Logger logger;
	
	// -------------------- constructor --------------------
	
	/** Convenience constructor that simply calls <code>{@link #UncaughtThrowableLogger(Logger) this}( {@link LogUtil#getLogger2} )</code>. */
	public UncaughtThrowableLogger() {
		this( LogUtil.getLogger2() );
	}
	
	/**
	* Fundamental constructor.
	* Uses logger to record all otherwise uncaught Throwables.
	* <p>
	* @throws IllegalArgumentException if logger == null
	*/
	public UncaughtThrowableLogger(Logger logger) throws IllegalArgumentException {
		Check.arg().notNull(logger);
		
		this.logger = logger;
	}
	
	// -------------------- Thread.UncaughtExceptionHandler api --------------------
	
	public void uncaughtException(Thread thread, Throwable throwable) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("AN UNCAUGHT Throwable HAS BEEN DETECTED").append(newline);
		
		String threadInfo = (thread != null) ? ThreadUtil.toString(thread) : "UNKNOWN (null was supplied)" + newline;
		sb.append("Thread reporting the uncaught Throwable: ").append(threadInfo);
		
		String throwableInfo = (throwable != null) ? ThrowableUtil.toString(throwable) : "UNKNOWN (null was supplied)" + newline;
		sb.append("Uncaught Throwable: ").append(throwableInfo);
		
		String msg = sb.toString();
		
		logger.logp(Level.SEVERE, "UncaughtThrowableLogger", "uncaughtException", msg);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static final String threadnameNew = "Thread executing UncaughtThrowableLogger.UnitTest";
		private static final String exceptionMsg = "deliberately generated test Exception";
		
		private String threadnameOriginal;
		private File logFile;
		private Logger logger;
		private UncaughtThrowableLogger uncaughtThrowableLogger;
		
		@Before public void setUp() throws Exception {
			threadnameOriginal = Thread.currentThread().getName();
			Thread.currentThread().setName(threadnameNew);
			
			logFile = new File(LogUtil.getLogDirectory(), "UncaughtThrowableLogger.UnitTest.log");
			
			Handler handler = new FileHandler(logFile.getPath());
			handler.setLevel(Level.ALL);
			
			logger = Logger.getAnonymousLogger();
			logger.setLevel(Level.ALL);
			LogUtil.removeHandlers(logger);
			logger.addHandler(handler);
			
			uncaughtThrowableLogger = new UncaughtThrowableLogger(logger);
		}
		
		@After public void tearDown() throws Exception {
			LogUtil.close(logger);
			FileUtil.delete(logFile);
			
			Thread.currentThread().setName(threadnameOriginal);
		}
		
		@Test public void test_uncaughtException_shouldPass1() throws Exception {
			System.out.println();
			System.out.println("Calling uncaughtException with the current thread and a deliberately generated Exception:");
			uncaughtThrowableLogger.uncaughtException(Thread.currentThread(), new Exception(exceptionMsg));
			confirmLogFileContains(threadnameNew);
			confirmLogFileContains(exceptionMsg);
		}
		
		@Test public void test_uncaughtException_shouldPass2() throws Exception {
			System.out.println();
			System.out.println("Calling uncaughtException with the current thread and a null Throwable:");
			uncaughtThrowableLogger.uncaughtException(Thread.currentThread(), null);
			confirmLogFileContains(threadnameNew);
			confirmLogFileContains("Throwable: UNKNOWN (null was supplied)");
		}
		
		@Test public void test_uncaughtException_shouldPass3() throws Exception {
			System.out.println();
			System.out.println("Calling uncaughtException with a null thread and a deliberately generated Exception:");
			uncaughtThrowableLogger.uncaughtException(null, new Exception(exceptionMsg));
			confirmLogFileContains("Thread reporting the uncaught Throwable: UNKNOWN (null was supplied)");
			confirmLogFileContains(exceptionMsg);
		}
		
		@Test public void test_uncaughtException_shouldPass4() throws Exception {
			System.out.println();
			System.out.println("Calling uncaughtException with a null thread and a null Throwable:");
			uncaughtThrowableLogger.uncaughtException(null, null);
			confirmLogFileContains("Thread reporting the uncaught Throwable: UNKNOWN (null was supplied)");
			confirmLogFileContains("Throwable: UNKNOWN (null was supplied)");
		}
		
		private void confirmLogFileContains(String s) throws Exception {
			String logContents = FileUtil.readString(logFile);
			if (!logContents.contains(s)) throw new Exception(logFile.getPath() + " fails to contain the String \"" + s + "\"");
		}
		
	}
	
}
