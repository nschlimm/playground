/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--concerning test for randomness:
	"I've checked the randomness every way that I know (correlation of x(n) with x(n+1,2,3,4); relative frequencies of same-sign runs), including listening to the bit stream coming out"
	http://compilers.iecc.com/comparch/article/95-08-058
	
	Statistics tells us a few things about random bits. Zeros ought to occur as often as ones. A pair of zeros should occur half as often as single zero, and as often as a pair of ones. A triplet ought to occur half as often as a pair, a quarter as often as a single bit. If you take samples in pairs, a graph shouldn't show clumps. There are other statistical tests such as the chi-squared test you can perform on a stream of numbers to show how random (or how much they stray from random) they are.
	http://www.merrymeet.com/jon/usingrandom.html
	
	Here is a free program ent which has several tests:
		http://www.fourmilab.ch/random/
*/

package bb.util;

import bb.util.logging.LogUtil;
import ec.util.MersenneTwisterFast;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.junit.Test;

/**
* Provides static utility methods relating to {@link Random}.
* <p>
* This class is multithread safe: all of its fields are multithread safe types.
* <p>
* @author Brent Boyer
* @see <a href="http://en.wikipedia.org/wiki/Hardware_random-number_generator">Wikipedia article on random numbers</a>
* @see <a href="http://www.softpanorama.org/Algorithms/random_generators.shtml">Random Generators</a>
* @see <a href="http://burtleburtle.net/bob/rand/testsfor.html">Tests for Random Number Generators</a>
* @see <a href="http://burtleburtle.net/bob/hash/hashfaq.html#dist">The chi-squared test</a>
* @see <a href="http://www.cs.berkeley.edu/~daw/rnd/java-spinner">Java code using thread behavior to generate random numbers</a>
*/
public final class RandomUtil {
	
	// -------------------- static fields --------------------
	
	private static final AtomicLong id = new AtomicLong();
	
	private static final ThreadLocal<MersenneTwisterFast> threadLocal = new ThreadLocal<MersenneTwisterFast>() {
		protected MersenneTwisterFast initialValue() {
			//return new Random( makeSeed() );
			return new MersenneTwisterFast( makeSeed() );
		}
	};
	
	// -------------------- makeSeedXXX --------------------
	
	/**
	* This method first attempts to generate a high quality seed by calling {@link #makeSeedSecure() makeSeedSecure}.
	* This result should be of extremely high, cryptographic quality
	* (i.e. possibly come in part from a low level hardware source like diode noise, or at least from something like /dev/random,
	* as well as perhaps also satisfy some uniqueness aspects).
	* If any Exception is thrown by this step, it is caught and a value of 0 is assigned to the seed.
	* <p>
	* This method then generates an additional seed by calling {@link #makeSeedUnique makeSeedUnique}
	* which is highly likely to satisfy some uniqueness requirements.
	* This second seed defends against potential problems in certain implementations of SecureRandom (or lack thereof) on some platforms.
	* <p>
	* A bitwise XOR of the two seeds is finally returned.
	* <p>
	* @see <a href="http://forum.java.sun.com/thread.jspa?threadID=590499">My forum posting</a>
	*/
	public static long makeSeed() {
		long seedPossiblyHighQuality;
		try {
			seedPossiblyHighQuality = makeSeedSecure();
			LogUtil.getLogger2().logp(Level.FINER, "RandomUtil", "makeSeed", "Good: makeSeedSecure worked, so makeSeed will use a cryptographically strong seed as part of its initialization");
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.INFO, "RandomUtil", "makeSeed", "Note: makeSeedSecure generated the following Throwable, so makeSeed will fall back on another algorithm", t);
			seedPossiblyHighQuality = 0L;
		}
		
