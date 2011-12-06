/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util.logging;

import bb.net.Emailer;
import bb.util.BufferFixed;
import bb.util.Check;
import bb.util.DateUtil;
import bb.util.Execute;
import bb.util.TimeLength;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.logging.ErrorManager;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
* Logs to an email account.
* <p>
* One feature of this class is that it dynamically adjusts a capture window time.
* The first LogRecord to be published will be emailed with very small delay (1 second),
* but if many more LogRecords arrive soon thereafter, the delay will be exponentially increased (up to 1 minute)
* in order to batch as many LogRecords as possible into a single email.
* See {@link #getLogRecords getLogRecords} for details.
* <p>
* It can be useful to attach an instance to the root Logger, and have it publish serious logs.
* <p>
* <i>It is unclear whether or not this class is multithread safe.</i>
* There may be issues with its superclass; see {@link HandlerAbstract} for more discussion.
* Concerning the state added by this class, every method is synchronized
* (in the case of {@link #getLogRecords getLogRecords}, it is internally synchronized in multiple places; see code).
* <p>
* @author Brent Boyer
*/
public class HandlerEmail extends HandlerAbstract {
	
	// -------------------- instance fields --------------------
	
	/**
	* Accumulates the next snapshot of LogRecords that is to be emailed.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private BufferFixed<LogRecord> bufferFixed;
	
	/**
	* Is the property File that configures {@link #threadEmailer}'s {@link EmailSender#emailer emailer}.
	* <p>
	* Contract: is never null after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private File emailConfigFile;
	
	/**
	* Is the email subject field's prefix used by {@link #threadEmailer}'s {@link EmailSender#emailer emailer}.
	* The complete subject field will end in a timestamp that is added by the EmailSender.
	* <p>
	* Contract: is never {@link Check#notBlank blank} after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private String subjectPrefix;
	
	/**
	* Is the email destination address field used by {@link #threadEmailer}'s {@link EmailSender#emailer emailer}.
	* <p>
	* Contract: is never {@link Check#notBlank blank} after construction, but is nulled out once and for all when {@link #close close} is first called.
	*/
	private String emailDestAddr;
	
	/**
	* Executes a {@link EmailSender} instance.
	* <p>
	* Contract: is null until lazy initialized by {@link #publish publish}, then is nulled out once and for all when {@link #close close} is first called.
	*/
	private Thread threadEmailer;
	
	/**
	* Is used by {@link #getLogRecords getLogRecords} to implement email rate throttling.
	* <p>
	* Contract: is > 0.
	*/
	private long timeSleep = TimeLength.second;
	
	// -------------------- constructors --------------------
	
	/**
	* Convenience constructor.
	* <p>
	* Some of this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure}.
	* However, the {@link BufferFixed#sizeMax} field is assigned from calling <code>{@link #getIntProperty getIntProperty}(getClass().getName() + ".sizeMax", 1000)</code>.
	* In other words, {@link BufferFixed#sizeMax} is set to whatever is defined for it in the logging properties file,
	* else it defaults to 1000 if not defined there.
	* Similarly, the {@link #emailConfigFile}, {@link #subjectPrefix}, and {@link #emailDestAddr} fields are assigned
	* from <code>.emailConfigFile</code>, <code>.subjectPrefix</code>, and <code>.emailDestAddr</code> properties respectively.
	*/
	public HandlerEmail() {
		synchronized (this) {	// needed to safely publish bufferFixed, at a minimum (and maybe some of the state mutated by configure)
			bufferFixed = new BufferFixed<LogRecord>( getIntProperty(HandlerEmail.class.getName() + ".sizeMax", 1000) );
			emailConfigFile = new File( getStringProperty(HandlerEmail.class.getName() + ".emailConfigFile", "<This is a bogus path that will surely cause an Exception>") );
			subjectPrefix = getStringProperty(HandlerEmail.class.getName() + ".subjectPrefix", "HandlerEmail:");
			emailDestAddr = getStringProperty(HandlerEmail.class.getName() + ".emailDestAddr", "<This is a bogus email address text that will surely cause an Exception>");
			configure();
		}
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* Some of this instance's initial configuration comes from the {@link LogManager} properties described in {@link HandlerAbstract#configure HandlerAbstract.configure}.
	* However, the {@link BufferFixed#sizeMax}, {@link #emailConfigFile}, {@link #subjectPrefix}, and {@link #emailDestAddr} fields are assigned from the corresponding params.
	* <p>
	* @throws IllegalArgumentException if sizeMax <= 0;
	* emailConfigFile is {@link Check#validFile not valid};
	* subjectPrefix or emailDestAddr is {@link Check#notBlank blank}
	*/
	public HandlerEmail(int sizeMax, File emailConfigFile, String subjectPrefix, String emailDestAddr) throws IllegalArgumentException {
		// sizeMax checked by BufferFixed below
		Check.arg().validFile(emailConfigFile);
		Check.arg().notBlank(subjectPrefix);
		Check.arg().notBlank(emailDestAddr);
		
		synchronized (this) {	// needed to safely publish bufferFixed, at a minimum (and maybe some of the state mutated by configure)
			bufferFixed = new BufferFixed<LogRecord>(sizeMax);
			this.emailConfigFile = emailConfigFile;
			this.subjectPrefix = subjectPrefix;
			this.emailDestAddr = emailDestAddr;
			configure();
		}
	}
	
	// -------------------- overriden Handler methods --------------------
	
	/**
	* First call releases all resources used by this instance.
	* All future calls to {@link #publish publish} will be silently ignored.
	*/
	@Override public synchronized void close() {
		if (!isAlive()) return;
		
		super.close();
		
		bufferFixed = null;
		
		emailConfigFile = null;
		
		subjectPrefix = null;
		
		emailDestAddr = null;
		
		if (threadEmailer != null) {	// must null check, because it could have never been initialized
			threadEmailer.interrupt();
			threadEmailer = null;
		}
	}
	
	/** Implementation here does nothing, since the notify done by publish effectively acts as an auto-flush if threadEmailer is waiting inside getLogRecords. */
	@Override public synchronized void flush() {}
	
	/**
	* If {@link #isAlive isAlive} or {@link #isLoggable isLoggable}(record) returns false, then immediately returns.
	* Else adds record to {@link #bufferFixed} and notifies {@link #threadEmailer} if is waiting inside {@link #getLogRecords getLogRecords}.
	*/
	@Override public synchronized void publish(LogRecord record) {
		try {
			if (!isAlive()) return;
			if (!isLoggable(record)) return;	// Note: isLoggable checks if a) record is null b) record's Level passes this instance's Level, and c) record passes this instance's Filter
			
			if (threadEmailer == null) {
				threadEmailer = new Thread( new EmailSender(this, emailConfigFile, subjectPrefix, emailDestAddr) );
				threadEmailer.setPriority( Thread.NORM_PRIORITY );
				threadEmailer.start();
			}
			
			bufferFixed.add(record);
			
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
	
	// -------------------- getLogRecords --------------------
	
	private String getLogRecords() throws InterruptedException {
			// synch here, since are accessing shared state and possibly doing a wait:
		synchronized (this) {
			if (!isAlive()) return "NO MORE LOGS: this HandlerEmail instance has been closed";
			
			if (bufferFixed.size() == 0) {
				long t1 = System.nanoTime();
				while (bufferFixed.size() == 0) {
					wait();
				}
				//timeSleep = TimeLength.second;	// restore this value to its default
					// used to use just the simple line above, but have seen circumstances in which it resets timeSleep to a small value too quickly, which then allows a burst of emails before the doubling below restores decent throttling; this is a problem because e.g. gmail will kill your account for a day if you exceed ~500 emails...
				long t2 = System.nanoTime();
				long timeWaited = (t2 - t1) * 1000 * 1000;	// 1000 * 1000 converts ns into ms
				timeSleep /= 2;	// try halving timeSleep, since there was no data when we went down this branch
				timeSleep -= timeWaited;	// also subtract the time actually waited in loop above, since long wait times there are equivalent to long sleep times in terms of achieving email throttling
				timeSleep = Math.max( timeSleep, TimeLength.second );	// and insist that sleep at least 1 second below
			}
			else {	// email rate throttling: if have logs already present...
				timeSleep = Math.min( 2 * timeSleep, 8 * TimeLength.minute );	// ...then double the amount of time (up to 8 minutes) that sleep below
			}
		}
		
			// CRITICAL: do NOT synch when do the sleep below, since that can hold the lock for far too long, blocking external threads trying to publish!
		Thread.sleep(timeSleep);	// sleep to allow even more logs to be added before send the email below (since that is WAY more efficient, and also reduces email inbox clutter)
		
			// synch once more, since are accessing shared state:
		BufferFixed.State<LogRecord> state;
		synchronized (this) {
			state = bufferFixed.getAndResetState();
		}
		
			// no need to synch beyond this point, since only access local variables:
		StringBuilder sb = new StringBuilder();
		
		sb.append("[Note: the data below was accumulated after a rate throttling sleep time of ").append( timeSleep ).append(" milliseconds, which is in adddition to the time spent sending the previous email]").append("\n");
		sb.append("\n");
		
		sb.append( state.getDescription() ).append("\n");
		
		sb.append("\n");
		for (LogRecord record : state.deque) {
			String msg = getFormatter().format(record);
			sb.append(msg);
			if (!(msg.endsWith("\n") || msg.endsWith("\r"))) sb.append("\n");
		}
		
		return sb.toString();
	}
	
	// -------------------- EmailSender (static inner class) --------------------
	
	private static class EmailSender implements Runnable {
		
		private final HandlerEmail handlerEmail;
		private final Emailer emailer;
		private final String subjectPrefix;
		private final String emailDestAddr;
		
		private EmailSender(HandlerEmail handlerEmail, File emailConfigFile, String subjectPrefix, String emailDestAddr) throws Exception {
			this.handlerEmail = handlerEmail;
			this.emailer = Emailer.make(emailConfigFile);
			this.subjectPrefix = subjectPrefix;
			this.emailDestAddr = emailDestAddr;
		}
		
		public void run() {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					String subject = subjectPrefix + " " + DateUtil.getTimeStamp();
					String messageBody = handlerEmail.getLogRecords();
					emailer.send(subject, messageBody, emailDestAddr);
				}
			}
			catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
			catch (Throwable t) {
				LogUtil.getLogger2().logp(Level.SEVERE, "HandlerEmail.EmailSender", "run", "caught an unexpected Throwable", t);
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
				unitTest.test_all_makeLogsSeriallySlowly( 20, new HandlerEmail() );
				//unitTest.test_all_makeLogsConcurrentlyRapidly( Integer.MAX_VALUE, new HandlerEmail() );
				
				return null;
			} } );
		}
		
	}
	
}
