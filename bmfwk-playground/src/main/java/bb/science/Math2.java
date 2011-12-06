/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
--Fast InvSqrt:
	http://www.beyond3d.com/articles/fastinvsqrt/

+++ to minimize roundoff error in those array methods below which involve summing all the elements,
the following procedure should have better (the best?) precision:
	--sort the array into ascending order
	--find that pair of adjacent elements whose difference is the smallest
	--remove those 2 nearest neighbors from the array, add them together, and then add the sum back to the array
		(put it in that slot which maintains the ascending order)
	--repeat the above procedure until only 1 element is left; that will be the desired sum of all the original elements
The problems with this algorithm are both increased complexity as well as horrid performance.
At a minimum, if could figure out how to still use java arrays (as opposd to a List of some type),
would still need to make an initial copy of the array that is supplied, as well as do n sorts
(albeit inserting an element into an already sorted array is O(n)).
These costs are why have not yet attempted to do it, but maybe Mathematica does something like it...

For a cheaper to compute, but hopefull high accuracy algorithm compared to the above, what about the Kahan summation algorithm:
	http://en.wikipedia.org/wiki/Compensated_summation
?  Actually, my sst method below now incorporates a form of compensated summation, need to test it...
*/

package bb.science;

import bb.io.FileUtil;
import bb.util.Benchmark;
import bb.util.Check;
import bb.util.logging.LogUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

/**
* This class offers additional static mathematical methods beyond the ones offered in {@link Math}.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see <a href="http://math.nist.gov/javanumerics/#libraries">NIST Java numerics page</a>
* @author Brent Boyer
*/
public class Math2 {
	
	// -------------------- constants --------------------
	
	/**  Stores the value of <code>1 / sqrt(2*pi)</code>. */
	public static final double inverseSqrt2pi = 1.0 / Math.sqrt( 2*Math.PI );
	
	/** A default value for the errorTolerance param of the {@link #normalize(double[], double) normalize} method. */
	public static final double normalizationErrorTolerance_default = 1e-6;
	
	
	
	
	// ==================== SECTION 1: SCALAR METHODS ====================
	
	
	
	
	// -------------------- equals --------------------
	
	/**
	* Determines if the two double args are equal or not.
	* The sole reason why this method was written was because the computation <code>d1 == d2</code>
	* fails to handle the nasty case that both d1 and d2 are NaN:
	* this method returns true in that case, whereas that computation returns false because NaN always returns false when used in tests like that.
	*/
	public static boolean equals(double d1, double d2) {
		if (Double.isNaN(d1)) return Double.isNaN(d2);
		return d1 == d2;
	}
	
	// -------------------- sign, hasSameSign, compare --------------------
	
	/**
	* Implements the <a href="http://en.wikipedia.org/wiki/Sign_function">sign function</a>:
	* returns -1 if x < 0, 0 if x == 0, 1 if x > 0.
	* <p>
	* Starting with JDK 1.5, there is a new method {@link Math#signum(double) Math.signum}
	* which is equivalent to this method except that it returns NaN when presented with a NaN argument
	* instead of throwing an IllegalArgumentException like this method does.
	* <p>
	* @throws IllegalArgumentException if x is not a comparable number (i.e. is NaN)
	*/
	public static double sign(double x) throws IllegalArgumentException {
		if (x < 0) return -1.0;
		else if (x == 0.0) return 0.0;
		else if (x > 0) return 1.0;
		else throw new IllegalArgumentException("x = " + x + " is not a comparable number");
	}
	
	/**
	* Determines whether or not x1 and x2 have the same sign:
	* returns <code>{@link #sign sign}(x1) == sign(x2)</code>.
	* <p>
	* @throws IllegalArgumentException if x is not a comparable number (i.e. is NaN)
	*/
	public static boolean hasSameSign(double x1, double x2) throws IllegalArgumentException {
		return sign(x1) == sign(x2);
	}
	
	/**
	* Compares the two double args:
	* returns -1 if d1 < d2, 0 if d1 == d2, 1 if d1 > d2.
	* <p>
	* The original motivation for this method was to simplify the writing of {@link Comparable}/{@link Comparator}.
	* The reason why you cannot simply return d1 - d2 is because an int value is needed.
	* You could, however, return <code>(int) {@link #sign sign}( d1 - d2 )</code>, which is equivalent to <code>compare(d1, d2)</code>.
	* <p>
	* @throws IllegalArgumentException if d1 and d2 are not comparable; should only happen if at least one of them is NaN
	*/
	public static int compare(double d1, double d2) throws IllegalArgumentException {
		if (d1 < d2) return -1;
		else if (d1 == d2) return 0;
		else if (d1 > d2) return 1;
		else throw new IllegalArgumentException("unable to compare d1 = " + d1 + " and d2 = " + d2 + "; at least one of them should be NaN");
	}
	
	/**
	* Compares the two int args:
	* returns -1 if i1 < i2, 0 if i1 == i2, 1 if i1 > i2.
	* <p>
	* The original motivation for this method was to simplify the writing of {@link Comparable}/{@link Comparator}.
	* The reason why you cannot simply return i1 - i2 is because Java has retarded numerical behavior
	* (instead of spilling over into infinite values or throwing an Exception, excessive differences "wrap around",
	* for instance Integer.MAX_VALUE - (-1) == Integer.MIN_VALUE).
	*/
	public static int compare(int i1, int i2) {
		if (i1 < i2) return -1;
		else if (i1 == i2) return 0;
		else if (i1 > i2) return 1;
		else throw new IllegalArgumentException("unable to compare i1 = " + i1 + " and i2 = " + i2 + "; this should never happen");
	}
	
	/**
	* Compares the two long args:
	* returns -1 if l1 < l2, 0 if l1 == l2, 1 if l1 > l2.
	* <p>
	* The original motivation for this method was to simplify the writing of {@link Comparable}/{@link Comparator}.
	* The reason why you cannot simply return l1 - l2 is because a) an int value is needed and b) Java has retarded numerical behavior
	* (instead of spilling over into infinite values, excessive differences "wrap around",
	* for instance Long.MAX_VALUE - (-1) == Long.MIN_VALUE).
	*/
	public static int compare(long l1, long l2) {
		if (l1 < l2) return -1;
		else if (l1 == l2) return 0;
		else if (l1 > l2) return 1;
		else throw new IllegalArgumentException("unable to compare l1 = " + l1 + " and l2 = " + l2 + "; this should never happen");
	}
	
	// -------------------- modulo --------------------
	
	/**
	* Returns a mod b.
	* <p>
	* This method is a true modulo function: b must be positive, and the result is guaranteed to lie inside [0, b - 1].
	* Therefore, it differs from the java % operator, which is more precisely known as the
	* <a href="http://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.17.3">remainder operator</a>
	* because % can accept any non-zero value for b, but will produce negative results when a < 0.
	* <p>
	* @throws IllegalArgumentException if b <= 0
	*/
	public static int modulo(int a, int b) throws IllegalArgumentException {
		Check.arg().positive(b);
		
		if (a < 0) {
			a += b;	// try adding just b to a and see if that makes it positive
			if (a < 0) {
				a += (((-a) / b) + 1) * b;	// if a still negative, then add enough multiples of b to a that should guarantee that it will become positive
				// Note: because b > 0 and first did a += b, the above line works even in extreme cases like a originally near Integer.MIN_VALUE
			}
			assert (a >= 0) : "algorithm failure: starting with some value of a < 0, failed to transform a into a positive number; b = " + b;
		}
		
		return a % b;	// at this point, a is positive, so can always use the remainder operator to get the mod result
	}
	
	// -------------------- logs, powers, magnitudes, etc --------------------
	
	/** Returns the logarithm of x in the supplied base. */
	public static double log(double base, double x) {
		return Math.log(x) / Math.log(base);
	}
// +++ Sun should add this method to the Math class; see http://developer.java.sun.com/developer/bugParade/bugs/4074599.html
	
	/** Returns the logarithm of x in base 10. */
	public static double log10(double x) {
		return log(10.0, x);
	}
// +++ Sun should add this method to the Math class; see http://developer.java.sun.com/developer/bugParade/bugs/4074599.html
// +++ it appears that 1.5 has added it: http://java.sun.com/j2se/1.5.0/fixedbugs/fixedbugs.html
	
	/** Returns the specified integer power of 10. */
	public static double power10(int power) {
//		return Math.pow(10, power);
// +++ above line is less accurate than the one below; see http://developer.java.sun.com/developer/bugParade/bugs/4358794.html
return Double.parseDouble("1E" + power);
	}
	
	/**
	* Returns the <i>magnitude</i> of x in a decimal (i.e. power of 10) scale.
	* <p>
	* To be precise, let <code>10^exponent</code> designate the largest power of 10 which does not exceed <code>|x|</code>.
	* Then this method returns <code><i>10^exponent</i></code> (not <code>exponent</code>, like the {@link #orderOfMagnitude orderOfMagnitude} method does).
	*/
	public static double magnitude(double x) {
		return power10( orderOfMagnitude( x ) );
	}
	
	/**
	* Returns the <i>order of magnitude</i> of x in a decimal (i.e. power of 10) scale.
	* <p>
	* To be precise, let <code>10^exponent</code> designate the largest power of 10 which does not exceed <code>|x|</code>.
	* Then this method returns <code><i>exponent</i></code> (not <code>10^exponent</code>, like the {@link #magnitude magnitude} method does).
	* <p>
	* Another way to view this is if x is written in scientific notation as
	* <blockquote><code>(+/-)a.bc... * 10^exponent</code></blockquote>
	* where <code>a, b, c...</code> are all decimal digits and additionally <code>a > 0</code>,
	* then this method returns <code>exponent</code>.
	* For example, <code>54321</code> is written in scientific notation as <code>5.4321*10^4</code>,
	* so this method returns <code>4</code> if supplied with <code>54321</code>.
	* <p>
	* @see <a href="http://en.wikipedia.org/wiki/Orders_of_magnitude">Orders of magnitude</a>
	*/
	public static int orderOfMagnitude(double x) {
		return (int)
			Math.floor(
				log10(
					Math.abs(x)
				)
			);
	}
	
	// -------------------- random numbers --------------------
	
	/**
	* Returns a number with the specified magnitude but random coefficient.
	* To be precise, the generic template for a positive number written in scientific notation is
	* <blockquote>x.yz... * 10^exponent</blockquote>
	* where x is a non zero digit and y,z,... are unrestricted digits,
	* so that x.yz... is a number in the range [1, 10).
	* What this method does is generate a random number for the coefficient component (x.yz...)
	* and multiples it times the specified power of 10.
	*/
	public static double nextRandomWithMagnitude(int magnitude) {
		return ( (Math.random()*9) + 1 ) * Math.pow(10, magnitude);	// note that since random returns a result in [0, 1), the result of this method will never be in the next higher magnitude
	}
	
	// -------------------- statistics functions: gaussianPdf, gaussianCdf, gaussianFit, gaussianAndersonDarling, gaussianKolmogorovSmirnov --------------------
	
	/**
	* Returns the value of the standard (i.e. mean = 0.0 and sd (standard deviation) = 1.0)
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>
	* of x.
	* <p>
	* Contract: the result is always in the range [0, {@link #inverseSqrt2pi}], and is never NaN or infinite.
	* <p>
	* @throws IllegalArgumentException if x is NaN
	*/
	public static double gaussianPdf(double x) throws IllegalArgumentException {
		return gaussianPdf(x, 0.0, 1.0);
	}
	
	/**
	* Returns the value of the
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>
	* of x, given the parameters mean and sd (standard deviation).
	* <p>
	* Contract: the result is always in the range [0, {@link #inverseSqrt2pi} / sd], and is never NaN or infinite.
	* <p>
	* @throws IllegalArgumentException if a combination of x, mean, sd is encountered which causes the result to be invalid
	* (e.g. any arg is NaN, or x and mean are identically signed infinities, or sd is infinite)
	*/
	public static double gaussianPdf(double x, double mean, double sd) throws IllegalArgumentException {
		// Note: defer arg checks for now and assume good; only check the result
		
		double z = (x - mean) / sd;
		double factor = inverseSqrt2pi / sd;
		double pdf = factor * Math.exp(-0.5 * z * z);
		
			// now do quick check of just pdf, and do not even try to figure out the cause if a problem is found since can be complicated; since problems are hopefully rare, this should be performance optimal:
		if ((pdf < 0) || (pdf > factor) || Double.isNaN(pdf) || Double.isInfinite(pdf)) throw new IllegalArgumentException("computed gaussianPdf = " + pdf + " which is an invalid result; occured for x = " + x + ", mean = " + mean + ", sd = " + sd);
		
		return pdf;
	}
	
	/**
	* Returns the value of the standard (i.e. mean = 0.0 and sd (standard deviation) = 1.0)
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Cumulative_distribution_function">cumulative distribution function (CDF)</a>
	* of x.
	* <p>
	* Contract: the result is always in the range [0, 1], and is never NaN or infinite.
	* <p>
	* @throws IllegalArgumentException if x is NaN
	*/
	public static double gaussianCdf(double x) throws IllegalArgumentException {
		return gaussianCdf(x, 0.0, 1.0);
	}
	
