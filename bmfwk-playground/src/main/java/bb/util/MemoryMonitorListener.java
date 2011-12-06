/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;

import java.io.Closeable;

/**
* Interface for receiving events from {@link MemoryMonitor}.
* <p>
* As with typical listener classes,
* implementations should also catch and handle any Throwables generated inside these methods
* since MemoryMonitor has little idea how to handle them.
* <p>
* Implementations should be multithread safe,
* since MemoryMonitor makes no guarantee which thread(s) will call these methods.
* <p>
* @author Brent Boyer
*/
public interface MemoryMonitorListener extends Closeable {
	
	/**
	* Called when the associated MemoryMonitor instance has just started monitoring.
	* This method will be called repeatedly if MemoryMonitor goes thru multiple start and stop cycles.
	*/
	void onMonitoringStarted();
	
	/**
	* Called when the associated MemoryMonitor instance has just stopped monitoring.
	* This method will be called repeatedly if MemoryMonitor goes thru multiple start and stop cycles.
	*/
	void onMonitoringStopped();
	
	/**
	* Called when the associated MemoryMonitor instance has detected some error while monitoring.
	* <p>
	* @param t the Throwable which caused the error
	*/
	void onMonitoringError(Throwable t);
	
	/**
	* Called whenever the associated MemoryMonitor instance has measured a new memory state.
	* This method will be called repeatedly.
	* <p>
	* @param state the MemoryState which was just measured
	*/
	void onMemoryState(MemoryState state);
	
	/**
	* Called whenever the associated MemoryMonitor instance first detects the low memory state
	* when previously it had detected a not low memory state.
	* This method will be called repeatedly if the memory cycles in and out of low memory.
	* <p>
	* @param state the MemoryState for which low memory was just detected
	*/
	void onMemoryLow(MemoryState state);
	
	/**
	* Called whenever the associated MemoryMonitor instance first detects a not low memory state
	* when previously it had detected the low memory state.
	* This method will be called repeatedly if the memory cycles in and out of low memory.
	* <p>
	* @param state the MemoryState for which not low memory was just detected
	*/
	void onMemoryNotLow(MemoryState state);

	/** Called when the associated MemoryMonitor is being closed. */
	@Override void close();
	
}
