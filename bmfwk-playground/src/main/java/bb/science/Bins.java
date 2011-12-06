/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--2 general approaches to bin intervals are:
	1) equal widths (as below)
	2) equal number of samples per bin
		pros:
			--handles outliers VASTLY better; the problem with equal width bins is that a single extreme outlier will leave you with just two non-empty bins:
				one which contains the outlier, and another which contains all the rest of the data; in between are all empty ones
			--is much better suited to computing chi-squared test statistics (see Math2.gaussianFit), which require minimum counts in each bin
		cons:
			--the bin widths are necessarily irregular, which can make interpretation more difficult, especially when graphed
				probably need to use an index number to graph the bins if want to have any resolution, since if use the usual bin boundaries, all the non-outlier bins will apppear squished together


+++ probably should pull Intervals out into a separate top level class
	--possibly first making an interface and having the code below be the implementation for equal width intervals
	--this would be especially useful if have another implementation for equal number of samples per bin as described above
	--I have already done most of the heavy lifting simply by makign Intervals an inner class below


+++ another feature that may want to support (especially useful for distributions that can occur over all real numbers) is to allow special bin intervals for +- infinity


--in principle, another use for bins is data compaction:
	--since with bins you do not store the individual data points, but instead merely note what bin each falls into by increasing the bin count,
		a potentially enormous reduction in memory use can be achieved.
	--this use is not currently supported by this class, ONLY because its current api for adding data is solely thru its constructors, which all take just a single double[]
	--but this class could be modified to support adding data continuously
		--this might only work, however, for bins that are defined by known bin boundaries in advance?
*/

package bb.science;

import bb.io.FileUtil;
import bb.util.Check;
import bb.util.HashUtil;
import bb.util.StringUtil;
import bb.util.logging.LogUtil;
import java.io.File;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;

