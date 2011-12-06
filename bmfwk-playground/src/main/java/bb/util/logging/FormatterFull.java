/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.io.ParseReader;
import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.DateUtil;
import static bb.util.StringUtil.newline;
import bb.util.ThrowableUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.text.ParseException;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.logging.XMLFormatter;
import org.junit.Assert;
import org.junit.Test;

/**
* Like {@link XMLFormatter}, this class uses almost all of the information in a {@link LogRecord} in its output
* and is ideal for a FileHandler to use.
* It differs in that it uses a plain text form, instead of xml, so that it is more human readable
* (e.g. by some operator pouring thru log files).
* See {@link #format format} for details.
* <p>
* The {@link Parser} inner class may be used to parse LogRecords from text
* which was generated from LogRecords formatted by this class.
* For example, this could be used by a program to analyse log files.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class FormatterFull extends Formatter {
	
	// -------------------- constants --------------------
	
	private static final String entrySeparator = "--------------------------------------------------";
	
	private static final String spacer = "    ";
	
	// -------------------- format --------------------
	
	/**
	* Formats record into a String that looks like:
	* <pre><code>
	* --------------------------------------------------
	* level    seq#<i>sss</i>    date    thread#<i>ttt</i>    className.methodName
	* [optional message, with any localization and param substitution applied]
	* [optional Throwable; first line is its class name and message, followed by its stack trace]
	* </code></pre>
	* <p>
	* Notes:
	* <ol>
	*  <li>
	*		<code><i>sss</i></code> is record's sequence number, which uniquely identifies it
	*		among every LogRecord known by LogManager
	*  </li>
	*  <li><code><i>ttt</i></code> is record's int thread ID value</li>
	*  <li>if the optional message is present, then localization and param substitution are perfomed by a call to {@link #formatMessage formatMessage}(record)</li>
	*  <li>a {@link bb.util.StringUtil#newline newline} is always appended to the message if it is non-null, so the user need not add this</li>
	* </ol>
	* <p>
	* This format was chosen for several reasons.
	* First, it contains all the useful information:
	* every "get" method of record is used except for getLoggerName and getResourceBundleName.
	* Second, it has a logical, top down order: all the metadata is in the top row,
	* followed by the actual data (message and Throwable).
	* Third, its compact form minimizes the number of lines.
	* Fourth, the dashed separator visually delimits LogRecords, making it easier for a human reader to parse the logs
	* <p>
	* @param record the LogRecord to be formatted; must not be null
	* @return a String representation of record
	* @throws IllegalArgumentException if record == null; if record's message, after formatting, contains {@link #entrySeparator}
	*/
	public String format(LogRecord record) throws IllegalArgumentException {
		Check.arg().notNull(record);
		
		StringBuilder sb = new StringBuilder(256);
		
		sb.append( entrySeparator ).append(newline);
		
		Date date = new Date( record.getMillis() );
		
		String className = record.getSourceClassName();
		if (className == null) className = "<unknown class>";
		
		String methodName = record.getSourceMethodName();
		if (methodName == null) methodName = "<unknown method>";
		
		sb.append( record.getLevel() )
			.append( spacer ).append( "seq#" ).append( record.getSequenceNumber() )
			.append( spacer ).append( DateUtil.getTimeStamp(date) )
			.append( spacer ).append( "thread#" ).append( record.getThreadID() )
			.append( spacer ).append( className ).append( '.' ).append( methodName )
			.append(newline);
		
		String message = formatMessage(record);	// NOTE: formatMessage is defined in the Formatter superclass; it internally calls record.getMessage, record.getResourceBundle(), and record.getParameters()
		if (message != null) {
			if (message.contains(entrySeparator)) throw new IllegalArgumentException("after formatting, record's message = " + message + " which contains entrySeparator = " + entrySeparator + " which is bad because it will screw up any subsequent parsing of LogRecords");
			sb.append( message ).append(newline);
		}
		
		Throwable t = record.getThrown();
		if (t != null) {
			sb.append( ThrowableUtil.toString(t) );
		}
		
		return sb.toString();
	}
	
	// -------------------- Parser (static inner class) --------------------
	
	/**
	* Class which parses {@link LogRecord}s from data generated by {@link #format format}.
	* Instances are constructed with a Reader (e.g. a FileReader) to the data,
	* and users may then call {@link #next next} to read off each LogRecord in sequence.
	* <p>
	* This class is not multithread safe.
	*/
	public static class Parser implements Closeable {
		
		private final ParseReader in;
		private final StringBuilder sb = new StringBuilder();
		private final LogRecordData data = new LogRecordData();
		
		/**
		* Constructor.
		* <p>
		* The final action is a call to {@link #confirmEntrySeparatorNext confirmEntrySeparatorNext},
		* which ensures that the user of this instance can immediately make a call to {@link #next next}.
		* <p>
		* @throws IllegalArgumentException if reader == null
		* @throws IOException if any I/O problem occurs
		* @throws IllegalStateException if confirmEntrySeparatorNext finds a problem
		*/
		public Parser(Reader reader) throws IllegalArgumentException, IOException, IllegalStateException {
			Check.arg().notNull(reader);
			
			if (reader instanceof ParseReader) in = (ParseReader) reader;
			else in = new ParseReader( reader );
			
			confirmEntrySeparatorNext();
		}
		
		private void confirmEntrySeparatorNext() throws IOException, IllegalStateException {
			String line = in.readLine();
			if (line == null) return;
			if (!line.equals(entrySeparator)) throw new IllegalStateException("the first line is not entrySeparator, as expected, but = " + line);
		}
		
		/**
		* This method assumes that either an entrySeparator has previously been read from in,
		* so that data for a LogRecord is next,
		* or that end of stream has been encountered.
		* If data is available on in, it parses that data,
		* and returns a newly constructed LogRecord instance that records the information as faithfully as possible.
		* Else it returns null.
		* <p>
		* The LogRecord must have been written in a format exactly equivalent to that
		* produced by the {@link #format format} method.
		* <p>
		* <i>The result returned by this method may not exactly equal the original LogRecord.</i>
		* In particular, it cannot undo localization and parameter substitution,
		* nor does it attempt to parse any Throwable information.
		* Instead, that information is simply added in its raw text form to the message field of the result.
		* <p>
		* @throws ParseException if any problem in parsing is encountered
		*/
		public LogRecord next() throws ParseException {
			try {
				sb.setLength(0);
				
				// there is no search for the next entrySeparator, as it should have been read either in confirmEntrySeparatorNext or parseLinesRemaining
				if (!parseLineSecond()) return null;
				parseLinesRemaining();
				
				LogRecord record = new LogRecord(data.level, data.message);
				record.setMillis(data.millis);
				record.setSequenceNumber(data.sequenceNumber);
				record.setSourceClassName(data.className);
				record.setSourceMethodName(data.methodName);
				record.setThreadID(data.threadId);
				return record;
			}
			catch (ParseException pe) {
				throw pe;
			}
			catch (Exception e) {
				throw new ParseException("The following Exception was caught while parsing: " + ThrowableUtil.getTypeAndMessage(e), in.getLineNumber());
			}
		}
		
		private boolean parseLineSecond() throws IOException, ParseException, NumberFormatException, IllegalArgumentException {
			String line = in.readLine();
			if (line == null) return false;
			
			String[] tokens = line.split(spacer, -1);
			if (tokens.length != 5) throw new ParseException("the line of text after the entry separator split into " + tokens.length + " tokens when it should have split into exactly 5 tokens", in.getLineNumber());
			
			data.level = LogUtil.parseLevel(tokens[0]);
			
			if (!tokens[1].startsWith("seq#")) throw new ParseException("tokens[1] of the line of text after the entry separator does not start with \"seq#\"", in.getLineNumber());
			tokens[1] = tokens[1].substring("seq#".length());	// skip over "seq#"
			data.sequenceNumber = Long.parseLong(tokens[1]);
			
			data.millis = DateUtil.parseTimeStamp(tokens[2]).getTime();
			
			if (!tokens[3].startsWith("thread#")) throw new ParseException("tokens[3] of the line of text after the entry separator does not start with \"thread#\"", in.getLineNumber());
			tokens[3] = tokens[3].substring("thread#".length());	// skip over "thread#"
			data.threadId = Integer.parseInt(tokens[3]);
			
			int i = tokens[4].lastIndexOf('.');
			if (i == -1) throw new ParseException("failed to find the '.' char between the class and method name: " + tokens[4], in.getLineNumber());
			if (i == tokens[4].length() - 1) throw new ParseException("the text that should be the class and method name ends in a '.' char: " + tokens[4], in.getLineNumber());
			data.className = tokens[4].substring(0, i);
			data.methodName = tokens[4].substring(i + 1, tokens[4].length());
			
			return true;
		}
		
		/**
		* As per the {@link #next next} contract,
		* will lump any remaining info into {@link #data}.{@link LogRecordData#message}.
		*/
		private void parseLinesRemaining() throws IOException {
			while (true) {
				String line = in.readLine();
				if (line == null) {	// encountered end of stream
					break;
				}
				else if (line.equals(entrySeparator)) {
					break;
				}
				else {
					sb.append(line).append(newline);
				}
			}
			
			if (sb.length() > 0) {
				int lengthToUse = sb.length() - newline.length();	// need to NOT include the final newline as part of message, since it was added by format and is not part of the original
				data.message = sb.substring(0, lengthToUse);
			}
		}
		
		/** Closes all resources used by this instance. */
		@Override public void close() {
			StreamUtil.close(in);
		}
		
		private static class LogRecordData {
			private Level level;
			private long sequenceNumber;
			private long millis;
			private int threadId;
			private String className;
			private String methodName;
			private String message;
			
			private LogRecordData() {}
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_LogRecordParser() throws Exception {
			Logger logger = null;
			FormatterFull.Parser parser = null;
			try {
				FormatterFull logFormatterFull = new FormatterFull();
				
				ConsoleHandler consoleHandler = new ConsoleHandler();
				consoleHandler.setFormatter( logFormatterFull );
				consoleHandler.setLevel(Level.ALL);
				
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				
				StreamHandler streamHandler = new StreamHandler( baos, logFormatterFull );
				streamHandler.setLevel(Level.ALL);
				
				logger = Logger.getAnonymousLogger();
				logger.setLevel(Level.ALL);
				logger.setUseParentHandlers(false);
				LogUtil.removeHandlers(logger);
				logger.addHandler(consoleHandler);
				logger.addHandler(streamHandler);
				
				System.err.println();
				System.err.println("Below is an echo of the LogRecords formatted by FormatterFull:");
				logger.entering("FormatterFull.UnitTest", "main", new String[] {"param#1", "param#2", "param#3", "param#4", "param#5", "param#6", "param#7", "param#8", "param#9"} );
				logger.exiting("FormatterFull.UnitTest", "main", "result" );
				logger.throwing("FormatterFull.UnitTest", "main", new Exception("deliberately generated Exception", new Exception("with a cause too")) );
				logger.logp(Level.INFO, null, null, "logging null class and method names");
				logger.logp(Level.INFO, "FormatterFull.UnitTest", "main", "Message param {0} works: {1}", new String[] {"substitution", "YES!"} );
				logger.logp(Level.INFO, "FormatterFull.UnitTest", "main", "logging an Exception", new Exception("deliberately generated Exception", new Exception("with a cause too")));
				for (Level level : LogUtil.getLevels()) {
					logger.logp(level, "FormatterFull.UnitTest", "main", "logging level = " + level.toString());
				}
				LogUtil.flush( logger );
				
				byte[] logBytes1 = baos.toByteArray();
				
				InputStreamReader isr = new InputStreamReader( new ByteArrayInputStream( logBytes1 ) );
				parser = new FormatterFull.Parser( isr );
				baos.reset();
				PrintWriter pw = new PrintWriter(baos);
				for (LogRecord record = parser.next(); record != null; record = parser.next()) {
					pw.print( logFormatterFull.format(record) );
				}
				pw.close();
				
				byte[] logBytes2 = baos.toByteArray();
				
				String[] lines1 = toLines(logBytes1);
				String[] lines2 = toLines(logBytes2);
				String errMsg = "FormatterFull.Parser failed to accurately reproduce in text form the LogRecords: lines1.length = " + lines1.length + " != lines2.length = " + lines2.length;
				Assert.assertTrue( errMsg, lines1.length == lines2.length );
				for (int i = 0; i < lines1.length; i++) {
					errMsg = "FormatterFull.Parser failed to accurately reproduce in text form the LogRecords: lines1[" + i + "] = " + lines1[i] + " !equals lines2[" + i + "] = " + lines2[i];
					Assert.assertEquals( errMsg, lines1[i], lines2[i] );
				}
			}
			finally {
				LogUtil.close( logger );
				StreamUtil.close(parser);
			}
		}
		
		private String[] toLines(byte[] bytes) {
			return new String(bytes).split("(\r\n|\n|\r)", -1);
		}
		
	}
	
}
