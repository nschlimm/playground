/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.science;

import bb.util.ArrayUtil;
import bb.util.Check;
import bb.util.DateUtil;
import bb.util.ThrowableUtil;
import bb.util.TimeLength;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Stores statistics over time of an arbitrary data source.
* <p>
* To understand how this class operates, imagine that time is divided into equal sized bins.
* Whenever the data source generates a value, it is stored in the bin for the time when the value occurred.
* Statistics for the data in each bin can therefore be calculated; this class stores the min, max, mean, and median.
* This class also records the bins size (i.e. the number of data values that were added).
* <p>
* The reason why time is divided into bins is because these bin statistics can then be analysed to see how the statistics change over time.
* For example, you could plot the bin mean versus the time of the bin (its first time) to see how the mean changes.
* <p>
* Each bin is characterised by its start time and its length.
* To be precise, a bin covers the semi-open time interval [binStart, binStart + binLength).
* <p>
* <i>In order to save memory, the current version of this class only stores the raw data values for the current bin under consideration.</i>
* As soon as a time of occurence arrives which is outside of this current bin,
* the current bin is converted into its summary statistics (which hopefully frees a lot of memory) and a new bin is formed.
* <i>It is an error if a time of occurrence arrives which falls into a previously considered bin.</i>
* This is not a major limitation, since it will never happen for a data source that is generating data in real time, since time flows in one direction.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
* @see Samples
*/
public class StatsOverTime {
	
	// -------------------- constants --------------------
	
	/** Default value for {@link #binLength}. */
	private static final long binLength_default = TimeLength.minute;
	
	// -------------------- instance fields --------------------
	
	/**
	* Stores the start Date of the current bin.
	* <p>
	* Contract: is never null after the first call to {@link #add add}.
	*/
	private Date binStart;
	
	/**
	* Length of time (in milliseconds) of every bin.
	* <p>
	* Contract: must be > 0.
	*/
	private final long binLength;
	
	/**
	* Stores the data values of the current bin.
	* <p>
	* Contract: is never null after the first call to {@link #add add}.
	*/
	private Samples samples;
	
	/**
	* Maps a bin's start Date to its Stats.
	* <p>
	* Contract: is never null, but may be empty, and never contains a null key or value.
	*/
	private final SortedMap<Date,Stats> dateToStats = new TreeMap<Date,Stats>();
	
	// -------------------- constructors --------------------
	
	/** Calls <code>{@link #StatsOverTime(long) this}({@link #binLength_default})</code>. */
	public StatsOverTime() {
		this(binLength_default);
	}
	
	/**
	* Fundamental constructor.
	* <p>
	* @throws IllegalArgumentException if binLength is not {@link Check#positive positive}
	*/
	public StatsOverTime(long binLength) throws IllegalArgumentException {
		Check.arg().positive(binLength);
		
		this.binLength = binLength;
	}
	
	// -------------------- public api: addElseLog, add, toString --------------------
	
	/**
	* Chiefly just calls <code>{@link #add add}(date, value)</code>.
	* However, in the event that any Throwable occurs, this method attempts to always log it to logger2,
	* and if that raises yet another Throwable, tries to log it to {@link LogUtil#getLogger2 LogUtil.getLogger2}.
	* So, it is unlikely that this method should ever throw any type of Throwable.
	* <p>
	* The original motivation for this method was to handle occaisional failures to add.
	* In particular, add (via createNewBin) throws an IllegalStateException if date was refers to a previous bin.
	* This can occur when the operating system resets time to jump backwards.
	* Relevant causes for these backwards jumps are timezone change days,
	* corrections from a time server, or (in theory) negative leap second days (which have yet to occur in practice).
	*/
	public void addElseLog(Date date, double value, Logger2 logger2) {
		try {
			Check.arg().notNull(logger2);
			
			add(date, value);
		}
		catch (Throwable t1) {
			try {
				logger2.logp(Level.SEVERE, "StatsOverTime", "addElseLog", "unexpected Throwable occurred", t1);
			}
			catch (Throwable t2) {
				LogUtil.getLogger2().logp(Level.SEVERE, "StatsOverTime", "addElseLog", "while trying to log t1 (" + ThrowableUtil.getTypeAndMessage(t1) + "), yet another unexpected Throwable occurred", t2);
			}
		}
	}
	
	/**
	* Adds value to the bin appropriate for date.
	* <p>
	* @throws IllegalArgumentException if date is null; value is not {@link Check#normal normal};
	* @throws IllegalStateException if date falls in a bin that has already been considered by this instance (and this instance has moved on to at least one other different bin)
	*/
	public void add(Date date, double value) throws IllegalArgumentException, IllegalStateException {
		// date checked by isInCurrentBin and createNewBin below
		// value checked by samples.add below
		
		if (binStart == null) {
			createNewBin(date);
		}
		else if (!isInCurrentBin(date)) {
			calcStatsOfCurrentBin();
			createNewBin(date);
		}
		
		samples.add(value);
	}
	
