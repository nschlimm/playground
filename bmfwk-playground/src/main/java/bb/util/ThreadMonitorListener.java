/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;

/**
* Interface for receiving events from {@link ThreadMonitor}.
* <p>
* As with typical listener classes,
* implementations should also catch and handle any Throwables generated inside these methods
* since ThreadMonitor has little idea how to handle them.
* <p>
* Implementations should be multithread safe,
* since ThreadMonitor makes no guarantee which thread(s) will call these methods.
* <p>
* @author Brent Boyer
*/
public interface ThreadMonitorListener {
	
	/**
	* Called when the associated ThreadMonitor instance has just started monitoring.
	* This method will be called repeatedly if ThreadMonitor goes thru multiple start and stop cycles.
	*/
	void onMonitoringStarted();
	
	/**
	* Called when the associated ThreadMonitor instance has just stopped monitoring.
	* This method will be called repeatedly if ThreadMonitor goes thru multiple start and stop cycles.
	*/
	void onMonitoringStopped();
	
	/**
	* Called when the associated ThreadMonitor instance has detected some error while monitoring.
	* <p>
	* @param t the Throwable which caused the error
	*/
	void onMonitoringError(Throwable t);
	
	/**
	* Called whenever the associated ThreadMonitor instance has measured a new thread state.
	* This method will be called repeatedly.
	* <p>
	* @param state the entire thread state which was just measured
	*/
	void onThreadState(String state);
	
	/**
	* Called whenever the associated ThreadMonitor instance first detects deadlock
	* when previously it had detected no deadlock.
	* This method will be called repeatedly if the JVM cycles in and out of deadlock.
	* <p>
	* @param state the thread state of just the deadlocked threads
	*/
	void onDeadlocked(String state);
	
	/**
	* Called whenever the associated ThreadMonitor instance first detects no deadlock
	* when previously it had detected deadlock.
	* This method will be called repeatedly if the JVM cycles in and out of deadlock.
	* <p>
	* @param state the entire thread state which was just measured
	*/
	void onNotDeadlocked(String state);
	
}
