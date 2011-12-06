/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ this guy claims to have really good int & long hash functions:
	http://www.concentric.net/~Ttwang/tech/inthash.htm
and they look really fast too...

+++the new wikipedia article on hash tables claims that some simple ones are really good:
	http://en.wikipedia.org/wiki/Hash_table
		the Jenkins One-at-a-time hash
		http://www.burtleburtle.net/bob/hash/doobs.html
		http://burtleburtle.net/bob/hash/index.html
		http://eternallyconfuzzled.com/tuts/algorithms/jsw_tut_hashing.aspx
		http://bretm.home.comcast.net/hash/7.html
			the above url has C++ code that should be easily translatable into Java

+++ when I have time, I need to revisit the various enhance methods and see if I can quantify how good they are
(ideally, would find one which is good enough for hash tables, since the SHA-! algorithm currently used inside enhance,
while it is of cryptographic strength, is also fairly slow); see also
	Bret Mulvey's AvalancheTest
	http://en.wikipedia.org/wiki/Hash_table
	http://bretm.home.comcast.net/hash/

The requirements are
	1) output is ~uniform distributed across all possible int values
		"...The real requirement then is that a good hash function should distribute hash values uniformly for the keys that users actually use"
		http://burtleburtle.net/bob/hash/doobs.html
	2) there be no sequential correlation (e.g. if take some consective sequence, like 1-2-3-4, then each value tends to mapped to a very different int

I need to write code which
	1) goes thru every int value and measures the bin distribution for say 128 or 1024 bins
	2) for every (nonoverlapping, for speed of computation) group of consecutive N (say 1024) ints,
	measure the average number of bin collisions, and the worst across this group,
	as well as the corresponding quantities averaged across all the groups


+++ email all results to Doug Lea as well:
	dl@cs.oswego.edu


--do sudoku puzzles have any relation to hashes?
	--in particular, for cryptographic hashes, where you want it be easy to go 1-way and difficult to go the reverse,
		sudoku puzzles are easy to verify whether or not a proposed solution is valid, but it is much harder to either
			a) come up with that solution in the first place
			b) come up with a valid puzzle in the first place
	--by the way, how do you come up with a puzzle in the first place?
		randomly generate a complete grid, then randomly remove elements, and for each removal confirm that your solver algorithm can find a unique solution?
	--and how do the above considerations change when you consider generalized sudoku puzzles?
		--here, let n designate the number of elements in a row/column/box,
		--if m is the number of elements along a box's side, then need m^2 = n (to ensure that the box contains n elements)
		--also need n/m to be an integer, so that a full number of boxes fit along each dimension;
		--observe that if m^2 = n, then n/m is always an integer, so these requirements are compatible
		--the normal sudoku that people solve is the m=3 (n=9) case
	--and can you generate multi-dimensional sudoku (boxes within boxes)?
*/

package bb.util;

import java.security.MessageDigest;
import java.text.DecimalFormat;
import org.junit.Test;

