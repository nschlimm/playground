/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer notes:

+++ should i use the new System.nanoTime and goto nanosecond resolution?

*/


package bb.util;


/**
* This class provides a simple latch that is set once a specified amount of time has elapsed.
* The amount of time that must elapse is supplied to the constructor,
* and the {@link #latched latched} method may be queried to determine if this time has elapsed.
* <p>
* The original motivation for this class was for polling applications
* where the polling should only be tried for a maximum amount of time before it is considered to have failed.
* <p>
* <b>Warning:</b> this class may not operate properly if some external process resets the system clock.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class TimeElapsedLatch {


	// -------------------- instance fields --------------------
	
	
	private final long timeStarted = System.currentTimeMillis();
	
	
	private final long timeElapsedTillLatch;
	
	
	// -------------------- constructor --------------------
	
	
	/**
	* Constructor.
	* <p>
	* @param timeElapsedTillLatch the amount of time (in milliseconds) that must elapse till this instance will latch
	* @throws IllegalArgumentException if timeElapsedTillLatch < 0
	*/
	public TimeElapsedLatch(long timeElapsedTillLatch) throws IllegalArgumentException {
		Check.arg().notNegative(timeElapsedTillLatch);

		this.timeElapsedTillLatch = timeElapsedTillLatch;
	}


	// -------------------- latched --------------------
	
	
	/**
	* Returns true once the specified amount of time has elapsed, false until then.
	*/
	public boolean latched() {
		long timeNow = System.currentTimeMillis();
		long timeDelta = timeNow - timeStarted;
		return (timeDelta > timeElapsedTillLatch);
	}
	
	
}