/**
* <h4>Introduction</h4>
* Used to sort data into distinct bins (also known as intervals or cells).
* The original data values are never stored.
* Instead, just the number of values in each bin is maintained.
* The most common use is to measure the distribution of some sample,
* since the bins can be directly used to plot <a href="http://en.wikipedia.org/wiki/Histograms">histograms</a>.

* <h4>Data</h4>
* The data type is restricted to double values.
* Like {@link Samples}, this class places no restriction on the values except that they must be normal (non-NaN and non-infinite).
* This restriction means that the results returned by {@link #getBounds getBounds}, {@link #getBoundsMid getBoundsMid}, and {@link #getPdf getPdf}
* may be safely supplied to other classes (e.g. the statistical routines inside {@link Math2}).

* <h4>Bins</h4>
* A bin is defined as an interval (i.e. some continuous subrange) in the value space
* along with a count of the data values which fall inside that interval.

* <h4>Intervals</h4>
* This class guarantees that the set of bin intervals it generates will always cover every value pesented to this class.
* These intervals are always <i>approximately</i> equally sized.
* (Because doubles are used for all the calculations, floating point error may be present.
* Consequently, bin interval widths may slightly vary.)
* Every interval is distinct; it never overlaps with another interval.
* <p>
* This class <i>almost always</i> uses the following closed-open bin interval convention:
* each is of the form <code>[x0, x1)</code>,
* that is, it includes all values <code>x</code> that satisfy <code>x0 <= x < x1</code>.
* So, the left boundary is included inside the interval, but the right boundary is not.
* <p>
* The <i>sole exception</i> is the rightmost (largest) interval: it is a fully closed interval of the form <code>[x0, x1]</code>.
* Reason: one way to specify bin intervals is by supplying the min and max values that should be covered by the intervals,
* and users will want that max value to occur inside an interval that includes more points than just that single max value.
* For example, suppose the user is generating histograms of percents, and they want 10 bins to cover the range <code>[0, 100]</code>.
* Then with the interval scheme just described, the 10 intervals in this case will be
* <code>[0, 10), [10, 20), [20, 30), [30, 40), [40, 50), [50, 60), [60, 70), [70, 80), [80, 90), [90, 100]</code>.
* <p>
* This class always use a finite set of contiguous intervals.
* These can be written, in order, as <code>[x0, x1), [x1, x2), [x2, x3), ..., [xN-1, xN]</code>
* where <code>N</code> is the total number of intervals.
* A simplifying convention when speaking of contiguous intervals is to only list the beginning of each interval,
* since each bin's end is the beginning of the subsequent bin.
* Thus, we designate the previous set of intervals as <code>{x0, x1, x2, ..., xN-1}</code>.
* This is the format of bin interval boundary points that is returned by {@link #getBounds getBounds}.
* Note that because the intervals are always equally sized,
* <code>{x0, x1, x2, ..., xN-1}</code> is equivalent to <code>{x0, x0 + width, x0 + 2*width), ..., x0 + (N - 1)*width}</code>.

* <h4>Concurrency</h4>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class Bins {
	
	// -------------------- fields --------------------
	
	private final Intervals intervals;
	
	private final double[] bounds;
	
	private final long[] counts;
	
	// -------------------- constructors --------------------
	
	/**
	* Returns <code>{@link #Bins(double[], Intervals) this}( values, {@link Intervals#make(double[], double, double) Intervals.make}(values, offset, width) )</code>.
	* <p>
	* @throws IllegalArgumentException if values == null; values.length == 0; any element of values is NaN;
	* offset is not {@link Check#normal normal};
	* width is not {@link Check#normalPositive normal and positive}
	* @throws IllegalStateException if some internal problem occurs
	*/
	public Bins(double[] values, double offset, double width) throws IllegalArgumentException, IllegalStateException {
		this( values, Intervals.make(values, offset, width) );
	}
	
	/**
	* Returns <code>{@link #Bins(double[], Intervals) this}( values, {@link Intervals#make(double[], int) Intervals.make}(values, numberIntervals) )</code>.
	* <p>
	* @throws IllegalArgumentException if values == null; values.length == 0; any element of values is NaN;
	* numberIntervals <= 0
	*/
	public Bins(double[] values, int numberIntervals) throws IllegalArgumentException, IllegalStateException {
		this( values, Intervals.make(values, numberIntervals) );
	}
	
	/**
	* Returns <code>{@link #Bins(double[], Intervals) this}( values, new {@link Bins.Intervals#Bins.Intervals(double, double, int) Intervals}(begin, end, numberIntervals) )</code>.
	* <p>
	* @throws IllegalArgumentException if begin is not {@link Check#normal normal};
	* end is not normal;
	* begin is not < end;
	* numberIntervals <= 0
	*/
	public Bins(double[] values, double begin, double end, int numberIntervals) throws IllegalArgumentException, IllegalStateException {
		this( values, new Intervals(begin, end, numberIntervals) );
	}
	
	/**
	* Returns <code>{@link #Bins(double[], Intervals) this}( values, new {@link Bins.Intervals#Bins.Intervals(double, double, int) Intervals}(begin, end, numberIntervals) )</code>.
	* <p>
	* @throws IllegalArgumentException if values or intervals is null;
	* any element of values falls outside the range [begin, end]
	*/
	private Bins(double[] values, Intervals intervals) throws IllegalArgumentException {
		Check.arg().notNull(values);
		Check.arg().notNull(intervals);
		
		this.intervals = intervals;
		
		int n = intervals.number;
		
		bounds = new double[n];
		for (int i = 0; i < n; i++) {
			bounds[i] = intervals.boundary(i);
		}
		
		counts = new long[n];
		for (double d : values) {
			int index = intervals.index(d);
			counts[index] += 1;	// increment the count of the appropriate bin
		}
	}
	
	// -------------------- toString --------------------
	
	@Override public String toString() {
		return
			"intervals = " + intervals.toString() + "\n"
			+ "bounds = " + StringUtil.toString(bounds, ", ") + "\n"
			+ "boundsMid = " + StringUtil.toString(getBoundsMid(), ", ") + "\n"
			+ "countTotal = " + getCountTotal() + "\n"
			+ "counts = " + StringUtil.toString(counts, ", ") + "\n"
			+ "pdf = " + StringUtil.toString(getPdf(), ", ") + "\n";
		
		//double[][] matrix = new double[][] { getBounds(), getPdf() };
		//return StringUtil.arraysToTextColumns(matrix, new String[] {"x", "pdf(x)"});
	}
	
	// -------------------- accessors --------------------
	
	/** Accessor for {@link #intervals}. */
	public Intervals getIntervals() {
		return intervals;
	}
	
	/**
	* Accessor for {@link #bounds}.
	* <p>
	* <b>Warning:</b> the field is directly returned, not a copy, so mutating the result invalidates this instance.
	* <i>So, only mutate the result if this instance will no longer be used.</i>
	*/
	public double[] getBounds() {
		return bounds;
	}
	
	/**
	* Accessor for {@link #counts}.
	* <p>
	* <b>Warning:</b> the field is directly returned, not a copy, so mutating the result invalidates this instance.
	* <i>So, only mutate the result if this instance will no longer be used.</i>
	*/
	public long[] getCounts() {
		return counts;
	}
	
	/** Accessor for {@link Intervals#width intervals.width}. */
	public double getIntervalWidth() {
		return intervals.width;
	}
	
	// -------------------- convenience methods --------------------
	
	/**
	* Returns the bin interval midpoints, that is, the points that are in the middle of each interval.
	* <p>
	* This method is useful if the user wishes to characterize each bin interval by its middle point instead of left boundary.
	*/
	public double[] getBoundsMid() {
		double delta = intervals.width / 2.0;
		double[] mids = new double[bounds.length];
		for (int i = 0; i < mids.length; i++) {
			mids[i] = bounds[i] + delta;
		}
		return mids;
	}
	
	/** Returns the total count, that is, the sum of all the elements of {@link #counts}. */
	public long getCountTotal() {
		long n = 0;
		for (long count : counts) n += count;
		return n;
	}
	
	/**
	* Returns the <a href="http://en.wikipedia.org/wiki/Probability_density_function">probability density function (PDF)</a>
	* that is approximated by the bins.
	* For each bin, this is the probability that a sample falls in the bin, divided by the bin's width.
	* In code: <code>(count / n) / width</code>.
	* Here, <code>n</code> is the total number of stored values (i.e. {@link #getCountTotal getCountTotal})
	* (so <code>count / n</code> converts the bin count to a probabilty)
	* and width is the {@link #intervals bin interval's} {@link Intervals#width width}
	* (so <code>/ width</code> converts the probability into a probability density).
	* <p>
	* One reason why the PDF is useful is that, when sufficiently many values have been added the bins,
	* the PDF curve becomes approximately independent both of the number of stored values and of the bin width,
	* assuming that the values are drawn from a stable distribution.
	*/
	public double[] getPdf() {
		long n = getCountTotal();
		
		double[] pdf = new double[counts.length];
		for (int i = 0; i < pdf.length; i++) {
			pdf[i] = (((double) counts[i]) / n) / intervals.width;
		}
		return pdf;
	}
	