	/**
	* Returns a string report describing the statsistics over time.
	* <p>
	* If no data has actually been added to this instance, then the result simply states that.
	* <p>
	* Otherwise, the result has multiple lines.
	* The first line is a column header line, and all the remaining lines are the bin statistics, in ascending time order of the bins.
	* Each line is tab delimted, so the result is immediately usable by external programs (e.g. can be pasted into a spreadsheet and graphed).
	*/
	@Override public String toString() {
		if (binStart == null) return "Statistics over time are UNAVAILABLE because no values have been recorded so far";
		
		calcStatsOfCurrentBin();	// flush any currently stored bin data into its Stats so that it will be present in the result
		
		StringBuilder sb = new StringBuilder( dateToStats.size() * 128 );
		sb.append("index").append("\t").append("date").append("\t").append("size").append("\t").append("min").append("\t").append("max").append("\t").append("mean").append("\t").append("median").append("\n");
		int i = 0;
		for (Map.Entry<Date,Stats> entry : dateToStats.entrySet()) {
			String date = DateUtil.getTimeStamp( entry.getKey() );
			Stats stats = entry.getValue();
			sb.append(i++).append("\t").append(date).append("\t").append(stats.size).append("\t").append(stats.min).append("\t").append(stats.max).append("\t").append(stats.mean).append("\t").append(stats.median).append("\n");
		}
		return sb.toString();
	}
	
	// -------------------- private helper methods --------------------
	
	private boolean isInCurrentBin(Date date) throws IllegalArgumentException {
		Check.arg().notNull(date);
		
		if (binStart == null) return false;
		
		long timeDiff = date.getTime() - binStart.getTime();
		return (0 <= timeDiff) && (timeDiff < binLength);
	}
	
	private void createNewBin(Date date) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(date);
		
		binStart = new Date( binLength * (date.getTime() / binLength) );
// +++ WARNING: algorithm above may not produce desired results if leap seconds are supported in the system's time and you want bins to always start on the same time of day
		if (dateToStats.containsKey(binStart)) throw new IllegalStateException("cannot create a new bin at binStart = " + DateUtil.getTimeStamp(binStart) + " for date = " + DateUtil.getTimeStamp(date) + " because that bin has already been accounted for");
		
		int sizeInitial = (samples != null) ? samples.size() : 1024;	// performance optimization: use the size of the current bin to initialize what the size of the next bin will be, hopefully optimizing memory use; this only works if successive bin sizes are serially correlated; an alternative to this heuristic is to track the max bin size ever encountered (pros: this will have better cpu performance when the bins sizes fluctuate randomly; cons: wastes more memory on average and requires an extra field to hold the state)
		samples = new Samples(sizeInitial);
	}
	
	/** May be repeatedly called (e.g. by {@link #toString toString}) on the current bin, as it will simply overwrite the previous mapping. */
	private void calcStatsOfCurrentBin() {
		if (binStart == null) return;
		
		dateToStats.put( binStart, new Stats(samples.values()) );
	}
	
	// -------------------- Stats (static inner class) --------------------
	
	private static class Stats {
		
		private final int size;
		private final double min;
		private final double max;
		private final double mean;
		private final double median;
		
		private Stats(double[] values) {
			Arrays.sort(values);
			size = values.length;
			min = Math2.min(values);
			max = Math2.max(values);
			mean = Math2.mean(values);
			median = Math2.median(values);
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_add_shouldPass() {
			StatsOverTime sot = new StatsOverTime();
			
			Random random = new Random();
			
			int[] minutes = new int[1000];
			for (int i = 0; i < minutes.length; i++) {
				minutes[i] = i;
			}
			ArrayUtil.shuffle(minutes, random);	// CRITICAL: do this so that we add to bins in random order below in order to test that add still works correctly
			
			for (int i = 0; i < minutes.length; i++) {
				int n = minutes[i];	// n has 3 uses below: a) it specifies the number of minutes in the bin start date b) it specifies the number of values in the bin c) the values are always the n integers in the range [0, n)
				long minuteStart = n * TimeLength.minute;	// use a) described above
				for (int j = 0; j < n; j++) {	// use b) described above
					long timeWithinMinute = random.nextInt( (int) TimeLength.minute );
					Date date = new Date( minuteStart + timeWithinMinute );
					double value = j;	// use c) described above
					sot.add(date, value);
				}
			}
			sot.calcStatsOfCurrentBin();	// flush any currently stored bin data into its Stats so that it will be present for the comparison below
			
				// OK, now that have added all the data to sot, confirm that each Stats is what it ought to be:
			
			int n = 1;	// do not start at 0, since the 0th minute above actually added no data, so it is has no Stats
			for (Stats stats : sot.dateToStats.values()) {
				Assert.assertEquals(n, stats.size);
				
				Assert.assertEquals(0, stats.min, 0);
				
				Assert.assertEquals(n - 1, stats.max, 0);
				
				double meanExpected = (n - 1) / 2.0;	// the formula for the summation over i = [0, n - 1] = n * (n - 1) / 2, and then need to divide that by the n values to get the formula here
				Assert.assertEquals(meanExpected, stats.mean, 0);
				
				double medianExpected = meanExpected;	// the mean should equal the median when the sample is symmetric, which it is here; can also confirm that the (n - 1) / 2.0 formula used here is correct by looking at the source code of Math2.median using k = 1, q = 2, and numbers[j] = j
				Assert.assertEquals(medianExpected, stats.median, 0);
				
				++n;
			}
		}
		
		@Test(expected=IllegalStateException.class) public void test_add_shouldFail() {
			StatsOverTime sot = new StatsOverTime();
			
			Date minute1 = new Date(0);
			sot.add(minute1, 1);
			
			Date minute2 = new Date(TimeLength.minute);
			sot.add(minute2, 2);
			
				// this is what should fail: revisiting a previous bin:
			sot.add(minute1, 3);
		}
		
	}
	
}

