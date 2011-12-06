/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ when jdk 7 comes out, explore its Fork-join framework
	http://www.ibm.com/developerworks/java/library/j-jtp11137.html
and see if can parallelize the computations in this class
	--in particular, might be able to parallelize doResampling
	--ON THE OTHER HAND, PARALLELIZING THE LOW LEVEL CODE SIMPLY MAY NOT BE NEEDED:
		the current BCa algorithm with the default params below on typical datasets of interest to me (i.e. size <= 100)
		is already executing in ~1.2 seconds on my 2.2 GHz Intel Core 2 Duo E4500 machine

+++ currently, the truly heavy computational load is in the UnitTest inner class below
	--its determineCoverage and compareBootstrapCiWithTheory methods use thread pools to use all the cpus on a given box
	--HOWEVER the next leap in performance would be to additionally use grid software and distribute tasks to many different machines
		http://www.jppf.org/index.php

--this file contains all my (rough) notes on the bootstrap technique:
	../doc/bootstrap/bootstrappingResources.txt

--this file has a section that discusses java statistical packages (I use JSci.maths.statistics below):
	../doc/bootstrap/otherJavaLibraries.txt
*/


package bb.science;


import JSci.maths.statistics.BinomialDistribution;
import JSci.maths.statistics.ChiSqrDistribution;
import JSci.maths.statistics.NormalDistribution;
import JSci.maths.statistics.TDistribution;
import bb.io.ConsoleUtil;
import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.RandomUtil;
import bb.util.ThreadPoolExecutor2;
import bb.util.logging.LogUtil;
import ec.util.MersenneTwisterFast;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
* Performs statistical bootstrap calculations.
* <p>
* Concerning the quality of the results: bootstrapping is no magic bullet.
* So, if you supply garbage inputs, the outputs are also garbage.
* One example is insufficient data: the {@link #Bootstrap(double[], int, double, Estimator[]) fundamental constructor}
* will accept a sample which has but one element in it, but the statistical results that are calculated are almost certainly worthless.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class Bootstrap {
	
	
	// -------------------- constants --------------------
	
	
	/**
	* Default value for {@link #numberResamples}.
	* Its value of 100,000 was recommended by Tim Hesterberg (private communication)
	* as providing the best balance between high accuracy and reasonable computation time.
	*/
	public static final int numberResamples_default = 100 * 1000;
	
	
	/**
	* Default value for {@link #confidenceLevel}.
	* Its value of 0.95 is the usual 95% confidence level used in statistics.
	*/
	private static final double confidenceLevel_default = 0.95;
	
	
	/**
	* Default value for the estimators param of the fundamental constructor.
	* Its value is the mean, median, and standard deviation estimators typically used in statistics.
	*/
	private static final Estimator[] estimators_default = new Estimator[] {new EstimatorMean(), new EstimatorMedian(), new EstimatorSd()};
	
	
	// -------------------- instance fields --------------------
	
	
	private final double[] sample;
	
	
	private final int numberResamples;
	
	
	private final double confidenceLevel;
	
	
	private final ConcurrentHashMap<Estimator,Estimate> estimatorToEstimate;
	
	
	// -------------------- constructors --------------------
	
	
	/**
	* Convenience constructor that simply calls
	* <code>{@link #Bootstrap(double[], int) this}(sample, {@link #numberResamples_default})</code>.
	* <p>
	* @throws IllegalArgumentException if sample == null; sample.length == 0
	*/
	public Bootstrap(double[] sample) throws IllegalArgumentException {
		this(sample, numberResamples_default);
	}
	
	
	/**
	* Convenience constructor that simply calls
	* <code>{@link #Bootstrap(double[], int, double) this}(sample, numberResamples, {@link #confidenceLevel_default})</code>.
	* <p>
	* @throws IllegalArgumentException if sample == null; sample.length == 0
	*/
	public Bootstrap(double[] sample, int numberResamples) throws IllegalArgumentException {
		this(sample, numberResamples, confidenceLevel_default);
	}
	
	
	/**
	* Convenience constructor that simply calls
	* <code>{@link #Bootstrap(double[], int, double, Estimator[]) this}(sample, numberResamples, confidenceLevel, {@link #estimators_default})</code>.
	* <p>
	* @throws IllegalArgumentException if sample == null; sample.length == 0; numberResamples < 1;
	* confidenceLevel <= 0 or confidenceLevel >= 1
	*/
	public Bootstrap(double[] sample, int numberResamples, double confidenceLevel) throws IllegalArgumentException {
		this(sample, numberResamples, confidenceLevel, estimators_default);
	}
	
	
	/**
	* Fundamental constructor.
	* <p>
	* @throws IllegalArgumentException if sample == null; sample.length < 1; numberResamples < 1;
	* confidenceLevel <= 0 or confidenceLevel >= 1; estimators == null; estimators.length == 0; the names of estimators fail to all be unique
	*/
	public Bootstrap(double[] sample, int numberResamples, double confidenceLevel, Estimator... estimators) throws IllegalArgumentException {
		Check.arg().notEmpty(sample);
		Check.arg().positive(numberResamples);
		if ((confidenceLevel <= 0) || (confidenceLevel >= 1) || Double.isNaN(confidenceLevel)) throw new IllegalArgumentException("confidenceLevel = " + confidenceLevel + " is an illegal value");
		Check.arg().notEmpty(estimators);
		Set<String> estimatorNames = new HashSet<String>();
		for (Estimator estimator : estimators) estimatorNames.add(estimator.getName());
		if (estimatorNames.size() < estimators.length) throw new IllegalArgumentException("estimatorNames.size() = " + estimatorNames.size() + " < estimators.length = " + estimators.length + " (i.e. there is duplication of Estimator names)");
		
		this.sample = sample;
		this.numberResamples = numberResamples;
		this.confidenceLevel = confidenceLevel;
		this.estimatorToEstimate = calcEstimates(estimators);
		
/*
+++ as a diagnostic to catch any serial correlation, which invalidates normal bootstrapping,
should also automatically do another bootstrap where the resampling takes blocks of 2 neighbors at a time,
and see if the resulting stats radically differ?
	--problem: in real samples, especially ones of small size,
	the block resampling is likely to ALWAYS differ somewhat from the usual results, even if there is no serial correlation,
	so how do you quantify at what point to warn the user?

NOTE: Benchmark now does an autocorrelation test to detect serial correlation in its measurements; see its diagnoseSerialCorrelation method
*/
	}
	
	
	// -------------------- calcEstimates and helper methods --------------------
	
	
	private ConcurrentHashMap<Estimator,Estimate> calcEstimates(Estimator[] estimators) {
		//return calcEstimates_percentile(estimators);
		return calcEstimates_BCa(estimators);
	}
	
	
	/**
	* Performs a bootstrap calculation, determining one {@link Estimate} for each element of estimators.
	* The simple bootstrap percentile method is used to calculate the confidence intervals.
	*/
	private ConcurrentHashMap<Estimator,Estimate> calcEstimates_percentile(Estimator[] estimators) {
		try {
			Map<Estimator,double[]> resampleMap = doResampling(estimators);	// call this once, rather than in loop below, since it is vastly computationally more effective to reuse a given resample among all the Estimators
			
			ConsoleUtil.overwriteLine("calculating an Estimate for each Estimator...");
			ConcurrentHashMap<Estimator,Estimate> resultMap = new ConcurrentHashMap<Estimator,Estimate>();
			double alpha2 = (1 - confidenceLevel) / 2;
			int indexLower = (int) Math.max( Math.round( alpha2 * numberResamples ), 0 );	// lower bound of 0 ensures valid array subscript below
			int indexUpper = (int) Math.min( Math.round( (1 - alpha2) * numberResamples ), numberResamples - 1 );	// upper bound of numberResamples - 1  ensures valid array subscript below
			for (Estimator estimator : estimators) {
				double point = estimator.calculate(sample);
				double[] resampleEsts = resampleMap.get(estimator);
				resultMap.put( estimator, new Estimate(point, resampleEsts[indexLower], resampleEsts[indexUpper], confidenceLevel) );
			}
			return resultMap;
		}
		finally {
			ConsoleUtil.eraseLine();
		}
	}
	
	
	/**
	* Performs a bootstrap calculation, determining one {@link Estimate} for each element of estimators.
	* The Bias Corrected accelerated (BCa) bootstrap percentile method is used to calculate the confidence intervals.
	* See pp. 185ff of "An Introduction to the Bootstrap", B. Efron and R. Tibshirani, Chapman and Hall, 1993
	* for a description of the calculation technique.
	*/
	private ConcurrentHashMap<Estimator,Estimate> calcEstimates_BCa(Estimator[] estimators) {
		try {
			Map<Estimator,double[]> resampleMap = doResampling(estimators);
			
			ConsoleUtil.overwriteLine("calculating an Estimate for each Estimator...");
			ConcurrentHashMap<Estimator,Estimate> resultMap = new ConcurrentHashMap<Estimator,Estimate>();
			double alpha = 1 - confidenceLevel;	// WARNING: my definition of alpha is the normal one, but Efron uses a value that is half of this one
			NormalDistribution normalDistribution = new NormalDistribution();
			double z1 = normalDistribution.inverse( alpha / 2 );	// Efron writes this as z^(alpha)
			double z2 = -z1;	// my z2 is normalDistribution.inverse( 1 - (alpha / 2) ) = -normalDistribution.inverse( alpha / 2 ) = -z1 by a mathematical identity and is what Efron writes as z^(1 - alpha)
			for (Estimator estimator : estimators) {
				double point = estimator.calculate(sample);
				
				if (sample.length == 1) {	// must detect this special case here, since the calcAcceleration/calcJackknifeEsts calls below will crash on this case
					resultMap.put( estimator, new Estimate(point, point, point, confidenceLevel) );
					continue;
				}
				
				double[] resampleEsts = resampleMap.get(estimator);
						// calculate the bias and acceleration estimates:
				double b = calcBias(point, resampleEsts, normalDistribution);	// Efron writes this as z-hat-sub0
				double a = calcAcceleration(estimator);	// Efron writes this as a-hat
						// calculate the percentile correction factors; see the 2 formulas in Eq. 14.10
				double b_z1 = b + z1;
				double a1 = normalDistribution.cumulative(b + (b_z1 / (1 - (a * b_z1))) );	// Efron writes this as alpha-sub1
				double b_z2 = b + z2;
				double a2 = normalDistribution.cumulative(b + (b_z2 / (1 - (a * b_z2))) );	// Efron writes this as alpha-sub2
						// calculate the nearest indices where the CI bounds occur:
				int indexLower = (int) Math.max( Math.round( a1 * numberResamples ), 0 );	// lower bound of 0 ensures valid array subscript below
				int indexUpper = (int) Math.min( Math.round( a2 * numberResamples ), numberResamples - 1 );	// upper bound of numberResamples - 1  ensures valid array subscript below
				
				resultMap.put( estimator, new Estimate(point, resampleEsts[indexLower], resampleEsts[indexUpper], confidenceLevel) );
			}
			return resultMap;
		}
		finally {
			ConsoleUtil.eraseLine();
		}
	}
	
	
	/**
	* Generates {@link #numberResamples} bootstrap resamples.
	* For each resample, determines one point estimate for each element of estimators.
	* The result is a Map from each Estimator to its array of resampled point estimates.
	* Each array is sorted before return.
	*/
	private Map<Estimator,double[]> doResampling(Estimator[] estimators) {
		ConsoleUtil.overwriteLine("performing bootstrap resampling...");
		int length = sample.length;
		double[] resample = new double[length];	// reuse this array in loop below to save on allocation costs
		MersenneTwisterFast random = RandomUtil.get();
		Map<Estimator,double[]> resampleMap = new HashMap<Estimator,double[]>();
		for (int i = 0; i < numberResamples; i++) {
			if (i % 1000 == 0) ConsoleUtil.overwriteLine("executing bootstrap resample #" + i + "/" + numberResamples);
			for (int j = 0; j < resample.length; j++) {
				resample[j] = sample[ random.nextInt(length) ];
			}
			for (Estimator estimator : estimators) {
				double[] resampleEsts = resampleMap.get(estimator);
				if (resampleEsts == null) {	// lazy initialize the Map
					resampleEsts = new double[numberResamples];
					resampleMap.put(estimator, resampleEsts);
				}
				resampleEsts[i] = estimator.calculate(resample);
			}
		}
		
		ConsoleUtil.overwriteLine("sorting bootstrap resamples...");
		for (Estimator estimator : estimators) {
			Arrays.sort( resampleMap.get(estimator) );
		}
		
		return resampleMap;
	}
	
	
	/**
	* Calculates the bias estimation for a BCa bootstrap.
	* See Eq. 14.14 p. 186 of "An Introduction to the Bootstrap", B. Efron and R. Tibshirani, Chapman and Hall, 1993.
	*/
	private double calcBias(double point, double[] resampleEsts, NormalDistribution normalDistribution) {
		int count = 0;
		for (double d : resampleEsts) count += (d < point) ? 1 : 0;
		double probability = ((double) count) / numberResamples;
		return normalDistribution.inverse( probability );
	}
	
	
	/**
	* Calculates the acceleration estimation for a BCa bootstrap.
	* See Eq. 14.15 p. 186 of "An Introduction to the Bootstrap", B. Efron and R. Tibshirani, Chapman and Hall, 1993.
	*/
	private double calcAcceleration(Estimator estimator) {
		double[] jackknifeEsts = calcJackknifeEsts(estimator);
		double jackknifeMean = Math2.mean(jackknifeEsts);
		double sumOfSquares = 0.0;
		double sumOfCubes = 0.0;
		for (double d : jackknifeEsts) {
			double diff = jackknifeMean - d;
			double diffSquared = diff * diff;
			double diffCubed = diff * diffSquared;
			
			sumOfSquares += diffSquared;
			sumOfCubes += diffCubed;
		}
		return sumOfCubes / (6 * Math.pow(sumOfSquares, 1.5));	// NOTE: there is an error in source's formula: he squares only T-bar, when the correct thing to do is square the whole difference e.g. see p. 24 of http://www.meb.ki.se/~aleplo/Compstat/CS05_7.pdf
// +++ Note: this jackknife calculation of a is the usual simple calculation, but more sophisticated and accurate estimates are discussed in Efron as well as "Bootstrap Methods and their Application" by Davison AC, Hinkley DV.
	}
	
	
	/** Jackknifes {@link #sample}, calculating a point estimate for each jackknife resample using estimator. */
	private double[] calcJackknifeEsts(Estimator estimator) {
		double[] jackknifeEsts = new double[sample.length];
		for (int i = 0; i < jackknifeEsts.length; i++) {
			double[] jackknifeSample = new double[sample.length - 1];
			int k = 0;
			for (int j = 0; j < sample.length; j++) {
				if (j == i) continue;	// this is the jackknife: the ith jackknifeSample skips sample[i]
				jackknifeSample[k++] = sample[j];	// otherwise retain all the other samples
			}
			jackknifeEsts[i] = estimator.calculate(jackknifeSample);
		}
		return jackknifeEsts;
	}
	
	
	// -------------------- accessors --------------------
	
	
	/**
	* Returns the {@link Estimate} which corresponds to estimator.
	* <p>
	* @throws IllegalArgumentException if estimator == null; if there is no result for it
	*/
	public Estimate getEstimate(Estimator estimator) throws IllegalArgumentException {
		Check.arg().notNull(estimator);
		
		return getEstimate(estimator.getName());
	}
	
	
	/**
	* Returns the first {@link Estimate} which corresponds to estimator with the same name as estimatorName.
	* <p>
	* @throws IllegalArgumentException if estimatorName is blank; if there is no result for it
	*/
	public Estimate getEstimate(String estimatorName) throws IllegalArgumentException {
		Check.arg().notBlank(estimatorName);
		
		for (Estimator estimator : estimatorToEstimate.keySet()) {
			if (estimatorName.equals(estimator.getName())) return estimatorToEstimate.get(estimator);
		}
		throw new IllegalArgumentException("estimatorName = " + estimatorName + " does not correspond to any Estimator that this instance was constructed with");
	}
	
	
	// -------------------- Estimator (static inner interface) and common implementations --------------------
	
	
	/**
	* Specifies the api for classes that calculate an estimate for a statistic from a sample.
	* <p>
	* Implementations must be multithread safe.
	*/
	public static interface Estimator {
		
		/**
		* Returns the name of the Estimator.
		* <p>
		* Contract: the result is never blank (null or empty).
		*/
		String getName();
		
		/**
		* Calculates a point estimate for the statistic based on sample.
		* <p>
		* @throws IllegalArgumentException if sample is null or zero-length; any element of sample is NaN
		*/
		double calculate(double[] sample) throws IllegalArgumentException;
		
	}
	
	
	/**
	* Calculates a point estimate for the population's arithmetic mean from sample.
	* <p>
	* This class is multithread safe: it is stateless.
	*/
	public static class EstimatorMean implements Estimator {
		
		public EstimatorMean() {}
		
		public String getName() { return "mean"; }
		
		public double calculate(double[] sample) throws IllegalArgumentException {
			// sample checked by Math2.mean below
			
			return Math2.mean(sample);
		}
		
	}
	
	
	/**
	* Calculates a point estimate for the population's median from sample.
	* <p>
	* This class is multithread safe: it is stateless.
	*/
	public static class EstimatorMedian implements Estimator {
		
		public EstimatorMedian() {}
		
		public String getName() { return "median"; }
		
		public double calculate(double[] sample) throws IllegalArgumentException {
			Check.arg().notNull(sample);
			// remaining checks on sample done by Math2.median below
			
			double[] sampleSorted = sample.clone();	// must make a clone since do not want the sort side effect to affect the caller
			Arrays.sort(sampleSorted);
			return Math2.median(sampleSorted);
		}
		
	}
	
	
	/**
	* Calculates a point estimate for the population's standard deviation from sample.
	* <p>
	* This class is multithread safe: it is stateless.
	*/
	public static class EstimatorSd implements Estimator {
		
		public EstimatorSd() {}
		
		public String getName() { return "sd"; }
		
		public double calculate(double[] sample) throws IllegalArgumentException {
			// sample checked by Math2.sd below
			
			return Math2.sd(sample);
		}
		
	}
	
	
	// -------------------- Estimate (static inner class) --------------------
	
	
	/**
	* Holds a complete (point and interval) estimate for some {@link Estimator}.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
	*/
	public static class Estimate {
		
		/**
		* Records a single value ("point") estimate.
		* This value may or may not be the maximum likelihood estimate (it totally depends on the Estimator used).
		* <p>
		* Contract: may be any value, including NaN (if undefined) or infinity.
		*/
		protected final double point;
		
		/**
		* Is the lower bound (start of the confidence interval) of the estimate.
		* <p>
		* Contract: may be any value, including NaN (if undefined) or infinity.
		* However, if NaN, then {@link #upper} must also be NaN,
		* and if not NaN, then upper must also be not NaN and lower must be <= upper.
		*/
		protected final double lower;
		
		/**
		* Is the upper bound (end of the confidence interval) of the estimate.
		* <p>
		* Contract: same as {@link #lower}.
		*/
		protected final double upper;
		
		/**
		* Specifies the confidence level of the confidence intervals.
		* <p>
		* Units: none; is a dimensionless fractional number in the range (0, 1).
		* <p>
		* Note: the <i>percent</i> confidence level is 100 times this quantity.
		* <p>
		* Contract: must be inside the open interval (0, 1), and is never NaN or infinite.
		* <p>
		* @see <a href="http://en.wikipedia.org/wiki/Confidence_level">article on confidence intervals</a>
		*/
		protected final double confidenceLevel;
		
		public Estimate(double point, double lower, double upper, double confidenceLevel) throws IllegalArgumentException {
			if (Double.isNaN(lower)) {
				if (!Double.isNaN(upper)) throw new IllegalArgumentException("lower is NaN, but upper = " + upper + " != NaN");
			}
			else {
				if (Double.isNaN(upper)) throw new IllegalArgumentException("lower = " + lower + " != NaN, but upper is NaN");
				if (lower > upper) throw new IllegalArgumentException("lower = " + lower + " > upper = " + upper);
			}
			if ((confidenceLevel <= 0) || (confidenceLevel >= 1) || Double.isNaN(confidenceLevel)) throw new IllegalArgumentException("confidenceLevel = " + confidenceLevel + " is an illegal value");
			
			this.point = point;
			this.lower = lower;
			this.upper = upper;
			this.confidenceLevel = confidenceLevel;
		}
		
		public double getPoint() { return point; }
		public double getLower() { return lower; }
		public double getUpper() { return upper; }
		public double getConfidenceLevel() { return confidenceLevel; }
		
		@Override public String toString() { return point + " CI = [" + lower + ", " + upper + "]"; }
		
		public boolean confidenceIntervalContains(double value) throws IllegalArgumentException {
			if (Double.isNaN(value)) throw new IllegalArgumentException("value is NaN");
			
			return (lower <= value) && (value <= upper);
		}
		
	}
	
	
	// -------------------- UnitTest (static inner class) --------------------
	
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		// -------------------- constants --------------------
		
		//private static final MemoryMonitor memoryMonitor = new MemoryMonitor( TimeLength.minute );
		//private static final MemoryMonitorListenerImpl memoryListener = new MemoryMonitorListenerImpl();
		
		private static final Distribution[] distributions = new Distribution[] {
			new GaussianStandard(),	// well behaved tails, non-skewed, non-serially correlated
			new CauchyStandard(),	// fat tails, non-skewed, non-serially correlated
			new ExponentialStandard()	// well behaved tails, skewed, non-serially correlated
// +++ also need a distribution which has serial correlation; main issue: need known analytical results for it; Tim Hesterberg says that a "simple autoregressive Gaussian sequence is pretty tractable"
		};
		
			// default values for the params (WARNING: some methods below override these values):
		private static final int sampleLength = 100;
		
		private static final int numberResamples = 100 * 1000;
		
		private static final double confidenceLevel = 0.95;
		
		private static final int numberTrials = 1;	// used only when debugging; runs in ~90 s on my laptop
		//private static final int numberTrials = 10;	// used only when debugging; runs in ~476 s on my laptop
		//private static final int numberTrials = 100;	// used only as part of determining execution time scaling; runs in ~4.46 ks on my laptop
		//private static final int numberTrials = 1000;	// used only as part of determining execution time scaling; runs in ~45 ks on my laptop
		//private static final int numberTrials = 5 * 1000;	// use this to get accurate results; runs in ~30 ks??? on my laptop or desktop???
		
		// -------------------- fields --------------------
		
		/** A PrintWriter for method output.  Defaults to the console, but may be changed to write to a file. */
		private PrintWriter pw = new PrintWriter( System.out );
		
		// -------------------- setupClass, teardownClass --------------------
		
		@BeforeClass public static void setupClass() throws InterruptedException {
			//memoryMonitor.addListener( memoryListener );
			//memoryMonitor.startMonitoring();
		}
		
		@AfterClass public static void teardownClass() throws java.io.IOException {
			//try { memoryMonitor.stopMonitoring(); } catch (Throwable t) { t.printStackTrace(System.err); }
			//try { memoryListener.close(); } catch (Throwable t) { t.printStackTrace(System.err); }
		}
		
		// -------------------- determineNumberOfTrials, precisionOfCoverage --------------------
		
		/**
		* Explores how the CI coverage relative precision changes as a function of the number of trials.
		* Execute this method to get an idea of how many trials you should execute to achieve a desired precision.
		* <p>
		* See the file .../doc/bootstrap/testResults/determineNumberOfTrials.txt for sample output.
		* Summary: to get ~1 digit of precison, need ~100 trials, to get ~2 digits of precison, need ~10,000 trials,
		* and to get each sucessive digit requires 100X more trials (e.g. 1,000,000 trials for 3 digits)
		* because of the very slow 1/sqrt(n) convergence.
<!--
+++ can get faster improvement using variance reduction techniques:
	read these for an introduction:
		http://www.riskglossary.com/link/monte_carlo_method.htm
		http://www.math.ntu.edu.tw/~hchen/teaching/Computing/notes/montecarlo2.pdf
		An Introduction to Financial Option Valuation: Mathematics, Stochastics and Computation by Desmond J. Higham, Cambridge University Press, 2004
		Bratley, Fox, and Schrage (1987)
		Fishman (1996)
		L’Ecuyer (1994)
	shows the relationship between bootstrap and monte carlo (integration)
		http://www.scs.gmu.edu/~jgentle/csi771/07s/l5_boot.pdf
		
	Tim Hesterberg thinks that quasi-random numbers (low-discrepancy sequences) is a promising technique; see:
		http://www.puc-rio.br/marco.ind/quasi_mc.html
		
My conclusions:
	--control variate technique will definitely work
		--could use the theoretical CI and its known coverage properties as the control variate;
		it should be very highly correlated with the bootstrap CIs coverage;
		to get the highest correlation, will want to use the actual bootstrap coverage estimate for the confidence level of the theory CI (e.g. 93%) instead of the nominal (e.g. 95%) confidence level
	
	--antithetic variables will also work:
		--some references:
			http://www-stat.stanford.edu/~susan/courses/s208/node14.html#SECTION00281100000000000000
			
			Antithetic resampling for the bootstrap HALL Biometrika.1989; 76: 713-724
			http://links.jstor.org/sici?sici=0006-3444%28198912%2976%3A4%3C713%3AARFTB%3E2.0.CO%3B2-2&size=LARGE&origin=JSTOR-enlargePage
			
			The Bootstrap and Edgeworth Expansion By Peter G. Hall (has a section on this)
		
		--simply modify how do the sampling from only Ui to also include 1 - Ui; see pp.28ff of http://www.math.ntu.edu.tw/~hchen/teaching/Computing/notes/montecarlo2.pdf
	
	--stratified sampling
		--see pp. 17ff of http://www.rci.rutgers.edu/~drhoover/SIMULATIONR.PDF
		
		--unclear how it could be used in bootstrap
-->
		*/
		@Test public void determineNumberOfTrials() throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() {
				double q = 0.99;
				pw.println("confidenceLevel = " + confidenceLevel + ", q = " + q + ":");
				pw.println("\t" + "numberOfTrials --> precisionOfCoverage");
				int[] points = new int[] {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10*1000, 20*1000, 50*1000, 100*1000, 200*1000, 500*1000};
				for (int n : points) {
					pw.println("\t" + n + " --> " + precisionOfCoverage(n, confidenceLevel, q));
				}
				return null;
			} };
			executeCode(task, "determineNumberOfTrials", "execution time = ");
		}
		
		/**
		* Assume that you perform a CI generating process n times,
		* and that each CI has the exact same coverage probabilty (i.e. confidenceLevel) specified by p.
		* Let N be defined as the number of those n CIs which actually contained the true value
		* Then the probability distribution for N is the binomial distribution B(n, p).
		* <p>
		* The mean value of N is n * p.
		* <p>
		* To get a measure for a reasonable range of values for N,
		* define the "central interval" to be the sole interval [lower, upper] which satisfies
		* cdf(upper) - cdf(lower) = q, and 1 - cdf(upper) = cdf(lower) = q/2
		* where cdf is the cumulative distribution function of B(n, p).
		* In other words, N lies within [lower, upper] with a probabilty of q,
		* and has equal probability of 1 - q/2 of falling below or above this "central interval".
		* Define the "central range" to be the length of this interval, i.e. upper - lower.
		* <p>
		* Then this method returns the "central range" divided by the mean, i.e. (upper - lower) / (n * p).
		* This is the relative precision that we can expect with probability q.
		*/
		private double precisionOfCoverage(int n, double p, double q) {
			double mean = n * p;
			
			BinomialDistribution binomialDistribution = new BinomialDistribution(n, p);
			double beta2 = (1 - q) / 2;
			double upper = binomialDistribution.inverse(1 - beta2);
			double lower = binomialDistribution.inverse(beta2);
			double rangeCentral = upper - lower;
				// The above code OUGHT to have been enough,
				// but the implementation of BinomialDistribution is buggy and returns NaN for large n.
				// So, need to detect if this has happened and try to work around it by using the gaussian approximation to the binomial
				// (see http://en.wikipedia.org/wiki/Binomial_distribution#Normal_approximation) which is better behaved:
			if (Double.isNaN(rangeCentral)) {
				if ( (n * p > 5) && (n * (1 - p) > 5) ) {
					double sigma = n * p * (1 - p);
					NormalDistribution normalDistribution = new NormalDistribution(mean, sigma);
					upper = normalDistribution.inverse(1 - beta2);	// NOTE: may ignore continuity correction to upper and lower because it cancels out in the difference below
					lower = normalDistribution.inverse(beta2);
					rangeCentral = upper - lower;
				}
				//else	// do nothing; return NaN...
			}
			
			return rangeCentral / mean;
		}
		
		// -------------------- vary_sampleLength --------------------
		
		/** Ouput is the relevant files in the doc/bootstrap/testResults directory of this project. */
		@Test public void vary_sampleLength() throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() throws Exception {
				pw.println("numberResamples = " + numberResamples + ", confidenceLevel = " + confidenceLevel + ", numberTrials = " + numberTrials + ":");
				for (Distribution distribution : distributions) {
					pw.println("\t" + distribution.getName() + ":");
					vary_sampleLength(distribution);
				}
				return null;
			} };
			executeCode(task, "vary_sampleLength", "execution time = ");
		}
		
		private void vary_sampleLength(Distribution distribution) throws Exception {
			int[] sampleLengthArray = new int[] {10, 20, 50, 100, 200, 500};
			for (int sLength : sampleLengthArray) {
				determineCoverage(distribution, sLength, numberResamples, confidenceLevel, numberTrials, "sampleLength = " + sLength);
			}
		}
		
		// -------------------- vary_numberResamples --------------------
		
		/** Ouput is the relevant files in the doc/bootstrap/testResults directory of this project. */
		@Test public void vary_numberResamples() throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() throws Exception {
				pw.println("sampleLength = " + sampleLength + ", confidenceLevel = " + confidenceLevel + ", numberTrials = " + numberTrials + ":");
				for (Distribution distribution : distributions) {
					pw.println("\t" + distribution.getName() + ":");
					vary_numberResamples(distribution);
				}
				return null;
			} };
			executeCode(task, "vary_numberResamples", "execution time = ");
		}
		
		private void vary_numberResamples(Distribution distribution) throws Exception {
			int[] numberResamplesArray = new int[] {100, 1000, 10*1000, 100*1000, 1000*1000};
			for (int nResamples : numberResamplesArray) {
				determineCoverage(distribution, sampleLength, nResamples, confidenceLevel, numberTrials, "numberResamples = " + nResamples);
			}
		}
		
		// -------------------- determineCoverage helper method for the vary_xxx code --------------------
		
		private void determineCoverage(final Distribution distribution, final int sampleLength, final int numberResamples, final double confidenceLevel, final int numberTrials, final String label) throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() throws Exception {
				CoverageResult coverageResult = new CoverageResult("\t\t\t");
				ThreadPoolExecutor pool = new ThreadPoolExecutor2();	// Note: this no-arg constructor will create numCpu threads, and a blocking queue that can hold 3 * numCpu tasks
				for (int i = 0; i < numberTrials; i++) {
					pool.execute( new CoverageTask(distribution, sampleLength, numberResamples, confidenceLevel, estimators_default, coverageResult) );
				}
				pool.shutdown();
				pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
				pw.println("\t\t" + label + ":" + "\n" + coverageResult.getAnswer());
				return null;
			} };
			executeCode(task, null, "\t\t\t" + "execution time = ");
		}
		
		// -------------------- compareBootstrapCiWithTheory --------------------
		
		/** Ouput is the relevant files in the doc/bootstrap/testResults directory of this project. */
		@Test public void compareBootstrapCiWithTheory() throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() throws Exception {
				pw.println("sampleLength = " + sampleLength + ", numberResamples = " + numberResamples + ", confidenceLevel = " + confidenceLevel + ", numberTrials = " + numberTrials + ":");
				for (Distribution distribution : distributions) {
					pw.println("\t" + distribution.getName() + ":");
					compareBootstrapCiWithTheory(distribution);
				}
				return null;
			} };
			executeCode(task, "compareBootstrapCiWithTheory", "execution time = ");
		}
		
		private void compareBootstrapCiWithTheory(final Distribution distribution) throws Exception {
			Callable<Void> task = new Callable<Void>() { public Void call() throws InterruptedException {
				CiResult ciResult = new CiResult("\t\t");
				ThreadPoolExecutor pool = new ThreadPoolExecutor2();	// Note: this no-arg constructor will create numCpu threads, and a blocking queue that can hold 3 * numCpu tasks
				for (int i = 0; i < numberTrials; i++) {
					pool.execute( new CiTask(distribution, sampleLength, numberResamples, confidenceLevel, estimators_default, ciResult) );
				}
				pool.shutdown();
				pool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
				pw.println( ciResult.getAnswer() );
				return null;
			} };
			executeCode(task, null, "\t\t" + "execution time = ");
		}
		
		// -------------------- helper methods: executeCode, toPercent --------------------
		
		private <T> void executeCode(Callable<T> task, String filename, String execTimePrefix) throws Exception {
			if (filename != null) pw = new PrintWriter( new FileOutputStream( LogUtil.makeLogFile(filename + ".txt") ), true );	// CRITICAL: use true to autoflush, since want to ensure that partial results are written both so that can view them as they come out, as well as if the process crashes before completion
			try {
				long t1 = System.nanoTime();
				task.call();
				long t2 = System.nanoTime();
				double executionTime = (t2 - t1) * 1e-9;
				pw.println( execTimePrefix + FormatUtil.toEngineeringTime(executionTime, 3) );
			}
			finally {
				if (filename != null) {
					StreamUtil.close(pw);	// CRITICAL: needed both to flush as well as ensure never used again
					pw = new PrintWriter(System.out);
				}
			}
		}
		
		private static String toPercent(double d) {
			return Math.round( 100 * d ) + "%";
		}
		
		// -------------------- Distribution (static inner interface) and implementations --------------------
		
		/**
		* Specifies the api for classes that model a probability distribution.
		* <p>
		* Implementations must be multithread safe.
		*/
		private static interface Distribution {
			
			String getName();
			
			double[] generateSample(int n);
			
			double getMean();
			
			double getMedian();
			
			double getSd();
			
			/** Returns a theoretically known Estimate for the mean given sample. */
			Estimate getMeanEst(double[] sample, double confidenceLevel);
			
			/** Returns a theoretically known Estimate for the median of this sample. */
			Estimate getMedianEst(double[] sample, double confidenceLevel);
			
			/** Returns a theoretically known Estimate for the sd of this sample. */
			Estimate getSdEst(double[] sample, double confidenceLevel);
			
		}
		
		/**
		* Implements some common functionality used by concrete subclasses.
		* <p>
		* This class is multithread safe: it is stateless.
		*/
		private abstract static class DistributionAbstract implements Distribution {
			
			/**
			* Implements an amazing theoretical result for the median confidence interval
			* which is valid for any distribution, and only assumes iid for the samples.
			* This result involves the binomial distribution.
			* <pre>
			It is described here on these webpages:
				perfeval.epfl.ch/printMe/conf-1.ppt	(see p. 12)
				http://www.stat.ufl.edu/STA6166/Fall06/17.STA6166%20Chapter5p2.pdf
				http://www.behav.org/QP/quant_large.pdf	(see Appendix II)
				http://stat-www.berkeley.edu/~stark/Teach/S240/Notes/ch5.htm	(see the final section)
				http://www-users.york.ac.uk/~mb55/intro/cicent.htm
				http://www.statsdirect.com/help/nonparametric_methods/qci.htm
			and in these books:
				Nonparametrics: Statistical Methods Based on Ranks, Holden-Day, 1975, by Lehmann p.182-183
				
				Introduction to Mathematical Statistics (6th Edition)
				by Robert V. Hogg, Allen Craig, by Joseph W. McKean (~p. 246 has an interesting discussion of this; see also their discussion in chapter 10)
				
				Practical Nonparametric Statistics (3rd edition), Wiley 1999, by Conover WJ (several people on the web referenced this)
				
				http://books.google.com/books?id=tARVZq4hq7UC&pg=PA173&lpg=PA173&dq=binomial+distribution+cdf+confidence+interval+median&source=web&ots=yFfj2N9vFi&sig=IilUl90CEaCqul8GCgpEASRjKVY#PPA173,M1
			If you do not want to calculate it, here are tables:
				http://www.math.unb.ca/~knight/utility/MedInt95.htm
				http://biomet.oxfordjournals.org/cgi/content/abstract/57/3/613
			and here is an applet:
				http://www.sph.emory.edu/~cdckms/median-final.html
			
			The sole drawback of this binomial result is that it is only exact for certain discrete values of the confidence level.
			In particular, the exact confidence levels are limited to a small set of values
			that is a function of the number of samples and the binomial distribution's cdf.
			
			To work around this limitation, 2 solutions are possible.
			
			First, if the confidence level that you desire is not one of the exact values,
			you may simply use the confidence interval produced by the next largest exact confidence level.
			This will be a conservative (too large) confidence interval for the level that you desire,
			but for large sample sizes, the error should typically be small.
			
			Second, a more rigorous technique is to use some form of interpolation.
			For example, the Hettmansperger-Sheather result uses linear interpolation to handle arbitrary confidence levels.
			This technique was originally described here:
				Confidence Interval Based on Interpolated Order Statistics,
				Hettmansperger, T. P., and Sheather, S. J.
				Statistical Probability Letters, 4: 75–79 1986
			and some web references are:
				http://www.itl.nist.gov/div898/software/dataplot/refman1/auxillar/mediancl.htm
				support.spss.com/Student/Documentation/Algorithms/14.0/errorbars.pdf
			This article claims that linear interpolation is the best that can do:
				Interpolated Nonparametric Prediction Intervals and Confidence Intervals
				Rudolf Beran, Peter Hall
				Journal of the Royal Statistical Society. Series B (Methodological), Vol. 55, No. 3 (1993), pp. 643-652
				http://links.jstor.org/sici?sici=0035-9246%281993%2955%3A3%3C643%3AINPIAC%3E2.0.CO%3B2-W&size=LARGE&origin=JSTOR-enlargePage
			and this one also advocates it:
				Nonparametric Ranked-set Sampling Confidence Intervals for Quantiles of a Finite Population
				Jayant V. Deshpande, Jesse Frey and Omer Ozturk
				http://www.springerlink.com/content/h067ml43p62r8145/
			See also this article:
				http://www.springerlink.com/content/t501k266761l5461/
			WARNING: if use these interpolation techniques, then the generality of the result is slightly reduced
			(there IS now some distributional dependence):
				"Confidence intervals for the population median based on interpolating adjacent order statistics are presented. They are shown to depend only slightly on the underlying distribution. A simple, nonlinear interpolation formula is given which works well for a broad collection of underlying distributions."
				http://stinet.dtic.mil/oai/oai?verb=getRecord&metadataPrefix=html&identifier=ADA152607
			
			In addition to the above binomial result, there is:
				"... a method given by Wilcox (see Reference below) on page 87, is based on the Maritz-Jarrett estimate of the standard error for a quantile"
				http://www.itl.nist.gov/div898/software/dataplot/refman1/auxillar/mediancl.htm
				
				a better way than the binomial or bootstrap using smoothing:
				http://www.informaworld.com/smpp/content~content=a780357685~db=all~jumptype=rss
				
				this old paper; gives results for point and interval of mean, median and mode of log normal:
				http://links.jstor.org/sici?sici=0006-3444%28197912%2966%3A3%3C567%3ACEOMOL%3E2.0.CO%3B2-W&size=LARGE&origin=JSTOR-enlargePage
				
				a novel way of using median CIs to obtain robust estimates:
				http://web.informatik.uni-bonn.de/IV/strelen/Lehre/Veranstaltungen/sim/Folien/24ASTC.pdf
			* </pre>
			*/
			@Override public Estimate getMedianEst(double[] sample, double confidenceLevel) {
				if (sample.length < 71) throw new IllegalArgumentException("sample.length = " + sample.length + " < 71, which is a problem for the current implementation");
				Check.arg().equals(confidenceLevel, 0.95);
				
					// The simple algorithm below comes from perfeval.epfl.ch/printMe/conf-1.ppt
					// (see p. 13 the n >= 71 result on the bottom of the page).
					// If want results for a broader range of sample.length and/or confidenceLevel (and eliminate the arg checks above),
					// then will need to use a search algorithm which starts with lower = 1 and upper = sample.length - 1
					// and alternates between incrementing lower and decrementing upper until confidenceLevel fails to be reached
					// (using the confidence level formula from perfeval.epfl.ch/printMe/conf-1.ppt p. 12);
					// then would need to use the last valid lower and upper values.
				double n = sample.length;
				double nRoot = Math.sqrt(n);
				int lower = (int) Math.floor( (0.5 * n) - (0.980 * nRoot) );
				int upper = (int) Math.ceil( (0.5 * n) + 1 + (0.980 * nRoot) );
				double[] sampleSorted = sample.clone();	// must make a clone since do not want the sort side effect to affect the caller
				Arrays.sort(sampleSorted);
				return new Estimate(getMedian(), sampleSorted[lower], sampleSorted[upper], confidenceLevel);
			}
			
			/** Returns a uniform random pick from the open interval (0, 1). */
			protected double random01(MersenneTwisterFast random) {
				double u = random.nextDouble();	// nextDouble returns from [0, 1), which unfortunately includes 0
				while (u == 0) { u = random.nextDouble(); }	// ensure u != 0
				return u;
			}
			
		}
		
		/**
		* Implements the standard gaussian distribution (i.e. mean = 0, sd = 1).
		* <p>
		* This class is multithread safe: it is stateless.
		* <p>
		* @see <a href="http://en.wikipedia.org/wiki/Normal_distribution">wikipedia article</a>
		*/
		private static class GaussianStandard extends DistributionAbstract {
			
			private GaussianStandard() {}
			
			public String getName() { return "GaussianStandard"; }
			
			@Override public double[] generateSample(int n) {
				double[] sample = new double[n];
				MersenneTwisterFast random = RandomUtil.get();
				for (int i = 0; i < sample.length; i++) {
					sample[i] = random.nextGaussian();
				}
				return sample;
			}
			
			@Override public double getMean() { return 0.0; }
			
			@Override public double getMedian() { return 0.0; }	// see http://en.wikipedia.org/wiki/Median#Medians_of_probability_distributions
			
			@Override public double getSd() { return 1.0; }
			
			/**
			* {@inheritDoc}
			* <p>
			* @see <a href="http://en.wikipedia.org/wiki/Confidence_level#Theoretical_example">this discussion</a>
			*/
			@Override public Estimate getMeanEst(double[] sample, double confidenceLevel) throws IllegalArgumentException {
				double meanSample = Math2.mean(sample);
				double sdSample = Math2.sd(sample);
				sdSample *= ((double) sample.length) / (sample.length - 1);	// must convert sd into the "divide by n - 1" form in order to use the t-distribution theory
				
				int degreesOfFreedom = sample.length - 1;
				double probabilityOneSided = (1 + confidenceLevel) / 2;
				double c = (new TDistribution(degreesOfFreedom)).inverse(probabilityOneSided);
				double ciDelta = c * sdSample / Math.sqrt(sample.length);
				double lower = meanSample - ciDelta;
				double upper = meanSample + ciDelta;
				
				return new Estimate(getMean(), lower, upper, confidenceLevel);
			}
			
			/**
			* {@inheritDoc}
			* <p>
			* @see <a href="http://www.tc3.edu/instruct/sbrown/stat/stdev1.htm#SigmaCI">Confidence Interval Estimate of Standard Deviation s</a>
			* @see <a href="http://www.selu.edu/Academics/Faculty/apearson/6-4ConfidenceIntervalsforVarianceandStandardDeviation.doc">Confidence Intervals for Variance and Standard Deviation</a>
			*/
			@Override public Estimate getSdEst(double[] sample, double confidenceLevel) throws IllegalArgumentException {
				double sdSample = Math2.sd(sample);
				sdSample *= ((double) sample.length) / (sample.length - 1);	// must convert sd into the "divide by n - 1" form in order to use the t-distribution theory
				
				int degreesOfFreedom = sample.length - 1;
				ChiSqrDistribution chiSqrDist = new ChiSqrDistribution(degreesOfFreedom);
				double alpha2 = (1 - confidenceLevel) / 2;
				double lower = sdSample * Math.sqrt( degreesOfFreedom / chiSqrDist.inverse(1 - alpha2) );
				double upper = sdSample * Math.sqrt( degreesOfFreedom / chiSqrDist.inverse(alpha2) );
				
				return new Estimate(getSd(), lower, upper, confidenceLevel);
			}
			
		}
		
		/**
		* Implements the standard Cauchy distribution (i.e. x0 = 0, gamma = 1).
		* <p>
		* This class is multithread safe: it is stateless.
		* <p>
		* @see <a href="http://en.wikipedia.org/wiki/Cauchy_distribution">wikipedia article</a>
		* @see <a href="http://www.itl.nist.gov/div898/handbook/eda/section3/eda3663.htm">NIST article</a>
		*/
		private static class CauchyStandard extends DistributionAbstract {
			
			private CauchyStandard() {}
			
			@Override public String getName() { return "CauchyStandard"; }
			
			@Override public double[] generateSample(int n) {
				double[] sample = new double[n];
				MersenneTwisterFast random = RandomUtil.get();
				for (int i = 0; i < sample.length; i++) {
					double p = random01(random);
					sample[i] = Math.tan( Math.PI * (p - 0.5) );	// see http://en.wikipedia.org/wiki/Cauchy_distribution#Cumulative_distribution_function
				}
				return sample;
			}
			
			@Override public double getMean() { return Double.NaN; }
			
			@Override public double getMedian() { return 0.0; }	// see http://en.wikipedia.org/wiki/Median#Medians_of_probability_distributions
			
			@Override public double getSd() { return Double.NaN; }
			
			@Override public Estimate getMeanEst(double[] sample, double confidenceLevel) {
				return new Estimate(getMean(), Double.NaN, Double.NaN, confidenceLevel);
			}
			
			@Override public Estimate getSdEst(double[] sample, double confidenceLevel) {
				return new Estimate(getSd(), Double.NaN, Double.NaN, confidenceLevel);
			}
			
// +++ but CAN use the median absolute deviation (MAD) to estimate a Cuachy's scale parameter: http://en.wikipedia.org/wiki/Median_absolute_deviation
			
		}
		
		/**
		* Implements the standard exponential distribution (i.e. lambda = 1).
		* <p>
		* This class is multithread safe: it is stateless.
		* <p>
		* @see <a href="http://en.wikipedia.org/wiki/Exponential_distribution">wikipedia article</a>
		* @see <a href="http://www.itl.nist.gov/div898/handbook/eda/section3/eda3667.htm">NIST article</a>
		*/
		private static class ExponentialStandard extends DistributionAbstract {
			
			private ExponentialStandard() {}
			
			public String getName() { return "ExponentialStandard"; }
			
			@Override public double[] generateSample(int n) {
				double[] sample = new double[n];
				MersenneTwisterFast random = RandomUtil.get();
				for (int i = 0; i < sample.length; i++) {
					double u = random01(random);
					sample[i] = -Math.log( u );	// see http://en.wikipedia.org/wiki/Exponential_distribution#Generating_exponential_variates
				}
				return sample;
			}
			
			@Override public double getMean() { return 1; }
			
			@Override public double getMedian() { return Math.log(2); }	// see http://en.wikipedia.org/wiki/Median#Medians_of_probability_distributions
			
			@Override public double getSd() { return 1; }
			
			/**
			* {@inheritDoc}
			* <p>
			* See <a href="http://www.amstat.org/publications/jse/v9n1/elfessi.html">this discussion</a>
			* (the solution is in the middle of the page;
			* the original solution is claimed to be found in Kapur, K. C. and Lamberson, L. R. (1977), Reliability in Engineering Design, New York: John Wiley & Sons, Inc.);
			* the beginning of this article also discusses at what the best estimators for the mean of the exp dist are).
			*/
			@Override public Estimate getMeanEst(double[] sample, double confidenceLevel) {
				double cTerm = (1 - confidenceLevel) / 2;
				double sumTerm = 2 * Math2.sum(sample);
				
				int degreesOfFreedom = 2 * sample.length;
				ChiSqrDistribution chiSqrDist = new ChiSqrDistribution(degreesOfFreedom);
				
				double lower = chiSqrDist.inverse(cTerm) / sumTerm;
				double upper = chiSqrDist.inverse(1 - cTerm) / sumTerm;
				
				return new Estimate(getMean(), lower, upper, confidenceLevel);
			}
			
			@Override public Estimate getSdEst(double[] sample, double confidenceLevel) {
return new Estimate(getSd(), Double.NaN, Double.NaN, confidenceLevel);
/*
+++ failed to find any sd CI theory for the exponential distribution (or the gamma distribution, of which it is a subcase); see also:
	Approximate confidence interval for standard deviation of nonnormal distributions by Douglas G. Bonett
	http://www.sciencedirect.com/science?_ob=ArticleURL&_udi=B6V8V-4DNHH11-1&_user=10&_coverDate=02%2F10%2F2006&_rdoc=1&_fmt=&_orig=search&_sort=d&view=c&_acct=C000050221&_version=1&_urlVersion=0&_userid=10&md5=990fd20ee7a5cf2a1c843634fceb3284
	
	Robust Confidence Interval for a Ratio of Standard Deviations by Douglas G. Bonett
	http://apm.sagepub.com/cgi/reprint/30/5/432
*/
			}
			
		}
		
		
