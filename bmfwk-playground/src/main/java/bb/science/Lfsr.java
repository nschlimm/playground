/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer's notes:

--C code for a 160 bit maximal LFSR: http://www.cryogenius.com/software/lfsr/
	
*/

package bb.science;

import bb.util.Check;
import bb.util.StringUtil;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

/**
* Implements a <a href="http://en.wikipedia.org/wiki/Linear_feedback_shift_register">linear feedback shift register</a> (LFSR).
* The register's size (an int named n) may be any value from 2 thru 32.
* Any starting state of the register (called the seed) may also be specifed.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
* @see <a href="http://forum.java.sun.com/thread.jspa?threadID=5276320&tstart=0">this forum posting</a>
*/
public class Lfsr {
	
	// -------------------- fields --------------------
	
	private static final Random random = new Random();
	private static int count = 1;
	
	private final int n;
	private final int mask;
	private final int taps;
	private int register;
	
	// -------------------- makeSeedNext, makeSeedRandom, makeMask, makeTaps --------------------
	
	/**
	* Returns the next seed suitable for an LFSR of size n.
	* The result (before masking) comes from an internal sequence
	* which starts at 1 and is incremented at least once each time this method is called.
	* This method is normally used when calling the seed specifying <code>{@link #Lfsr(int, int) constructor}</code>.
	* <p>
	* @throws IllegalArgumentException if n < 0 or n > 32
	*/
	public static int makeSeedNext(int n) throws IllegalArgumentException {
		// n checked by makeMask below
		
		int mask = makeMask(n);
		int seed;
		do {
			seed = (count++) & mask;	// CRITICAL: must mask seed in order to make it be a valid LFSR state
		} while (seed == 0);
		return seed;
	}
	
	/**
	* Returns a pseudo-random seed suitable for an LFSR of size n.
	* This method is normally used when calling the seed specifying <code>{@link #Lfsr(int, int) constructor}</code>.
	* <p>
	* This method is an
	* <p>
	* @throws IllegalArgumentException if n < 0 or n > 32
	*/
	public static int makeSeedRandom(int n) throws IllegalArgumentException {
		// n checked by makeMask below
		
		int mask = makeMask(n);
		int seed;
		do {
			seed = random.nextInt() & mask;	// CRITICAL: must mask seed in order to make it be a valid LFSR state
		}
		while (seed == 0);
		return seed;
	}
	
	/**
	* Returns an int with all the n low order bits set to 1 and all the high order bits set to 0.
	* When used as a bitwise AND mask, this will preserve the n low order bits and drop the high order bits.
	* <p>
	* @throws IllegalArgumentException if n < 0 or n > 32
	*/
	private static int makeMask(int n) throws IllegalArgumentException {
		Check.arg().notNegative(n);
		if (n > 32) throw new IllegalArgumentException("n = " + n + " > 32");
		
		if (n == 32) return ~0;	// ~0 is thirty two 1's in binary
			/*
			The n = 32 special case must be detected for two reasons, one obvious and one subtle.
			
			The obvious reason is that any int that is left shifted by 32 or more ought to pushed into a long value which is impossible, so you know something weird must happen.
			
			The subtle reason is the details of how Java's shift operators (<<, >>, >>>) work:
			they only use the 5 lower bits of the right side operand (i.e. shift amount).
			(This statement assumes that the left hand operand is an int; if it is a long, then the lower 6 bits are used.)
			THIS MEANS THAT THEY ONLY DO WHAT YOU THINK THEY WILL WHEN THE SHIFT AMOUNT IS INSIDE THE RANGE [0, 31].
			So, in the code above, 1 << n when n = 32 evaluates to 1 << 0 (because 32 has 0 in its lower 5 bits)
			so the overall expression is then (1 << 0) - 1 == 1 - 1 == 0 which is a wrong result.
			
			This "use only the lower shift bits" behavior is why this code (also suggested by Sean Anderson)
				return (~0) >>> (32 - n);
			cannot be used: it fails at n = 0 (returning thirty two ones instead of 0).
			
			References:
				http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6201273
				http://www.davidflanagan.com/blog/000021.html
				http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.19
			*/
		
		return (1 << n) - 1;	// acknowledgement: this technique sent to me by Sean Anderson, author of http://graphics.stanford.edu/~seander/bithacks.html
	}
	