	/**
	* Returns the value of the
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Cumulative_distribution_function">cumulative distribution function (CDF)</a>
	* of x, given the parameters mean and sd (standard deviation).
	* <p>
	* Contract: the result is always in the range [0, 1], and is never NaN or infinite.
	* The implementation here should be a monotonically increasing function of x.
	* It is claimed to be "accurate to double precision throughout the real line"
	* (see <a href="http://www.wilmott.com/pdfs/090721_west.pdf">this article</a>; the code here is adapted from Figure 2).
	* <p>
	* @throws IllegalArgumentException if a combination of x, mean, sd is encountered which causes the result to be invalid
	* (e.g. any arg is NaN, or x and mean are identically signed infinities, or sd is infinite)
	*/
	public static double gaussianCdf(double x, double mean, double sd) throws IllegalArgumentException {
		// performance optimization: defer arg checks for now and assume good; only check the result
		
		double z = (x - mean) / sd;
		double zAbs = Math.abs(z);
		
		double cdf;
		if (zAbs > 37) {
			cdf = 0.0;
		}
		else {
			double exponential = Math.exp( -(zAbs * zAbs) / 2 );
			if (zAbs < 7.07106781186547) {
				double polyNumer = (3.52624965998911E-02 * zAbs) + 0.700383064443688;
				polyNumer = (polyNumer * zAbs) + 6.37396220353165;
				polyNumer = (polyNumer * zAbs) + 33.912866078383;
				polyNumer = (polyNumer * zAbs) + 112.079291497871;
				polyNumer = (polyNumer * zAbs) + 221.213596169931;
				polyNumer = (polyNumer * zAbs) + 220.206867912376;
				
				double polyDenom = (8.83883476483184E-02 * zAbs) + 1.75566716318264;
				polyDenom = (polyDenom * zAbs) + 16.064177579207;
				polyDenom = (polyDenom * zAbs) + 86.7807322029461;
				polyDenom = (polyDenom * zAbs) + 296.564248779674;
				polyDenom = (polyDenom * zAbs) + 637.333633378831;
				polyDenom = (polyDenom * zAbs) + 793.826512519948;
				polyDenom = (polyDenom * zAbs) + 440.413735824752;
				
				cdf = exponential * (polyNumer / polyDenom);
			}
			else {
				double repFrac = zAbs + 0.65;
				repFrac = zAbs + (4 / repFrac);
				repFrac = zAbs + (3 / repFrac);
				repFrac = zAbs + (2 / repFrac);
				repFrac = zAbs + (1 / repFrac);
				
				cdf = (exponential / repFrac) / 2.506628274631;
			}
		}
		if (z > 0) cdf = 1 - cdf;
		
			// now do quick check of just cdf, and do not even try to figure out the cause if a problem is found since can be complicated; since problems are hopefully rare, this should be performance optimal:
		if ((cdf < 0) || (cdf > 1) || Double.isNaN(cdf) || Double.isInfinite(cdf)) throw new IllegalArgumentException("computed gaussianCdf = " + cdf + " which is an invalid result; occured for x = " + x + ", mean = " + mean + ", sd = " + sd);
		
		return cdf;
	}
/*
Implementation above evaluates the rational polynomials using Horner's rule.
This discusses Horner's rule's numerical accuracy:
	http://www.springerlink.com/content/nn152728m1751671/
See this recent work on improving Horner's rule's numerical accuracy:
	http://www2.computer.org/portal/web/csdl/doi/10.1109/ARITH.2007.21
	http://jeudi.inrialpes.fr/2003/Raweb/arenaire/uid59.html

Another (claimed by the authors) good normal CDF algorithm is
	http://www.irstat.ir/Files/En/JIRSS/JIRSS%20Vol%207/No1,2/Vol.%207,%20Nos.%201-2,%20pp%2057-72.pdf
Unfortunately, this article is so riddled with errors, I do not trust it.
*/

/*
code below is my PREVIOUS implementation, which uses Marsaglia's algorithm; am saving in case some huge error is discovered in the above...

	**
	* Returns the value of the
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Cumulative_distribution_function">cumulative distribution function (CDF)</a>
	* of x, given the parameters mean and sd (standard deviation).
	* <p>
	* Contract: the result is always in the range [0, 1], and is never NaN or infinite.
	* <i>The current implementation, however, does not guarantee monotonicity of the result.</i>
	* <p>
	* The current implementation is claimed to be "accurate to absolute error less than 8 * 10^(-16)"
	* by its <a href="http://www.jstatsoft.org/v11/a04/paper">original developer</a>).
	* The java code in this method is actually adapted from
	* <a href="http://www.cs.princeton.edu/introcs/21function/Gaussian.java.html">here</a>.
	* See also the bullet point in the "No closed form" section of <a href="http://www.cs.princeton.edu/introcs/21function/">this webpage</a>
	* where the formula is discussed.
	* <p>
	* @throws IllegalArgumentException if a combination of x, mean, sd is encountered which causes the result to be invalid
	* (e.g. any arg is NaN, or x and mean are identically signed infinities, or sd is infinite)
	*
	public static double gaussianCdf(double x, double mean, double sd) throws IllegalArgumentException {
		// performance optimization: defer arg checks for now and assume good; only check the result
		
		double z = (x - mean) / sd;
		if (z < -8.0) return 0.0;
		if (z > 8.0) return 1.0;
		
		double zSquared = z * z;	// performance optimization: precompute this once, since will reuse many times in the loop below
		double sum = 0.0;
		double term = z;
		for (int i = 3; ; i += 2) {	// performance optimization: take the sum + term != sum test out of the loop and put it in body below so that compute sum + term just once
			double sumLast = sum;
			sum += term;
			if (sum == sumLast) break;	// exit loop once adding terms no longer has any effect
			else term *= zSquared / i;	// else prepare next term
		}
		double g = 0.5 + (sum * gaussianPdf(z));
		
			// if g is just slightly out of bounds, then assume that floating point errors caused this, so correct it to ensure contract:
		double errorAbs = 8e-16;	// this is the claimed absolute error
		if ((g < 0) && (g >= 0 - errorAbs)) g = 0.0;	// short circuit performance optimization is to do the g < 0 test first, since that should typically return false
		if ((g > 1) && (g <= 1 + errorAbs)) g = 1.0;
		
			// now do quick check of just g, and do not even try to figure out the cause if a problem is found since can be complicated; since problems are hopefully rare, this should be performance optimal:
		if ((g < 0) || (g > 1) || Double.isNaN(g) || Double.isInfinite(g)) throw new IllegalArgumentException("computed gaussianCdf = " + g + " which is an invalid result; occured for x = " + x + ", mean = " + mean + ", sd = " + sd);
		
		return g;
	}
*/
	
/*
+++ also need the inverse of standard normal cumulative distribution function:
	http://home.online.no/~pjacklam/notes/invnorm/
	
	java implementations:
		http://home.online.no/~pjacklam/notes/invnorm/impl/karimov/
		http://home.online.no/~pjacklam/notes/invnorm/impl/misra/

ACTUALLY, maybe the most accurate thing to do is do a binary search on an accurate gaussianCdf implementation
	--see the Moro transform discussed here
		http://www.wilmott.com/pdfs/090721_west.pdf
	--code would be something like:

	* @throws IllegalArgumentException if any arg is NaN, p < 0 or p > 1, mean or sd is infinite
	public static double gaussianCdfInverse(double p, double mean, double sd) throws IllegalArgumentException {
		Check.arg().validProbability(p);
		Check.arg().normal(mean);
		Check.arg().normal(sd);
		
			// handle special cases:
		if (p == 0) return Double.NEGATIVE_INFINITY;
		if (p == 1) return Double.POSITIVE_INFINITY;
		
			// use bisection algorithm to quickly zero in on the 2 closest numbers
		double high = 37.0;	// see the code inside gaussianCdf for why choose this
		double low = -37.0;	// see the code inside gaussianCdf for why choose this
		while (!isWithinOneUlp(high, low)) {	// Note: do NOT use high > low as test, since this could result in an infinite loop below?
			double mid = (low + high) / 2.0;
			double pmid = gaussianCdf(mid, mean, sd);
			if (pmid <= p) low = mid;	// Note: giving the == case to low means this algorithm always favors moving low instead of high
			else high = mid;
		}
		return (low + high) / 2.0;
	}
*/
	
	/**
	* Determines that <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>
	* which best fits numbers.
	* <p>
	* The mean and sd (standard deviation) for the Gaussian are simply calculated as the
	* sample mean and sample standard deviation of numbers.
	* This appears to be the standard procedure for
	* <a href="http://en.wikipedia.org/wiki/Normal_distribution#Estimation_of_parameters">fitting a Gaussian PDF</a>.
	* <p>
	* Note that the values returned in the {@link DistributionFit#bounds bounds} field of the result
	* are the mid points of the bin intervals (i.e. they are not interval boundary points).
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static GaussianFit gaussianFit(double[] numbers) throws IllegalArgumentException {
		// numbers is checked by Math2.mean and sd below
		
		double mean = mean(numbers);
		double sd = sd(numbers, mean, false);	// use false (unbiased) simply to calculate using the "divide by N - 1" rule
		
		double andersonDarling = gaussianAndersonDarling(numbers);
		double kolmogorovSmirnov = gaussianKolmogorovSmirnov(numbers, mean, sd);
		
		int n = 20;
		Bins bins = new Bins(numbers, n);
		double[] midpoints = bins.getBoundsMid();
		double[] pdfObserved = bins.getPdf();
		double[] pdfTheory = calcPdfTheory(bins, mean, sd);
		
		return new GaussianFit(mean, sd, andersonDarling, kolmogorovSmirnov, midpoints, pdfObserved, pdfTheory);
	}

	private static double[] calcPdfTheory(Bins bins, double mean, double sd) {
		double width = bins.getIntervalWidth();
		double[] bounds = bins.getBounds();
		int n = bounds.length;
		double[] pdfTheory = new double[n];
		for (int i = 0; i < n; i++) {
			//pdfTheory[i] = gaussianPdf(midpoints[i], mean, sd);	// do NOT do this, as is VERY approximate, and fails when the bin intervals get too coarse; best is to do the exact CDF calculation below
			double x = bounds[i];
			pdfTheory[i] = (gaussianCdf(x + width, mean, sd) - gaussianCdf(x, mean, sd)) / width;
		}
		return pdfTheory;
	}

/*
Code below is how I originally implemented gaussianFit.
It operates by fitting a theoretical Gaussian to the observed PDF.
It was abandoned in favor of the simpler and more robust implementation above which operates directly on numbers.
However, I am keeping it around as an example of how to fit a distribution via the empirical PDF.
	
	General discussion of curve fitting, including nonlinear fitting:
		http://mathworld.wolfram.com/NonlinearLeastSquaresFitting.html
		http://www.aip.org/tip/INPHFA/vol-9/iss-2/p24.html
		http://www.wavemetrics.com/products/igorpro/dataanalysis/curvefitting.htm
		
	Levenberg-Marquardt algorithm for solving nonlinear least squares:
		http://en.wikipedia.org/wiki/Levenberg-Marquardt_algorithm
		
	Java code for Levenberg-Marquardt algorithm:
		http://users.utu.fi/jaolho/java/lma.zip
		http://www.ccp4.ac.uk/jwc/Xhtml/Jdl/JdlLib_class_list.html#s9
		http://www.idiom.com/~zilla/Computer/Javanumeric/LM.java
		http://www.amstat.org/publications/jse/secure/v7n2/taur.cfm
	
	Java numerical code:
		Colt open source library provides many high performance and high quality routines for linear algebra, sparse and dense matrices, statistical tools for data
		analysis, random number generators, array algorithms, mathematical functions and complex numbers.
		http://dsd.lbl.gov/~hoschek/colt/
		
		The Ninja project is working to make Java competitive with Fortran and C++ in the domain of technical computing.
		http://www.research.ibm.com/ninja/

import jaolho.data.lma.LMA;
import jaolho.data.lma.LMAFunction;

	public XXX gaussianFit(double[] numbers) throws IllegalStateException, IllegalArgumentException {
		// numbers is checked by Math2.mean and sd below
		
		double meanData = Math2.mean(numbers);
		double sdData = Math2.sd(numbers, meanData);
		double[] parameters = new double[] {meanData, sdData};	// great guess as to the best fit gaussian's mean and sd is the data's corresponding values
		Bins bins = new Bins(numbers, 20);
		double[][] dataPoints = new double[][] { bins.getBoundsMid(), bins.getPdf() };
		double[] weights = new double[bins[0].length];
		Arrays.fill(weights, 1);	// must use 1 since have no idea, in general, what the error weights for each point are
		
		LMA lma = new LMA(new GaussianLMAFunction(), parameters, dataPoints, weights);
		lma.fit();
		
		double meanFit = parameters[0];
		double sdFit = parameters[1];
			// uncomment these lines if want to see how close the least squares parameters are to the data's statistics:
		//System.out.println("meanFit = " + meanFit + ", meanData = " + meanData);
		//System.out.println("sdFit = " + sdFit + ", sdData = " + sdData);
		
			// calculate the r-squared, which is one goodness of fit measure:
		double sse = lma.chi2;	// see the "Least Squares Fitting" section in http://www.physics.ohio-state.edu/~gan/teaching/spring04/Chapter6.pdf
		double sst = Math2.sst(bins[1]);
		double rSquared = 1 - (sse/sst);	// see http://en.wikipedia.org/wiki/R-squared
		
		return XXX;
	}
	
	private static class GaussianLMAFunction extends LMAFunction {
		
		private GaussianLMAFunction() {}
		
		public double getY(double x, double[] a) {	// Note: no arg checking, since is only used by gaussianFit, and can assume that that is written correctly
			if (a[1] <= 0) return Double.NaN;	// CRITICAL: have seen LMA occaisionally generate non-positive values for a[1] (sd), which Math2.gaussianPdf will correctly crash on; in order to not stop the fitting process, however, have found that if simply return NaN then LMA.fir will reduce the step size until a[1] becomes positive, and thus all is well (except for some error messages that it prints out to System.err)
			return Math2.gaussianPdf(x, a[0], a[1]);
		}
		
		public double getPartialDerivate(double x, double[] a, int parameterIndex) {	// Note: no arg checking, since is only used by gaussianFit, and can assume that that is written correctly
			if (a[1] <= 0) return Double.NaN;	// CRITICAL: have seen LMA occaisionally generate non-positive values for a[1] (sd), which Math2.gaussianPdf will correctly crash on; in order to not stop the fitting process, however, have found that if simply return NaN then LMA.fir will reduce the step size until a[1] becomes positive, and thus all is well (except for some error messages that it prints out to System.err)
				// analytical version:
			double g = Math2.gaussianPdf(x, a[0], a[1]);
			double z = (x - a[0]) / a[1];
			switch (parameterIndex) {
				case 0: return g * (z / a[1]);
				case 1: return (g / a[1]) * ( (z * z) - 1 );
				default: throw new IllegalArgumentException("parameterIndex = " + parameterIndex + " is an illegal value");
			}
			
			*
				// numerical version (works too, but has issues such as accuracy, choice of da below, etc):
			double da = 1e-10;	// tried 1e-20 and ran into singular matrix issues; what OUGHT to do is have a loop and make da bigger if it causes a singular matrix; da should probably be made into a field in this case to cache the value which works
			double[] aModified = new double[] {a[0], a[1]};
			aModified[parameterIndex] += da;
			double dy = getY(x, aModified) - getY(x, a);
			return dy/da;
			*
		}
	}
*/
	
// +++ OK, the math in the next two methods is correct,
// but instead of hard coding these methods for the Gaussian distribution, I ought to make these method generic
// and supplu the distribution as a param...
	
