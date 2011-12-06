/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.util.Check;
import static bb.util.StringUtil.newline;
import bb.util.ThrowableUtil;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.junit.Test;

/**
* Like {@link SimpleFormatter}, this class uses only the essential information in a {@link LogRecord} in its output
* and is ideal for a ConsoleHandler to use.
* It differs in that it uses an even more abbreviated format, using just one line of text in most cases.
* See {@link #format format} for details.
* <p>
* This class is multithread safe: every public method which uses the non-multithread safe field {@link #hourFormat} is synchronized.
* <p>
* @author Brent Boyer
*/
public class FormatterShort extends Formatter {
	
	// -------------------- constants --------------------
	
	private static final String spacer = "  ";
	
	private static final DateFormat hourFormat = new SimpleDateFormat("HH:mm:ss");
	
	// -------------------- format --------------------
	
	/**
	* Formats record into a String that looks like:
	* <pre><code>
	* --------------------------------------------------
	* level  timeOfDay  className.methodName  [optional message, with any localization and param substitution applied]
	* [optional Throwable; first line is its class name and message, followed by its stack trace]
	* </code></pre>
	* <p>
	* This format was chosen for two reasons.
	* First, it contains the essential information.
	* Second, uses only one line of text in the normal case (no Throwable).
	* <p>
	* @param record the LogRecord to be formatted; must not be null
	* @return a String representation of record
	* @throws IllegalArgumentException if record == null
	*/
	public synchronized String format(LogRecord record) throws IllegalArgumentException {
		Check.arg().notNull(record);
		
		StringBuilder sb = new StringBuilder(256);
		
		Date date = new Date( record.getMillis() );
		
		sb.append( record.getLevel() ).append( spacer ).append( hourFormat.format(date) );
			
		String className = record.getSourceClassName();
		if (className == null) className = "<unknown class>";
		
		String methodName = record.getSourceMethodName();
		if (methodName == null) methodName = "<unknown method>";
		
		sb.append( spacer ).append( className ).append( '.' ).append( methodName );
		
		String message = formatMessage(record);	// NOTE: formatMessage is defined in the Formatter superclass; it internally calls record.getMessage, record.getResourceBundle(), and record.getParameters()
		if (message != null) {
			sb.append( spacer ).append( message ).append(newline);
		}
		else {
			sb.append(newline);
		}
		
		Throwable t = record.getThrown();
		if (t != null) {
			sb.append( ThrowableUtil.toString(t) );
		}
		
		return sb.toString();
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_format() {
			Logger logger = null;
			try {
				FormatterShort logFormatterShort = new FormatterShort();
				
				ConsoleHandler consoleHandler = new ConsoleHandler();
				consoleHandler.setFormatter( logFormatterShort );
				consoleHandler.setLevel(Level.ALL);
				
				logger = Logger.getAnonymousLogger();
				logger.setLevel(Level.ALL);
				logger.setUseParentHandlers(false);
				LogUtil.removeHandlers(logger);
				logger.addHandler(consoleHandler);
				
				System.err.println();
				System.err.println("Below is an echo of the LogRecords formatted by FormatterShort:");
				logger.entering("FormatterShort.UnitTest", "main", new String[] {"param#1", "param#2", "param#3", "param#4", "param#5", "param#6", "param#7", "param#8", "param#9"} );
				logger.exiting("FormatterShort.UnitTest", "main", "result" );
				logger.throwing("FormatterShort.UnitTest", "main", new Exception("deliberately generated Exception", new Exception("with a cause too")) );
				logger.logp(Level.INFO, null, null, "logging null class and method names");
				logger.logp(Level.INFO, "FormatterShort.UnitTest", "main", "Message param {0} works: {1}", new String[] {"substitution", "YES!"} );
				logger.logp(Level.INFO, "FormatterShort.UnitTest", "main", "logging an Exception", new Exception("deliberately generated Exception", new Exception("with a cause too")));
				for (Level level : LogUtil.getLevels()) {
					logger.logp(level, "FormatterShort.UnitTest", "main", "logging level = " + level.toString());
				}
				LogUtil.flush( logger );
			}
			finally {
				LogUtil.close( logger );
			}
		}
		
	}
	
}
