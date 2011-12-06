/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--JavaSound tutorials:
	http://java.sun.com/j2se/1.4.2/docs/guide/sound/programmer_guide/contents.html
	http://java.sun.com/docs/books/tutorial/sound/playing.html
		above uses AudioClip to play sounds (this is now mostly obsolete, was for applets?)
	http://mindprod.com/jgloss/sound.html
	http://www.jsresources.org/faq.html
		--has huge collection of FAQs

--mp3 support:
	http://java.sun.com/products/java-media/jmf/mp3/download.html
	http://www.javaworld.com/javaworld/jw-11-2000/jw-1103-mp3_p.html

--free sounds:
	http://www.eventsounds.com/newsrad.htm
	http://www.space1999.net/~moonbase99/sound.htm
	http://mindprod.com/jgloss/sound.html#SOURCES
*/

package bb.gui;

import bb.io.DirUtil;
import bb.io.StreamUtil;
import bb.net.UrlUtil;
import bb.util.Check;
import bb.util.Execute;
import bb.util.ThrowableUtil;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
* Provides static methods relating to sound.
* <p>
* <b>Warning: there is apparently a <a href="http://forum.java.sun.com/thread.jsp?forum=406&thread=508583">bug</a>
* in all JDKs at least thru 1.4.x, in which the JVM will not naturally terminate if the JavaSound API is used;
* System.exit must be used to force shutdown.</b>
* This bug has been fixed in JDK 1.5.0, and this class assumes that this or a later version of Java is being used.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class SoundUtil {
	
/*
+++ add a main here which does format conversion?  See some of the examples here:
	http://www.jsresources.org/examples/audio_conversion.html
	http://www.jsresources.org/examples/AudioConverter.html
The code below currently just prints out the conversion capabilities of your system.
I beieve that Java Sound supports plugin codecs (e.g. Tritonus?) that can extend the capabilities...
*/
	public static void main(final String[] args) throws Exception {
		Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
			Check.arg().hasSize(args, 1);
			
			printAudioFileFormatsCanWrite();
			printFileDescriptions( new File(args[0]) );
			
			return null;
		} } );
	}
	
	private static void printAudioFileFormatsCanWrite() {
		System.out.println();
		System.out.println("printAudioFileFormatsCanWrite:");
		AudioFileFormat.Type[] formats = AudioSystem.getAudioFileTypes();
		for (AudioFileFormat.Type format : formats) {
			System.out.println(format);
		}
	}
	
	private static void printFileDescriptions(File directory) throws Exception {
		System.out.println();
		System.out.println("printFileDescriptions (" + directory.getPath() + "):");
		File[] files = DirUtil.getFilesInTree(directory);
		for (File file : files) {
			try {
				printUrlDescription( UrlUtil.getFileUrl(file) );
			}
			catch (Exception e) {
				System.out.println(file.getPath() + " could not be analysed due to " + e);
			}
		}
	}
	
	private static void printUrlDescription(URL url) throws Exception {
		System.out.println();
		System.out.println(url);
		
		AudioFileFormat audioFileFormat = AudioSystem.getAudioFileFormat(url);
		System.out.println(audioFileFormat);
		
		AudioFormat.Encoding[] audioFormatEncodings = AudioSystem.getTargetEncodings( audioFileFormat.getFormat() );
		for (AudioFormat.Encoding audioFormatEncoding : audioFormatEncodings) {
			System.out.println("\t" + "--> " + audioFormatEncoding);
			
			AudioFormat[] audioFormats = AudioSystem.getTargetFormats( audioFormatEncoding, audioFileFormat.getFormat() );
			if (audioFormats.length == 0) System.out.println("\t\t" + "<no audioFormats reported--is this because AudioFormat.Encoding is the same?>");
			for (AudioFormat audioFormat : audioFormats) {
				System.out.println("\t\t" + audioFormat);
			}
		}
	}
	
	// -------------------- playSynch --------------------
	
	/**
	* Simply calls <code>{@link #playSynch(File, int) playSynch}(file, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playSynch(File file) throws IllegalArgumentException, RuntimeException {
		playSynch(file, 1);
	}
	
	/**
	* Creates a URL for file and supplies this to {@link #playSynch(URL, int) playSynch}.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}; numberLoops < 1
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playSynch(File file, int numberLoops) throws IllegalArgumentException, RuntimeException {
		try {
			Check.arg().validFile(file);
			// numberLoops checked by the playSynch call below
			
			URL url = UrlUtil.getFileUrl(file);
			playSynch( url, numberLoops );
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	/**
	* Simply calls <code>{@link #playSynch(URL, int) playSynch}(url, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if url == null
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playSynch(URL url) throws IllegalArgumentException, RuntimeException {
		playSynch(url, 1);
	}
	
	/**
	* Simply calls <code>{@link #play play}(url, numberLoops, true)</code>.
	* <p>
	* Because the synchronous parameter supplied to play is true,
	* the playing is done "synchronously", that is, this method does not return until the sound has finished playing.
	* <p>
	* @throws IllegalArgumentException if url == null; numberLoops < 1
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playSynch(URL url, int numberLoops) throws IllegalArgumentException, RuntimeException {
		play(url, numberLoops, true);
	}
	
	// -------------------- playAsynch --------------------
	
	/**
	* Simply calls <code>{@link #playAsynch(File, int) playAsynch}(file, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playAsynch(File file) throws IllegalArgumentException, RuntimeException {
		playAsynch(file, 1);
	}
	
	/**
	* Creates a URL for file and supplies this to {@link #playAsynch(URL, int) playAsynch}.
	* <p>
	* @throws IllegalArgumentException if file is {@link Check#validFile not valid}; numberLoops < 1
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playAsynch(File file, int numberLoops) throws IllegalArgumentException, RuntimeException {
		try {
			Check.arg().validFile(file);
			// numberLoops checked by the playAsynch call below
			
			URL url = UrlUtil.getFileUrl(file);
			playAsynch( url, numberLoops );
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	/**
	* Simply calls <code>{@link #playAsynch(URL, int) playAsynch}(url, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if url == null
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playAsynch(URL url) throws IllegalArgumentException, RuntimeException {
		playAsynch(url, 1);
	}
	
	/**
	* Simply calls <code>{@link #play play}(url, numberLoops, false)</code>.
	* <p>
	* Because the synchronous parameter supplied to play is false,
	* the playing is done "asynchronously", that is, this method immediately returns
	* while another thread carries out the playing of the sound.
	* <p>
	* @throws IllegalArgumentException if url == null; numberLoops < 1
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void playAsynch(URL url, int numberLoops) throws IllegalArgumentException, RuntimeException {
		play(url, numberLoops, false);
	}
	
	// -------------------- play --------------------
	
	/**
	* Plays the sound located at url in a loop that will execute numberLoops times.
	* <p>
	* The actual playing of the sound is always done by some internal thread in the sound system.
	* If the synchronous parameter is true, however, then this method behaves "synchronously", that is,
	* it does not return until that other thread has finished playing the sound.
	* Otherwise, this method behaves "asynchronously", that is,
	* it immediately returns while that other thread concurrently plays the sound.
	* <p>
	* <b>Warning: this method does nothing to prevent another thread from simultaneously using the sound output system</b>
	* (e.g. by calling some method of this class), in which case multiple sounds may be playing simultaneously.
	* <p>
	* @throws IllegalArgumentException if url == null; numberLoops < 1
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static void play(URL url, int numberLoops, boolean synchronous) throws IllegalArgumentException, RuntimeException {
		Check.arg().notNull(url);
		Check.arg().positive(numberLoops);
		
		try {
			Player player = new Player(url, numberLoops);
			player.play();
			if (synchronous) player.waitTillClosed();
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- timeToPlay --------------------
	
	/**
	* Returns <code>{@link #timeToPlay(Clip, int) timeToPlay}(clip, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if clip == null
	* @throws IllegalStateException if the line for clip is not open
	*/
	public static long timeToPlay(Clip clip) throws IllegalArgumentException, IllegalStateException {
		return timeToPlay(clip, 1);
	}
	
	/**
	* Returns the number of milliseconds that it should take to play clip in a loop that will execute numberLoops times.
	* The result is always an upper bound because it comes from rounding up a microsecond time.
	* <p>
	* @throws IllegalArgumentException if clip == null; numberLoops < 1
	* @throws IllegalStateException if the line for clip is not open
	*/
	public static long timeToPlay(Clip clip, int numberLoops) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(clip);
		Check.arg().positive(numberLoops);
		if (!clip.isOpen()) throw new IllegalStateException("the line for clip is not open");
		
		long timeMicroseconds = clip.getMicrosecondLength();
		if (timeMicroseconds == AudioSystem.NOT_SPECIFIED) throw new IllegalStateException("the line for clip must not be open, because calling clip.getMicrosecondLength() returned AudioSystem.NOT_SPECIFIED");
		
		double timeMilliseconds = Math.ceil( (numberLoops * timeMicroseconds) / 1000.0 );	// CRITICAL: for best accuracy, do all the arithmetic inside the Math.ceil()
		if (timeMilliseconds > Long.MAX_VALUE) throw new IllegalStateException("the result (approximately " + timeMilliseconds + " milliseconds) is greater then the max value of a long");
		
		return (long) timeMilliseconds;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private SoundUtil() {}
	
	// -------------------- Player (static inner class) --------------------
	
	/**
	* Generates a Clip from a {@link URL} and can {@link #play play} it,
	* but most importantly it robustly ensures that the clip is eventually closed
	* and that this close event gets propagated.
	* <p>
	* There seems to be a common code idiom for working with Java Sound that is not robust against known bugs and defects.
	* This approach has a LineListener alone listen for the Clip's STOP event and either closes the Clip at that point
	* and/or notifies the calling thread (which is waiting on the Clip instance, if synchronous sound playback is desired).
	* Examples of this code idiom include:
	* <ol>
	*  <li><a href="http://www.jsresources.org/examples/ClipPlayer.java.html">http://www.jsresources.org/examples/ClipPlayer.java.html</a></li>
	*  <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4434125">http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4434125</a> (see the source code in the initial posting)</li>
	*  <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4631192">http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4631192</a> (see the source code in the initial posting)</li>
	* </ol>
	* <p>
	* Unfortunately, as the bug reports cited above illustrate, there are many problems with Java Sound:
	* <ol>
	*  <li>it is a very bad idea to use the Clip instance itself as the wait/notify object, since it is used internally</li>
	*  <li>STOP events may fail to get generated</li>
	* </ol>
	* <p>
	* This class avoids all these problems.
	* First, it uses the Player instance itself as the wait/notify object.
	* Second, it does not solely rely on the event mechanism to close the Clip.
	* Instead, a task that executes in a dedicated background daemon thread is used to guarantee that {@link #close close}
	* is called after the expected amount of playing time has elapsed, which guards against dropped events.
	* <p>
	* This class is multithread safe.
	*/
	private static class Player implements Closeable, LineListener {
		
		/** @serial */
		private final URL url;
		/** @serial */
		private final int numberLoops;
		/** @serial */
		private final Clip clip;
		
		/** Condition predicate for this instance's condition queue (i.e. the wait/notifyAll calls below; see "Java Concurrency in Practice" by Goetz et al p. 296ff, especially p. 299).
		 @serial*/
		private boolean closeExecuted = false;
		
		private Player(URL url, int numberLoops) throws UnsupportedAudioFileException, IOException, LineUnavailableException, IllegalArgumentException, IllegalStateException, SecurityException {
			this.url = url;
			this.numberLoops = numberLoops;
			
			AudioInputStream ais = AudioSystem.getAudioInputStream(url);
			DataLine.Info info = new DataLine.Info(Clip.class, ais.getFormat());
			clip = (Clip) AudioSystem.getLine(info);
			clip.open(ais);
		}
		
		/**
		* Initiates the playing of clip in a loop that will execute numberLoops times.
		* (Some other thread in the sound system actually does the playing, not the calling thread,
		* so this method executes very quickly).
		* <p>
		* Schedules a background task which will call {@link #close close}
		* after a delay of {@link #getDuration getDuration}.
		* <p>
		* @throws IllegalStateException if clip is currently running
		*/
		private void play() throws IllegalStateException {
			if (clip.isRunning()) throw new IllegalStateException("clip is currently running");
			
			clip.addLineListener(this);	// do this here, since cannot do this in the constructor (see "Java Concurrency in Practice" by Goetz et al p. 41-2)
			clip.loop(numberLoops - 1);	// - 1 because of the weird way that loop operates
			
			Thread thread = new Thread( new Closer(), "SoundUtil.Player_closer_" + url );
			thread.setDaemon(true);	// CRITICAL: don't want this background thread to stall a normal JVM shutdown
			thread.setPriority( Thread.NORM_PRIORITY );
			thread.start();
		}
		
		private long getDuration() {
			return timeToPlay(clip, numberLoops);
		}
		
		/**
		* Waits until this instance is notified.
		* <p>
		* @throws InterruptedException
		*/
		private synchronized void waitTillClosed() throws InterruptedException {
			while (!closeExecuted) {
				this.wait();
			}
		}
		
		/** Calls clip.close, and then calls this instance's notifyAll. */
		@Override public synchronized void close() {
			if (closeExecuted) return;
			clip.close();
			clip.removeLineListener(this);
			closeExecuted = true;
			this.notifyAll();
		}
		
		public void update(LineEvent event) {
			if (event.getType().equals(LineEvent.Type.STOP)) {
				close();
			}
		}
		
		/** Sleeps for the duration of the sound playback, and then calls {@link StreamUtil#close StreamUtil.close}( Player.this ). */
		private class Closer implements Runnable {
			public void run() {
				try {
					Thread.sleep( getDuration() + 200 );	// CRITICAL: must add a bit of time beyond the duration so that this background thread does not compete with the Sound Event Dispatch thread; see http://forums.sun.com/thread.jspa?threadID=5374445
					StreamUtil.close( Player.this );
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Tests the parent class.
		* <p>
		* If this method is this Java process's entry point (i.e. first <code>main</code> method),
		* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
		* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
		* Otherwise, this method returns and leaves the JVM running.
		*/
		public static void main(final String[] args) {
			Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
					// should hear the sounds played serially:
				SoundUtil.playSynch( new File("../resource/audio/notify.wav") );
				SoundUtil.playSynch( new URL("http://www.eventsounds.com/wav/klrsanta.wav") );
				
				Thread.sleep(1*1000);
				
					// should hear all the sounds played simultaneously:
				SoundUtil.playAsynch( new File("../resource/audio/errorMinor.wav") );
				SoundUtil.playAsynch( new URL("http://www.eventsounds.com/wav/44magnum.wav") );
				
				Thread.sleep(3*1000);
				
				return null;
			} } );
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
	
	}
	
}