	/**
	* Calculates the <a href="http://en.wikipedia.org/wiki/Anderson%E2%80%93Darling_test">Anderson–Darling test statistic</a>
	* (as corrected for sample size)
	* for numbers against an assumed <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>.
	* See also <a href="http://www.itl.nist.gov/div898/handbook/eda/section3/eda35e.htm">this reference</a>
	* and <a href="http://www.isixsigma.com/forum/showthread.asp?messageID=62583">this discussion</a>.
	* <p>
	* Interpretation: if the result exceeds 0.632/0.751/.870/1.029,
	* then the null hypothesis is rejected for a 10%/5%/2.5%/1% respectively significance-level (alpha) test.
	* In other words, the smaller the result, the more likely it is that numbers comes from the Gaussian distribution.
	* Recall: the null hypothesis is that numbers follows the Gaussian distribution whose mean and sd are equal to the sample mean and sample sd of numbers,
	* and alpha is the probability of a Type I error (rejecting the null hypothesis when it is in fact true);
	* see <a href="http://en.wikipedia.org/wiki/Statistical_hypothesis_testing">this reference</a>.
	* <p>
	* Pros:
	* <ol>
	*  <li>the computation is fairly easy to program, and the only special function involved is the {@link #gaussianCdf Gaussian CDF}</li>
	*  <li>this test statisic is said to be the best one for precisely identifying Gaussian distributions</li>
	* </ol>
	* Cons:
	* <ol>
	*  <li>the computation requires copying numbers, and then relocating/rescaling and sorting that new array into standardized values</li>
	*  <li>
	*		<b>this method can return Infinity</b> when presented with data that departs significantly from a Gaussian distribution.
	*		This can occur, for example, with data that is almost entirely Gaussian but has just a single outlier that is many standard devations away from the mean.
	*		Debugging on 2009-09-28 showed that these infinite results are caused by inaccuracy in the current {@link #gaussianCdf(double) gaussianCdf} implementation
	*		(i.e. it returns 0 or 1 for non-infinite values).
	*		This causes the logarithms used by the Anderson–Darling test statistic to become infinite.
	*		These infinite results start for an outlier ~8.3 standard deviations away from the mean.
	*  </li>
	* </ol>
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double gaussianAndersonDarling(double[] numbers) throws IllegalArgumentException {
		Check.arg().notNull(numbers);	// all other check on numbers done by mean below
		
		double mean = mean(numbers);
		double sd = sd(numbers, mean, false);	// use false (unbiased) simply to calculate using the "divide by N - 1" rule
		
		int n = numbers.length;
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			y[i] = (numbers[i] - mean) / sd;
		}
		Arrays.sort(y);
		
		double sum = 0;
		for (int ii = 1; ii <= n; ii++) {	// the ii in this loop is the 1-offset i used in the wikipedia article
			int i = ii - 1;	// this i is the usual 0-offset java array index which we use for the y below
			double term1 = ((2 * ii) - 1) * Math.log( gaussianCdf(y[i]) );
			double term2 = ((2 * (n - ii)) + 1) * Math.log( 1 - gaussianCdf(y[i]) );
			sum += term1 + term2;
		}
		
		double A2 = (-n) - (sum / n);
		
		double nn = n;	// CRITICAL: must convert n to a double for use in the formula below, else operations like n * n could lead to int overflow at large int values
		double A2adj = A2 * (1 + (4 / nn) - (25 / (nn * nn)) );
		
		return A2adj;
	}
	
	/**
	* Calculates the "one sample" <a href="http://en.wikipedia.org/wiki/Kolmogorov%E2%80%93Smirnov_test#Kolmogorov.E2.80.93Smirnov_statistic">Kolmogorov–Smirnov statistic</a>
	* adjusted for the sample size (i.e. <code>D<sub>n</sub> * sqrt(n)</code>)
	* for numbers against the <a href="http://en.wikipedia.org/wiki/Normal_distribution">Gaussian</a> (i.e. normal)
	* <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>
	* that is specified by meanG and sdG.
	* See also <a href="http://www.itl.nist.gov/div898/handbook/eda/section3/eda35g.htm">this reference</a>.
	* <p>
	* Interpretation: <i>assuming n (i.e. numbers.length) > 40,</i> then if the result exceeds 1.07/1.22/1.36/1.52/1.63,
	* then the null hypothesis is rejected for a 20%/10%/5%/2%/1% respectively significance-level (alpha) test.
	* In other words, the smaller the result, the more likely it is that numbers comes from the Gaussian distribution.
	* Recall: the null hypothesis is that numbers follows the Gaussian distribution whose mean and sd are given by meanG and sdG,
	* and alpha is the probability of a Type I error (rejecting the null hypothesis when it is in fact true);
	* see Table 2.1 of <a href="http://www.ee.bilkent.edu.tr/grad/ms-thesis/can-ms.pdf">this reference</a>.
	* Note that meanG and sdG are supplied, and are not automatically calculated from the sample mean and sample sd of numbers.
	* (If this latter effect is desired, then the critical values must change for proper interpretation of the result;
	* see <a href="http://en.wikipedia.org/wiki/Lilliefors_test">this reference</a>.
	* or <a href="http://www.mathworks.com/access/helpdesk/help/toolbox/stats/index.html?/access/helpdesk/help/toolbox/stats/lillietest.htm">this reference</a>.)
	* <p>
	* Pros:
	* <ol>
	*  <li>the computation is fairly easy to program, and the only special function involved is the {@link #gaussianCdf Gaussian CDF}</li>
	*  <li>this method is far less vulnerable to returning Infinity compared to the {@link #gaussianAndersonDarling Anderson-Darling} calculation</li>
	* </ol>
	* Cons:
	* <ol>
	*  <li>the computation requires copying numbers, and then sorting that new array</li>
	*  <li>this test statisic is not as sensitive as the Anderson-Darling test</li>
	* </ol>
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double gaussianKolmogorovSmirnov(double[] numbers, double meanG, double sdG) throws IllegalArgumentException {
		Check.arg().notNull(numbers);	// all other check on numbers done by mean below
		
		int n = numbers.length;
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			y[i] = numbers[i];
		}
		Arrays.sort(y);
		
		double Dn = Double.NEGATIVE_INFINITY;
		double denom = 1.0 / n;
		for (int i = 0; i < n; i++) {
			double theory = gaussianCdf(y[i], meanG, sdG);
			
			double diff1 = Math.abs( theory - (i * denom) );
			if (Dn < diff1) Dn = diff1;
			
			double diff2 = Math.abs( theory - ((i + 1) * denom) );
			if (Dn < diff2) Dn = diff2;
		}
		
		return Dn * Math.sqrt(n);
	}
	
/*
+++ in the future, calculate other measures of the goodness of fit
	http://en.wikipedia.org/wiki/Goodness_of_fit
like
	chi-square
		http://www.itl.nist.gov/div898/handbook/eda/section3/eda35f.htm
			some of its cons:
				--the data have to be binnified
				--"This test is sensitive to the choice of bins...
					For the chi-square approximation to be valid, the expected frequency should be at least 5.
					This test is not valid for small samples, and if some of the counts are less than five, you may need to combine some bins in the tails."
				--The BEST binning approach with chi-squared is to divide the sample up into bins with ~equal sized number of samples
					--see Programmer notes in bb.science.Bins
				--requires critical values for the chi-squared distribution
				--those critical values for the chi-squared distribution depend on n, and I know of no adjustment that corrects for sample size
					(unlike the A2adj term in gaussianAndersonDarling below)
					which means that it is hard/impossible to rank the test statistic across many tests, each of which have different sample sizes
		http://www.stat.yale.edu/Courses/1997-98/101/chigf.htm
		http://en.wikipedia.org/wiki/Pearson%27s_chi-square_test
*/
	
	// -------------------- info theory: hammingXXX --------------------
	
	/**
	* Returns the Hamming distance between 2 ints.
	* The ints are viewed as binary strings, so the result is the number of bits where the 2 ints differ.
	* In particular, this method simply returns <code>{@link #hammingWeight hammingWeight}( bits1 ^ bits2 )</code>.
	* <p>
	* @see <a href="http://en.wikipedia.org/wiki/Hamming_distance">Wikipedia article on Hamming Distance</a>
	*/
	public static int hammingDistance(int bits1, int bits2) {
		return hammingWeight( bits1 ^ bits2 );
	}
	
	/**
	* Returns the Hamming weight of an int.
	* The int is viewed as a binary string, so the result is the number of bits equal to 1 (i.e. the "bit count").
	* <p>
	* @see <a href="http://en.wikipedia.org/wiki/Hamming_distance">Wikipedia article on Hamming Distance</a>
	*/
	public static int hammingWeight(int i) {
/*
		int weight = 0;
		for (int n = 0; n < 32; n++) {
			int lsb = i & 1;	// bitwise AND with 1 leaves just the least significant bit of i
			weight += lsb;	// add lsb to the result
			i >>>= 1;	// unsigned right shift i by 1 bit so that the next iteration detects the next bit; could use signed shift too (any performance difference?)
		}
		return weight;
// +++ the implementation above is about the slowest one possible.
// If ever use much again in the future, need to improve; see:
//	http://forum.java.sun.com/thread.jspa?messageID=4127141
// as well as see the resources listed in the bit twiddling section below,
// most especially the algorithms in http://graphics.stanford.edu/~seander/bithacks.html
*/
		return Integer.bitCount(i);	// new method as of 1.5!
	}
	
	// -------------------- bit twiddling --------------------
	
	/*
	code examples in general (usually C):
		http://graphics.stanford.edu/~seander/bithacks.html
		http://aggregate.org/MAGIC/
		http://www.garagegames.com/index.php?sec=mg&mod=resource&page=view&qid=386
		http://www.sjbaker.org/steve/software/cute_code.html
		http://www.ugcs.caltech.edu/~wnoise/base2.html
		http://www.cit.gu.edu.au/~anthony/info/C/bit_programming.txt
	code examples in java:
		http://mandala.co.uk/java/bitutils/BitUtils.java
	*/
	
	/**
	* Returns an int that equals the unsigned value of b's bits.
	* Specifically, the bit pattern of its least significant byte in the result is identical to the bit pattern of b,
	* and the most significant bytes are all 0.
	*/
	public static int byteToUnsignedInt(byte b) {
		return b & 0xFF;
	}
	
	// -------------------- misc methods: isWithinOneUlp, isWithin, checkNumbers --------------------
	
	/**
	* Determines whether or not a and b are within one ulp of each other.
	* <p>
	* @throws IllegalArgumentException if a or b is NaN or infinite
	*/
	public static boolean isWithinOneUlp(double a, double b) throws IllegalArgumentException {
		// args checked by isWithin below
		
		double ulp = (Math.abs(a) < Math.abs(b)) ? Math.ulp(a) : Math.ulp(b);	// logic needed because ulp is always measured from the smaller magnitude one
		return isWithin(a, b, ulp);
	}
	
	/**
	* Determines whether or not a and b are within epsilon of each other, that is,
	* that the distance between a and b is <= epsilon.
	* <p>
	* @throws IllegalArgumentException if a, b, or epsilon is NaN or infinite
	*/
	public static boolean isWithin(double a, double b, double epsilon) throws IllegalArgumentException {
		Check.arg().normal(a);
		Check.arg().normal(b);
		Check.arg().normal(epsilon);
		
		double distance = Math.abs(a - b);
		return distance <= epsilon;
	}
	