		return seedPossiblyHighQuality ^ makeSeedUnique();
	}
	
	/**
	* Returns <code>{@link #makeSeedSecure(String) makeSeedSecure}("SHA1PRNG")</code>.
	* <p>
	* @throws NoSuchAlgorithmException if the SHA1PRNG algorithm is not available in the caller's environment
	*/
	public static long makeSeedSecure() throws NoSuchAlgorithmException {
		return makeSeedSecure("SHA1PRNG");
	}
	
	/**
	* Returns <code>{@link #makeSeedSecure(SecureRandom) makeSeedSecure}( {@link SecureRandom#getInstance(String) SecureRandom.getInstance}(algorithm) )</code>.
	* <p>
	* @throws NoSuchAlgorithmException if the requested algorithm is not available in the caller's environment
	*/
	public static long makeSeedSecure(String algorithm) throws NoSuchAlgorithmException {
		return makeSeedSecure( SecureRandom.getInstance(algorithm) );
	}
	
	/**
	* Returns <code>{@link #makeSeedSecure(SecureRandom) makeSeedSecure}( {@link SecureRandom#getInstance(String, String) SecureRandom.getInstance}(algorithm, provider) )</code>.
	* <p>
	* @throws NoSuchAlgorithmException if the requested algorithm is not available from the provider
	* @throws NoSuchProviderException if the provider has not been configured
	* @throws IllegalArgumentException if the provider name is null or empty
	*/
	public static long makeSeedSecure(String algorithm, String provider) throws NoSuchAlgorithmException, NoSuchProviderException, IllegalArgumentException {
		return makeSeedSecure( SecureRandom.getInstance(algorithm, provider) );
	}
	
	/**
	* Returns <code>{@link NumberUtil#bytesBigEndianToLong NumberUtil.bytesBigEndianToLong}( {@link SecureRandom#generateSeed random.generateSeed}(8) )</code>.
	* <p>
	* @throws IllegalArgumentException if random == null
	*/
	public static long makeSeedSecure(SecureRandom random) throws IllegalArgumentException {
		Check.arg().notNull(random);
		
		return NumberUtil.bytesBigEndianToLong( random.generateSeed(8) );
	}
	
	/**
	* This seed value generating function <i>attempts</i> to satisfy these goals:
	* <ol>
	*  <li>return a unique result for each call of this method</li>
	*  <li>return a unique series of results for each different JVM invocation</li>
	*  <li>return results which are uniformly spread around the range of all possible long values</li>
	* </ol>
	* It uses the following techniques:
	* <ol>
	*  <li>
	*		an internal serial id field is incremented upon each call, so each call is guaranteed a different value;
	*		this field determines the high order bits of the result
	*  </li>
	*  <li>
	*		each call uses the result of {@link System#nanoTime System.nanoTime}
	*		to determine the low order bits of the result;
	*		this should be different each time the JVM is run (assuming that the system time is different)
	*  </li>
	*  <li>a hash algorithm is applied to the above numbers before putting them into the high and low order parts of the result</li>
	* </ol>
	* <p>
	* <b>Warnings:</b>
	* <ol>
	*  <li>
	*		the uniqueness goals cannot be guaranteed because the hash algorithm, while it is of high quality,
	*		is not guaranteed to be a 1-1 function (i.e. 2 different input ints might get mapped to the same output int).
	*  </li>
	*  <li>
	*		the result returned by this method is not cryptographically strong because there is insufficient entropy
	*		in the sources used (serial number and system time)
	*  </li>
	* </ol>
	*/
	public static long makeSeedUnique() {
		long hashSerialNumber = (long) HashUtil.hash( id.incrementAndGet() );
		long hashTime = (long) HashUtil.hash( System.nanoTime() );
		
		long bitsHigh = (hashSerialNumber << 32);
		long bitsLow = hashTime;
		
		return bitsHigh | bitsLow;
	}
	
	// -------------------- get --------------------
	
	/** Returns a Random instance that is local to the calling thread. */
