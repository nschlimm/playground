/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.util.Check;
import java.net.URL;

/**
* Provides static utility methods which play various sounds.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public class Sounds {
	
	// -------------------- constants --------------------
	
	private static final URL urlErrorMajor = Sounds.class.getResource("/audio/errorMajor.wav");
	private static final URL urlErrorMinor = Sounds.class.getResource("/audio/errorMinor.wav");
	private static final URL urlNotify = Sounds.class.getResource("/audio/notify.wav");
	
	// -------------------- playXXX --------------------
	
	/** Simply calls <code>{@link #playErrorMajor(boolean) playErrorMajor}(true)</code>. */
	public static void playErrorMajor() {
		playErrorMajor(true);
	}
	
	/**
	* Plays a major error sound.
	* <p>
	* @param synchronous if true, then this method executes synchronously (i.e. does not return until the sound is finished playing)
	* if false, then this method executes asynchronously (i.e. soon returns, while another thread plays the sound)
	*/
	public static void playErrorMajor(boolean synchronous) {
		if (synchronous) SoundUtil.playSynch(urlErrorMajor);
		else SoundUtil.playAsynch(urlErrorMajor);
	}
	
	/** Simply calls <code>{@link #playErrorMinor(boolean) playErrorMinor}(true)</code>. */
	public static void playErrorMinor() {
		playErrorMinor(true);
	}
	
	/**
	* Plays a minor error sound.
	* <p>
	* @param synchronous if true, then this method executes synchronously (i.e. does not return until the sound is finished playing)
	* if false, then this method executes asynchronously (i.e. soon returns, while another thread plays the sound)
	*/
	public static void playErrorMinor(boolean synchronous) {
		if (synchronous) SoundUtil.playSynch(urlErrorMinor);
		else SoundUtil.playAsynch(urlErrorMinor);
	}
	
	/** Simply calls <code>{@link #playNotify(boolean) playNotify}(true)</code>. */
	public static void playNotify() {
		playNotify(true);
	}
	
	/**
	* Plays a notify sound.
	* <p>
	* @param synchronous if true, then this method executes synchronously (i.e. does not return until the sound is finished playing)
	* if false, then this method executes asynchronously (i.e. soon returns, while another thread plays the sound)
	*/
	public static void playNotify(boolean synchronous) {
		if (synchronous) SoundUtil.playSynch(urlNotify);
		else SoundUtil.playAsynch(urlNotify);
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private Sounds() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		public static void main(String[] args) throws Exception {
			Check.arg().empty(args);
			
			Sounds.playNotify(false);
			Sounds.playErrorMinor(false);
			Sounds.playErrorMajor(false);
			
			Thread.sleep(3*1000);
			
			Sounds.playNotify(true);
			Sounds.playErrorMinor(true);
			Sounds.playErrorMajor(true);
			
			//System.exit(0);	// there was an old bug that required this, but jdk 6 at least does not need this...
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
