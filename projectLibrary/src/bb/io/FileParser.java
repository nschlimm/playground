/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.io;


import bb.util.Check;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
* Many file formats consist of lines of data, with tokens of data on each line being separated by a constant set of delimiters.
* Familiar examples are tab, space, and comma delimited files.
* This class was written to aid the parsing of such file types.
* <p>
* You simply construct an instance for the desired file,
* along with regular expressions for the delimiter token(s) and nondata lines (e.g. comment or blank lines).
* Then you may repeatedly call {@link #readDataLine readDataLine} and process the data.
* When finished, call {@link #close close}.
* <p>
* <b>Warning:</b> parsing of files like tab, space, and comma delimited files may be a lot more complicated if
* the tokens themselves may contain any of the token delimiters. In this case, you will need to know
* how the delimiter is escaped so that it can appear inside a token (e.g. Excel may put double quotes around tokens).
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class FileParser implements Closeable {


	private final File file;
	private final ParseReader in;
	private final Pattern tokenDelimiterPattern;
	private final Pattern nondataLinePattern;

	private int lastLineNumber = -1;


	/**
	* Constructor.
	* <p>
	* @param tokenDelimiterRegexp regular expression to match token delimiters
	* (e.g. "[ ]+|[\\t,]" matches one or more spaces, or a single tab or comma)
	* @param nondataLineRegexp regular expression to nondata lines
	* (e.g. "#.*|\\s*" matches any line which starts with '#' or which is empty or all whitespace);
	* may be null in which case every line is treated as a data line
	* @throws IllegalArgumentException if file is null, does not exist,
	* is a directory, or if it refers to a file that cannot be read by this application;
	* tokenDelimiterRegexp == null
	* @throws IllegalStateException if file holds more than {@link Integer#MAX_VALUE} bytes (which cannot be held in a java array)
	* @throws SecurityException if a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to file
	* @throws IOException if an I/O problem occurs
	* @throws UnsupportedEncodingException if the default char encoding used by ParseReader is not supported (this should never happen)
	* @throws PatternSyntaxException if either regex's syntax is invalid
	*/
	public FileParser(File file, String tokenDelimiterRegexp, String nondataLineRegexp) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException, UnsupportedEncodingException, PatternSyntaxException {
		Check.arg().notNull(file);
		Check.arg().notNull(tokenDelimiterRegexp);

		this.file = file;
//		this.in = new ParseReader(file);
		this.in = new ParseReader( new ByteArrayInputStream( FileUtil.readBytes(file) ) );	// is faster to read all the bytes at once
		this.tokenDelimiterPattern = Pattern.compile(tokenDelimiterRegexp);
		this.nondataLinePattern = (nondataLineRegexp != null) ? Pattern.compile(nondataLineRegexp) : null;
	}


	/**
	* Reads the next line of data for the file, parses all the tokens on that line (using tokenDelimiterRegexp), and returns them.
	* Any nondata lines encountered are skipped over.
	* If end of file is encountered, then null is returned.
	*/
	public String[] readDataLine() throws IOException {
		String line;
		do {
			lastLineNumber = in.getLineNumber();
			line = in.readLine();
			if (line == null) return null;
		} while (isNonDataLine(line));

		return tokenDelimiterPattern.split(line);
	}


	public boolean isNonDataLine(String line) {
		return (nondataLinePattern != null) ? nondataLinePattern.matcher(line).matches() : false;
	}


	/**
	* Returns the location (line # and file path) associated with the <i>previous</i> call to {@link #readDataLine readDataLine}.
	* Typically use this method when reporting errors associated with the data obtained from that call.
	* <p>
	* @throws IllegalStateException if getLocation called before readDataLine has ever been called
	*/
	public String getLocation() throws IllegalStateException {
		if (lastLineNumber == -1) throw new IllegalStateException("getLocation called before readDataLine has ever been called");
		return "line number " + lastLineNumber + " of file " + file.getPath();
	}


	/** Closes all resources associated with the parsing. */
	@Override public void close() {
		StreamUtil.close(in);
	}


}