/*
	public static Random get() {
		return randomPerThread.get();
	}
*/
	
	/** Returns a MersenneTwisterFast instance that is local to the calling thread, so thread contention is guaranteed to never occur. */
	public static MersenneTwisterFast get() {
		return threadLocal.get();
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private RandomUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_makeSeed() throws Exception {
			System.out.println();
			System.out.println( "Several outputs from calling makeSeed:" );
			for (int i = 0; i < 50; i++) {
				System.out.println( makeSeed() );
			}
			
			System.out.println();
			System.out.println( "Several outputs from calling makeSeedUnique:" );
			for (int i = 0; i < 50; i++) {
				System.out.println( makeSeedUnique() );
			}
			
			System.out.println();
			System.out.println( "Several outputs from calling makeSeedSecure:" );
			for (int i = 0; i < 50; i++) {
				System.out.println( makeSeedSecure() );
			}
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			n = 128 * 1024
				synchronized MersenneTwisterFast: first = 207.439 ns, mean = 22.028 ns (CI deltas: -4.631 ps, +5.274 ps), sd = 159.556 ns (CI deltas: -25.309 ns, +32.037 ns) WARNING: SD VALUES MAY BE INACCURATE
				unsynchronized MersenneTwisterFast: first = 11.235 ns, mean = 10.949 ns (CI deltas: -2.072 ps, +2.114 ps), sd = 95.789 ns (CI deltas: -12.099 ns, +14.455 ns) WARNING: SD VALUES MAY BE INACCURATE
				get (unsynchronized MersenneTwisterFast): first = 54.316 ns, mean = 19.574 ns (CI deltas: -4.562 ps, +4.993 ps), sd = 154.575 ns (CI deltas: -21.387 ns, +26.610 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		* <p>
		* Note: the low time for get is realistic:
		* my BenchmarkDataStructureAccess class measured access times for ThreadLocal at around 12 ns,
		* so add that to the intrinsic 14 ns and you get 26 ns as reported above.
		* <p>
		* Conclusions:
		* 1) in a known single threaded environment, should always use a dedicated MersenneTwisterFast instance
		* 2) but the moment multiple threads are involved, you are better off using get
		* than using a synchronized class, and this is true even without the thread contention that is present
		* in highly concurrent situations which makes the case for going with ThreadLocal even stronger.
		*/
		@Test public void benchmark_get() {
			final int n = 128 * 1024;
			
			Runnable task1 = new SingleThread_MersenneTwisterFast(n, true);
			System.out.println("synchronized MersenneTwisterFast: " + new Benchmark(task1, n));
			
			Runnable task2 = new SingleThread_MersenneTwisterFast(n, false);
			System.out.println("unsynchronized MersenneTwisterFast: " + new Benchmark(task2, n));
			
			Runnable task3 = new SingleThread_RandomPerThread(n);
			System.out.println("get (unsynchronized MersenneTwisterFast): " + new Benchmark(task3, n));
		}
		
		private static final class SingleThread_MersenneTwisterFast implements Runnable {
			
			private final MersenneTwisterFast random = new MersenneTwisterFast();
			private final int n;
			private final boolean synch;
			private int state = 0;	// needed to prevent DCE since this is a Runnable
			
			SingleThread_MersenneTwisterFast(int n, boolean synch) {
				this.n = n;
				this.synch = synch;
			}
			
			@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
			
			public void run() {
				if (synch) {
					for (int i = 0; i < n; i++) {
						synchronized(random) {
							state ^= random.nextInt();
						}
					}
				}
				else {
					for (int i = 0; i < n; i++) {
						state ^= random.nextInt();
					}
				}
			}
			
		}
		
		private static final class SingleThread_RandomPerThread implements Runnable {
			
			private final int n;
			private int state = 0;	// needed to prevent DCE since this is a Runnable
			
			SingleThread_RandomPerThread(int n) {
				this.n = n;
			}
			
			@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
			
			public void run() {
				for (int i = 0; i < n; i++) {
					state ^= RandomUtil.get().nextInt();
				}
			}
			
		}
		
	}
	
}