/*
+++ Are there other skewed distributions which have analytically known CIs for mean and sd?

CI for log-normal mean:
	http://cat.inist.fr/?aModele=afficheN&cpsidt=14376983
	http://psyphz.psych.wisc.edu/~shackman/zhou_SiM1997.pdf

Loh and Wu 1987 investigated BCa for skewed distributions, including t-distribution and weibull,
so maybe those distributions have exact CI results?  see also:
	http://www.doaj.org/doaj?func=abstract&recNo=6609&id=232497&q1=t&f1=all&b1=and&q2=&f2=all


+++ Here is a really powerful idea: artifically generate probability distributions!

Without any loss of generality, take the domain to be [0, 1].
Divide it up into n intervals.
Somehow (e.g. randomly) generate a non-negative value for each interval, say, taken from the range [0, 1].
Normalize each value so that their sum totals 1, making this a proper probability distribution.
By using a fine enough set of intervals, you can simulate any probability distribution that you want:
gaussian, exponential, Cauchy, multi-peaked, fully random, etc.

Probably the most practical thing to simulate, however, is not something
either approximating a known parametric distribution (since these can be handled with code like above)
nor something truly random (since crazy stuff like that never happens in reality?)
but is something which generates "sane" distributions:
	--tail behavior could be truncated (e.g. like how exponential dist has no left tail)
	or some falloff (exp or power law)
	--the main region would be singly peaked, so the pdf is monotonically increasing up to a single max, and then monotonically decreasing
	--to realize the above, one possibility is to randomly pick values but impose serial correlation constraints on the randomness
*/
		
		
		// -------------------- CoverageTask, CoverageResult (static inner class) --------------------
		
		/** This class is NOT multithread safe: it expects to only be touched by a single thread. */
		private static class CoverageTask implements Runnable {
			
			private final Distribution distribution;
			private final int sampleLength;
			private final int numberResamples;
			private final double confidenceLevel;
			private final Estimator[] estimators;
			private final CoverageResult coverageResult;
			
			private CoverageTask(Distribution distribution, int sampleLength, int numberResamples, double confidenceLevel, Estimator[] estimators, CoverageResult coverageResult) {
				this.distribution = distribution;
				this.sampleLength = sampleLength;
				this.numberResamples = numberResamples;
				this.confidenceLevel = confidenceLevel;
				this.estimators = estimators;
				this.coverageResult = coverageResult;
			}
			
			private boolean equalParams(CoverageTask other) {
				return
					(this.distribution.equals(other.distribution)) &&
					(this.sampleLength == other.sampleLength) &&
					(this.numberResamples == other.numberResamples) &&
					(this.confidenceLevel == other.confidenceLevel) &&
					(this.estimators == other.estimators) &&
					(this.coverageResult.equals(other.coverageResult));
			}
			
			public void run() {
				try {
					double[] sample = distribution.generateSample(sampleLength);
					Bootstrap bootstrap = new Bootstrap( sample, numberResamples, confidenceLevel, estimators );
					coverageResult.include(this, bootstrap.estimatorToEstimate);
				}
				catch (Throwable t) {
					System.err.println();
					t.printStackTrace(System.err);
				}
			}
			
		}
		
		/**
		* Accumulates the results of running many individual {@link CoverageTask}s.
		* Using an instance of this class allows you to avoid having to retain references
		* to all the CoverageTasks that you would otherwise have to do if want sum up their results
		* once all have finished executing.
		* Since this instance uses little memory, while there may be huge numbers of CoverageTasks,
		* this is a big memory savings.
		* <p>
		* This class is multithread safe: every method is synchronized.
		*/
		private static class CoverageResult {
			
			private final Map<Estimator,Metrics> estimatorToMetrics = new HashMap<Estimator,Metrics>();
			
			private final String prefix;
			
			private CoverageTask taskFirst;
			
			private CoverageResult(String prefix) {
				this.prefix = prefix;
			}
			
			private synchronized void include(CoverageTask task, Map<Estimator,Estimate> estimatorToEstimate) throws IllegalArgumentException {
					// lazy initialize taskFirst if necessary, then confirm that all subsequent the tasks have the same params, as the remaining code below assumes this:
				if (taskFirst == null) taskFirst = task;
				else if (!task.equalParams(taskFirst)) throw new IllegalArgumentException("task has different parameters than taskFirst");
				
				for (Estimator estimator : task.estimators) {
					Estimate estimate = estimatorToEstimate.get(estimator);
					double valueTrue = getValueTrue(estimator, task.distribution);
					getMetrics(estimator).process(estimate, valueTrue);
				}
			}
			
			private synchronized double getValueTrue(Estimator estimator, Distribution distribution) throws IllegalStateException {
				if (estimator instanceof EstimatorMean) return distribution.getMean();
				else if (estimator instanceof EstimatorMedian) return distribution.getMedian();
				else if (estimator instanceof EstimatorSd) return distribution.getSd();
				else throw new IllegalStateException("estimator = " + estimator + " has no analogous method in distribution = " + distribution);
			}
			
			private synchronized Metrics getMetrics(Estimator estimator) {
				Metrics metrics = estimatorToMetrics.get(estimator);
				if (metrics == null) {
					metrics = new Metrics();
					estimatorToMetrics.put(estimator, metrics);
				}
				return metrics;
			}
			
			private synchronized String getAnswer() {
				StringBuilder sb = new StringBuilder(1024);
				for (Estimator estimator : taskFirst.estimators) {
					if (sb.length() > 0) sb.append("\n");
					Metrics metrics = estimatorToMetrics.get(estimator);
					sb.append(prefix + estimator.getName() + ": " + metrics.toString());
				}
				return sb.toString();
			}
			
			/** This class is NOT multithread safe: it expects its enclosing class to guard access to it. */
			private static class Metrics {
				
				private int countAll = 0;
				private int countNaN = 0;
				private int countLess = 0;
				private int countIn = 0;
				private int countGreater = 0;
				
				private Metrics() {}
				
				private void process(Estimate estimate, double valueTrue) {
					++countAll;
					
					if (Double.isNaN(valueTrue)) ++countNaN;
					else if (valueTrue < estimate.getLower()) ++countLess;
					else if (valueTrue <= estimate.getUpper()) ++countIn;
					else ++countGreater;
				}
				
				private double fracLess() { return ((double) countLess) / countAll; }
				private double fracIn() { return ((double) countIn) / countAll; }
				private double fracGreater() { return ((double) countGreater) / countAll; }
				
				@Override public String toString() {
					if (countNaN == 0) return "in = " + fracIn() + " (less = " + fracLess() + ", greater = " + fracGreater() + ")";
					else return "UNDEFINED: " + toPercent(((double) countNaN) / countAll) + " of the true values are NaN";
				}
			}
			
		}
		
		// -------------------- CiTask, CiResult (static inner class) --------------------
		
		/** This class is NOT multithread safe: it expects to only be touched by a single thread. */
		private static class CiTask implements Runnable {
			
			private final Distribution distribution;
			private final int sampleLength;
			private final int numberResamples;
			private final double confidenceLevel;
			private final Estimator[] estimators;
			private final CiResult ciResult;
			
			private CiTask(Distribution distribution, int sampleLength, int numberResamples, double confidenceLevel, Estimator[] estimators, CiResult ciResult) {
				this.distribution = distribution;
				this.sampleLength = sampleLength;
				this.numberResamples = numberResamples;
				this.confidenceLevel = confidenceLevel;
				this.estimators = estimators;
				this.ciResult = ciResult;
			}
			
			private boolean equalParams(CiTask other) {
				return
					(this.distribution.equals(other.distribution)) &&
					(this.sampleLength == other.sampleLength) &&
					(this.numberResamples == other.numberResamples) &&
					(this.confidenceLevel == other.confidenceLevel) &&
					(this.estimators == other.estimators) &&
					(this.ciResult.equals(other.ciResult));
			}
			
			public void run() {
				try {
					double[] sample = distribution.generateSample(sampleLength);
					
					Bootstrap bootstrap = new Bootstrap( sample, numberResamples, confidenceLevel, estimators );
					Map<Estimator,Estimate> resultsBs = bootstrap.estimatorToEstimate;
					
					Map<Estimator,Estimate> resultsTheory = new HashMap<Estimator,Estimate>();
					for (Estimator estimator : estimators) {
						resultsTheory.put( estimator, getEstimateTheory(estimator, sample) );
					}
					
					ciResult.include(this, resultsBs, resultsTheory);
				}
				catch (Throwable t) {
					System.err.println();
					t.printStackTrace(System.err);
				}
			}
			
			private Estimate getEstimateTheory(Estimator estimator, double[] sample) throws IllegalStateException {
				if (estimator instanceof EstimatorMean) return distribution.getMeanEst(sample, confidenceLevel);
				else if (estimator instanceof EstimatorMedian) return distribution.getMedianEst(sample, confidenceLevel);
				else if (estimator instanceof EstimatorSd) return distribution.getSdEst(sample, confidenceLevel);
				else throw new IllegalStateException("estimator = " + estimator + " has no analogous method in distribution = " + distribution);
			}
			
		}
		
		/**
		* Accumulates the results of running many individual {@link CiTask}s.
		* Using an instance of this class allows you to avoid having to retain references
		* to all the CiTasks that you would otherwise have to do if want sum up their results
		* once all have finished executing.
		* Since this instance uses little memory, while there may be huge numbers of CiTasks,
		* this is a big memory savings.
		* <p>
		* This class is multithread safe: every method is synchronized.
		*/
		private static class CiResult {
			
			private final Map<Estimator,Metrics> estimatorToMetrics = new HashMap<Estimator,Metrics>();
			
			private final String prefix;
			
			private CiTask taskFirst;
			
			private CiResult(String prefix) {
				this.prefix = prefix;
			}
			
			private synchronized void include(CiTask task, Map<Estimator,Estimate> resultsBs, Map<Estimator,Estimate> resultsTheory) throws IllegalArgumentException {
					// lazy initialize taskFirst if necessary, then confirm that all subsequent the tasks have the same params, as the remaining code below assumes this:
				if (taskFirst == null) taskFirst = task;
				else if (!task.equalParams(taskFirst)) throw new IllegalArgumentException("task has different parameters than taskFirst");
				
				for (Estimator estimator : task.estimators) {
					Metrics metrics = getMetrics(estimator);
					Estimate estBs = resultsBs.get(estimator);
					Estimate estTheory = resultsTheory.get(estimator);
					metrics.process(estBs, estTheory);
				}
			}
			
			private synchronized Metrics getMetrics(Estimator estimator) {
				Metrics metrics = estimatorToMetrics.get(estimator);
				if (metrics == null) {
					metrics = new Metrics();
					estimatorToMetrics.put(estimator, metrics);
				}
				return metrics;
			}
			
			private synchronized String getAnswer() {
				StringBuilder sb = new StringBuilder(1024);
				for (Estimator estimator : taskFirst.estimators) {
					if (sb.length() > 0) sb.append("\n");
					Metrics metrics = estimatorToMetrics.get(estimator);
					sb.append(prefix + estimator.getName() + ": " + metrics.toString());
				}
				return sb.toString();
			}
			
			/** This class is NOT multithread safe: it expects its enclosing class to guard access to it. */
			private static class Metrics {
				
				private int countAll = 0;
				private double bsInTheory = 0;
				private double theoryInBs = 0;
				
				private Metrics() {}
				
				private void process(Estimate estBs, Estimate estTheory) {
					++countAll;
					
					double lowerBs = estBs.getLower();
					double upperBs = estBs.getUpper();
					double lowerTheory = estTheory.getLower();
					double upperTheory = estTheory.getUpper();
					if (Double.isNaN(lowerTheory)) {
						assert Double.isNaN(upperTheory) : "lowerTheory is NaN, but upperTheory = " + upperTheory + " is not";
						bsInTheory = Double.NaN;
						theoryInBs = Double.NaN;
						return;
					}
					
					double lengthBs = upperBs - lowerBs;
					double lengthTheory = upperTheory - lowerTheory;
					double lowerBoth = Math.max(lowerBs, lowerTheory);
					double upperBoth = Math.min(upperBs, upperTheory);
					double lengthBoth = upperBoth - lowerBoth;
					bsInTheory += lengthBoth / lengthBs;
					theoryInBs += lengthBoth / lengthTheory;
				}
				
				private double fracBsInTheory() { return bsInTheory / countAll; }
				private double fracTheoryInBs() { return theoryInBs / countAll; }
				
				@Override public String toString() {
					if (Double.isNaN(fracTheoryInBs())) {
						assert Double.isNaN(fracBsInTheory()) : "fracTheoryInBs() returns NaN, but fracBsInTheory() returns " + fracBsInTheory() + " which is not NaN";
						return "--UNDEFINED: the theory CI has NaN bounds, so cannot compare it with the bootstrap CI";
					}
					else if ((fracBsInTheory() >= 0.75) && (fracTheoryInBs() >= 0.75)) {
						return "--good: on average, " + toPercent(fracBsInTheory()) + " of the bootstrap CI lies inside the theory CI, and " + toPercent(fracTheoryInBs()) + " of the theory CI lies inside the bootstrap CI";
					}
					else if ((fracBsInTheory() >= 0.5) && (fracTheoryInBs() >= 0.5)) {
						return "--Fair: on average, " + toPercent(fracBsInTheory()) + " of the bootstrap CI lies inside the theory CI, and " + toPercent(fracTheoryInBs()) + " of the theory CI lies inside the bootstrap CI";
					}
					else if ((fracBsInTheory() >= 0.25) && (fracTheoryInBs() >= 0.25)) {
						return "--POOR: on average, only " + toPercent(fracBsInTheory()) + " of the bootstrap CI lies inside the theory CI, and only " + toPercent(fracTheoryInBs()) + " of the theory CI lies inside the bootstrap CI";
					}
					else if ((fracBsInTheory() >= 0.0) && (fracTheoryInBs() >= 0.0)) {
						return "--BAD: on average, only " + toPercent(fracBsInTheory()) + " of the bootstrap CI lies inside the theory CI, and only " + toPercent(fracTheoryInBs()) + " of the theory CI lies inside the bootstrap CI";
					}
					else {
						return "--DISASTER: the bootstrap CI NEVER HAS ANY OVERLAP WITH THE theory CI";
					}
				}
				
			}
			
		}
		
	}
	
	
}


