/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.gui.Sounds;
import bb.util.Check;
import bb.util.Execute;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
* Plays a major error sound if a {@link Level#SEVERE} LogRecord is encountered,
* plays a minor error sound if a {@link Level#WARNING} LogRecord is encountered,
* or plays a notify sound if any other LogRecord is encountered.
* (A LogRecord is "encountered" if it is passed to {@link #publish publish} and publish accepts it for publication.)
* If, while the sound is playing, more LogRecords arrive, then the worst Level seen during this time determines what the next sound will be.
* <p>
* It can be useful to attach an instance to the root Logger, and have it publish serious logs.
* <p>
* <i>It is unclear whether or not this class is multithread safe.</i>
* There may be issues with its superclass; see {@link HandlerAbstract} for more discussion.
* Concerning the state added by this class, every method is synchronized.
* <p>
* @author Brent Boyer
*/
public class HandlerAudio extends HandlerAbstract {
	
	// -------------------- instance fields --------------------
	
	/**
	* Stores the set of Levels encountered by {@link #publish publish} since the last call to {@link #getLevelWorst getLevelWorst}.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private Set<Level> levels = new HashSet<Level>();
	
	/**
	* Executes a {@link SoundPlayer} instance.
	* <p>
	* Contract: is null until lazy initialized by {@link #publish publish}, then is nulled out once and for all when {@link #close close} is first called.
	*/
	private Thread threadPlayer;
	
	// -------------------- constructor --------------------
	
	/**
	* Constructor.
	* <p>
	* All this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure}.
	*/
	public HandlerAudio() {
		configure();
	}
	
	// -------------------- overriden Handler methods --------------------
	
	/**
	* First call releases all resources used by this instance.
	* All future calls to {@link #publish publish} will be silently ignored.
	*/
	@Override public synchronized void close() {
		if (!isAlive()) return;
		
		super.close();
		
		levels = null;
		
		threadPlayer.interrupt();
		threadPlayer = null;
	}
	
	/** Implementation here does nothing, since the notify done by publish effectively acts as an auto-flush if threadPlayer is waiting inside getLevelWorst. */
	@Override public synchronized void flush() {}
	
	/**
	* If {@link #isAlive isAlive} or {@link #isLoggable isLoggable}(record) returns false, then immediately returns.
	* Else, adds record's Level to {@link #levels} and notifies {@link #threadPlayer} if is waiting inside {@link #getLevelWorst getLevelWorst}.
	*/
	@Override public synchronized void publish(LogRecord record) {
		try {
			if (!isAlive()) return;
			if (!isLoggable(record)) return;	// Note: isLoggable checks if a) record is null b) record's Level passes this instance's Level, and c) record passes this instance's Filter
			
			if (threadPlayer == null) {
				threadPlayer = new Thread( new SoundPlayer(this) );
				threadPlayer.setPriority( Thread.NORM_PRIORITY );
				threadPlayer.start();
			}
			
			levels.add( record.getLevel() );
			
			notify();
		}
		catch (Exception e) {
			reportError(null, e, ErrorManager.GENERIC_FAILURE);	// report the exception to any registered ErrorManager
		}
	}
	
	// -------------------- overriden HandlerAbstract methods --------------------
	
	/**
	* Implementation here returns {@link Level#WARNING}.
	*/
	@Override protected synchronized Level getLevelDefault() {
		return Level.WARNING;
	}
	
	// -------------------- getLevelWorst --------------------
	
	private synchronized Level getLevelWorst() throws InterruptedException {
		if (!isAlive()) return null;
		
		try {
			while (levels.size() == 0) {
				wait();
			}
			
			if (levels.contains(Level.SEVERE)) {
				return Level.SEVERE;
			}
			else if (levels.contains(Level.WARNING)) {
				return Level.WARNING;
			}
			else {
				return null;
			}
		}
		finally {
			if (levels != null) levels.clear();
		}
	}
	
	// -------------------- SoundPlayer (static inner class) --------------------
	
	private static class SoundPlayer implements Runnable {
		
		private final HandlerAudio handlerAudio;
		
		private SoundPlayer(HandlerAudio handlerAudio) {
			this.handlerAudio = handlerAudio;
		}
		
		public void run() {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					Level worst = handlerAudio.getLevelWorst();
					if (Level.SEVERE.equals(worst)) {	// CRITICAL: here and below, order matters, put worst as the param, since it can be null
						Sounds.playErrorMajor();	// CRITICAL: here and below, play the sound synchronously, so that do not re-execute this loop until the current sound is finished
					}
					else if (Level.WARNING.equals(worst)) {
						Sounds.playErrorMinor();
					}
					else {
						Sounds.playNotify();
					}
				}
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
			catch (Throwable t) {
				LogUtil.getLogger2().logp(Level.SEVERE, "HandlerAudio.SoundPlayer", "run", "caught an unexpected Throwable", t);
			}
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest extends HandlerAbstract.UnitTest {
		
		public static void main(final String[] args) throws Exception {
			Execute.thenContinue( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				UnitTest unitTest = new UnitTest();
				unitTest.test_all_makeLogsSeriallySlowly( 20, new HandlerAudio() );
				//unitTest.test_all_makeLogsConcurrentlyRapidly( Integer.MAX_VALUE, new HandlerAudio() );
				
				return null;
			} } );
		}
		
	}
	
}
