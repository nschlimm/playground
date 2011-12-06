/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer's notes:

+++ change this class from console output to a gui, with graphical progress bar?

*/


package bb.util;


import bb.io.ConsoleUtil;


/**
* Class that stores the amount of progress completed by some other process.
* When created (by calling {@link #begin begin}), this class starts a new internal thread that
* mostly sleeps but wakes up periodicly and prints out the amount of progress.
* The external process is responsible for updating the amount of progress by calling {@link #setPercentCompleted setPercentCompleted}.
* <p>
* This class is multithread safe: every public method is synchronized.
* <p>
* @author Brent Boyer
*/
public class ProgressIndicator {


	private final StatusUpdater statusUpdater = new StatusUpdater();
	private double percentCompleted = 0.0;


	private ProgressIndicator() {}


	public static ProgressIndicator begin() {
		ProgressIndicator pi = new ProgressIndicator();
		pi.statusUpdater.start();
		return pi;
	}


	public synchronized void end() {
		try {
			statusUpdater.interrupt();
			statusUpdater.join();
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();	// never swallow this; see http://www-128.ibm.com/developerworks/java/library/j-jtp05236.html
		}
	}


	public synchronized double getPercentCompleted() { return percentCompleted; }


	/**
	* @param percentCompleted the percentage of the overall effort that has been completed; must be expressed decimally
	* @throws IllegalArgumentException if percentCompleted < 0.0 or percentCompleted > 1.0
	*/
	public synchronized void setPercentCompleted(double percentCompleted) throws IllegalArgumentException {
		Check.arg().notNegative(percentCompleted);
		if (percentCompleted > 1.0) throw new IllegalArgumentException("percentCompleted = " + percentCompleted + " is > 1.0");

		this.percentCompleted = percentCompleted;
	}


	private class StatusUpdater extends Thread {
		
		private static final long sleepTime = 1*1000;
	
		public void run() {
			long timeStart = System.currentTimeMillis();
			while (true) {
				try {
					Thread.sleep(sleepTime);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();	// never swallow this; see http://www-128.ibm.com/developerworks/java/library/j-jtp05236.html
					break;
				}
				double percentCompleted = getPercentCompleted();
				if (percentCompleted == 1.0) break;
				long timeNow = System.currentTimeMillis();
				long timeElapsed = (timeNow - timeStart);
				long timeRemaining = (long) (timeElapsed * (1 - percentCompleted) / percentCompleted);
				ConsoleUtil.overwriteLine("Completed: " + (percentCompleted*100.0) + "%  Elapsed: " + timeElapsed + "  Remaining: " + timeRemaining);
			}
			ConsoleUtil.eraseLine();
		}
		
	}


}