	/**
	* Returns an int which can function as the taps of n order maximal LFSR.
	* <p>
	* @throws IllegalArgumentException if n < 2 or n > 32
	*/
	private static int makeTaps(int n) throws IllegalArgumentException {
			// There is no easy algorithm to generate the taps as a function of n.
			// Instead simply have to rely on known results.
			// The values below are all taken from http://homepage.mac.com/afj/taplist.html
			// (except for case 2; I think that I figured that one out myself).
			// A less complete reference is http://en.wikipedia.org/wiki/Linear_feedback_shift_register#Some_Polynomials_for_Maximal_LFSRs
		switch (n) {
			case 2: return  (1 << 1) | (1 << 0);	// i.e. 2 1
			case 3: return  (1 << 2) | (1 << 1);	// i.e. 3 2
			case 4: return  (1 << 3) | (1 << 2);	// i.e. 4 3
			case 5: return  (1 << 4) | (1 << 2);	// i.e. 5 3
			case 6: return  (1 << 5) | (1 << 4);	// i.e. 6 5
			case 7: return  (1 << 6) | (1 << 5);	// i.e. 7 6
			case 8: return  (1 << 7) | (1 << 6) | (1 << 5) | (1 << 0);	// i.e. 8 7 6 1
			case 9: return  (1 << 8) | (1 << 4);	// i.e. 9 5
			case 10: return  (1 << 9) | (1 << 6);	// i.e. 10 7
			case 11: return  (1 << 10) | (1 << 8);	// i.e. 11 9
			case 12: return  (1 << 11) | (1 << 10) | (1 << 9) | (1 << 3);	// i.e. 12 11 10 4
			case 13: return  (1 << 12) | (1 << 11) | (1 << 10) | (1 << 7);	// i.e. 13 12 11 8
			case 14: return  (1 << 13) | (1 << 12) | (1 << 11) | (1 << 1);	// i.e. 14 13 12 2
			case 15: return  (1 << 14) | (1 << 13);	// i.e. 15 14
			case 16: return  (1 << 15) | (1 << 14) | (1 << 12) | (1 << 3);	// i.e. 16 15 13 4
			case 17: return  (1 << 16) | (1 << 13);	// i.e. 17 14
			case 18: return  (1 << 17) | (1 << 10);	// i.e. 18 11
			case 19: return  (1 << 18) | (1 << 17) | (1 << 16) | (1 << 13);	// i.e. 19 18 17 14
			case 20: return  (1 << 19) | (1 << 16);	// i.e. 20 17
			case 21: return  (1 << 20) | (1 << 18);	// i.e. 21 19
			case 22: return  (1 << 21) | (1 << 20);	// i.e. 22 21
			case 23: return  (1 << 22) | (1 << 17);	// i.e. 23 18
			case 24: return  (1 << 23) | (1 << 22) | (1 << 21) | (1 << 16);	// i.e. 24 23 22 17
			case 25: return  (1 << 24) | (1 << 21);	// i.e. 25 22
			case 26: return  (1 << 25) | (1 << 24) | (1 << 23) | (1 << 19);	// i.e. 26 25 24 20
			case 27: return  (1 << 26) | (1 << 25) | (1 << 24) | (1 << 21);	// i.e. 27 26 25 22
			case 28: return  (1 << 27) | (1 << 24);	// i.e. 28 25
			case 29: return  (1 << 28) | (1 << 26);	// i.e. 29 27
			case 30: return  (1 << 29) | (1 << 28) | (1 << 27) | (1 << 6);	// i.e. 30 29 28 7
			case 31: return  (1 << 30) | (1 << 27);	// i.e. 31 28
			case 32: return (1 << 31) | (1 << 30) | (1 << 29) | (1 << 9);	// i.e. 32 31 30 10
			
			default: throw new IllegalArgumentException("n = " + n + " is an illegal value");
		}
	}
	
	// -------------------- constructors and helper methods --------------------
	
