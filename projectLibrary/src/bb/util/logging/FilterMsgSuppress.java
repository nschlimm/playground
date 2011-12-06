/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ need a way to unsupress substrings too!
	--also modify DialogMsgSuppress to have a new button that when pressed, displays the set of suppressed substrings, and allows the user to select one and have it be removed from the set
*/

package bb.util.logging;

import bb.util.Check;
import bb.util.StringUtil;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
* {@link Filter} implementation which rejects LogRecords if their message contains a known bad substring.
* <p>
* <i>Note that the only state, {@link #msgsSuppressed}, is a static field, and therefore shared by all instances.</i>
* This behavior was chosen because there was a need, when this class was first written, to have the same substring filter apply to several different Handlers
* (the {@link HandlerConsole} and {@link HandlerGui} instances which are usually attached to the root Logger; see this project's logging.properties file for example configuration).
* <p>
* One way that a user can configure this class is via {@link DialogMsgSuppress}.
* <p>
* This class is multithread safe: its only state, {@link #msgsSuppressed}, is a multithread safe type.
* <p>
* @author Brent Boyer
*/
public class FilterMsgSuppress implements Filter {
	
	// -------------------- static fields --------------------
	
	/**
	* Stores all the message substrings which identify messages to be suppressed.
	* <p>
	* Contract: is never null, and must be a multithread safe type.
	*/
	private static final Set<String> msgsSuppressed = new CopyOnWriteArraySet<String>();
	
	// -------------------- suppressMsg --------------------
	
	/**
	* Adds msg to {@link #msgsSuppressed}.
<!--
* Before adding msg, it is first converted to lower case,
* which is part of what makes the substring search in {@link #isLoggable isLoggable} case insensitive.
-->
	* <p>
	* @throws IllegalArgumentException if msg is {@link Check#notBlank blank}
	*/
	public static void suppressMsg(String msg) throws IllegalArgumentException {
		Check.arg().notBlank(msg);
		
//		msg = msg.toLowerCase();
		msgsSuppressed.add(msg);
	}
	
	// -------------------- format --------------------
	
	/**
	* First gets record's {@link LogRecord#getMessage raw message}, that is, <i>before localization or formatting</i>.
	* Immediately returns true if that message is {@link StringUtil#isBlank blank}.
	* Else, searches thru every element of {@link #msgsSuppressed}, returning false if message contains any element as a substring.
<!--
* Before the search starts, the message is first converted to lower case, which effectively makes the substring search case insensitive.
-->
	* <p>
	* @throws IllegalArgumentException if record == null
	*/
	public boolean isLoggable(LogRecord record) throws IllegalArgumentException {
		Check.arg().notNull(record);
		
		String msg = record.getMessage();
		if (StringUtil.isBlank(msg)) return true;
//		msg = msg.toLowerCase();
		for (String supp : msgsSuppressed) {
			if (msg.contains(supp)) return false;
		}
		return true;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed: can be tested by the test classes of other classes, such as HandlerGui
	
}