	/**
	* Checks that numbers is non-null, non-empty, and every element is non-NaN.
	* In addition, if infinityBad is true, then checks that every element is not infinite.
	* <p>
	* @return returns numbers, to enable method call chaining
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element is NaN, or is infinite
	*/
	public static double[] checkNumbers(double[] numbers, boolean infinityBad) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] is NaN");
			if (infinityBad && Double.isInfinite(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] = " + numbers[i]);
		}
		
		return numbers;
	}
	
	
	
	
	// ==================== SECTION 1: ARRAY METHODS ====================
	
	
	
	
	// -------------------- sum --------------------
	
	/**
	* Returns the sum of every element of numbers.
	* <p>
	* @throws IllegalArgumentException if numbers == null
	*/
	public static double sum(double[] numbers) throws IllegalArgumentException {
		Check.arg().notNull(numbers);
		
		double sum = 0.0;
		for (double d : numbers) sum += d;
		return sum;
	}
	
	// -------------------- normalize, isNormalized, normalizationSum --------------------
	
	/**
	* Simply calls <code>{@link #normalize(double[], double) normalize}(numbers, {@link #normalizationErrorTolerance_default})</code>.
	* <p>
	* @param numbers the array of numbers to normalize
	* @return returns numbers, to enable method call chaining
	* @throws IllegalArgumentException if numbers == null; any element of numbers is < 0, is NaN, or is infinite;
	* every element of numbers is 0
	* @throws IllegalStateException if normalization failed, which is defined as isNormalized(numbers, normalizationErrorTolerance_default) returns false
	*/
	public static double[] normalize(double[] numbers) throws IllegalArgumentException, IllegalStateException {
		return normalize(numbers, normalizationErrorTolerance_default);
	}
	
	/**
	* Normalizes numbers, that is, divides each element by that constant factor which causes
	* the sum of (the new values of) numbers to equal 1.
	* <p>
	* The elements of numbers must be legitimate values for normalization to even make sense.
	* Specifically, each must be >= 0, non-NaN and non-infinite.
	* <p>
	* Because of floating point errors, normalization is usually imperfect.
	* To cope, the normalization is deemed to have succeeded
	* if {@link #isNormalized isNormalized}(numbers, errorTolerance) returns true.
	* <p>
	* @param numbers the array of numbers to normalize
	* @param errorTolerance specifies how much normalization error to tolerate
	* @return returns numbers, to enable method call chaining
	* @throws IllegalArgumentException if numbers == null; any element of numbers or errorTolerance is < 0, is NaN, or is infinite;
	* every element of numbers is 0
	* @throws IllegalStateException if normalization failed, which is defined as isNormalized(numbers, errorTolerance) returns false
	*/
	public static double[] normalize(double[] numbers, double errorTolerance) throws IllegalArgumentException, IllegalStateException {
		// numbers checked by normalizationSum below
		// errorTolerance checked by isNormalized below
		
		double sum = normalizationSum(numbers);
		for (int i = 0; i < numbers.length; i++) {
			numbers[i] = numbers[i] / sum;
		}
		if (!isNormalized(numbers, errorTolerance)) throw new IllegalStateException("normalization failed (error > errorTolerance = " + errorTolerance);
		
		return numbers;
	}
	
	/**
	* Returns <code>{@link #isNormalized(double[], double) isNormalized}(numbers, {@link #normalizationErrorTolerance_default})</code>.
	* <p>
	* @param numbers the array of numbers to check for normalization
	* @throws IllegalArgumentException if numbers == null; any element of numbers is < 0, is NaN, or is infinite;
	* every element of numbers is 0
	*/
	public static boolean isNormalized(double[] numbers) throws IllegalArgumentException {
		return isNormalized(numbers, normalizationErrorTolerance_default);
	}
	
	/**
	* Reports whether or not numbers is normalized within an error specified by errorTolerance.
	* Specifically, if sumOfNumbers denotes {@link #normalizationSum normalizationSum}(numbers),
	* then |sumOfNumbers - 1| must be <= errorTolerance.
	* <p>
	* @param numbers the array of numbers to check for normalization
	* @param errorTolerance specifies how much normalization error to tolerate
	* @throws IllegalArgumentException if numbers == null; any element of numbers or errorTolerance is < 0, is NaN, or is infinite;
	* every element of numbers is 0
	*/
	public static boolean isNormalized(double[] numbers, double errorTolerance) throws IllegalArgumentException {
		// numbers checked by normalizationSum below
		Check.arg().normalNotNegative(errorTolerance);
		
		double sum = normalizationSum(numbers);
		double error = Math.abs(sum - 1.0);
		return (error <= errorTolerance);
	}
	
	/**
	* Returns the sum of every element of numbers, which is also the normalization factor for numbers.
	* <p>
	* This method differs from a generic array summation method solely in that it checks
	* that every element of numbers is a legitimate value for normalization to even make sense.
	* Specifically, each must be >= 0, non-NaN and non-infinite.
	* <p>
	* Contract: this method guarantees to never return a result that is <= 0, NaN, or infinity.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* any element of numbers is is NaN, infinite, or < 0; if every element of numbers == 0;
	* if the result is an invalid value
	*/
	public static double normalizationSum(double[] numbers) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		double sum = 0.0;
		for (int i = 0; i < numbers.length; i++) {
			// Note: defer NaN and infinity checks for now and assume good; see below
			Check.arg().notNegative(numbers[i]);
			
			sum += numbers[i];
		}
		
			// now do quick checks, and only spend the time to figure out the cause if a problem is found; since problems are hopefully rare, this should be performance optimal:
		if (Double.isNaN(sum) || Double.isInfinite(sum)) {
			checkNumbers(numbers, true);
			throw new IllegalArgumentException("calculated an invalid sum (" + sum + "), but could not determine the cause (if positive infinity, then must have been overflow)");
		}
		Check.arg().positive(sum);	// < 0.0 should NEVER happen; == 0.0 should only happen if every element of numbers == 0
		
		return sum;
	}
	
	// -------------------- subtractParallelComponent --------------------
	
	/**
	* Subtracts from vector v1 that component which lies parallel to vector v2.
	* Upon return from this method, v1 will be perpendicular to v2.
	* <p>
	* @return returns v1, to enable method call chaining
	* @throws IllegalArgumentException if either of v1 == null; v2 == null;
	* v1.length != v2.length; v1.length = v2.length = 0;
	* any element of either v1 or v2 is NaN or infinity;
	* every element of v2 is 0
	* @see "Brent's analytical work for development of the formulas"
	*/
	public static double[] subtractParallelComponent(double[] v1, double[] v2) throws IllegalArgumentException {
		Check.arg().notEmpty(v1);
		Check.arg().hasSize(v2, v1.length);
		
		int n = v1.length;
		double sum1 = 0;
		double sum2 = 0;
		for (int i = 0; i < n; i++) {
			// Note: defer NaN, infinity, and 0 checks for now and assume good; see below
			
			sum1 += v1[i] * v2[i];
			sum2 += v2[i] * v2[i];
		}
		
			// now do quick checks, and only spend the time to figure out the cause if a problem is found; since problems are hopefully rare, this should be performance optimal:
		if (Double.isNaN(sum1) || Double.isInfinite(sum1)) {
			checkNumbers(v1, true);
			checkNumbers(v2, true);
			throw new IllegalArgumentException("calculated an invalid sum1 (" + sum1 + "), but could not determine the cause (if infinity, then must have been overflow)");
		}
		Check.arg().positive(sum2);	// < 0.0 should NEVER happen; == 0.0 should only happen if every element of v2 == 0
		if (Double.isNaN(sum2) || Double.isInfinite(sum2)) {
			checkNumbers(v2, true);
			throw new IllegalArgumentException("calculated an invalid sum2 (" + sum2 + "), but could not determine the cause (if positive infinity, then must have been overflow)");
		}
		
			// now do the actual parallel component subtraction:
		double alpha = sum1 / sum2;
		for (int i = 0; i < n; i++) {
			v1[i] -= alpha * v2[i];
		}
		
		return v1;
	}
	
	// -------------------- xxxLeastSquaresFit --------------------
	
	/**
	* Given a series of 2D ordered pairs stored in the xValues and yValues arrays,
	* that is, the points (xValues[0], yValues[0]), (xValues[1], yValues[1]), ...,
	* this method determines the coefficients a and b of the linear fit y = a + bx
	* as well as some of the fitness measures.
	* Specifically, the result is the double array {a, b, r2, ssr, sse}
	* where a and b are the linear fit parameters, r2 is the correlation coefficient,
	* ssr is the sum of squared residuals, and sse is the sum of squared errors.
	* <p>
	* Contract: this method guarantees to never return an element in the result that is NaN or infinity.
	* <p>
	* @throws IllegalArgumentException if xValues or yValues == null; xValues.length or yValues.length == 0;
	* any element of xValues or yValues is NaN or infinite; xValues.length != yValues.length
	* @see <a href="http://mathworld.wolfram.com/LeastSquaresFitting.html">Least Squares Fitting</a>
	* @see <a href="http://mathworld.wolfram.com/CorrelationCoefficient.html">Correlation Coefficient</a>
	*/
	public static double[] linearLeastSquaresFit(double[] xValues, double[] yValues) throws IllegalArgumentException {
		// xValues and yValues initially checked by mean below
		
		double xAvg = mean(xValues);
		double yAvg = mean(yValues);
		
			// but the length check will be mised by the above calls, so do it now:
		Check.arg().hasSize(xValues, yValues.length);
		
		int n = xValues.length;
		double ssxx = 0.0;
		double ssxy = 0.0;
		double ssyy = 0.0;
		for (int i = 0; i < n; i++) {
			double x = xValues[i];
			double y = yValues[i];
			ssxx += x * x;
			ssxy += x * y;
			ssyy += y * y;
		}
		ssxx -= n * xAvg * xAvg;
		ssxy -= n * xAvg * yAvg;
		ssyy -= n * yAvg * yAvg;
		
		double b = ssxy / ssxx;
		double a = yAvg - (b * xAvg);
		double r2 = (ssxy * ssxy) / (ssxx * ssyy);
		double ssr = ssyy * r2;
		double sse = ssyy - ssr;
		
		Check.arg().normal(a);	// can only happen if there is some problem with xValues or yValues
		Check.arg().normal(b);
		Check.arg().normal(r2);
		Check.arg().normal(ssr);
		Check.arg().normal(sse);
		
		return new double[] {a, b, r2, ssr, sse};
	}
	
	// -------------------- autocoXXX statistics calculations: autocorrelation, autocovariance --------------------
	
	/*
	References in the code below:
	
		BOX-JENKINS = "Time Series Analysis: Forecasting & Control (2nd Edition, 1976)" by George Box, Gwilym M. Jenkins, Gregory Reinsel
			--poorly scanned version of Chapters 2-3: http://www.stat.purdue.edu/~wsc/learning/reading/time.series/boxjenkins.ch2-3.pdf
			--note: there is also a 1994 3rd ed with Gregory Reinsel as a new author:
				--ANY ONLINE SOURCE OF THIS TEXT?
				--buy here: http://www.amazon.com/Time-Analysis-Forecasting-Control-3rd/dp/0130607746
				--snippet of amazon review, which tells of other texts to look at:
					This is not an up-to-date text on the theory of time series.
					It deals strictly with the time domain approach and does not include recent advances
					including nonlinear and bilinear models, models with non-Gaussian innovations
					and bootstrap or other resampling methods.
					To get a balanced approach that includes the theory for frequency and time domain approaches
					the book by Shumway, the latest edition of the Brockwell and Davis text
					and the latest edition of Fuller's text are appropriate.
					For a graduate course I taught at UC Santa Barbara in 1981 I used the first edition of Fuller's book.
					Anderson provides a thorough account of the time domain theory.
					Excellent texts that specialize in the frequency domain approach are Bloomfield's second edition
					and the two volume book by Priestley.
					Brillinger's text is also worthwhile for those interested in spectral theory (frequency domain statistics)
		
		MEKO = David M. Meko's course "GEOS 585A, Applied Time Series Analysis"
			--http://www.ltrr.arizona.edu/~dmeko/geos585a.html
			--BELOW WE ONLY BORROW MATERIAL FROM WEEK 3 NOTES: http://www.ltrr.arizona.edu/~dmeko/notes_3.pdf
		
		See also
			--http://www.xycoon.com/basics.htm
				(V.I.1-17) onwards
	*/
	
	/**
	* Returns the autocorrelation function (acf) of numbers along with confidence intervals (CIs) for each element.
	* <p>
	* The result contains three double arrays.
	* Each array has the same length, which is one less than numbers' length.
	* The first array is the acf, the second array is the CI lower bounds, the third array is the CI upper bounds.
	* Note that the CI calculation involves the "large lag standard error",
	* and so it assumes that numbers is a stationary Gaussian data series, among other assumptions/approximations.
	* <p>
	* The calculation first computes the autocovariance function by calling {@link #autocovariance autocovariance}.
	* <b>See that method's javadocs for warnings on the length of numbers
	* and how many elements in the result are likely trustworthy.</b>
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	* @throws IllegalStateException if any element of the result is calculated to be NaN or infinity
	*/
	public static double[][] autocorrelation(double[] numbers) throws IllegalArgumentException, IllegalStateException {
		// numbers checked by autocovariance below
		
		int N = numbers.length;
		double[] c = autocovariance(numbers);
		
		double[] r = new double[c.length];
		r[0] = 1;
		for (int k = 1; k < r.length; k++) {
			r[k] = c[k] / c[0];	// see MEKO eq. (6)
			if (Double.isNaN(r[k]) || Double.isInfinite(r[k])) throw new IllegalStateException("r[" + k + "] is " + r[k]);
		}
		
		double[] llse = new double[r.length];	// llse = large-lag standard error; for the calculation of llse & vark below, see MEKO eq. (10)
		llse[0] = 0;
		for (int k = 1; k < r.length; k++) {
			double vark = 0;	// is the variance of r[k]
			for (int i = 1; i < k; i++) vark += r[i] * r[i];
			vark *= 2;
			vark += 1;
			vark /= N;
			llse[k] = Math.sqrt(vark);
			if (Double.isNaN(llse[k]) || Double.isInfinite(llse[k])) throw new IllegalStateException("llse[" + k + "] is " + llse[k]);
		}
				
		double[] ciLower = new double[r.length];	// confidence interval lower bound for a confidence level of 95%
		double[] ciUpper = new double[r.length];	// confidence interval upper bound for a confidence level of 95%
		ciLower[0] = 1;	// there should be NO uncertainty about r[0], right?
		ciUpper[0] = 1;	// there should be NO uncertainty about r[0], right?
		double meanr = -1.0 / N;	// see MEKO eq. (8); only valid for k > 0
		for (int k = 1; k < r.length; k++) {
			double delta = 1.96 * llse[k];
			ciLower[k] = meanr - delta;	// standard result (assuming Gaussian)
			ciUpper[k] = meanr + delta;	// standard result (assuming Gaussian)
			if (Double.isNaN(ciLower[k]) || Double.isInfinite(ciLower[k])) throw new IllegalStateException("ciLower[" + k + "] is " + ciLower[k]);
			if (Double.isNaN(ciUpper[k]) || Double.isInfinite(ciUpper[k])) throw new IllegalStateException("ciUpper[" + k + "] is " + ciUpper[k]);
		}
		
		return new double[][] {r, ciLower, ciUpper};
	}
	
	/**
	* Returns the autocovariance function (acvf) of numbers.
	* <p>
	* The result has a length one less than numbers' length.
	* <p>
	* <b>Beware of the following issues:</b>
	* <ol>
	*  <li>should probably not call this method if numbers has less than 50 elements</li>
		<!-- requiring N >= 50 is suggested by BOX-JENKINS on p. 33 -->
		<!-- requiring N >= 50-100 is suggested by http://w3eos.whoi.edu/12.747/notes/lect06/l06s02.html "common sense tells you that you ought to avoid doing this kind of analysis for time series shorter than 50-100 observations" -->
	*  <li>only the first 20-25% elements in the result are likely trustworthy</li>
		<!-- examining no more than N/4 is suggested by BOX-JENKINS on p. 33 -->
		<!-- examining no more than N/5 is suggested by http://w3eos.whoi.edu/12.747/notes/lect06/l06s02.html "Now common sense tells you not to believe those lags much more than N/5" -->
	* </ol>
	* <b>All these issues go unchecked by this method; it is up to the user to address.</b>
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	* @throws IllegalStateException if any element of the result is calculated to be NaN or infinity
	*/
	public static double[] autocovariance(double[] numbers) throws IllegalArgumentException, IllegalStateException {
		// numbers checked by mean below
		
		double mean = mean(numbers);
		
		int N = numbers.length;
		double[] c = new double[N - 1];	// for the calculation of c below, see MEKO eq. (5)
		for (int k = 0; k < c.length; k++) {
			double sum = 0;
			for (int i = 0; i < N - k; i++) sum += (numbers[i] - mean) * (numbers[i + k] - mean);
			c[k] = sum / N;	// this is a biased estimate, but is conjectured to have lower MSE than the smaller biased "/ (N - k)" estimator; see MEKO eq. (7)
			if (Double.isNaN(c[k]) || Double.isInfinite(c[k])) throw new IllegalStateException("c[" + k + "] is " + c[k]);
		}
		return c;
	}
	
	// -------------------- low level statistics calculations: min, max, mean, median, quantile, sd, variance, sst --------------------
	
	/**
	* Returns the minimum element of numbers.
	* <p>
	* Contract: the result is never NaN.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double min(double[] numbers) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		double min = Double.POSITIVE_INFINITY;
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] is NaN");
			
			if (min >= numbers[i]) min = numbers[i];
		}
		return min;
	}
	
	/**
	* Returns the maximum element of numbers.
	* <p>
	* Contract: the result is never NaN.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double max(double[] numbers) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] is NaN");
			
			if (max <= numbers[i]) max = numbers[i];
		}
		return max;
	}
	
	/**
	* Returns both the minimum and maximum element of numbers.
	* The result is always a 2 element array, with the min at index 0 and the max at index 1.
	* <p>
	* If you need both the min and the max calculated, calling this method once is more efficient
	* than calling {@link #min min} and {@link #max max} separately because numbers is only iterated over once.
	* <p>
	* Contract: the result is never null, always has length == 2, and never has a NaN element.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double[] minMax(double[] numbers) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] is NaN");
			
			if (min >= numbers[i]) min = numbers[i];
			if (max <= numbers[i]) max = numbers[i];
		}
		return new double[] {min, max};
	}
	
	/**
	* Returns the arithmetic mean of numbers.
	* <p>
	* In the terminology of statistics, if numbers is the population, then the result is the population's mean.
	* But if numbers is merely a sample from the population, then the result is the sample mean,
	* which is an <a href="http://en.wikipedia.org/wiki/Unbiased_estimator">unbiased estimate</a> of the population's mean.
	* <p>
	* Contract: the result is never NaN, but may be infinite.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double mean(double[] numbers) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		
		double sum = 0.0;
		for (int i = 0; i < numbers.length; i++) {
			// Note: defer NaN check for now and assume good; see below
			
			sum += numbers[i];
		}
		
			// now do quick checks, and only spend the time to figure out the cause if a problem is found; since problems are hopefully rare, this should be performance optimal:
		if (Double.isNaN(sum)) {
			checkNumbers(numbers, false);
			throw new IllegalStateException("calculated a NaN sum, but failed to find a NaN element; this should never happen");	// this should never happen, so am not declaring throws IllegalStateException
		}
		
		return sum / numbers.length;
	}
	
	/**
	* Returns the median element of numbers.
	* <p>
	* The implementation here simply returns <code>{@link #quantile quantile}(numbers, 1, 2)</code>.
	* <i>This technique requires numbers to be sorted by the user before calling this method.</i>
	* <p>
	* Contract: the result is never NaN but could be infinity.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN; numbers is not sorted
	*/
	public static double median(double[] numbers) throws IllegalArgumentException {
		return quantile(numbers, 1, 2);
// +++ there is a faster linear algorithm for medians if the numbers are not sorted: http://en.wikipedia.org/wiki/Selection_algorithm
	}
	
	/**
	* Returns the kth q-<a href="http://en.wikipedia.org/wiki/Quantile">quantile</a> of numbers.
	* <p>
	* Special case: if <code>numbers.length == 1</code>, then this method immediately returns <code>numbers[0]</code> regardless of the values of k and q.
	* <p>
	* Otherwise, the calculation uses the <a href="http://en.wikipedia.org/wiki/Quantile#Estimating_the_quantiles_of_a_population">Weighted average</a> technique.
	* <i>This technique requires numbers to be sorted by the user before calling this method.</i>
	* <p>
	* Contract: the result is never NaN but could be infinity.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN; numbers is not sorted;
	* k < 1; q < 2; k >= q
	*/
	public static double quantile(double[] numbers, int k, int q) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		for (int i = 0; i < numbers.length; i++) {
			if (Double.isNaN(numbers[i])) throw new IllegalArgumentException("numbers[" + i + "] is NaN");
			if ((i > 0) && (numbers[i - 1] > numbers[i])) throw new IllegalArgumentException("sort failure: numbers[" + (i - 1) + "] = " + numbers[i - 1] + " > numbers[" + i + "] = " + numbers[i]);
		}
		Check.arg().positive(k);
		if (q < 2) throw new IllegalArgumentException("q = " + q + " < 2");
		if (k >= q) throw new IllegalArgumentException("k = " + k + " >= q = " + q);
		
		if (numbers.length == 1) return numbers[0];
		
		double p = ((double) k) / q;
		double index = (numbers.length - 1) * p;
		int j = (int) Math.floor(index);
		double g = index - j;
		return numbers[j] + (g * (numbers[j + 1] - numbers[j]));	// use j and j + 1 instead of j + 1 and j + 2 of wikipedia because of 0-offset java arrays
	}
	
	/**
	* Returns <code>{@link #sd(double[], boolean) sd}(numbers, true)</code>.
	* <p>
	* <i>Use this version only when mean has not previously been calculated,</i>
	* since this method will internally calculate it.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double sd(double[] numbers) throws IllegalArgumentException {
		return sd(numbers, true);
	}
	
	/**
	* Returns <code>{@link #sd(double[], double, boolean) sd}(numbers, {@link #mean mean}(numbers), biased)</code>.
	* <p>
	* <i>Use this version only when mean has not previously been calculated,</i>
	* since this method will internally calculate it.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; any element of numbers is NaN
	*/
	public static double sd(double[] numbers, boolean biased) throws IllegalArgumentException {
		return sd(numbers, mean(numbers), biased);
	}
	
	/**
	* Returns <code>{@link #sd(double[], double, boolean) sd}(numbers, mean, true)</code>.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* mean or any element of numbers is NaN
	*/
	public static double sd(double[] numbers, double mean) throws IllegalArgumentException {
		return sd(numbers, mean, true);
	}
	
	/**
	* Returns the standard deviation of numbers.
	* <p>
	* In the terminology of statistics, if numbers is the population, then the result is the population's standard deviation.
	* But if numbers is merely a sample from the population, then the result is the sample standard deviation,
	* which is a <a href="http://en.wikipedia.org/wiki/Unbiased_estimator">biased estimate</a> of the population's standard deviation.
	* <p>
	* This method simply returns the square root of {@link #variance(double[], double, boolean) variance}(numbers, mean, biased).
	* <i>Therefore, the mean and biased parameters must have the exact meanings expected by variance.</i>
	* In particular, the biased parameter will control whether or not the <i>variance</i> estimate is biased.
	* <i>It does not control the bias of the standard deviation estimate returned by this method.
	* In fact, the estimate returned by this method is always biased regardless of the value of the biased parameter.</i>
	* The effect of biased == true is that variance will use the "divide by N" rule,
	* while biased == false causes variance to use the "divide by N - 1" rule.
	* <p>
	* A <a href="http://en.wikipedia.org/wiki/Unbiased_estimation_of_standard_deviation">correction</a>
	* exists to get an unbiased estimator for the standard deviation if normality is assumed.
	* This method does not implement this correction, however, for two reasons.
	* First, that unbiased estimator is inferior to the simple "divide by N" estimator:
	* <blockquote>
	*	[the "divide by N" estimator has] uniformly smaller mean squared error than does the unbiased estimator,
	*	and is the maximum-likelihood estimate when the population is normally distributed.<br/>
	*	<a href="http://en.wikipedia.org/wiki/Standard_deviation#Estimating_population_standard_deviation_from_sample_standard_deviation">Reference</a>
	* </blockquote>
	* Second, it is better to avoid assumptions like normality.
	* <p>
	* Summary of the above: use biased == true if want the most accurate result.
	* Only use biased == false if there is some other requirement for it
	* (e.g if are computing confidence intervals, the standard theory which leads to Student's t-distribution
	* was developed using the biased == false, "divide by N - 1", estimator).
	* <p>
	* Contract: the result is always >= 0 (including positive infinity), and is never NaN.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; mean or any element of numbers is NaN
	* @see <a href="http://mathworld.wolfram.com/StandardDeviationDistribution.html">Mathworld article on the sample standard deviation distribution</a>
	*/
	public static double sd(double[] numbers, double mean, boolean biased) throws IllegalArgumentException {
		return Math.sqrt( variance(numbers, mean, biased) );
	}
	
	/**
	* Returns <code>{@link #variance(double[], boolean) variance}(numbers, true)</code>.
	* <p>
	* Here, the default value of true for biased is supplied because that yields the most accurate results
	* (see {@link #variance(double[], double, boolean) variance}).
	* <p>
	* <i>Use this version only when mean has not previously been calculated,</i>
	* since this method will internally calculate it.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* mean is calculated to be or any element of numbers is NaN
	*/
	public static double variance(double[] numbers) throws IllegalArgumentException {
		return variance(numbers, true);
	}
	
	/**
	* Returns <code>{@link #variance(double[], double, boolean) variance}(numbers, {@link #mean mean}(numbers), biased)</code>.
	* <p>
	* <i>Use this version only when mean has not previously been calculated,</i>
	* since this method will internally calculate it.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* mean is calculated to be or any element of numbers is NaN
	*/
	public static double variance(double[] numbers, boolean biased) throws IllegalArgumentException {
		return variance(numbers, mean(numbers), biased);
	}
	
	/**
	* Returns <code>{@link #variance(double[], double, boolean) variance}(numbers, mean, true)</code>.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* mean or any element of numbers is NaN
	*/
	public static double variance(double[] numbers, double mean) throws IllegalArgumentException {
		return variance(numbers, mean, true);
	}
	
	/**
	* Returns the variance of numbers.
	* <p>
	* Statistically speaking, if numbers is the population, then the result is the population's variance.
	* But if numbers is merely a sample from the population, then the result is an estimate of the population's variance.
	* <p>
	* The mean parameter must be the arithmetic mean of numbers (i.e. what {@link #mean mean}(numbers) returns).
	* <p>
	* Algorithms exist for computing the variance without doing an explicit calculation of the mean.
	* For example, one can compute the sum of the squares of numbers and subtract the square of the sum of numbers,
	* with both sums efficiently calculated in a single loop;
	* see <a href="http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Algorithm_I">Algorithm 1</a>.
	* And there is even an algorithm that does not require storing all the elements,
	* and so is good for streaming data; see: <a href="http://mathworld.wolfram.com/SampleVarianceComputation.html">this article</a>
	* <p>
	* Nevertheless, this method requires that the mean parameter be supplied
	* because the most accurate numerical algorithms use differences from the mean
	* (this method relies on {@link #sst(double[], double) sst}; see additional algorithm notes there).
	* <p>
	* The biased parameter determines whether or not the result is a biased or unbiased extimate
	* (assuming that numbers is a sample from a population).
	* Specifically, this method returns <code>{@link #sst(double[], double) sst}(numbers, mean) / denominator</code>.
	* If biased is true, then denominator is numbers.length (i.e. the "divide by N" rule),
	* else denominator is numbers.length - 1 (i.e. the "divide by N - 1" rule).
	* <p>
	* There are a few situations where one should use the unbiased (biased param == false) estimator.
	* The best example is probably the calculation of confidence intervals,
	* because the conventional theory (which leads to Student's t-distribution) was developed using this unbiased variance estimator.
	* <p>
	* In general, however, the biased estimator is more accurate:
	* <pre>
		What is the BEST estimator for the population variance given a sampling of the population?
		
		The naive estimator formula is simply
			variance = SST / n
		where SST is the sum of the squares of the differences of each sample from the estimated population mean, and n is the number of samples.  But this estimator is biased.
		
		The usual formula for the unbiased estimator is
			variance = SST / (n - 1)
			
		Now, here is the trickiness: while unbiasedness is nice, what you really want is a low mean squared error (MSE):
			http://en.wikipedia.org/wiki/Estimator
		In fact, the OPTIMAL estimator is one with the minimum MSE (MMSE):
			http://en.wikipedia.org/wiki/Minimum_mean_squared_error
			
		Now, this article, says that the
			SST / n
		estimator has a "lower estimation variability"
		
		http://en.wikipedia.org/wiki/Unbiased_estimator
		where I assume that "lower estimation variability" has the same meaning as "variance of the estimator" in that MSE reference above:
			http://en.wikipedia.org/wiki/Estimator
			
		In fact, its smaller estimator variance, combined with its negative bias (due to the larger denominator, which causes it to underestime the true variance), actually causes the simple n formula to have lower MSE than the n - 1 one:
			http://en.wikipedia.org/wiki/Mean_squared_error.htm
			
		That implies the n estimator is better than the n - 1 one.
		
		But is it the OPTIMAL one--the estimator with MMSE?
		
		Or is that an unknown at present in statistics?
		
		This article
			http://cnx.rice.edu/content/m11267/latest/
		has a seemingly related discussion, but the Example 1 that they give seems to be irrelevant (e.g. you generally do NOT know the variance-sub-n quantity, nor the mean and variance of the theta quantities, so his result in formulas 9 or 10 is practically useless).
	* </pre>
<!--
+++ further questions:

--what about the sd estimator?
Here too, the biased one is better than the unbiased sd estimator
	[the "divide by N" estimator has] uniformly smaller mean squared error than does the unbiased estimator,
	and is the maximum-likelihood estimate when the population is normally distributed.<br/>
	http://en.wikipedia.org/wiki/Standard_deviation#Estimating_population_standard_deviation_from_sample_standard_deviation
but what is the OPTIMAL sd est?
	--Note that for estimating the variance, as opposed to the sd, it looks like th edivide by n + 1 formula is not only lower in MSE than the the divide by n - 1 formula but is even lower in MSE than the divide by n formula
		http://en.wikipedia.org/wiki/Mean_squared_error#Examples

--given estimators with a negative bias,
is it possible to set things up so that the negative bias cancels out the positive definite estimator variance
and so you get MSE = 0?
	NO: because the bias enters into the MSE via its square, so it is always non-negative term for the MSE
	http://en.wikipedia.org/wiki/Mean_squared_error

	Note: the wiki reference above also makes the statement:
		The unbiased model with the smallest MSE is generally interpreted as best explaining the variability in the observations.
	WHY do they only consider unbiased estimators?



http://www.wilmott.com/


Stats questions/research ideas:

1) if know the bias of an estimator,
can you simply subtract it to obtain a new (shifted) estimator which is now unbiased
but should have the same variance (and hence lower MSE)?

and what ARE the optimal estimators for the common stats?  or are they not known?
	so far, i just know that the /n is better than /(n - 1), but do not know that it is optimal globally


2) for conf intervals, is it possible that could obtain a narrower interval
if instead of +- the SAME constant from the mean,
you did + of one amount and - of a different amount?

I think that doing +- the same amount is, in fact, optimal for certain assumptions about the probability distribution,
such as that it is symmetric about the mean (e.g. as it is for a Gaussian),
but this will not be true for other asymmetric distributions (e.g. log-normal, chi-square)

Answer: if look at the chi-squared distribution used for conf intervals for sd/var,
it turns out that the standard theory uses asymmetric + and - values from the mean.
Furthermore, at least one person has noted that the standard theory does not give minimum intervals:
	... the resulting ... confidence interval is not the shortest that can be formed using the available data. The tables and appendixes gives solutions for a and b that yield confidence interval of minimum length for the standard deviation.
	http://cnx.org/content/m13496/latest/


3) if you have multiple estimators, can you combine them somehow to, say, get narrower conf intervals?

for mean estimators, we not only have the n vs n - 1 estimators,
but consider a geometric mean estimator that is the nth root of the product of the n samples;
	--what are the properties of this one?
		--it is nonlinear, so it almost certainly is biased...
	--could one play with the root value to obtain a better quality estimator?
	--could it have better robustness characteristics?

there have to be an infinite number of other estimators,
which may mean that it is impossible to work with the entire space of possible estimators

what possibly may work instead is to come up with an algorithm that takes a set of estimators
and generates a new estimator that is some function of the original set but is guaranteed to be a better one;
then you could add that new one back to the set and repeat the process?


4) THIS ARTICLE HAS SOME INTERESTING THOUGHTS, such as what he calls "ANTI-biased" estimators, which he achieves by leaving out samples
	http://www.mathpages.com/home/kmath497.htm
Makes me think: is there any way that you could show that you can get a better estimator using more complicated calculations?

What about doing n jacknife estimates (where each variance_i is calculated using some other estimator but with x_i left out) and then using all n of the results somehow to prove bounds on the population variance?
Maybe would need to use nonlinear operations like Maximum(variance_i) and/or Minimum(variance_i)


articles:
	Can the Sample Variance Estimator Be Improved by Using a Covariate?
	http://www.ingentaconnect.com/content/asa/jabes/2002/00000007/00000002/art00003
	
	A Note on an Estimator for the Variance That Utilizes the Kurtosis
	http://www.jstor.org/pss/2684353


outlines how to derive that the / n variance estimator is biased:
	http://www.ds.unifi.it/VL/VL_EN/sample/sample4.html
	http://www.math.uah.edu/stat/sample/Variance.xhtml

-->
	* <p>
	* Contract: the result is always >= 0 (including positive infinity), and is never NaN.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; mean or any element of numbers is NaN
	* @see <a href="http://mathworld.wolfram.com/SampleVariance.html">Mathworld article on sample variance</a>
	* @see <a href="http://en.wikipedia.org/wiki/Unbiased_estimator">Wikipedia article on unbiased estimators</a>
	*/
	public static double variance(double[] numbers, double mean, boolean biased) throws IllegalArgumentException {
		double denominator = biased ? numbers.length : numbers.length - 1;
		return sst(numbers, mean) / denominator;
	}
	
	/**
	* Returns <code>{@link #sst(double[], double) sst}(numbers, {@link #mean mean}(numbers))</code>.
	* <p>
	* <i>Use this version only when mean has not previously been calculated,</i>
	* since this method will internally calculate it.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0;
	* mean is calculated to be or any element of numbers is NaN
	*/
	public static double sst(double[] numbers) throws IllegalArgumentException {
		return sst(numbers, mean(numbers));
	}
	
	/**
	* Caluculates the SST (Sum of Squares, Total), that is,
	* the sum of the squares of the differences from mean of each element of numbers.
	* <p>
	* In order to obtain the highest accuracy, this method uses a form of
	* <a href="http://en.wikipedia.org/wiki/Compensated_summation">compensated summation</a>
	* (see <a href="http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Algorithm_II_.28compensated.29">Algorithm II (compensated)</a>).
	* <p>
	* Contract: the result is always >= 0 (including positive infinity), and is never NaN.
	* <p>
	* @throws IllegalArgumentException if numbers == null; numbers.length == 0; mean or any element of numbers is NaN
	* @see <a href="http://en.wikipedia.org/wiki/Total_sum_of_squares">Wikipedia article on Total sum of squares</a>
	*/
	public static double sst(double[] numbers, double mean) throws IllegalArgumentException {
		Check.arg().notEmpty(numbers);
		// Note: defer NaN check of mean for now and assume good; see below
		
		double sum2 = 0.0;
		double sumc = 0.0;	// the compensation sum, which would always be zero if had infinite precision; see the link cited in this method's javadocs for details
		for (int i = 0; i < numbers.length; i++) {
			// Note: defer NaN check of numbers[i] for now and assume good; see below
			
			double diff = numbers[i] - mean;
			sum2 += diff * diff;
			sumc += diff;
		}
		
		double sum = sum2 - (sumc * sumc / numbers.length);
		
			// now do quick checks, and only spend the time to figure out the cause if a problem is found; since problems are hopefully rare, this should be performance optimal:
		if (sum < 0) throw new IllegalStateException("calculated sum = " + sum + " < 0; this should never happen");	// this should never happen, so am not declaring throws IllegalStateException
		if (Double.isNaN(sum)) {
			if (Double.isNaN(mean)) throw new IllegalArgumentException("mean is NaN");
			checkNumbers(numbers, false);
			throw new IllegalStateException("calculated a NaN sum, but failed to find a NaN element; this should never happen");	// this should never happen, so am not declaring throws IllegalStateException
		}
		
		return sum;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private Math2() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Confirms that certain corner cases concerning floating point infinity arithmetic
		* behave as my code above assumes.
<!--
+++ I submitted feedback to sun on 2007/9/2 about the Java Lang Spec 2nd edition lacking a discussion of these cases;
they got back to me and acknowledged the issue, which i think they put in as an RFE, but i cannot find it now...
-->
		*/
		@Test public void test_infinityBehavior() {
			Assert.assertTrue( Double.POSITIVE_INFINITY + Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY );
			Assert.assertTrue( Double.isNaN(Double.POSITIVE_INFINITY + Double.NEGATIVE_INFINITY) );
			Assert.assertTrue( Double.isNaN(Double.NEGATIVE_INFINITY + Double.POSITIVE_INFINITY) );
			Assert.assertTrue( Double.NEGATIVE_INFINITY + Double.NEGATIVE_INFINITY == Double.NEGATIVE_INFINITY );
			
			Assert.assertTrue( Double.isNaN(Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY) );
			Assert.assertTrue( Double.POSITIVE_INFINITY - Double.NEGATIVE_INFINITY == Double.POSITIVE_INFINITY );
			Assert.assertTrue( Double.NEGATIVE_INFINITY - Double.POSITIVE_INFINITY == Double.NEGATIVE_INFINITY );
			Assert.assertTrue( Double.isNaN(Double.NEGATIVE_INFINITY - Double.NEGATIVE_INFINITY) );
			
			Assert.assertTrue( Double.POSITIVE_INFINITY * Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY );
			Assert.assertTrue( Double.POSITIVE_INFINITY * Double.NEGATIVE_INFINITY == Double.NEGATIVE_INFINITY );
			Assert.assertTrue( Double.NEGATIVE_INFINITY * Double.POSITIVE_INFINITY == Double.NEGATIVE_INFINITY );
			Assert.assertTrue( Double.NEGATIVE_INFINITY * Double.NEGATIVE_INFINITY == Double.POSITIVE_INFINITY );
			
			Assert.assertTrue( Double.isNaN(Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY) );
			Assert.assertTrue( Double.isNaN(Double.POSITIVE_INFINITY / Double.NEGATIVE_INFINITY) );
			Assert.assertTrue( Double.isNaN(Double.NEGATIVE_INFINITY / Double.POSITIVE_INFINITY) );
			Assert.assertTrue( Double.isNaN(Double.NEGATIVE_INFINITY / Double.NEGATIVE_INFINITY) );
		}
		
		@Test public void test_modulo() {
				// b is min acceptable value (i.e. 1):
			Assert.assertTrue( modulo(1, 1) == 0 );
			
				// a is at extremes:
			Assert.assertTrue( modulo(Integer.MIN_VALUE, 2) == 0 );
			Assert.assertTrue( modulo(Integer.MAX_VALUE, 2) == 1 );
			
				// normal cases:
			Assert.assertTrue( modulo(10, 3) == 1 );
			Assert.assertTrue( modulo(-10, 3) == 2 );
			Assert.assertTrue( modulo(-1, 100) == 99 );
		}
		
		@Test public void test_logsPowersEtc() {
			Assert.assertTrue( log(2.0, 4.0) == 2.0 );
			Assert.assertTrue( isWithinOneUlp(log10(1000.0), 3.0) );
			Assert.assertTrue( isWithinOneUlp(power10(-1), 0.1) );
			Assert.assertTrue( orderOfMagnitude(1.2345e-2) == -2.0 );
			Assert.assertTrue( orderOfMagnitude(nextRandomWithMagnitude(15)) == 15 );
		}
		
		@Test public void test_statisticsFunctions() {
				// normal cases
// +++ hmm, could not find a table of values...
// need to get a copy of Mathematica and generate a very high quality version
				// special cases:
			Assert.assertTrue( gaussianPdf(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1) == 0 );
			Assert.assertTrue( gaussianPdf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1) == 0 );
			
				// normal cases (values taken from http://links.math.rpi.edu/devmodules/probstat/concepts/html/phi.html)
				//	see also http://links.math.rpi.edu/devmodules/probstat/concepts/html/gauss_cdf.html
// +++ in view of how the current version of gaussianCdf is supposed to be accurate to 10^-16, I need more accurate values below
// need to get a copy of Mathematica and generate a very high quality version
			double[][] tableCdf = new double[][] {
				new double[] {-3.0, 1.3498980e-03},
				new double[] {-2.5, 6.2096653e-03},
				new double[] {-2.0, 2.2750132e-02},
				new double[] {-1.5, 6.6807201e-02},
				new double[] {-1.0, 1.5865525e-01},
				new double[] {-0.5, 3.0853754e-01},
				new double[] {0.0, 5.0000000e-01},
				new double[] {0.5, 6.9146246e-01},
				new double[] {1.0, 8.4134475e-01},
				new double[] {1.5, 9.3319280e-01},
				new double[] {2.0, 9.7724987e-01},
				new double[] {2.5, 9.9379033e-01},
				new double[] {3.0, 9.9865010e-01}
			};
			for (double[] entry : tableCdf) {
				Assert.assertEquals( entry[1], gaussianCdf(entry[0]), 1e-7 );
			}
				// special cases:
			Assert.assertTrue( gaussianCdf(Double.NEGATIVE_INFINITY, 0, 1) == 0 );
			Assert.assertTrue( gaussianCdf(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1) == 0 );
			Assert.assertTrue( gaussianCdf(Double.POSITIVE_INFINITY, 0, 1) == 1 );
			Assert.assertTrue( gaussianCdf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1) == 1 );
		}
		