// +++ may wish to offer a toPoints method which converts bounds and counts into a single Point2D.Double[]
// that other Java graphing packages could use, assuming that they take that format
	
	// -------------------- Intervals (static inner class) --------------------
	
	/**
	* Specifies how a given set of intervals are laid out.
	* <p>
	* The left (start) boundary of the leftmost (smallest) interval is given by {@link #begin}.
	* The right (end) boundary of the rightmost (largest) interval is given by {@link #end}.
	* In between these bounds, a total of {@link #number} intervals are present, each approximately of the same size {@link #width}.
	* <p>
	* Using the interval boundary convention discussed in the {@link Bins class javadocs},
	* the boundaries are <code>{begin, begin + 1*width, ..., begin + (number - 1)*width}</code>.
	* Written out explicitly, the intervals are
	* <pre><code>
		[begin, begin + 1*width),
		[begin + 1*width, begin + 2*width),
		...,
		[begin + (number - 1)*width, end]
	* </code></pre>
	* where <code>end == number*width</code>.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
	*/
	public static class Intervals {
		
		// -------------------- fields --------------------
		
		private final double begin;
		private final double end;
		private final int number;
		private final double width;	// caches (end - begin) / number
		
		// -------------------- make (factory method) --------------------
		
		/**
		* Specifies that minimal set of intervals which satisifes these conditions:
		* <ol>
		*  <li>every element of values is inside one of the intervals</li>
		*  <li>every interval in the set has the same size, equal to the width param</li>
		*  <li>
		*		every interval's boundary points are separated from offset by an integral multiple of width.
		*		In code: <code>boundaryPoint = offset + (i * width)</code> for some int i.
		*  </li>
		* </ol>
		* <p>
		* This method finds the min and max elements of values,
		* which it uses to compute appropriate values for {@link #begin} and {@link #end}.
		* Note that offset need not lie inside the intervals (i.e. inside [begin, end]).
		* <p>
		* This method is useful when you want control over where the interval boundary points fall.
		* A common requirement might be that the boundaries are always integers.
		* In this case, supply offset = 0 and width = 1.
		* <p>
		* @throws IllegalArgumentException if values == null; values.length == 0; any element of values is NaN;
		* offset is not {@link Check#normal normal};
		* width is not {@link Check#normalPositive normal and positive}
		* @throws IllegalStateException if some internal problem occurs
		*/
		private static Intervals make(double[] values, double offset, double width) throws IllegalArgumentException, IllegalStateException {
			// values checked by minMax below
			Check.arg().normal(offset);
			Check.arg().normalPositive(width);
			
			double[] minMax = Math2.minMax(values);
			double min = minMax[0];
			double max = minMax[1];
			
			double mark1 = Math.floor( (min - offset) / width );
			double begin = offset + (mark1 * width);
			Check.state().isTrue(begin <= min);
			Check.state().isTrue(min < begin + width);
			
			double mark2 = Math.ceil( (max - offset) / width );
			double end = offset + (mark2 * width);
			Check.state().isTrue(max <= end);
			Check.state().isTrue(end - width < max);
			
			double n = mark2 - mark1;
			Check.state().isTrue(n <= Integer.MAX_VALUE);
			Check.state().isTrue(n == Math.ceil(n));
			
			Intervals intervals = new Intervals(begin, end, (int) n);
			//Check.state().isTrue(width == intervals.width);	// am commenting this out, because floating point errors make this problematic...
			return intervals;
		}
		
		/**
		* Specifies that set of intervals which satisifes these conditions:
		* <ol>
		*  <li>every element of values is inside one of the intervals</li>
		*  <li>the number of intervals in the set equals the number param</li>
		*  <li>the left (starting) boundary of the leftmost (smallest) interval equals the minimum element of values</li>
		*  <li>the right (ending) boundary of the rightmost (largest) interval equals the maximum element of values</li>
		* </ol>
		* <p>
		* This method finds the min and max elements of values,
		* which it uses for {@link #begin} and {@link #end}.
		* <p>
		* @throws IllegalArgumentException if values == null; values.length == 0; any element of values is NaN;
		* number <= 0
		* @throws IllegalStateException if some internal problem occurs
		*/
		private static Intervals make(double[] values, int number) throws IllegalArgumentException, IllegalStateException {
			// values checked by minMax below
			// number checked by Intervals below
			
			double[] minMax = Math2.minMax(values);
			double min = minMax[0];
			double max = minMax[1];
			return new Intervals(min, max, number);
		}
		
		// -------------------- constructor --------------------
		
		/**
		* Constructor.
		* <p>
		* @throws IllegalArgumentException if begin is not {@link Check#normal normal};
		* end is not normal;
		* begin is not < end;
		* number <= 0
		* @throws IllegalStateException if some internal problem occurs
		*/
		private Intervals(double begin, double end, int number) throws IllegalArgumentException, IllegalStateException {
			Check.arg().normal(begin);
			Check.arg().normal(end);
			Check.arg().isTrue(begin < end);
			Check.arg().positive(number);
			
			this.begin = begin;
			this.end = end;
			this.number = number;
			this.width = (end - begin) / number;
			Check.state().normalPositive(width);
			//Check.state().isTrue(end == begin + (number * width));
				// cannot reliably do the above because of floating point error, so insist on the weaker condition that the left boundary of the rightmost interval occurs before end:
			Check.state().isTrue(begin + ((number - 1) * width) < end);
		}
		
		// -------------------- accessors --------------------
		
		/** Accessor for {@link #begin}. */
		public double getBegin() {
			return begin;
		}
		
		/** Accessor for {@link #end}. */
		public double getEnd() {
			return end;
		}
		
		/** Accessor for {@link #number}. */
		public int getNumber() {
			return number;
		}
		
		/** Accessor for {@link #width}. */
		public double getWidth() {
			return width;
		}
		
		// -------------------- equals, hashCode, toString --------------------
		
		/**
		* Determines equality based on whether or not obj is an Intervals instance
		* whose every field equals that of this instance.
		*/
		@Override public final boolean equals(Object obj) {	// for why is final, see the file equalsImplementation.txt
			if (this == obj) return true;
			if (!(obj instanceof Intervals)) return false;
			
			Intervals other = (Intervals) obj;
			return
				(this.begin == other.begin) &&
				(this.end == other.end) &&
				(this.number == other.number) &&
				(this.width == other.width);
		}
		
		@Override public final int hashCode() {	// for why is final, see the file equalsImplementation.txt
			return
				HashUtil.hash(begin)
				^ HashUtil.hash(end)
				^ number
				^ HashUtil.hash(width);
		}
		
		@Override public String toString() {
			return "begin = " + begin + ", end = " + end + ", number = " + number + ", width = " + width;
		}
		
		// -------------------- boundary, index --------------------
		
		/**
		* Returns the ith interval boundary's left value (i.e. the smallest value that is inside the interval).
		* <p>
		* Contract: the result is >= {@link #begin} and < {@link #end}
		* <p>
		* @throws IllegalArgumentException if i is < 0 or >= {@link #number}
		*/
		private double boundary(int i) throws IllegalArgumentException {
			Check.arg().notNegative(i);
			Check.arg().isTrue(i < number);
			
			return begin + (i * width);	// because of the checks on i above and the earlier checks in the constructor, this method's contract is guaranteed to be satisfied
		}
		
		/**
		* Returns the index of the interval which contains d.
		* <p>
		* Contract: the result is guaranteed to be >= 0 and < {@link #number}.
		* <p>
		* @throws IllegalArgumentException if d is not {@link Check#normal normal}
		*/
		private int index(double d) throws IllegalArgumentException {
			Check.arg().normal(d);
			Check.arg().isTrue(begin <= d);
			Check.arg().isTrue(d <= end);
			
			//return (int) Math.floor( (d - begin) / width );
				// cannot reliably do the above because of floating point error, so do explicit checks:
			int result = (int) Math.floor( (d - begin) / width );
			if (result == number) {
				result = number - 1;	// assmume that this was caused by floating point error, and so truncate it back down to the correct max value
			}
			else if (result > number) {
				throw new IllegalArgumentException("d = " + d + " caused index to calculate a result = " + result + " which is > number; this instance: " + toString());	// this case is hopeless
			}
			return result;
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/** Tests a very simple case of a small data sample put into 2 bins, where the expected results are trivial to calculate. */
		@Test public void test_all_shouldPass1() throws Exception {
			double[] values = new double[] {1, 2, 3, 4};
			Bins bins = new Bins(values, 2);
			
			Intervals intervalsExpected = new Intervals(1, 4, 2);
			Assert.assertEquals( intervalsExpected, bins.intervals );
			
			double[] boundsExpected = new double[] {1, 2.5};
			Assert.assertArrayEquals( boundsExpected, bins.bounds, 0.0 );
			
			long[] countsExpected = new long[] {2, 2};
			Assert.assertArrayEquals( countsExpected, bins.counts );
		}
		
		/** Tests the percent histogram example cited in the {@link Bins class javadocs}. */
		@Test public void test_all_shouldPass2() throws Exception {
			double[] values = new double[] {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
			Bins bins = new Bins(values, 0.0, 10.0);
			
			Intervals intervalsExpected = new Intervals(0, 100, 10);
			Assert.assertEquals( intervalsExpected, bins.intervals );
			
			double[] boundsExpected = new double[] {0, 10, 20, 30, 40, 50, 60, 70, 80, 90};
			Assert.assertArrayEquals( boundsExpected, bins.bounds, 0.0 );
			
			long[] countsExpected = new long[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 2};	// final is 2, because both 90 and 100 will be there
			Assert.assertArrayEquals( countsExpected, bins.counts );
		}
		
		/** Tests many random scenarios by checking that Bins can at least be constructed without Exception. */
		@Test public void test_all_shouldPass3() throws Exception {
			Random random = new Random();
			for (int i = 0; i < 100; i++) {
				double[] values = valuesRandom(random);
				
				int number = 1 + random.nextInt(values.length);	// so, number is a random pick from [1, values.length]
				Bins bins = new Bins(values, number);
				
				Assert.assertEquals( values.length, bins.getCountTotal() );
			}
		}
		
		private double[] valuesRandom(Random random) {
			double[] values = new double[ lengthRandom(random) ];
			
			int powerMax = (int) Math.floor( Math.log10( Double.MAX_VALUE ) );
--powerMax;
// +++ added the line above because on rare occaisions, with extreme double values, have seen Intervals.width get calculated as Infinity
			int power = 1 + random.nextInt(powerMax);
			double base = Math.pow(10, power);	// so, base is a random pick from the set {10, 100, ..., 10^powerMax}
			
			for (int i = 0; i < values.length; i++) {
				values[i] = valueRandom(base, random);
			}
			return values;
		}
		
		private int lengthRandom(Random random) {
			int power = 1 + random.nextInt(6);
			int base = (int) Math.pow(10, power);	// so, lengthBase is a random pick from the set {10, 100, 1000, 10000, 100000, 1000000}
			int min = 2;
			return min + random.nextInt(base - min + 1);	// so, length is a random pick from [min, base]
		}
		
		private double valueRandom(double base, Random random) {
			double sign = random.nextBoolean() ? +1 : -1;
			double fraction = random.nextDouble();
			return sign * fraction * base;
		}
		
// +++ hmm, the test below has no assertions and requires manual inspection, which is not great...
		@Test public void test_whiteNoise() throws Exception {
			int n = 1000 * 1000;
			double[] values = new double[n];
			Random random = new Random();
			for (int i = 0; i < n; i++) {
				values[i] = random.nextDouble();
			}
			Bins bins = new Bins(values, 100);
			File file = LogUtil.makeLogFile("binsOfWhiteNoise.txt");
			FileUtil.writeString(bins.toString(), file, false);
			System.out.println("graph the file " + file.getPath() + " to see if the bins are approximately uniform in value");
		}
		
	}
	
}