	/**
	* Convenience constructor that simply calls <code>{@link #Lfsr(int, int) Lfsr}(n, 1)</code>.
	* <p>
	* @throws IllegalArgumentException if n < 2 or n > 32
	*/
	public Lfsr(int n) throws IllegalArgumentException {
		this(n, 1);
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* @throws IllegalArgumentException if n < 2 or n > 32;
	* if seed == 0 or has high bits (i.e. beyond the nth bit) set
	*/
	public Lfsr(int n, int seed) throws IllegalArgumentException {
		if (n < 2) throw new IllegalArgumentException("n = " + n + " < 2");
		if (n > 32) throw new IllegalArgumentException("n = " + n + " > 32");
		if (seed == 0) throw new IllegalArgumentException("seed == 0; illegal because this will cause the internal state to be stuck at 0 forever");
		
		this.n = n;
		mask = makeMask(n);
		taps = makeTaps(n);
		register = seed;
		
		if ((seed & (~mask)) != 0) throw new IllegalArgumentException("seed = " + seed + " has high bits (i.e. beyond the nth bit) set, making it invalid state for a n = " + n + " LFSR");
	}
	
	// -------------------- accessors --------------------
	
	/** Accessor for {@link #mask}. */
	public int getMask() { return mask; }
	
	/** Accessor for {@link #taps}. */
	public int getTaps() { return taps; }
	
	/** Accessor for {@link #register}. */
	public int getRegister() { return register; }
	
	// -------------------- advance --------------------
	
	/** Convenience method that simply returns {@link #advance advance}(1). */
	public int advance() { return advance(1); }
	
	/**
	* Advances the internal state of this instance by numberTransitions.
	* <p>
	* Ignoring an initial argument check and a final return statement,
	* this method is essentially 2 lines of code: a loop head and its body.
	* The loop body involves 6 bitwise and/or unary int operators, <i>so it is very simple and fast</i>.
	* In spite of the simplicity of the computation, the LFSR internal state
	* (stored in the {@link #register} field) is pseudo-random.
	* It should be impossible for a smart compiler to cut many corners and avoid doing the computations.
	* <p>
	* @return the final value of the internal state after all the transitions have been carried out
	* @throws IllegalArgumentException if numberTransitions < 0
	*/
	public int advance(long numberTransitions) throws IllegalArgumentException {
		Check.arg().notNegative(numberTransitions);
		
		for (long i = 0; i < numberTransitions; i++) {
			register = ((register >>> 1) ^ (-(register & 1) & taps)) & mask;	// register & 1 selects the low order bit, and do - to create an int that is all 1's if was 1, or all 0's if was 0; the rest is easy to understand
		}
		return register;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_period() {
			for (int i = 2; i <= 32; i++) {
				checkPeriod(i, "");
				checkPeriod(i, "makeSeedNext");
				checkPeriod(i, "makeSeedRandom");
			}
		}
		
		private void checkPeriod(int n, String mode) {
			Lfsr lfsr;
			if (mode.equals("")) lfsr = new Lfsr(n);
			else if (mode.equals("makeSeedNext")) lfsr = new Lfsr(n, makeSeedNext(n));
			else if (mode.equals("makeSeedRandom")) lfsr = new Lfsr(n, makeSeedRandom(n));
			else throw new IllegalArgumentException("mode = " + mode + " is unsupported");
			
			System.out.println("checkPeriod(" + mode + "): n = " + n + "; mask[binary] = " + toBinaryString(lfsr.mask) + "; taps[binary] = " + toBinaryString(lfsr.taps));
			int registerInitial = lfsr.register;
			long period = 0;
			do {
				//System.out.println( toBinaryString(lfsr.register) );	// only uncomment if testing small n to confirm that results make sense
				lfsr.advance();
				++period;
			} while (lfsr.register != registerInitial);
			diagnosePeriod(period, n);
		}
		
		private String toBinaryString(int i) {
			return StringUtil.toLength( Integer.toBinaryString(i), 32, true, '0' );
		}
		
		private void diagnosePeriod(long period, int n) {
			long periodExpected = (1L << n) - 1L;
			String errMsg = "period = " + period + " IS NOT EQUAL TO THE MAXIMAL LENGTH VALUE of " + periodExpected + " for n = " + n;
			Assert.assertTrue( errMsg, period == periodExpected );
		}
		
	}
	
}


/*
Below is code for a Gray code, which is somewhat similar to a LFSR:

// http://en.wikipedia.org/wiki/Gray_code#Programming_algorithms
public class GrayCode implements Callable<Integer> {

	public Integer call() {
		int n = 3;
		int mask = makeMask(n);
		int gc = 0;
		int gcFinal = encode(1 << n);
		int period = 0;
		do {
			System.out.println("gc = " + gc + " <-> " + toBinaryString(gc, n));
			gc = encode( decode(gc) + 1 );
			++period;
		} while (gc != gcFinal);
		
		if (period == (1 << n)) System.out.println("period = " + period + " as expected for n = " + n);
		else System.out.println("period = " + period + " IS NOT EQUAL TO THE MAXIMAL LENGTH VALUE for n = " + n);
		
		return gc;
	}
	
	private int encode(int binary) {
		return binary ^ (binary >> 1);
	}
	
	private int decode(int gc) {
		int ish = 1;
		int ans = gc;
		while (true) {
			int idiv = ans >> ish;
			ans ^= idiv;
			if (idiv <= 1 || ish == 32) return ans;
			ish <<= 1; // double number of shifts next time
		}
	}
	
}
*/