// +++ hmm, the test below has no assertions and requires manual inspection, which is not great...
		@Test public void test_gaussianFit() throws Exception {
			int n = 1000 * 1000;
			double[] numbers = new double[n];
			Random random = new Random();
			for (int i = 0; i < n; i++) {
				numbers[i] = random.nextGaussian();
			}
			GaussianFit fit = gaussianFit(numbers);
			File file = LogUtil.makeLogFile("binsOfGaussianFit.txt");
			FileUtil.writeString(fit.toString(), file, false);
			System.out.println("graph the file " + file.getPath() + " to see if the bins are approximately Gaussian");
		}
		
		/**
		* When last looked at the output on 2009-09-24, consistently saw:
		* <pre><code>
			andersonDarling1 (Gaussian sample) = 0.13459667082165735, andersonDarling2 (uniform distribution sample) = 11170.383267133551
			andersonDarling1 (Gaussian sample) = 0.18940867401726738, andersonDarling2 (uniform distribution sample) = 11064.463336125908
			andersonDarling1 (Gaussian sample) = 0.3984548680792433, andersonDarling2 (uniform distribution sample) = 11038.706942927185
			andersonDarling1 (Gaussian sample) = 0.41313940626760093, andersonDarling2 (uniform distribution sample) = 11217.005208263494
			andersonDarling1 (Gaussian sample) = 0.27588003071356976, andersonDarling2 (uniform distribution sample) = 11115.653184588584
		* </code></pre>
		* The average value above for andersonDarling1 of ~0.25 compares well with the value 0.2576117
		* that is reported in the "anderson darling normal test y1" section of
		* <a href="http://www.itl.nist.gov/div898/handbook/eda/section3/eda35e.htm">this reference</a>.
		*/
		@Test public void test_gaussianAndersonDarling() {
			int n = 1000 * 1000;
			Random random = new Random();
			
			double[] numbers1 = new double[n];
			for (int i = 0; i < n; i++) {
				numbers1[i] = random.nextGaussian();
			}
			double andersonDarling1 = gaussianAndersonDarling(numbers1);
			
			double[] numbers2 = new double[n];
			for (int i = 0; i < n; i++) {
				numbers2[i] = random.nextDouble();
			}
			double andersonDarling2 = gaussianAndersonDarling(numbers2);
			
			System.out.println( "andersonDarling1 (Gaussian sample) = " + andersonDarling1 + ", andersonDarling2 (uniform distribution sample) = " + andersonDarling2 );
			Assert.assertTrue( andersonDarling1 < andersonDarling2 );
		}
		
		/**
		* When last looked at the output on 2009-09-24, consistently saw:
		* <pre><code>
			kolmogorovSmirnov1 (Gaussian sample) = 0.5506861389029671, kolmogorovSmirnov2 (uniform distribution sample) = 57.55263461482368
			kolmogorovSmirnov1 (Gaussian sample) = 0.7466358894082092, kolmogorovSmirnov2 (uniform distribution sample) = 57.34064194461067
			kolmogorovSmirnov1 (Gaussian sample) = 0.7624236873765011, kolmogorovSmirnov2 (uniform distribution sample) = 57.16759617069217
			kolmogorovSmirnov1 (Gaussian sample) = 0.836100754503244, kolmogorovSmirnov2 (uniform distribution sample) = 57.28774638323175
			kolmogorovSmirnov1 (Gaussian sample) = 0.6035095000266333, kolmogorovSmirnov2 (uniform distribution sample) = 57.28436577988172
		* </code></pre>
		* The average value above for kolmogorovSmirnov1 of ~0.65 is well under the critical value (so this test correctly accepts 1 as a Gaussian).
		* The average value above for kolmogorovSmirnov2 of ~57 is way over the critical value (so this test correctly rejects 2 as a Gaussian).
		*/
		@Test public void test_gaussianKolmogorovSmirnov() {
			int n = 1000 * 1000;
			Random random = new Random();
			
			double[] numbers1 = new double[n];
			for (int i = 0; i < n; i++) {
				numbers1[i] = random.nextGaussian();
			}
			double mean1 = mean(numbers1);
			double sd1 = sd(numbers1, mean1);
			double kolmogorovSmirnov1 = gaussianKolmogorovSmirnov(numbers1, mean1, sd1);
			
			double[] numbers2 = new double[n];
			for (int i = 0; i < n; i++) {
				numbers2[i] = random.nextDouble();
			}
			double mean2 = mean(numbers2);
			double sd2 = sd(numbers2, mean2);
			double kolmogorovSmirnov2 = gaussianKolmogorovSmirnov(numbers2, mean2, sd2);
			
			System.out.println( "kolmogorovSmirnov1 (Gaussian sample) = " + kolmogorovSmirnov1 + ", kolmogorovSmirnov2 (uniform distribution sample) = " + kolmogorovSmirnov2 );
			Assert.assertTrue( kolmogorovSmirnov1 < kolmogorovSmirnov2 );
		}
		
		/**
		* Results on 2009-03-16 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			n = 1000
				gaussianCdf: first = 144.364 ns, mean = 8.418 ns (CI deltas: -1.923 ps, +2.269 ps), sd = 94.479 ns (CI deltas: -15.358 ns, +22.652 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		@Test public void benchmark_statisticsFunctions() {
			final int n = 1000;
			Runnable task = new Runnable() {
				private double state;	// needed to prevent DCE since this is a Runnable
				@Override public String toString() { return String.valueOf(state); }	// needed to prevent DCE since this is a Runnable
				public void run() {
					for (int i = 0; i < n; i++) state += gaussianCdf(n);
				}
			};
			System.out.println("gaussianCdf: " + new Benchmark(task, n));
		}
		
		@Test public void test_infoTheory() {
			Assert.assertTrue( hammingWeight(Integer.MIN_VALUE) == 1 );
			Assert.assertTrue( hammingWeight(Integer.MAX_VALUE) == 31 );
			Assert.assertTrue( hammingWeight(0) == 0 );
			Assert.assertTrue( hammingWeight(1) == 1 );
// +++ could likewise do all the other powers of 2...
			
			Assert.assertTrue( hammingWeight(15) == 4 );
			
			Assert.assertTrue( hammingDistance(Integer.MIN_VALUE, Integer.MAX_VALUE) == 32 );
			Assert.assertTrue( hammingDistance(0, 1) == 1 );
			Assert.assertTrue( hammingDistance(15, 16) == 5 );
// +++ should test by generating every binary string ("011010001...") and then turning them into ints and then seeing if bit count matches
		}
		
		@Test public void test_bitTwiddling() {
			byte m128 = (byte) -128;
			Assert.assertTrue( byteToUnsignedInt(m128) == 128 );
			byte m127 = (byte) -127;
			Assert.assertTrue( byteToUnsignedInt(m127) == 129 );
			byte m1 = (byte) -1;
			Assert.assertTrue( byteToUnsignedInt(m1) == 255 );
			byte p127 = (byte) 127;
			Assert.assertTrue( byteToUnsignedInt(p127) == 127 );
		}
		
// +++ test misc methods?  well, currently they are used by other tests, so probably ok for now...
		
		@Test public void test_normalize() {
			double[] numbers5 = new double[] {1, 2, 3, 4, 5};
			double[] numbers5normalized = new double[] {1 / 15.0, 2 / 15.0, 3 / 15.0, 4 / 15.0, 5 / 15.0};
			Assert.assertTrue( Arrays.equals(normalize(numbers5), numbers5normalized) );
			
			double[] numbersMany = new double[1000 * 1000];
			Random r = new Random();
			for (int i = 0; i < numbersMany.length; i++) {
				numbersMany[i] = Math.abs( r.nextGaussian() );
			}
			Assert.assertTrue( isNormalized(normalize(numbersMany)) );
		}
		
		@Test public void test_subtractParallelComponent() {
			double[] x = new double[] {1, 0};
			double[] y = new double[] {0, 1};
			double[] xpy = new double[] {1, 1};
			Assert.assertTrue( Arrays.equals(subtractParallelComponent(xpy, x), y) );
		}
		
		@Test public void test_linearLeastSquaresFit() {
			double a = -1.0;
			double b = -2.0;
			double[] xValues = new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
			double[] yValuesExact = new double[xValues.length];
			double[] yValuesSlightlyRandom = new double[xValues.length];
			for (int i = 0; i < xValues.length; i++) {
				yValuesExact[i] = a + (b * xValues[i]);
				yValuesSlightlyRandom[i] = yValuesExact[i] + (Math.random() / 3.0);
			}
			
			double[] fitOfExact = linearLeastSquaresFit(xValues, yValuesExact);
			Assert.assertTrue(fitOfExact[0] == a);
			Assert.assertTrue(fitOfExact[1] == b);
			Assert.assertTrue(fitOfExact[2] == 1.0);
			Assert.assertTrue(fitOfExact[4] == 0.0);
			
			double[] fitOfSlightlyRandom = linearLeastSquaresFit(xValues, yValuesSlightlyRandom);
			System.out.println( "Fit result for slight random deviations from a linear curve:");
			System.out.println( "a = " + fitOfSlightlyRandom[0] + " (should be close to " + a + "), b = " + fitOfSlightlyRandom[1] + " (should be close to " + b + "), r2 = " + fitOfSlightlyRandom[2] + " (should be close to 1), ssr = " + fitOfSlightlyRandom[3] + ", sse = " + fitOfSlightlyRandom[4] + " (should be close to 0)" );
		}
		
		// Note: this test was taken from BOX-JENKINS pp. 32ff (see references in the autocorrelation statistics section above)
		@Test public void test_autocorrelationStatisticsMethods() {
			double[] numbers10 = new double[] {
				47, 64, 23, 71, 38, 64, 55, 41, 59, 48
			};
			
			double mean = mean(numbers10);
			Assert.assertEquals( 51, mean, 1e-6 );
			
			double[] c = autocovariance(numbers10);
			Assert.assertEquals( 189.6, c[0], 1e-3 );
			Assert.assertEquals( -149.7, c[1], 1e-3 );
			
			double[] r = autocorrelation(numbers10)[0];	// 0th element is the acf
			Assert.assertEquals( -0.79, r[1], 1e-3 );
			
			double[] numbers70 = new double[] {
				47, 64, 23, 71, 38, 64, 55, 41, 59, 48,
				71, 35, 57, 40, 58, 44, 80, 55, 37, 74,
				51, 57, 50, 60, 45, 57, 50, 45, 25, 59,
				50, 71, 56, 74, 50, 58, 45, 54, 36, 54,
				48, 55, 45, 57, 50, 62, 44, 64, 43, 52,
				38, 59, 55, 41, 53, 49, 34, 35, 54, 45,
				68, 38, 50, 60, 39, 59, 40, 57, 54, 23
			};
			double[] r15 = new double[] {
				1.0,	// BOX-JENKINS does not have this lag 0 result in Table 2.2 on p. 34, but you need it in order to compare with r below
				-0.39, 0.30, -0.17, 0.07, -0.10,
				-0.05, 0.04, -0.04, -0.01, 0.01,
				0.11, -0.07, 0.15, 0.04, -0.01
			};
			r = autocorrelation(numbers70)[0];	// 0th element is the acf
			for (int i = 0; i < r15.length; i++) {
				Assert.assertEquals( "failed on element #" + i, r15[i], r[i], 6e-3 );
			}
		}
		
		@Test public void test_lowLevelStatisticsArrayMethods() {
			double[] numbers9 = new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
			Assert.assertTrue( mean(numbers9) == 5 );
			Assert.assertTrue( median(numbers9) == 5 );
			Assert.assertTrue( quantile(numbers9, 1, 4) == 3 );
			Assert.assertTrue( quantile(numbers9, 2, 4) == 5 );
			Assert.assertTrue( quantile(numbers9, 3, 4) == 7 );
			Assert.assertTrue( isWithinOneUlp(sd(numbers9), Math.sqrt(60.0 / 9.0)) );
			Assert.assertTrue( isWithinOneUlp(variance(numbers9), 60.0 / 9.0) );
			
			double[] numbersMany = new double[1000 * 1000];
			Random r = new Random();
			for (int i = 0; i < numbersMany.length; i++) {
				numbersMany[i] = r.nextGaussian();
			}
			Assert.assertEquals( 0, mean(numbersMany), 0.0025 );
			Assert.assertEquals( 1, sd(numbersMany), 0.01 );
			Assert.assertEquals( 1, variance(numbersMany), 0.01 );
			
// +++ really need to add some tests which stress the precision,
// especially to confirm that the compensated summation inside sst really works;
// see http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Example
		}
		
	}
	
}


/*
below is an old implementation for  that I used before I started using the JSci TDistribution class
	
	**
	* Implements the inverse function of the cumulative distribution function (cdf) of Student's t-distribution
	* (i.e. returns that value of x such that the t-distribution's cdf evaluated at x equals probability).
	* <p>
The current implementation uses a table lookup approach.
This is obviously problematic because of limited coverage:
there are many valid values for degreesOfFreedom and/or probability which are not in the table,
which means that this method can only fail in those circumstances.
<!--
+++ fix this with a better algorithm in the future that can handle arbitrary legitimate args
-->
	* <p>
	* Contract: the result is always in the range [-Infinity, Infinity], and is never NaN
	* <p>
	* @throws IllegalArgumentException if degreesOfFreedom <= 0; probability < 0
* @throws IllegalStateException if the table lookup fails for degreesOfFreedom and/or probability;
this does not imply that those args are invalid, just that the table is limited
	*
	public static double tDistributionCdfInverse(int degreesOfFreedom, double probability) throws IllegalArgumentException, IllegalStateException {
		Check.arg().positive(degreesOfFreedom);
		Check.arg().notNegative(probability);
		
		if (probability == 0) return Double.NEGATIVE_INFINITY;
		if (probability == 1) return Double.POSITIVE_INFINITY;
		
			// values below copied from the table here: http://en.wikipedia.org/wiki/Student%27s_t-distribution#Table_of_selected_values
		int[] dofArray = new int[] {1,  2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40, 50, 60, 80, 100, 120};
		double[] probabilities = new double[] {0.75, 0.80, 0.85, 0.90, 0.95, 0.975, 0.99, 0.995, 0.9975, 0.999, 0.9995};
		double[][] x = new double[][] {
			new double[] {1.000, 1.376, 1.963, 3.078, 6.314, 12.71, 31.82, 63.66, 127.3, 318.3, 636.6},
			new double[] {0.816, 1.061, 1.386, 1.886, 2.920, 4.303, 6.965, 9.925, 14.09, 22.33, 31.60},
			new double[] {0.765, 0.978, 1.250, 1.638, 2.353, 3.182, 4.541, 5.841, 7.453, 10.21, 12.92},
			new double[] {0.741, 0.941, 1.190, 1.533, 2.132, 2.776, 3.747, 4.604, 5.598, 7.173, 8.610},
			new double[] {0.727, 0.920, 1.156, 1.476, 2.015, 2.571, 3.365, 4.032, 4.773, 5.893, 6.869},
			new double[] {0.718, 0.906, 1.134, 1.440, 1.943, 2.447, 3.143, 3.707, 4.317, 5.208, 5.959},
			new double[] {0.711, 0.896, 1.119, 1.415, 1.895, 2.365, 2.998, 3.499, 4.029, 4.785, 5.408},
			new double[] {0.706, 0.889, 1.108, 1.397, 1.860, 2.306, 2.896, 3.355, 3.833, 4.501, 5.041},
			new double[] {0.703, 0.883, 1.100, 1.383, 1.833, 2.262, 2.821, 3.250, 3.690, 4.297, 4.781},
			new double[] {0.700, 0.879, 1.093, 1.372, 1.812, 2.228, 2.764, 3.169, 3.581, 4.144, 4.587},
			new double[] {0.697, 0.876, 1.088, 1.363, 1.796, 2.201, 2.718, 3.106, 3.497, 4.025, 4.437},
			new double[] {0.695, 0.873, 1.083, 1.356, 1.782, 2.179, 2.681, 3.055, 3.428, 3.930, 4.318},
			new double[] {0.694, 0.870, 1.079, 1.350, 1.771, 2.160, 2.650, 3.012, 3.372, 3.852, 4.221},
			new double[] {0.692, 0.868, 1.076, 1.345, 1.761, 2.145, 2.624, 2.977, 3.326, 3.787, 4.140},
			new double[] {0.691, 0.866, 1.074, 1.341, 1.753, 2.131, 2.602, 2.947, 3.286, 3.733, 4.073},
			new double[] {0.690, 0.865, 1.071, 1.337, 1.746, 2.120, 2.583, 2.921, 3.252, 3.686, 4.015},
			new double[] {0.689, 0.863, 1.069, 1.333, 1.740, 2.110, 2.567, 2.898, 3.222, 3.646, 3.965},
			new double[] {0.688, 0.862, 1.067, 1.330, 1.734, 2.101, 2.552, 2.878, 3.197, 3.610, 3.922},
			new double[] {0.688, 0.861, 1.066, 1.328, 1.729, 2.093, 2.539, 2.861, 3.174, 3.579, 3.883},
			new double[] {0.687, 0.860, 1.064, 1.325, 1.725, 2.086, 2.528, 2.845, 3.153, 3.552, 3.850},
			new double[] {0.686, 0.859, 1.063, 1.323, 1.721, 2.080, 2.518, 2.831, 3.135, 3.527, 3.819},
			new double[] {0.686, 0.858, 1.061, 1.321, 1.717, 2.074, 2.508, 2.819, 3.119, 3.505, 3.792},
			new double[] {0.685, 0.858, 1.060, 1.319, 1.714, 2.069, 2.500, 2.807, 3.104, 3.485, 3.767},
			new double[] {0.685, 0.857, 1.059, 1.318, 1.711, 2.064, 2.492, 2.797, 3.091, 3.467, 3.745},
			new double[] {0.684, 0.856, 1.058, 1.316, 1.708, 2.060, 2.485, 2.787, 3.078, 3.450, 3.725},
			new double[] {0.684, 0.856, 1.058, 1.315, 1.706, 2.056, 2.479, 2.779, 3.067, 3.435, 3.707},
			new double[] {0.684, 0.855, 1.057, 1.314, 1.703, 2.052, 2.473, 2.771, 3.057, 3.421, 3.690},
			new double[] {0.683, 0.855, 1.056, 1.313, 1.701, 2.048, 2.467, 2.763, 3.047, 3.408, 3.674},
			new double[] {0.683, 0.854, 1.055, 1.311, 1.699, 2.045, 2.462, 2.756, 3.038, 3.396, 3.659},
			new double[] {0.683, 0.854, 1.055, 1.310, 1.697, 2.042, 2.457, 2.750, 3.030, 3.385, 3.646},
			new double[] {0.681, 0.851, 1.050, 1.303, 1.684, 2.021, 2.423, 2.704, 2.971, 3.307, 3.551},
			new double[] {0.679, 0.849, 1.047, 1.299, 1.676, 2.009, 2.403, 2.678, 2.937, 3.261, 3.496},
			new double[] {0.679, 0.848, 1.045, 1.296, 1.671, 2.000, 2.390, 2.660, 2.915, 3.232, 3.460},
			new double[] {0.678, 0.846, 1.043, 1.292, 1.664, 1.990, 2.374, 2.639, 2.887, 3.195, 3.416},
			new double[] {0.677, 0.845, 1.042, 1.290, 1.660, 1.984, 2.364, 2.626, 2.871, 3.174, 3.390},
			new double[] {0.677, 0.845, 1.041, 1.289, 1.658, 1.980, 2.358, 2.617, 2.860, 3.160, 3.373}
		};
		
		int dofIndex = -1;
		for (int i = 0; i < dofArray.length; i++) {
			if (degreesOfFreedom == dofArray[i]) {
				dofIndex = i;
				break;
			}
		}
		if (dofIndex == -1) throw new IllegalStateException("degreesOfFreedom = " + degreesOfFreedom + " is simply not covered by the current table lookup");
		
		int probabilityIndex = -1;
		double errorTolerance = 1e-6;
		for (int i = 0; i < probabilities.length; i++) {
			if (isWithin(probability, probabilities[i], errorTolerance)) {
				probabilityIndex = i;
				break;
			}
		}
		if (probabilityIndex == -1) throw new IllegalStateException("probability = " + probability + " is simply not covered by the current table lookup");
		
		return x[dofIndex][probabilityIndex];
	}


and here is code for the above that used to be in UnitTest.test_statisticsFunctions:
				// normal cases (values taken from the table in http://en.wikipedia.org/wiki/Student%27s_t-distribution):
			report(tDistributionCdfInverse(1, 0.75) == 1.000, "tDistributionCdfInverse(1, 0.75) == 1.000");
			report(tDistributionCdfInverse(2, 0.80) == 1.061, "tDistributionCdfInverse(2, 0.80) == 1.061");
			report(tDistributionCdfInverse(3, 0.85) == 1.250, "tDistributionCdfInverse(3, 0.85) == 1.250");
			report(tDistributionCdfInverse(4, 0.90) == 1.533, "tDistributionCdfInverse(4, 0.90) == 1.533");
			report(tDistributionCdfInverse(5, 0.95) == 2.015, "tDistributionCdfInverse(5, 0.95) == 2.015");
			report(tDistributionCdfInverse(6, 0.975) == 2.447, "tDistributionCdfInverse(6, 0.975) == 2.447");
			report(tDistributionCdfInverse(7, 0.99) == 2.998, "tDistributionCdfInverse(7, 0.99) == 2.998");
			report(tDistributionCdfInverse(8, 0.995) == 3.355, "tDistributionCdfInverse(8, 0.995) == 3.355");
			report(tDistributionCdfInverse(9, 0.9975) == 3.690, "tDistributionCdfInverse(9, 0.9975) == 3.690");
			report(tDistributionCdfInverse(10, 0.999) == 4.144, "tDistributionCdfInverse(10, 0.999) == 4.144");
			report(tDistributionCdfInverse(11, 0.9995) == 4.437, "tDistributionCdfInverse(11, 0.9995) == 4.437");
				// special cases:
			report(tDistributionCdfInverse(1000, 0) == Double.NEGATIVE_INFINITY, "tDistributionCdfInverse(1000, 0) == Double.NEGATIVE_INFINITY");
			report(tDistributionCdfInverse(2000, 1) == Double.POSITIVE_INFINITY, "tDistributionCdfInverse(2000, 1) == Double.POSITIVE_INFINITY");


and here is code that used to test and confirm that TDistribution agreed well enough with the table:
	int[] dofArray = new int[] {1,  2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40, 50, 60, 80, 100, 120};
	double[] probabilities = new double[] {0.75, 0.80, 0.85, 0.90, 0.95, 0.975, 0.99, 0.995, 0.9975, 0.999, 0.9995};
	double max = Double.NEGATIVE_INFINITY;
	double min = Double.POSITIVE_INFINITY;
	for (int i = 0; i < dofArray.length; i++) {
		for (int j = 0; j < probabilities.length; j++) {
			double v1 = Math2.tDistributionCdfInverse(dofArray[i], probabilities[j]);
			double v2 = (new TDistribution(dofArray[i])).inverse(probabilities[j]);
			double diff = Math.abs( (v1 - v2) / v1 );
			if (diff > max) max = diff;
			if (diff < min) min = diff;
			d.p.s("i = " + i + ", j = " + j + ", diff = " + diff);

		}
	}
	d.p.s("max = " + max);
	d.p.s("min = " + min);
	if (true) return;

its output was:
	max = 6.947705205645032E-4
	min = 2.220446049250313E-16
*/