/**
* Provides various static utility methods for dealing with hashes.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public final class HashUtil {
	
	// -------------------- constants --------------------
	
	/** A 31-bit prime number. */
	private static final int prime1 = 1491735241;	// hex: 0x58EA12C9
	
	/** A 31-bit prime number. */
	private static final int prime2 = 2041543619;	// hex: 0x79AF7BC3
	
	// -------------------- enhance --------------------
	
	/**
	* Attempts to return a very high quality hash function on h
	* (i.e. one that is uniformly distributed among all possible int values, .
	* <p>
	* This method is needed if h is initially a poor quality hash.
	* Prime example: {@link Integer#hashCode Integer.hashCode} simply returns the int value, which is an extremely bad hash.
	* <p>
	* The implementation here first attempts to use the extremely strong SHA-1 hash algorithm on h.
* If any problem occurs (e.g. the algorithm is unavailable), {@link #enhanceFallback4 enhanceFallback4}(h) is returned.
	* <p>
	* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/security/CryptoSpec.html#MessageDigest">Sun documentation on MessageDigest</a>
	* @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/security/CryptoSpec.html#MDEx">Sun documentation on MessageDigest</a>
	* @see <a href="http://webcat.sourceforge.net/javadocs/pt/tumba/parser/RabinHashFunction.html">Rabin fingerprint hash method</a>
	* @see <a href="http://forum.java.sun.com/thread.jspa?threadID=590499">My forum posting</a>
	*/
	public static int enhance(int h) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update( (byte) (h >>> 24) );
			md.update( (byte) (h >>> 16) );
			md.update( (byte) (h >>>  8) );
			md.update( (byte) (h >>>  0) );
			byte[] digest = md.digest();
			int digestAsInt = 0;
			for (int i = 0; i < digest.length; i++) {
				int shift = (i % 4) * 8;	// produces 0, 8, 16, 24 (i.e. with the << operator below, this puts the byte's bits in the correct slot in digestAsInt)
				digestAsInt ^= ((digest[i] & 0xFF) << shift);
			}
			return digestAsInt;
		}
		catch (Exception e) {
return enhanceFallback4(h);	// seems to produce better results; see reply 28 of http://forum.java.sun.com/thread.jspa?threadID=590499
		}
	}
	
	/**
	* Returns a quick to compute hash of the input h.
	* Only used by {@link #enhance enhance} as a fallback algorithm in the event of a problem.
	*/
	public static int enhanceFallback1(int h) {
// +++ the code below was taken from java.util.HashMap.hash--is this a published, known algorithm?  is there a better one?
/*
Personal email from Doug Lea:
	Josh Bloch (mostly) and I arrived at this by considering all
	transformations less than some number (that I forget) of instructions
	(and not considering offsets by constants etc), and then checked how
	they filled commonly sized power-of-two-sized hashtables for all
	possible inputs. The one we kept fared best. Someday we intend
	to write this up somewhere. The balance between getting good spread
	and low overhead (and inlinability) of the scrambling function seems
	to be about right.  There's no pretense that this works well for other
	purposes or even other kinds of tables, so I'm not surprised it
	doesn't look as good as some others using other metrics.
*/
		h += ~(h << 9);
		h ^=  (h >>> 14);
		h +=  (h << 4);
		h ^=  (h >>> 10);
		return h;
	}
	
	/**
	* Returns a quick to compute hash of the input h.
	* Only used by {@link #enhance enhance} as a fallback algorithm in the event of a problem.
	*/
	private static int enhanceFallback2(int h) {
// +++ the code below was taken from MersenneTwisterFast (http://www.cs.umd.edu/users/seanl/gp/mersenne/MersenneTwisterFast.java)--is this a published, known algorithm?  is there a better one?
		h ^= (h >>> 11);
		h ^= (h << 7) & 0x9D2C5680;
		h ^= (h << 15) & 0xEFC60000;
		h ^= (h >>> 18);
		return h;
	}
	
	/**
	* Returns a quick to compute hash of the input h.
	* Only used by {@link #enhance enhance} as a fallback algorithm in the event of a problem.
	*/
	private static int enhanceFallback3(int h) {
// +++ the code below was taken from reply #27 of http://forum.java.sun.com/thread.jspa?threadID=590499&start=15&tstart=0
		return (prime1 * h) + prime2;
	}
	
	private static int enhanceFallback4(int h) {
// +++ this code is a concatenation of all the fast algorithms; it seems to be better than any individual one
		return enhanceFallback2( enhanceFallback3( enhanceFallback1( h ) ) );
	}
	
	// -------------------- hash --------------------
	
	/** Returns a high quality hash for the double arg d. */
	public static int hash(double d) {
		long v = Double.doubleToLongBits(d);
		return enhance(
			(int) (v ^ (v >>> 32))	// the algorithm on this line is the same as that used in Double.hashCode
		);
	}
	
	/** Returns a high quality hash for the long arg l. */
	public static int hash(long l) {
		return enhance(
			(int) (l ^ (l >>> 32))	// the algorithm on this line is the same as that used in Long.hashCode
		);
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private HashUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_enhance() {
			System.out.println( " i" + toLength("enhance(i)") + toLength("enhanceFallback1(i)") + toLength("enhanceFallback2(i)") + toLength("enhanceFallback3(i)") + toLength("enhanceFallback4(i)") );
			System.out.println( StringUtil.repeatChars('-', 117) );
			for (int i = 0; i < 50; i++) {
				System.out.print( StringUtil.toLength(i, 2) );
				System.out.print( toLength( HashUtil.enhance(i) ) );
				System.out.print( toLength( HashUtil.enhanceFallback1(i) ) );
				System.out.print( toLength( HashUtil.enhanceFallback2(i) ) );
				System.out.print( toLength( HashUtil.enhanceFallback3(i) ) );
				System.out.println( toLength( HashUtil.enhanceFallback4(i) ) );
			}
		}
		
		private String toLength(String s) {
			return StringUtil.toLength( s, 23, true, ' ' );
		}
		
		private String toLength(int i) {
			DecimalFormat df = new DecimalFormat("0,000,000,000");
			return StringUtil.toLength( df.format(i), 23, true, ' ' );
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			n = 16
				enhance: first = 22.498 us, mean = 979.975 ns (CI deltas: -884.021 ps, +908.711 ps), sd = 3.604 us (CI deltas: -422.230 ns, +569.728 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_enhance() {
			final int n = 16;	// want n small so that a) the arrays below fit into the cache and b) hotspot does loop unrolling which will eliminate loop stuff from the benchmark
			Runnable task = new Runnable() {
				private final int[] values = new int[n];
				{ for (int i = 0; i < n; i++) { values[i] = i; } }
				private int state;	// needed to prevent DCE since this is a Runnable
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				public void run() {
					for (int i = 0; i < n; i++) {
						state ^= HashUtil.enhance( values[i] );
					}
				}
			};
			System.out.println("enhance: " + new Benchmark(task, n));
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			n = 16
				hash(double): first = 6.928 us, mean = 981.331 ns (CI deltas: -1.130 ns, +1.069 ns), sd = 4.453 us (CI deltas: -531.258 ns, +617.889 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_hash_double() {
			final int n = 16;	// want n small so that a) the arrays below fit into the cache and b) hotspot does loop unrolling which will eliminate loop stuff from the benchmark
			Runnable task = new Runnable() {
				private final double[] values = new double[n];
				{ for (int i = 0; i < n; i++) { values[i] = (double) i; } }
				private int state;	// needed to prevent DCE since this is a Runnable
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				public void run() {
					for (int i = 0; i < n; i++) {
						state ^= HashUtil.hash( values[i] );
					}
				}
			};
			System.out.println("hash(double): " + new Benchmark(task, n));
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			n = 16
				hash(long): first = 4.124 us, mean = 982.978 ns (CI deltas: -1.002 ns, +1.017 ns), sd = 4.101 us (CI deltas: -554.859 ns, +683.065 ns) WARNING: SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_hash_long() {
			final int n = 16;	// want n small so that a) the arrays below fit into the cache and b) hotspot does loop unrolling which will eliminate loop stuff from the benchmark
			Runnable task = new Runnable() {
				private final long[] values = new long[n];
				{ for (int i = 0; i < n; i++) { values[i] = (long) i; } }
				private int state;	// needed to prevent DCE since this is a Runnable
				
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				
				public void run() {
					for (int i = 0; i < n; i++) {
						state ^= HashUtil.hash( values[i] );
					}
				}
			};
			System.out.println("hash(long): " + new Benchmark(task, n));
		}
		
	}
	
}
