package com.schlimm.java7.concurrency.random;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link MySillyThreadLocalRandom} uses static field instead of {@link ThreadLocal} - anything else like
 * {@link ThreadLocalRandom}
 * 
 * @author Niklas Schlimm
 * 
 */
@SuppressWarnings("unused")
public class MySillyThreadLocalRandom extends Random {

	// same constants as Random, but must be redeclared because private
	private static final long multiplier = 0x5DEECE66DL;
	private static final long addend = 0xBL;
	private static final long mask = (1L << 48) - 1;

	private long rnd;

	boolean initialized;

	private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

	/*
	 * Silly thread local only uses static instead of thread local variable
	 */
	private static MySillyThreadLocalRandom threadLocalRandom = new MySillyThreadLocalRandom();

	/**
	 * Constructor called only by localRandom.initialValue.
	 */
	MySillyThreadLocalRandom() {
		super();
		initialized = true;
	}

	/**
	 * Returns the current thread's {@code ThreadLocalRandom}.
	 * 
	 * @return the current thread's {@code ThreadLocalRandom}
	 */
	public static MySillyThreadLocalRandom current() {
		return threadLocalRandom;
	}

	/**
	 * Throws {@code UnsupportedOperationException}. Setting seeds in this generator is not supported.
	 * 
	 * @throws UnsupportedOperationException
	 *             always
	 */
	public void setSeed(long seed) {
		if (initialized)
			throw new UnsupportedOperationException();
		rnd = (seed ^ multiplier) & mask;
	}

	/**
	 * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
	 * (exclusive).
	 * 
	 * @param least
	 *            the least value returned
	 * @param bound
	 *            the upper bound (exclusive)
	 * @throws IllegalArgumentException
	 *             if least greater than or equal to bound
	 * @return the next value
	 */
	public int nextInt(int least, int bound) {
		if (least >= bound)
			throw new IllegalArgumentException();
		return nextInt(bound - least) + least;
	}

	/**
	 * Returns a pseudorandom, uniformly distributed value between 0 (inclusive) and the specified value (exclusive).
	 * 
	 * @param n
	 *            the bound on the random number to be returned. Must be positive.
	 * @return the next value
	 * @throws IllegalArgumentException
	 *             if n is not positive
	 */
	public long nextLong(long n) {
		if (n <= 0)
			throw new IllegalArgumentException("n must be positive");
		// Divide n by two until small enough for nextInt. On each
		// iteration (at most 31 of them but usually much less),
		// randomly choose both whether to include high bit in result
		// (offset) and whether to continue with the lower vs upper
		// half (which makes a difference only if odd).
		long offset = 0;
		while (n >= Integer.MAX_VALUE) {
			int bits = next(2);
			long half = n >>> 1;
			long nextn = ((bits & 2) == 0) ? half : n - half;
			if ((bits & 1) == 0)
				offset += n - nextn;
			n = nextn;
		}
		return offset + nextInt((int) n);
	}

	/**
	 * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
	 * (exclusive).
	 * 
	 * @param least
	 *            the least value returned
	 * @param bound
	 *            the upper bound (exclusive)
	 * @return the next value
	 * @throws IllegalArgumentException
	 *             if least greater than or equal to bound
	 */
	public long nextLong(long least, long bound) {
		if (least >= bound)
			throw new IllegalArgumentException();
		return nextLong(bound - least) + least;
	}

	/**
	 * Returns a pseudorandom, uniformly distributed {@code double} value between 0 (inclusive) and the specified value
	 * (exclusive).
	 * 
	 * @param n
	 *            the bound on the random number to be returned. Must be positive.
	 * @return the next value
	 * @throws IllegalArgumentException
	 *             if n is not positive
	 */
	public double nextDouble(double n) {
		if (n <= 0)
			throw new IllegalArgumentException("n must be positive");
		return nextDouble() * n;
	}

	/**
	 * Returns a pseudorandom, uniformly distributed value between the given least value (inclusive) and bound
	 * (exclusive).
	 * 
	 * @param least
	 *            the least value returned
	 * @param bound
	 *            the upper bound (exclusive)
	 * @return the next value
	 * @throws IllegalArgumentException
	 *             if least greater than or equal to bound
	 */
	public double nextDouble(double least, double bound) {
		if (least >= bound)
			throw new IllegalArgumentException();
		return nextDouble() * (bound - least) + least;
	}

	private static final long serialVersionUID = -5851777807851030925L;

}
