/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer Notes:
--one major problem with the code below is that it uses ActionEvent.getWhen which is a 1.4+ only method
--need to rework the code that deals with diagnosis of duplicate events
*/

package bb.gui;

import bb.util.Check;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
* Abstract implementation of ActionListener.
* This class does not implement actionPerformed (the sole method of ActionListener);
* that is for concrete subclasses to implement (and is why this class is abstract).
* Instead, this class's {@link #isDuplicateEvent isDuplicateEvent} method handles erroneous duplicate ActionEvents.
* <p>
* One known type of dupilcate event is with java.awt.Buttons:
* if the user clicks once on a Button only one event should be fired,
* but known bugs in AWT can cause multiple events to be fired.
* This class attempts to identify such duplicate events.
* <p>
* Application of this class is trivial. Old code that looks like
* <pre><code>
*	someButton.addActionListener(
*		new ActionListener() {
*			public void actionPerformed(ActionEvent actionEvent) {
*				//action handling here...
*			}
*		}
*	);
* </code></pre>
* can be replaced with
* <pre><code>
*	someButton.addActionListener(
*		new DuplicateEventActionListener() {	// see DuplicateEventActionListener javadocs for why have to use it...
*			public void actionPerformed(ActionEvent actionEvent) {
*				if (isDuplicateEvent(actionEvent)) return;
*				//action handling here...
*			}
*		}
*	);
* </code></pre>
* if you wish to ignore duplicate events.
* <p>
* <b>Warning</b>: the current implementation of this class expects each instance to be associated
* with just one source of events (e.g. a single Button, as in the above code sample).
* <p>
* Like typical Java GUI code, this class is not multithread safe:
* it expects to only be called by {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}.
* This threading limitation is checked in every public method.
* <p>
* @see <a href="http://developer.java.sun.com/developer/bugParade/bugs/4531849.html">Sun bug report</a>
* @see <a href="http://saloon.javaranch.com/cgi-bin/ubb/ultimatebb.cgi?ubb=get_topic&f=34&t=000500">Java Ranch discussion</a>
* @author Brent Boyer
*/
public abstract class DuplicateEventActionListener implements ActionListener {
	
	/**
	* Specifies the maximum time difference allowed between 2 events for them to be considered duplicate events.
	* Value is in milliseconds.
	*/
	protected static final long duplicateEventMaxTimeDifference = 75;	// value determined experimentally (see the printInfo stuff below); hopefully this will not vary too much across platforms...
	
	/**
	* Occurence of the last event registered by this instance.
	* Value is in milliseconds.
	* Is initialized to 0 (i.e. "the epoch": January 1, 1970, 00:00:00 GMT).
	*/
	protected long lastEventTime = 0;
	
	protected long minimumNonDuplicateTimeDifference = Long.MAX_VALUE;
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalStateException if calling thread is not {@link EventQueue}'s {@link EventQueue#isDispatchThread dispatch thread}
	*/
	public DuplicateEventActionListener() throws IllegalStateException {
		Check.state().edt();
	}
	
	/** Must be implemented by concrete subclass. */
	public abstract void actionPerformed(ActionEvent ae);
	
	/**
	* Determines if this is a duplicate event or not by comparing the difference of
	* when actionEvent occured and {@link #lastEventTime}
	* with {@link #duplicateEventMaxTimeDifference}.
	* A side effect is that {@link #lastEventTime} is updated
	* to when actionEvent occured before this method returns.
	* <p>
	* @throws IllegalArgumentException if actionEvent is null
	*/
	protected boolean isDuplicateEvent(ActionEvent actionEvent) throws IllegalArgumentException {
		Check.arg().notNull(actionEvent);
		
		long currentEventTime = actionEvent.getWhen();
		long timeDifference = currentEventTime - lastEventTime;
		boolean isDuplicate = (timeDifference <= duplicateEventMaxTimeDifference);
		//printInfo(timeDifference, isDuplicate);	// UNCOMMENT THIS LINE if want to print out info to help you decide on the value of duplicateEventMaxTimeDifference

		lastEventTime = currentEventTime;
		return isDuplicate;
	}
	
	protected void printInfo(long timeDifference, boolean isDuplicate) {
		if (isDuplicate)
			System.out.println("DUPLICATE EVENT (timeDifference = " + timeDifference + " ms)");
		else {
			System.out.println();
			System.out.println("Non-duplicate event (timeDifference = " + timeDifference + " ms)");

			if (timeDifference < minimumNonDuplicateTimeDifference) {
				minimumNonDuplicateTimeDifference = timeDifference;
				System.out.println("(NOTE: new minimumNonDuplicateTimeDifference = " + minimumNonDuplicateTimeDifference + " ms)");
			}
		}
	}
	
}
