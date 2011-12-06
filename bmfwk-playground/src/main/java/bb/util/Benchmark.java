/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ there is a big problem if you want to benchmark multiple things in a single JVM session: previous JVM history can color the results
	--for example, if you do a call to Benchmark for some class that initially has a data set (e.g. a specific array size),
		then hotspot will gather all of its profiling data for that configuration,
		and will then hard code those assumptions into its "optimized" inlined native code that is likely to never change in the future.
		This is bad if you subsequently call Benchmark on a version of that class with a different data set.
	--this would be totally curable of you could tell hotspot to flush all of its native code and start interpreting again
		--I emailed Cliff Click about this on 2009-11-23, and he said that there currently is no provision for this
	--work arounds:
		1) have each JVM session do a single benchmark
			--perhaps have a shell script test harness that, say, increments the data size for each run
			--pros: this approach is guaranteed to always give the best results
			--cons: sheln script languages, especially dos bat file, can be nasty
		2) use a single JVM session, but do a complex warmup strategy: cycle thru each benchmark task,
		executing it exactly once, then repeat the complete cycle until all of the tasks have a stable execution time.
		Then the optimizer should make a decision based on input from all the tasks.
		Cliff Click in his 2009-11-23 email response to me said that Brian Goetz used this approach.
			--pros: can do it all in one JVM session
			--cons: requires much more complicated coding (altho I COULD modify this class to support varags of tasks...)
			--main reason to reject: the answer that you get is not a pure (best case) result for each task
		3) have the initial JVM create a child process for each benchmark
			--pros: in theory, this would combine the best aspects of both approaches above
			--OPEN question: how you can send the Benchmark code and other stuff like the task to that new Process?
				--this might work:
					--have the new process execute a Java class which initially tries to read a serialized object from its std in
					--in the parent process, send a serialized version of the benchmarking code to that child process
					--then have the child process send a serialized version of all the results back to the parent process
					--problems:
						--how can you give the child process the parent's classpath?  (I think that this is either inheritable and/or configurable, so no worries)
						--if the child process communicates results back via serializing an object onto its std out,
							then might not that get confused with calls to Sysstem.out and err from the tasks's code?
						--all the involved classes must be serializable, which is not only an annoying requirement, but may be an impossible one (because third party libararies are being used)

+++ need to add a statistical test that detects if the measurements are multimodal (i.e. clustered), since that would be very bad
	--multimode detection is definitely needed for the problem that Josh Bloch describes below

+++ use the Formatter class or PrintWriter.printf to print integers with comma separators
and/or eliminate my toEngineering calls and use scientific notation
	--reason: those SI prefixes are convenient for people to read, but a pain for computers (e.g. spreadsheets)
	--see notes at start of FormatUtil

+++ should my outliers warning only be given if removing the outliers actually makes a difference in the statistics?
	--WITH MANY OF THE OUTLIER WARNINGS, IT LOOKS LIKE THEIR PRESENCE DOES NOT AFFECT THE MEAN AND SD CONCLUSIONS MUCH AT ALL

+++ the current implementation of this class is geared toward's handling Sun's hotspot JVM; how well does it work for other's (e.g. IBM, JRocket)?
	--probably OK if their JIT lifecycle is similar to Sun's?

--be aware that sometimes you get significantly different benchmark results from JVM run to JVM run
	--see Josh Bloch's talk:
		http://wiki.jvmlangsummit.com/MindTheSemanticGap
	--he attributes the cause as random inlining judgements being made by hotspot, with one set of inlining being much better than another; is non-deterministic because multi threads are involved

--IT IS POSSIBLE TO GET ASSEMBLY LISTINGS AND EXECUTION TIMINGS OF WHAT YOUR JAVA CODE GETS TURNED INTO:
	--see Figure 2 of this article
		http://www.infoq.com/articles/scalable-java-components
	--the lead author of that article, Zhi Gan, emailed me how he generated that figure:
		We are using "Performance Inspector" and VPA to generate the ASM view in the article.
		Both of them can be downloaded for free from http://perfinsp.sourceforge.net/  and http://www.alphaworks.ibm.com/tech/vpa

--notes concerning timing measurements:
	--the UnitTest inner class at the end of this has these methods
		benchmark_System_currentTimeMillis
		benchmark_System_nanoTime
	which will benchmark those System time measuring methods; you may want to run them on your own machine
	--System.currentTimeMillis web references:
		http://mindprod.com/jgloss/time.html#ACCURACY
	--System.nanoTime web references:
		http://www.simongbrown.com/blog/2007/08/20/millisecond_accuracy_in_java.html
		http://forum.java.sun.com/thread.jspa?threadID=5119176&messageID=9413087
		http://forum.java.sun.com/thread.jspa?threadID=730138&messageID=4202194
		http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6440250
		http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6519418
		http://blogs.sun.com/dholmes/entry/inside_the_hotspot_vm_clocks
	--Obtaining Accurate Timestamps under Windows XP:
		http://www.meinberg.de/english/sw/ntp.htm
		http://www.lochan.org/2005/keith-cl/useful/win32time.html
		http://support.microsoft.com/kb/307897
		http://support.microsoft.com/kb/314054#EXTERNAL
		http://www.timetools.co.uk/support/windows-xp-ntp-time-server.htm
	--NTP servers:
		try using a country specific pool server first:
			http://www.pool.ntp.org/
			http://support.ntp.org/bin/view/Servers/NTPPoolServers
		fallback on a stratum 1 server:
			http://support.ntp.org/bin/view/Servers/StratumOneTimeServers
	--config done on 2007/9/15 on my windoze laptop:
		1) edited my registry as decribed in http://support.microsoft.com/kb/314054#EXTERNAL
		2) for the ntp servers required by
			HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\W32Time\Parameters\NtpServer
		I used this:
			0.pool.ntp.org,1.pool.ntp.org,2.pool.ntp.org,time-b.nist.gov,0x1
		I would have liked to have used one of the regional or country servers for lower latency,
		but with all my frequent travelling, that would mean constantly editing the registry
		(and having to remember to do this in the first place)
		
		WARNING: to my horror, I have found that if open the Internet Time tab of the Date and Time control Panel,
		then it resets the value of
			HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Services\W32Time\Parameters\NtpServer
		to just the list of time servers that are available in that tab's pull down menu
		(for some reason, it does not seem to get the full list from the above registry value)
		SO NEVER OPEN THAT TAB, ELSE WILL NEED TO EDIT THE REGISTRY AGAIN...
	--disable Intel's SpeedStep during all benchmarking:
		"Under Microsoft Windows XP, SpeedStep support is built into the power management console under the control panel.
		In Windows XP a user can regulate the processor's speed indirectly by changing power schemes.
		The "Home/Office Desk" disables SpeedStep..."
		http://en.wikipedia.org/wiki/SpeedStep#Operating_system_support

--other people's benchmarking code:
	https://japex.dev.java.net/
		seems to be a new framework; supports fancy output like graphs
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6440250
		see the Evaluation section of this page
	http://cretesoft.com/archive/newsletter.do?issue=124&locale=en_US
		see near the middle of the page
	http://buytaert.net/statistically-rigorous-java-performance-evaluation
		blog about an OOPSLA 2007 paper
	http://www.itkovian.net/base/phd-obtained/
		relevant PhD thesis
	http://www.azulsystems.com/events/javaone_2002/microbenchmarks.pdf
		has a great example in the middle of how microbenchmarks are devilish to do in hotspot because of complex nature

--interesting paper "Java Performance Evaluation through Rigorous Replay Compilation"
	http://escher.elis.ugent.be/publ/Edocs/DOC/P108_138.pdf
*/

package bb.util;

import bb.io.ConsoleUtil;
import bb.science.Bootstrap;
import bb.science.FormatUtil;
import bb.science.Lfsr;
import bb.science.Math2;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.Assert;
import org.junit.Test;

/**
* Benchmarks the performance of a "task",
* defined here as arbitrary Java code that is contained inside either a {@link Callable} or {@link Runnable}.
* <i>The task may be anything from a microbenchmark to a full blown application.</i>
* This class implements all of the principles described in the two-part article
* <a href="http://www.ibm.com/developerworks/java/library/j-benchmark1.html">Robust Java benchmarking, Part 1: Issues</a>
* and <a href="http://www.ibm.com/developerworks/library/j-benchmark2/index.html">Robust Java benchmarking, Part 2: Statistics And solutions</a>.

* <h4>Philosophy</h4>

* The overall design goal was to make typical things easy to do, uncommon things possible to do, and bad things impossible to do.
* For example, as will be discussed in the usage section below,
* the choice was made to have the constructor do all the work,
* since that leads to the most simple and concise code.
* However, if something unusual needs to be done, such as changing one of the default measurement parameters,
* then that is possible too.
* <p>
* A related design goal was to automate as many aspects as possible,
* and minimize input from and interaction with the user.
* For example, this class tries to detect as many issues as possible
* (e.g. it checks if the measurements have outliers).

* <h4>Usage</h4>

* Most users of this class can perform reliable benchmarking simply by calling
* <code style="display: block; margin-left: 2em">System.out.println("My task: " + new Benchmark(task));</code>
* where task is either a <code>Callable</code> or <code>Runnable</code> created by the user to encapsulate the code to be measured.
* This single argument <code>Benchmark</code> constructor will carry out multiple execution time measurements
* and perform statistics calculations on them, so that upon return the new instance is populated with results.
* This code also makes an implicit call to {@link #toString toString} which will describe the essential statistics.
* <p>
* If custom tuning of the measurement parameters is required, then more complicated constructors may be used.
* <p>
* One simple tuning is if the task internally executes the same identical action many times,
* and you want statistics for that individual action (as opposed to the task as a whole).
* This is easily done with code like this:
* <code style="display: block; margin-left: 2em">System.out.println("My task: " + new Benchmark(task, n));</code>
* where <code>n</code> is the number of actions that you know task performs.
* See {@link Params#numberActions} for more discussion.
* <p>
* Another simple tuning is if the task should only be executed once.
* This is easily done with code like this:
* <code style="display: block; margin-left: 2em">System.out.println("My task: " + new Benchmark(task, false));</code>
* There will be no statistics reported in this case, just the single time measurement.
* See {@link Params#manyExecutions} for more discussion.
* <p>
* For uncommon situations, ultimate tunability over every measurement parameter
* may be achieved by constructing an instance of the {@link Params} inner class,
* modifying it as desired,
* and then directly supplying it along with task to the appropriate constructor.
* For example, here is how to configure <code>Benchmark</code> to measure CPU time:
*	<blockquote><code>
		Benchmark.Params params = new Benchmark.Params();<br/>
		params.setMeasureCpuTime(true);    // default is false (i.e. measure elapsed time)<br/>
		System.out.println("Some task's CPU time: " + new Benchmark(task, params));
*	</code></blockquote>

* <h4>Multiple benchmarks</h4>

* <b>If you need to perform multiple benchmarks, be careful about doing them all in a single JVM session.</b>
* A possible issue is that the JVM may make optimization decisions (branch predictions, memory layout, etc)
* that are appropriate for the first benchmark, but may be suboptimal for subsequent ones.
* Since there appears to be no way to programmatically tell the JVM
* to drop all of its optimizations and start profiling from scratch,
* the only alternative is to do every benchmark in a dedicated JVM session.
* The usual way to do this is to write a shell script which generates and runs all the benchmarking scenarios.
* The shell script files for the
* <a href="http://www.ellipticgroup.com/misc/projectArticle.zip">latest version of my benchmarking article code</a>
* now contain examples of how to do this.

* <h4>Result report</h4>

* In the examples above, <code>Benchmark</code>'s <code>toString</code> method was implicitly invoked to get a summary result report.
* However, if very detailed results are needed, then {@link #toStringFull toStringFull} may be used instead.
* Furthermore, various accessors are available if you need customized results.
* <p>
* Note that all the <code>toStringXXX</code> methods of this class, as well as its inner classes,
* have the property that regardless of whether or not the result contains multiple lines,
* the final char is never a newline.
* This means that the user should always call <code>println</code> on the results
* if a <code>PrintWriter</code> is being used for output.

* <h4>Interpretation of results</h4>

* The two main results reported by this class are
* <a href="http://en.wikipedia.org/wiki/Point_estimation">point estimates</a>
* for the <a href="http://en.wikipedia.org/wiki/Arithmetic_mean">arithmetic mean</a>
* and <a href="http://en.wikipedia.org/wiki/Standard_deviation">standard deviation</a> (sd)
* of a task's execution time.
* <p>
* Another important concern is the accuracy of those estimates,
* since inaccurate results, obviously, limit the conclusions that can be drawn
* (e.g. that one task executes faster than another).
* This class determines the accuracy of its estimates by producing
* <a href="http://en.wikipedia.org/wiki/Confidence_interval">confidence intervals</a>
* for both the mean and the sd.

* <h4>Blocks versus actions</h4>

* There are many places in the documentation below where I use the terms
* <i>block</i> and <i>action</i>.
* These terms are fully defined in the <i>Block statistics versus action statistics</i> section
* of the <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a>.

* <h4>Subclassability</h4>

* This class was designed to maximize subclass opportunities.
* For instance, all members--including those of its inner classes--are public or protected, and are never final.
* Also, the methods are fine grained to enable selective overriding.

* <h4>Multithread safety</h4>

* This class is not multithread safe.

* <h4>Alternatives</h4>

* See also {@link Execute}.

* <p>
* @author Brent Boyer
*/
public class Benchmark {
	
	// -------------------- static fields --------------------
	
	/**
	* Task used to estimate the execution time noise floor.
	* <p>
	* Must have minimal "true" execution time variation.
	* Instead, should only be subject to external noise
	* (e.g. from operating system context switches and/or time measurement errors).
	* So, <i>in a perfect environment, this task should always have the same execution time</i> (after some JVM warmup phase).
	* Also, desire the task's steady state execution time to be in a reasonable range, say between 1 ms and 1 s.
	* <p>
	* Perhaps one way to write a low noise task is if it satisfies these properties:
	* <ol>
	*  <li>is entirely CPU bound</li>
	*  <li>corollary: has no synchronization, does no I/O</li>
	*  <li>uses no memory beyond the CPU's registers</li>
	*  <li>corollary: cannot cause any garbage collection</li>
	* </ol>
	* <p>
	* It is probably best if task repeatedly performs a very simple but irreducable computation.
	* This computation should not give the JIT compiler many oportunities for optimization.
	* It also must guarantee to always be performed (i.e. no dead-code elimination).
	* <p>
	* Contract: must satisfy the contract of {@link #task}.
	* Additionally, to meet the no garbage collection condition,
	* is restricted to being a <code>Runnable</code> whose run method should create no new objects.
	*/
	protected static Runnable noiseTask = new LfsrAdvancer();
	
	/**
	* Maps a <code>Params</code> instance
	* to the <i>block statistics</i>
	* that result from benchmarking {@link #noiseTask}.
	* In particular, if <code>params</code> is a key of this <code>Map</code>,
	* then <code>new Benchmark(noiseTask, params).{@link #statsBlock}</code>
	* is the value.
	* <p>
	* Contract: is never <code>null</code>.
	*/
	protected static Map<Params,Stats> noiseMap = new HashMap<Params,Stats>();
	
	// -------------------- instance fields --------------------
	
	/**
	* Packages the target code that will be benchmarked.
	* <p>
	* Contract: is never <code>null</code> and is always either a {@link Callable} or {@link Runnable} instance.
	* To ensure that the computation that it carries out is never be subject to
	* <a href="http://www.ibm.com/developerworks/java/library/j-benchmark1.html#dce">dead-code elimination</a> (DCE),
	* the following rules must be obeyed:
	* <ol>
	*  <li>if a <code>Callable</code>, then the computation done by call must be used to generate the result returned by <code>call</code></li>
	*  <li>
	*		if a <code>Runnable</code>, then the computation done by <code>run</code> must be stored in some internal state.
	*		The <code>toString</code> method must be reimplemented, overriding <code>Object</code>'s, and must use that state in its result.
	*  </li>
	* </ol>
	* If these rules are obeyed, then {@link #preventDce preventDce} should guarantee that DCE never occurs.
	*/
	protected Object task;
	
	/**
	* Controls how this instance's benchmarking is done.
	* <p>
	* Contract: is never <code>null</code>.
	*/
	protected Params params;
	
	/**
	* First execution time for {@link #task}.
	* In general, this value includes all initialization effects (e.g. class loading, hotspot warmup, etc).
	* <p>
	* <b>Warning:</b> the value is actually just the first measurement of task that was done by this <code>Benchmark</code> instance.
	* So, if other code in this same JVM session has previously executed task,
	* then this value will not, in fact, reflect initialization effects.
	* <p>
	* Units: seconds.
	* <p>
	* Contract: is >= 0, and is never NaN or infinite.
	*/
	protected double timeExecFirst;
	
	/**
	* Records how many executions of {@link #task} were performed for each time measurement.
	* <p>
	* Contract: is > 0.
	*/
	protected long numberExecutions;	// Note: for safety use a long; reason: int max value is ~2*10^9 and on modern multi GHZ cpus, an operation that takes on the order of 1 clock cycle could lead to numberExecutions exceeding 10^9
	
	/**
	* If {@link #params}.{@link Params#getManyExecutions getManyExecutions} returns <code>true</code>,
	* then this instance performed multiple execution time measurements and stored the results here.
	* The values are only those from the steady state execution profile stage, and do not include the JVM warmup runs.
	* Each measurement is over {@link #numberExecutions} of {@link #task},
	* and is stored in this array in the order that it occurred (i.e. this array is not sorted).
	* <p>
	* Contract: is <code>null</code> if <code>params.getManyExecutions()</code> returns <code>false</code>, else is non-<code>null</code> with length = <code>params.{@link Params#getNumberMeasurements}</code>.
	*/
	protected Measurement[] measurements;
	
	/**
	* If <code>task</code> is a {@link Callable}, then records the last result of executing <code>task.call()</code>.
	* <p>
	* This is used to prevent dead-code elimination; see {@link #task} and {@link #preventDce preventDce}.
	* <p>
	* Contract: is always <code>null</code> if <code>task</code> is a {@link Runnable}; otherwise, may be anything.
	*/
	protected Object callResult;
	
	/**
	* Records any issues that were found while doing a final JVM clean after all measurements were carried out.
	* <p>
	* Contract: is either <code>null</code> if there are no issues, else is non blank.
	*/
	protected String cleanIssues;
	
	/**
	* Records any outlier issues that were found in the measurements.
	* <p>
	* Contract: is either <code>null</code> if there are no issues, else is non blank.
	*/
	protected String outlierIssues;
	
	/**
	* Records any serial correlation issues that were found in the measurements.
	* <p>
	* Contract: is either <code>null</code> if there are no issues, else is non blank.
	*/
	protected String serialCorrelationIssues;
	
	/**
	* If {@link #params}.{@link Params#getManyExecutions} returns true,
	* holds the <i>block statistics</i>.
	* In this case, what actually is measured is a block of {@link #numberExecutions} calls of task.
	* The statistics of these measurements are stored here.
	* <p>
	* Contract: is <code>null</code> if <code>params.getManyExecutions()</code> returns <code>false</code>, else is non-<code>null</code>.
	*/
	protected Stats statsBlock;
	
	/**
	* If {@link #params}.{@link Params#getManyExecutions} returns true,
	* holds the <i>action statistics</i>.
	* These are always calculated from {@link #statsBlock} and are never directly measured
	* (altho, if there is but 1 action per measurement, then this instance is the same as <code>statsBlock</code>).
	* <p>
	* Contract: is <code>null</code> if <code>params.getManyExecutions()</code> returns <code>false</code>, else is non-<code>null</code>.
	*/
	protected Stats statsAction;
	
	// -------------------- constructors --------------------
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Callable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params()})</code>. */
	public <T> Benchmark(Callable<T> task) throws IllegalArgumentException, IllegalStateException, Exception {
		this(task, new Params());
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Callable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(boolean) Params}(manyExecutions))</code>. */
	public <T> Benchmark(Callable<T> task, boolean manyExecutions) throws IllegalArgumentException, IllegalStateException, Exception {
		this(task, new Params(manyExecutions));
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Callable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(long) Params}(numberActions))</code>. */
	public <T> Benchmark(Callable<T> task, long numberActions) throws IllegalArgumentException, IllegalStateException, Exception {
		this(task, new Params(numberActions));
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Callable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(boolean, long) Params}(manyExecutions, numberActions))</code>. */
	public <T> Benchmark(Callable<T> task, boolean manyExecutions, long numberActions) throws IllegalArgumentException, IllegalStateException, Exception {
		this(task, new Params(manyExecutions, numberActions));
	}
	
	/**
	* Constructor that measures the execution time of a Callable task.
	* Simply calls <code>{@link #perform perform}(task, params)</code>.
	* So, when this constructor finishes, it is fully populated with results.
	* <p>
	* @param task the Callable whose <code>call</code> method's execution time will be measured; is assigned to the {@link #task} field
	* @param params assigned to the {@link #params} field
	* @throws IllegalArgumentException if <code>task == null</code>; <code>params == null</code>
	* @throws IllegalStateException if some problem is detected
	* @throws Exception (or some subclass) if <code>task.call</code> throws it
	*/
	public <T> Benchmark(Callable<T> task, Params params) throws IllegalArgumentException, IllegalStateException, Exception {
		perform(task, params);
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Runnable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params()})</code>. */
	public Benchmark(Runnable task) throws IllegalArgumentException, IllegalStateException {
		this(task, new Params());
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Runnable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(boolean) Params}(manyExecutions))</code>. */
	public Benchmark(Runnable task, boolean manyExecutions) throws IllegalArgumentException, IllegalStateException {
		this(task, new Params(manyExecutions));
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Runnable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(long) Params}(numberActions))</code>. */
	public Benchmark(Runnable task, long numberActions) throws IllegalArgumentException, IllegalStateException {
		this(task, new Params(numberActions));
	}
	
	/** Convenience constructor that simply calls <code>{@link #Benchmark(Runnable, Params) this}(task, new {@link Benchmark.Params#Benchmark.Params(boolean, long) Params}(manyExecutions, numberActions))</code>. */
	public Benchmark(Runnable task, boolean manyExecutions, long numberActions) throws IllegalArgumentException, IllegalStateException {
		this(task, new Params(manyExecutions, numberActions));
	}
	
	/**
	* Identical to the {@link #Benchmark(Callable, Params) Callable constructor} except that <code>task</code> is a <code>Runnable</code>.
	* <p>
	* @param task the <code>Runnable</code> whose <code>run</code> method's execution time will be measured; is assigned to the {@link #task} field
	* @param params assigned to the {@link #params} field
	* @throws IllegalArgumentException if <code>task == null</code>; <code>params == null</code>
	* @throws IllegalStateException if some problem is detected
	* @throws RuntimeException (or some subclass) if some other problem occurs
	*/
	public Benchmark(Runnable task, Params params) throws IllegalArgumentException, IllegalStateException, RuntimeException {
		try {
			perform(task, params);
		}
		catch (RuntimeException re) {
			throw re;
		}
		catch (Exception e) {
			throw new RuntimeException("A checked Exception was caught (see cause)--BUT THIS IS STRANGE: it should never happen for a Runnable task", e);
		}
	}
	
	/**
	* No-arg constructor.
	* <p>
	* Allows instance creation without causing a benchmark.
	* For example, subclasses may wish to not have the constructor automatically carry out benchmarking.
	* Another use is to test instance methods of this class without doing a benchmark first
	* (e.g. see {@link UnitTest#test_diagnoseSerialCorrelation UnitTest.test_diagnoseSerialCorrelation}).
	*/
	protected Benchmark() {}
	
	// -------------------- perform and helper methods --------------------
	
	/**
	* Carries out the benchmarking of <code>task</code>
	* according to the specifications found in <code>params</code>.
	* <p>
	* @param task either a <code>Callable</code> or a <code>Runnable</code>; packages the target code that will be benchmarked; is assigned to the {@link #task} field
	* @param params assigned to the {@link #params} field
	* @throws IllegalArgumentException if <code>task == null</code>;
	* <code>task</code> is neither a <code>Callable</code> or <code>Runnable</code>;
	* <code>params == null</code>
	* @throws IllegalStateException if some problem is detected
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected void perform(Object task, Params params) throws IllegalArgumentException, IllegalStateException, Exception {
		Check.arg().notNull(task);
		if (!(task instanceof Callable) && !(task instanceof Runnable)) throw new IllegalArgumentException("task is neither a Callable or Runnable");
		Check.arg().notNull(params);
		
		this.task = task;
		this.params = params;
		
		try {
			osSpecificPreparation();
			doMeasurementFirst();
			if (params.getManyExecutions()) {
				warmupJvm();
				determineNumberExecutions();
				doMeasurements();
				cleanJvmFinal();
				diagnoseOutliers();
				diagnoseSerialCorrelation();
				calculateStats();
			}
			else {
				cleanJvmFinal();
				numberExecutions = 1;
				measurements = null;
				statsBlock = null;
				statsAction = null;
			}
			checkState();
		}
		finally {
			clearUserMsgs();
		}
	}
	
	/**
	* Calls operating system specific commands to prepare for benchmarking.
	* <p>
	* The current implementation only does something if the operating system is determined to be from Microsoft.
	* In this case, it forces pending idle tasks to execute by running the command<br/>
	* &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>Rundll32.exe advapi32.dll,ProcessIdleTasks</code><br/>
	* <b>Warning:</b> this may take several minutes to complete, especially if this is the first time that it has been called in a while.
	* <p>
	* This method never throws an <code>Exception</code>.
	* If one occurs, it is caught and its stack trace is printed to <code>System.err</code>.
	*/
	protected void osSpecificPreparation() {
		try {
			sendUserMsg("osSpecificPreparation...");
			if (OsUtil.isMicrosoft()) {
//				sendUserMsg("executing Rundll32.exe advapi32.dll,ProcessIdleTasks; this may take several minutes...");
//				OsUtil.execSynch("Rundll32.exe advapi32.dll,ProcessIdleTasks");	// this will fail if this is a really old version of windows?  thats one reason to trap Exceptions below...; see also http://www.microsoft.com/whdc/archive/benchmark.mspx
// +++ decided to suppress this because it can take so long the first time that is executed that my users may freak out and think that the program is stalled
			}
			else if (OsUtil.isUnix()) {
				//OsUtil.execSynch("...");
// +++ anything standard to do here?  need to talk to unix experts... see http://www.unix.com/showthread.php?p=302166530#post302166530
			}
		}
		catch (Exception e) {
			clearUserMsgs();
			System.err.println("osSpecificPreparation caught the following Exception:");
			e.printStackTrace(System.err);
			System.err.println("IT WILL BE IGNORED AND BENCHMARKING WILL PROCEED.");
		}
	}
	
	/**
	* Performs the first measurement of {@link #task}'s execution time.
	* The result is recorded in {@link #timeExecFirst}.
	* <p>
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected void doMeasurementFirst() throws Exception {
		cleanJvm();
		
		sendUserMsg("performing first execution of task...");
		timeExecFirst = measure(1).executionTime;
	}
	
	/**
	* In order to give hotspot optimization a chance to complete,
	* {@link #task} is executed many times (with no recording of the execution time).
	* This phase lasts for the time specified by {@link #params}.{@link Params#getWarmupTime}.
	* <p>
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected void warmupJvm() throws Exception {
		cleanJvm();
		
		sendUserMsg("performing many executions of task (lasting " + params.getWarmupTime() + "+ s) to fully warmup the JVM...");
		long n = 1;
		for (long start = System.nanoTime(); System.nanoTime() - start < params.getWarmupTime() * 1e9; n *= 2) {	// * 1e9 converts warmupTime to ns
			measure(n);	// ignore result
		}
	}
	
	/**
	* Determines how many executions of {@link #task} are required in order for the sum
	* of their execution times to equal {@link #params}.{@link Params#getExecutionTimeGoal getExecutionTimeGoal}.
	* The result is stored in {@link #numberExecutions}.
	* <p>
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected void determineNumberExecutions() throws Exception {
		cleanJvm();
		
		JvmState jvmState = new JvmState();
		String jvmStateDiff = null;
		for (long n = 1; ; ) {
			sendUserMsg("determining how many executions of task are required for executionTimeGoal to be met; trying n = " + n + "..." + ((jvmStateDiff != null) ? "(Note: redoing this n because " + jvmStateDiff + ")" : ""));
			Measurement m = measure(n);
			if (m.executionTime <= params.getExecutionTimeGoal()) {	// n is not large enough to cause ExecutionTimeGoal to be met
				n *= 2;	// so double n and retry; note: if n overflows then measure will detect it
				jvmStateDiff = null;
				continue;
			}
			else if (!m.jvmState.equals(jvmState)) {	// n seems to be large enough to meet ExecutionTimeGoal, but the JVM state changed during the measurement so retry loop with same value of n to make sure
				jvmStateDiff = m.jvmState.difference(jvmState);
				jvmState = m.jvmState;
				continue;
			}
			else {	// have obtained a reliable estimate of n
				numberExecutions = n;
				return;
			}
		}
	}
	
	/**
	* Measures many many executions of {@link #task}.
	* Each measurement is over a block of {@link #numberExecutions} calls to <code>task</code>.
	* The number of measurements is specified by {@link #params}.{@link Params#getNumberMeasurements getNumberMeasurements}.
	* The results are recorded in {@link #measurements}.
	* <p>
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected void doMeasurements() throws Exception {
		cleanJvm();
		
		measurements = new Measurement[params.getNumberMeasurements()];
		JvmState jvmState = new JvmState();
		String jvmStateDiff = null;
		int total = 0;
		for (int i = 0; i < measurements.length; i++, total++) {
			sendUserMsg("doing measurement #" + (i + 1) + "/" + measurements.length + ((jvmStateDiff != null) ? "; NOTE: total execution count = " + (total + 1) + " (restarted loop because " + jvmStateDiff + ")" : ""));
			measurements[i] = measure(numberExecutions);
			if (!measurements[i].jvmState.equals(jvmState)) {
				jvmStateDiff = measurements[i].jvmState.difference(jvmState);
				jvmState = measurements[i].jvmState;	// reset to the latest JvmState
				i = -1;	// causes the loop to restart at i = 0 on its next iteration
			}
		}
		
		preventDce();
	}
	
	/**
	* Attempts to prevent a sophisticated compiler from using
	* <a href="http://www.ibm.com/developerworks/java/library/j-benchmark1.html#dce">dead-code elimination</a>
	* to skip computations that should be benchmarked.
	* <p>
	* The implementation here writes data to the console
	* that always depends on both <code>{@link #task}.toString()</code> and <code>{@link #callResult}.toString()</code>.
	* This ensures that as long as task obeys its contract, regardless of whether it is a Runnable or Callable, then bad DCE never occurs.
	* Actually, to cut down on low level output like this, this method only prints out this information if some conditional logic is passed.
	* The form of this logic should cause output to almost never appear, however, the JVM does not know this, and so will be forced to do all the computations.
	*/
	protected void preventDce() {
		String s = task.toString();
		if ((s != null) && (s.hashCode() == 0x12345678)) System.out.println("task = " + s + " (this output is part of DCE elimination; IGNORE IT)");	// the hashCode check USUALLY eliminates this from appearing, which is good, as it avoid concole clutter; thank you Cliff Click for this trick
		s = (callResult != null) ? callResult.toString() : "null";
		if ((s != null) && (s.hashCode() == 0x12345678)) System.out.println("callResult = " + s + " (this output is part of DCE elimination; IGNORE IT)");
			// Note: the sole purpose of the s.hashCode() == 0x12345678 check is to ensure that no output appears the vast majority of the time, as per javadocs, since it is highly unlikely that that particular hash value appear
	}
	
	/**
	* Measures how long it takes to execute a call to {@link #cleanJvm cleanJvm}.
	* Compares this time with total {@link #task} execution time held in {@link #measurements},
	* and issues a warning if this <code>cleanJvm</code> time is excessive
	* (since this means that the <code>task</code> execution times do not properly account for cleanup costs).
	*/
	protected void cleanJvmFinal() {
		sendUserMsg("performing a final JVM clean...");
		
		long t1 = timeNs();
		cleanJvm();
		long t2 = timeNs();
		double timeClean = timeDiffSeconds(t1, t2);
		
		double timeRun;
		if (params.getManyExecutions()) {
			timeRun = 0;
			for (Measurement m : measurements) timeRun += m.executionTime;
		}
		else {
			timeRun = timeExecFirst;
		}
		
		double cleanFraction = timeClean / timeRun;
		if (cleanFraction > params.getCleanFractionThreshold()) cleanIssues = "--EXECUTION TIMES MAY BE TOO SMALL" + "\n" + "--a final JVM cleanup took an extra " + (100 * cleanFraction) + "% of the total execution time" + "\n" + "--this time should be included in the measurements";
		else cleanIssues = null;
	}
	
	/**
	* Diagnoses {@link #measurements}
	* for the presence of <a href="http://en.wikipedia.org/wiki/Outlier#Mathematical_definitions">outliers</a>.
	* <p>
	* Implementation here uses the <a href="http://en.wikipedia.org/wiki/Interquartile_range">interquartile range</a>
	* to define what constitutes an outlier
	* (i.e. the <a href="http://en.wikipedia.org/wiki/Box_plot">boxplot</a> technique)
<!--
+++ another simple outlier test is the median absolute deviation (MAD) http://en.wikipedia.org/wiki/Median_absolute_deviation
	~~an example of it being used to detect outliers is found here: http://books.google.co.th/books?id=M59rMlCM4FIC&pg=PA409&lpg=PA409&dq=%22median+absolute+deviation%22+outlier&source=web&ots=1A92L3bhIf&sig=lIwBsBo43Ox4WJ-ZG5OjnNe40TE&hl=en

+++ more sophisticated approaches are also possible:
	~~overview: http://www.eng.tau.ac.il/~bengal/outlier.pdf
	~~a cluster based algorithm that claims to eliminate the clustering parameters that plague other algorithms: http://www.cs.ualberta.ca/~zaiane/postscript/pakdd06.pdf
	~~another clustering alghorithm ("The main idea is to first iteratively move data points toward cluster centers"): http://www.math.yorku.ca/~stevenw/pub/sw14.pdf
-->
	* and assigns a non-<code>null</code> message to {@link #outlierIssues} if any outliers are detected.
	* <p>
	* @throws IllegalStateException if called when <code>measurements == null</code>
	<!-- NOTE: references in the code below are documented in Math2 in the autocoXXX section -->
	*/
	protected void diagnoseOutliers() throws IllegalStateException {
		sendUserMsg("diagnosing measurements for outliers...");
		
		double[] times = getTimes();
		Arrays.sort(times);	// CRITICAL: must sort for Math2.quantile below to work
		
		int q = 4;	// i.e. want quartiles
		double quartile1 = Math2.quantile(times, 1, q);
		double median = Math2.median(times);
		double quartile3 = Math2.quantile(times, 3, q);
		double iqr = quartile3 - quartile1;	// interquartile range
		
		double lowExtreme = quartile1 - (3 * iqr);
		double lowMild = quartile1 - (1.5 * iqr);
		double highExtreme = quartile3 + (3 * iqr);
		double highMild = quartile3 + (1.5 * iqr);
		
		List<String> lowExtremeList = new ArrayList<String>();
		List<String> lowMildList = new ArrayList<String>();
		List<String> highExtremeList = new ArrayList<String>();
		List<String> highMildList = new ArrayList<String>();
		for (int i = 0; i < times.length; i++) {
			if (times[i] < lowExtreme) lowExtremeList.add("#" + i + " = " + FormatUtil.toEngineeringTime(times[i], 3));	// CRITICAL: the order in which do the comparisons below and also that do else if really matters for the logic to be correct
			else if (times[i] < lowMild) lowMildList.add("#" + i + " = " + FormatUtil.toEngineeringTime(times[i], 3));
			else if (times[i] > highExtreme) highExtremeList.add("#" + i + " = " + FormatUtil.toEngineeringTime(times[i], 3));
			else if (times[i] > highMild) highMildList.add("#" + i + " = " + FormatUtil.toEngineeringTime(times[i], 3));
		}
		if (lowExtremeList.size() + lowMildList.size() + highExtremeList.size() + highMildList.size() > 0) {
			outlierIssues =
				"--EXECUTION TIMES APPEAR TO HAVE OUTLIERS" + "\n"
				+ "--this was determined using the boxplot algorithm with median = " + FormatUtil.toEngineeringTime(median, 3) + ", interquantileRange = " + FormatUtil.toEngineeringTime(iqr, 3);
			if (lowExtremeList.size() > 0) outlierIssues += "\n" + "--" + lowExtremeList.size() + " are EXTREME (on the low side): " + StringUtil.toString(lowExtremeList, ", ");
			if (lowMildList.size() > 0) outlierIssues += "\n" + "--" + lowMildList.size() + " are mild (on the low side): " + StringUtil.toString(lowMildList, ", ");
			if (highExtremeList.size() > 0) outlierIssues += "\n" + "--" + highExtremeList.size() + " are EXTREME (on the high side): " + StringUtil.toString(highExtremeList, ", ");
			if (highMildList.size() > 0) outlierIssues += "\n" + "--" + highMildList.size() + " are mild (on the high side): " + StringUtil.toString(highMildList, ", ");
		}
		else outlierIssues = null;
	}
	
	/**
	* Diagnoses {@link #measurements} for the presence of <a href="http://en.wikipedia.org/wiki/Serial_correlation">serial correlation</a>.
	* <p>
	* Implementation here computes the autocorrelation function as a function of lag,
	* counts how many values fall outside their 95% confidence interval,
	* and assigns a non-<code>null</code> message to {@link #serialCorrelationIssues} if this count exceeds the expected count.
<!--
+++ the main bad aspect of this approach is that it involves several assumptions,
such as that the time series process is a stationary Gaussian process.
Is there a better nonparametric approach to detecting serial correlation?

~~J. P. Lewis suggested that I look into mutual information (presumably of t[i] with t[i - 1]?)
	~~general articles:
		http://en.wikipedia.org/wiki/Mutual_information
	~~mentions use of MI to measure serial correlation (but does not develop the idea): http://www.stat.berkeley.edu/~brill/Papers/bjps1.pdf

~~the author of that last paper, David Brillinger, responded to an email inquiry from me and suggested that I
			"...also consider spectrum analysis.  It has the advantage of often suggesting alternative models when independence / lack of correlation is rejected. It is also nonparametric."
		I did some web research, but could find no good free online materials describing this in detail.
		Best source found is this book: http://www.amazon.com/Introduction-Spectral-Analysis-Petre-Stoica/dp/0132584190
		Sketchy course notes: http://www.dsp.sun.ac.za/dsp833/dsp833_lecture01.pdf

~~standard tests:
	~~Durbin-Watson statistic
		http://en.wikipedia.org/wiki/Durbin%E2%80%93Watson_statistic
	~~would general tests for randomness, like the Chi-square Test be appropriate?
		http://www.biostat.pitt.edu:16080/biost2096/lecture8.pdf
	~~program ent ("entropy"?) for checking randomness:
		http://www.fourmilab.ch/random/

~~Testing for Serial Correlation with Exponentially Distributed Variates
	http://links.jstor.org/sici?sici=0006-3444%28196712%2954%3A3%2F4%3C395%3ATFSCWE%3E2.0.CO%3B2-K&size=LARGE&origin=JSTOR-enlargePage
	
~~ROBUST INFERENCE FOR THE MEAN IN THE PRESENCE OF SERIAL CORRELATION AND HEAVY-TAILED DISTRIBUTIONS
	http://journals.cambridge.org/action/displayAbstract?fromPage=online&aid=111943
	
~~discusses how serial correlation can give you less information than would be present if the samples are independent
	http://books.google.com/books?id=png6zsomkVQC&pg=PA292&lpg=PA292&dq=mean+confidence+interval+serial+correlation&source=web&ots=f_nDQtjftl&sig=QAzNZ9hEKfaAELap6kIdPvJJkbI
	
~~Testing the assumptions of linear regression
	(discusses how to measure if serial correlation is present in the context of linear regression)
	http://www.duke.edu/~rnau/testing.htm
-->
	* <p>
	* @throws IllegalStateException if called when <code>measurements == null</code>
	*/
	protected void diagnoseSerialCorrelation() throws IllegalStateException {
		sendUserMsg("diagnosing measurements for serial correlation...");
		
		double[] times = getTimes();
		int N = times.length;
		if (N < 50) {
			serialCorrelationIssues = "--UNKNOWN IF EXECUTION TIMES HAVE SERIAL CORRELATION OR NOT: N = " + N + " < 50 is too small for autocorrelation tests";	// requiring N >= 50 is suggested by BOX-JENKINS on p. 33
			return;
		}
		
		double[][] acf = Math2.autocorrelation(times);
		double[] r = acf[0];
		double[] ciLower = acf[1];
		double[] ciUpper = acf[2];
		
			// NOTE: references in the code below are documented in Math2 in the autocoXXX section
		//double meanr = -1.0 / N;	// see MEKO eq. (8)
		int K = (int) Math.round( N / 4.0 );	// examining no more than N/4 is suggested by BOX-JENKINS on p. 33
		K = Math.min(K, 20);	// decided to limit the exploration to at MOST 20 lags
		List<String> outsideList = new ArrayList<String>();
		int numOutsideExpected = (int) Math.round( (1 - 0.95) * K );	// if K is large enough, it IS to be expected that some of the r[k] fall outside of the 95% CI
		for (int k = 1; k < K; k++) {
			if ((r[k] < ciLower[k]) || (r[k] > ciUpper[k])) {
				double mean = (ciUpper[k] + ciLower[k]) / 2;
				double sigma = (ciUpper[k] - ciLower[k]) / (2 * 1.96);
				double diff = r[k] - mean;
				double scale = diff / sigma;
				String side = (diff > 0) ? "above" : "below";
				outsideList.add("r[" + k + "] is " + scale + " sigma " + side + " its mean");
			}
		}
		if (outsideList.size() > numOutsideExpected) {
			serialCorrelationIssues =
				"--EXECUTION TIMES HAVE SERIAL CORRELATION" + "\n"
				+ "--" + numOutsideExpected + " of the " + K + " autocorrelation function coefficients (r[k]) that were computed are expected to fall outside their 95% CI" + "\n"
				+ "--but found these " + outsideList.size() + ": " + StringUtil.toString(outsideList, ", ") + "\n"
				+ "--the 95% CI for the r[k] was calculated as mean +- 1.96 sigma (i.e. a Gaussian distribution was assumed)";
		}
		else serialCorrelationIssues = null;
// +++ I AM NOT ENTIRELY SURE THAT THIS "countOutside > countOutsideExpected" ALGORITHM ABOVE IS CORRECT!
// It seems to be what MEKO describes in his section "3.5 Large-lag standard error".
// And on p. 2 of http://www.fordham.edu/economics/vinod/et2le2.pdf just below eq. (2) it also seems to describe this.
// HOWEVER, when I look at the derivation in BOX-JENKINS (see p. 34, section 2.1.16, eq. 2.1.11 and following),
// that equation is derived under the hypothesis that r[k] ~= 0 for all k > some q.
// Thus, when BOX-JENKINS comes to an example, they do NOT follow the algorithm above.
// Instead what they do is first test the hypothesis that q = 1 by seeing if any of the r[k] for k >= 1
// exceed the large lag 95% CI; only if that hypothisis fails do they go on to consider the q = 2 hypothesis
// (see "An example" on pp. 35-36).

// +++ Another issue is that after the large lag SE section in MEKO, he goes on to section "3.6 Hypothesis test on r1"
// How do these hypothesis tests relate to the large lag SE CI algorithm?
	}
	
	/**
	* Calculates {@link #statsBlock} from {@link #measurements}.
	* Then derives {@link #statsAction} from <code>statsBlock</code>.
	* <p>
	* @throws IllegalStateException if called when <code>measurements == null</code>
	*/
	protected void calculateStats() throws IllegalStateException {
		sendUserMsg("calculating the block statistics (each data point comes from " + numberExecutions + " executions)...");
		Bootstrap bootstrap = new Bootstrap( getTimes(), Bootstrap.numberResamples_default, params.getConfidenceLevel() );
		Bootstrap.Estimate mean = bootstrap.getEstimate("mean");
		Bootstrap.Estimate sd = bootstrap.getEstimate("sd");
		String sdIssues = diagnoseSd(mean.getPoint(), sd.getPoint());
		statsBlock = new Stats( mean.getPoint(), mean.getLower(), mean.getUpper(), sd.getPoint(), sd.getLower(), sd.getUpper(), sdIssues );
		
		sendUserMsg("calculating the action statistics (each data point involves " + getNumberActionsPerMeasurement() + " actions)...");
		statsAction = statsBlock.forActions( getNumberActionsPerMeasurement() );
	}
	
	/**
	* Returns an array of just the execution times stored in {@link #measurements}.
	* <p>
	* @throws IllegalStateException if called when <code>measurements == null</code>
	*/
	protected double[] getTimes() throws IllegalStateException {
		Check.state().notNull(measurements);
		
		double[] times = new double[measurements.length];
		for (int i = 0; i < times.length; i++) times[i] = measurements[i].executionTime;
		return times;
	}
	
	/**
	* Diagnoses the <code>sd</code> parameter
	* to see if it really represents the intrinsic variation in {@link #task}'s execution time
	* or if it is just reflecting environmental noise.
	* See the "Standard deviation measurement issues" section of the
	* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
	* <p>
	* @param mean the mean execution time; must be from the <i>block statistics</i>
	* @param sd the standard deviation of the execution time; must be from the <i>block statistics</i>
	*/
	protected String diagnoseSd(double mean, double sd) {
		if (task == noiseTask) return null;
		if (!params.getEstimateNoiseFloor()) return "--sd results have unknown validity (the environmental noise test was skipped)";
		
		Stats statsNt = noiseMap.get(params);
		if (statsNt == null) {
			Benchmark b = new Benchmark(noiseTask, params);
			statsNt = b.statsBlock;
			noiseMap.put( (Params) params.clone(), statsNt );	// CRITICAL: must clone params so that the key put into noiseMap is encapsulated; this is essential because Params is a mutable class and if simply stored params, the user could change it which would be a disaster
		}
		double meanNt = statsNt.getMean();
		double sdNt = statsNt.getSd();
		
			// normalize each standard deviation by the sqrt of time so that make a true comparison later on:
		sd /= Math.sqrt(mean);
		sdNt /= Math.sqrt(meanNt);
		
		double sdFraction = Math.min( sdNt / sd, 1 );
		if (sdFraction > params.getSdFractionThreshold()) return "--block sd values MAY NOT REFLECT TASK'S INTRINSIC VARIATION" + "\n" + "--guesstimate: environmental noise explains at least " + (100 * sdFraction) + "% of the measured sd";
		
		return null;
	}
	
	/**
	* Checks that all the state of this instance satisfies its contracts.
	* <p>
	* @throws IllegalStateException if any violation is found
	*/
	protected void checkState() throws IllegalStateException {
		sendUserMsg("checking this instance's state...");
		if (task == null) throw new IllegalStateException("task == null");
		if (params == null) throw new IllegalStateException("params == null");
		if ((timeExecFirst < 0) || Double.isNaN(timeExecFirst) || Double.isInfinite(timeExecFirst)) throw new IllegalStateException("timeExecFirst = " + timeExecFirst + " is an illegal value");
		if (numberExecutions <= 0) throw new IllegalStateException("numberExecutions = " + numberExecutions + " <= 0");
		if ((measurements != null) && (measurements.length != params.getNumberMeasurements())) throw new IllegalStateException("measurements != null but measurements.length = " + measurements.length + " != params.getNumberMeasurements() = " + params.getNumberMeasurements());
		if ((task instanceof Runnable) && (callResult != null)) throw new IllegalStateException("task instanceof Runnable && callResult != null");
		if ((cleanIssues != null) && StringUtil.isBlank(cleanIssues)) throw new IllegalStateException("cleanIssues != null but is blank");
		if ((outlierIssues != null) && StringUtil.isBlank(outlierIssues)) throw new IllegalStateException("outlierIssues != null but is blank");
		if ((serialCorrelationIssues != null) && StringUtil.isBlank(serialCorrelationIssues)) throw new IllegalStateException("serialCorrelationIssues != null but is blank");
		if (params.getManyExecutions() && (statsBlock == null)) throw new IllegalStateException("params.getManyExecutions() returns true, but statsBlock == null");
		if (params.getManyExecutions() && (statsAction == null)) throw new IllegalStateException("params.getManyExecutions() returns true, but statsAction == null");
	}
	
	/**
	* Attempts to restore the JVM to a pristine state by aggressively performing
	* object finalization and garbage collection.
	*/
	protected void cleanJvm() {
		MemoryMeasurer.restoreJvm();
	}
	
	/**
	* Displays a <i>non-critical</i> message to the user.
	* <p>
	* <i>Only use this method for information like program progress.
	* Do not use it for results or error messages.</i>
	* <p>
	* Implementation here only does something if
	* {@link #params}.{@link Params#getConsoleFeedback getConsoleFeedback} returns true.
	* If that is the case, it writes <code>msg</code> to the current console line.
	* In order to avoid flooding the console with data,
	* it truncates <code>msg</code> if necessary in order to fit on a single line,
	* and overwrites anything that was previously on the current console line.
	*/
	protected void sendUserMsg(String msg) {
		if (params.getConsoleFeedback()) {
			if (task == noiseTask) msg = "[noise determination]: " + msg;
			ConsoleUtil.overwriteLine(msg);
		}
	}
	
	/** Clears any output that may have been written by calls to {@link #sendUserMsg sendUserMsg}. */
	protected void clearUserMsgs() {
		if (params.getConsoleFeedback()) ConsoleUtil.eraseLine();
	}
	
	/**
	* Measures the execution time of <code>n</code> calls of {@link #task}.
	* <p>
	* Units: seconds.
	* <p>
	* Contract: the result is always >= 0.
	* <p>
	* @throws IllegalArgumentException if <code>n <= 0</code>
	* @throws Exception (or some subclass) if <code>task</code> is a <code>Callable</code> and <code>task.call</code> throws it
	*/
	protected Measurement measure(long n) throws IllegalArgumentException, Exception {
		Check.arg().positive(n);
		
		long t1 = timeNs();
		if (task instanceof Callable) {
			Callable callable = (Callable) task;
			for (long i = 0; i < n; i++) {
				callResult = callable.call();
			}
		}
		else if (task instanceof Runnable) {
			Runnable runnable = (Runnable) task;
			for (long i = 0; i < n; i++) {
				runnable.run();
			}
		}
		else {
			throw new IllegalStateException("task is neither a Callable or Runnable--this should never happen");
		}
		long t2 = timeNs();
		return new Measurement( timeDiffSeconds(t1, t2), new JvmState() );
	}
	
	/**
	* Returns the time since some fixed but arbitrary offset, in nanoseconds.
	* Therefore, is only useful for differential time measurements.
	* CPU time is used if {@link #params}.{@link Params#getMeasureCpuTime getMeasureCpuTime} returns <code>true</code>,
	* else elapsed (aka wall clock) time is used.
	*/
	protected long timeNs() {
		if (params.getMeasureCpuTime()) return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
		return System.nanoTime();
	}
	
	/**
	* Robustly calculates and returns the difference <code>t2 - t1</code> <i>in seconds</i>.
	* <p>
	* @param t1 first time, <i>in nanoseconds</i>
	* @param t2 second time, <i>in nanoseconds</i>
	* @throws IllegalArgumentException if an issue is discovered
	*/
	protected double timeDiffSeconds(long t1, long t2) throws IllegalArgumentException {
		if (t1 > t2) throw new IllegalArgumentException("clock ran backwards: t1 = " + t1 + " > t2 = " + t2);
		long diff = t2 - t1;	// CRITICAL: only compute diff once know that t1 <= t2 in order to detect overflow below
		Check.arg().notNegative(diff);	// I should not have to detect this, JVM should, this is a defect in Java's numerics...
		return diff * 1e-9;	// 1e-9 converts units from nanoseconds to seconds
	}
	
	// -------------------- accessors --------------------
	
	/**
	* Returns the <i>action</i> execution time as determined from the first measurement,
	* namely, <code>{@link #timeExecFirst} / {@link #params}.{@link Params#getNumberActions}</code>.
	*/
	public double getFirst() {
		return timeExecFirst / params.getNumberActions();
	}
	
	/**
	* Returns the number of action executions in each measurement.
	* This is equal to {@link #params}.{@link Params#getNumberActions getNumberActions()} * {@link #numberExecutions}.
	*/
	public long getNumberActionsPerMeasurement() {
		return params.getNumberActions() * numberExecutions;
	}
	
	/** Returns {@link #callResult}. */
	public Object getCallResult() {
		return callResult;
	}
	
	/**
	* Returns {@link #statsBlock}.
	* <p>
	* @throws IllegalStateException if <code>params.getManyExecutions()</code> returns false (there are no Stats)
	*/
	public Stats getStatsBlock() throws IllegalStateException {
		if (!params.getManyExecutions()) throw new IllegalStateException("params.getManyExecutions() returns false (there are no Stats)");
		
		return statsBlock;
	}
	
	/**
	* Returns {@link #statsAction}.
	* <p>
	* @throws IllegalStateException if <code>params.getManyExecutions()</code> returns false (there are no Stats)
	*/
	public Stats getStatsAction() throws IllegalStateException {
		if (!params.getManyExecutions()) throw new IllegalStateException("params.getManyExecutions() returns false (there are no Stats)");
		
		return statsAction;
	}
	
	/**
	* Alias for {@link #getStatsAction getStatsAction}.
	* This is a convenience method,
	* since most users will typically only be interested in the action statistics,
	* therefore this provides an abbreviation.
	* <p>
	* @throws IllegalStateException if <code>params.getManyExecutions()</code> returns false (there are no Stats)
	*/
	public Stats getStats() throws IllegalStateException {
		return getStatsAction();
	}
	
	/**
	* Returns the mean of the <i>action</i> execution times.
	* <p>
	* This is a convenience method that simply returns <code>{@link #getStats getStats}().{@link Stats#getMean getMean}()</code>.
	* <p>
	* @throws IllegalStateException if <code>params.getManyExecutions()</code> returns false (there are no Stats)
	*/
	public double getMean() throws IllegalStateException {
		return getStats().getMean();
	}
	
	/**
	* Returns the standard deviation of the <i>action</i> execution times.
	* <p>
	* This is a convenience method that simply returns <code>{@link #getStats getStats}().{@link Stats#getSd getSd}()</code>.
	* <p>
	* <b>Warning:</b> the result may be unreliable.
	* The {@link #toString toString} and {@link #toStringFull toStringFull} methods will warn if there are issues with it.
	* See the "Standard deviation warnings" section of the
	* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
	* <p>
	* @throws IllegalStateException if <code>params.getManyExecutions()</code> returns false (there are no Stats)
	*/
	public double getSd() throws IllegalStateException {
		return getStats().getSd();
	}
	
	// -------------------- toString, toStringFull and helper methods --------------------
	
	/**
	* Returns a <code>String</code> representation of this instance.
	* <p>
	* The implementation here merely summarizes the important information:
	* the <i>action's</i> first execution time, the <i>action's statistics</i> (if available),
	* and high level warnings.
	*/
	@Override public String toString() {
		String first = FormatUtil.toEngineeringTime(getFirst(), 3);
		return
			(params.getManyExecutions()
				? "first = " + first + ", " + getStats()
				: first
			)
			+ issueSummary();
	}
	
	/** Returns a summary of any issues that occured. */
	protected String issueSummary() {
		StringBuilder sb = new StringBuilder();
		if (params.getManyExecutions()) {
			if (cleanIssues != null) {
				if (sb.length() == 0) sb.append(" WARNING:");
				else sb.append(",");
				sb.append(" execution times may be inaccurate");
			}
			
			if (outlierIssues != null) {
				if (sb.length() == 0) sb.append(" WARNING:");
				else sb.append(",");
				if (outlierIssues.toLowerCase().contains("extreme")) sb.append(" EXECUTION TIMES HAVE EXTREME OUTLIERS");
				else sb.append(" execution times have mild outliers");
			}
			
			if (serialCorrelationIssues != null) {
				if (sb.length() == 0) sb.append(" WARNING:");
				else sb.append(",");
				sb.append(" execution times may have serial correlation");
			}
			
			if ((statsBlock.getSdIssues() != null) || (statsAction.getSdIssues() != null)) {
				if (sb.length() == 0) sb.append(" WARNING:");
				else sb.append(",");
				sb.append(" SD VALUES MAY BE INACCURATE");
			}
		}
		else {
			if (cleanIssues != null) sb.append(" WARNING: execution time may be inaccurate");
		}
		return sb.toString();
	}
	
	/** Returns a <code>String</code> representation of this instance with full details and explanations. */
	public String toStringFull() {
		if (params.getManyExecutions()) {
			StringBuilder sb = new StringBuilder(2048);
			
			sb.append("action statistics: ").append( toString() );
			
			if (numberExecutions > 1) {
				sb.append('\n');
				sb.append('\t').append("----------").append('\n');
				sb.append('\t').append("--the action statistics were calculated from block statistics").append('\n');
				sb.append('\t').append("--each block measured ").append( numberExecutions ).append(" task executions").append('\n');
				sb.append('\t').append("--the user says that task internally performs m = ").append( params.getNumberActions() ).append(" actions").append('\n');
				sb.append('\t').append("--then the number of actions per block measurement is a = ").append( getNumberActionsPerMeasurement() ).append('\n');
				sb.append('\t').append("--block statistics: ").append( statsBlock.toString() ).append('\n');
				sb.append('\t').append("--the forumla used to convert block statistics to action statistics (mean scales as 1/a, sd scales as 1/sqrt(a)) assumes that the action execution times are iid");
			}
			
			sb.append('\n');
			sb.append('\t').append("----------").append('\n');
			sb.append('\t').append("--each confidence interval (CI) is reported as either +- deltas from the point estimate, or as a closed interval ([x, y])").append('\n');
			sb.append('\t').append("--each confidence interval has confidence level = ").append( params.getConfidenceLevel() );
			
			appendIssues(cleanIssues, sb);
			appendIssues(outlierIssues, sb);
			appendIssues(serialCorrelationIssues, sb);
			appendIssues(statsBlock.getSdIssues(), sb);
			appendIssues(statsAction.getSdIssues(), sb);
			
			return sb.toString();
		}
		else {
			return "single execution time: " + toString();
		}
	}
	
	/** Helper method for {@link #toStringFull toStringFull} which handles issues fields like {@link #cleanIssues}. */
	protected void appendIssues(String issues, StringBuilder sb) {
		if (issues == null) return;
		
		sb.append('\n');
		sb.append('\t').append("----------").append('\n');
		sb.append('\t').append(issues.replace("\n", "\n\t"));
	}
	
	// -------------------- Params (static inner class) --------------------
	
	/** Holds parameters which specify how the benchmarking is carried out. */
	public static class Params implements Cloneable {
		
		/**
		* Specifies whether or not the execution time is measured as CPU time or elapsed ("wall clock") time.
		* <p>
		* Default value is <code>false</code> (i.e. measure elapsed time).
		*/
		protected boolean measureCpuTime = false;
		
		/**
		* Specifies whether or not many executions of {@link #task} should be performed (as opposed to executing it just once).
		* <p>
		* In general, should be <code>true</code> in order to obtain the most accurate results.
		* However, if either <code>task</code> takes very long to execute,
		* or if it is desired to measure initialization effects like class loading and hotpsot warmup, then should be <code>false</code>.
		* <p>
		* Default value is <code>true</code>.
		*/
		protected boolean manyExecutions = true;
		
		/**
		* If {@link #manyExecutions} is true,
		* then specifies the minimum amount of time that should execute {@link #task} for before start recording measurements.
		* <p>
		* Units: seconds.
		* <p>
		* Contract: must be > 0, and is never NaN or infinite.
		* <p>
		* Default value is 10.0 (seconds),
		* which is the minimum time recomended by Cliff Click, the HotSpot architect
		* (see p. 33 of <a href="http://www.azulsystems.com/events/javaone_2002/microbenchmarks.pdf">his 2002 JavaOne talk</a>).
		*/
		protected double warmupTime = 10.0;
		
		/**
		* If {@link #manyExecutions} is <code>true</code>,
		* then is used to determine how many executions of {@link #task} to perform for each timing.
		* Specifically, for each measurement, <code>task</code> must be executed enough times
		* to meet or exceed the amount of time specified here.
		* <p>
		* This value should be large enough that inaccuracy in timing measurements are small.
		* Since this class uses {@link System#nanoTime System.nanoTime} for its timings,
		* and this clock does not (across many platforms) reliably have better than a few 10s of ms accuracy,
		* this value should probably be at least 1 second in order to guarantee measurement error under 1%.
		* <i>Only use a smaller value if you know that System.nanoTime
		* on all the platforms that you deploy on has greater accuracy.</i>
		* <p>
		* Units: seconds.
		* <p>
		* Contract: must be > 0, and is never NaN or infinite.
		* <p>
		* Default value is 1 (second).
		*/
/*
Programmer note:
At one point, I thought that I could eliminate this field
if instead the code did a measurement to determine the accuracy of System.nanoTime.
In principle, this would allow much quicker benchmarking to be done on systems which have good clocks.
Chose not to do this because of the requirement mentioned in the numberMeasurements javadoc below
about how the the total benchmarking process needs to last for at least 1 minute,
which means that eliminating this field would gain nothing.
Could revisit this in the future if the resource reclamation accounting issue were solved some other way.
*/
		protected double executionTimeGoal = 1;
		
		/**
		* If {@link #manyExecutions} is <code>true</code>,
		* then once {@link #numberExecutions} has been determined, specifies the number of timing measurements to perform.
		* <p>
		* Contract: must be > 0.
		* <p>
		* Default value is 60.  The following considerations determined this value:
		* <ol>
		*  <li>
		*		15-25 is about the optimum number of samples for an accuracy versus time tradeoff,
		*		according to this <a href="http://en.wikipedia.org/wiki/T-distribution">one-sided confidence interval table</a>.
		*		Now, that table is only valid for mean confidence intervals on a Gaussian distribution.
		*		So, in order to accurately handle general distributions (which are likely to require more samples, especially skewed ones)
		*		and statistics besides the mean (also likely to require more samples)
		*		using the bootstrap technique (which is known to have coverage issues, especially with small numbers of samples),
		*		the number of samples should be at least 30.
		*  </li>
		*  <li>
		*		it, along with {@link #executionTimeGoal}, determines how long all of the measurements will take.
		*		As discussed <a href="http://www.ibm.com/developerworks/java/library/j-benchmark1.html#rr">here</a>,
		*		you want the total benchmarking process to last at least 1 minute
		*		in order to accurately sample garbage collection and object finalization behavior.
		*		Given <code>executionTimeGoal</code>'s default value of 1 second, that means this value should be at least 60.
		*  </li>
		*  <li>need at least 50 samples to do the autocorrelation tests in {@link #diagnoseSerialCorrelation diagnoseSerialCorrelation}</li>
		* </ol>
		* Conclusion: need at least max(30, 60, 50) = 60.
		*/
		protected int numberMeasurements = 60;
		
		/**
		* Maximum fraction of time that can be spent doing a final JVM cleanup
		* after all measurements have been performed
		* before a warning is issued that the measurements may be too small (because they failed to include cleanup costs).
		* <p>
		* Contract: must be > 0, and is never NaN or infinite.
		* <p>
		* Default value is 0.01 (i.e. 1%).
		*/
		protected double cleanFractionThreshold = 0.01;
		
		/**
		* If {@link #manyExecutions} is <code>true</code>,
		* then specifies the confidence level to use when calculating the confidence intervals for the statistics.
		* The <i>percent</i> confidence level is 100 times this quantity.
		* <p>
		* Units: none, is a dimensionless number.
		* <p>
		* Contract: must be inside the open interval (0, 1), and is never NaN or infinite.
		* <p>
		* Default value is 0.95, the standard value used in statistics.
		* <p>
		* @see <a href="http://en.wikipedia.org/wiki/Confidence_level">article on confidence intervals</a>
		*/
		protected double confidenceLevel = 0.95;
		
		/**
		* Maximum fraction of the standard deviation (sd) present in {@link #statsBlock}
		* which may be due to effects besides {@link #task}'s intrinsic variation
		* before a warning is issued that the sd is too high.
		* <p>
		* Contract: must be > 0, and is never NaN or infinite.
		* <p>
		* Default value is 0.01 (i.e. 1%).
		*/
		protected double sdFractionThreshold = 0.01;
		
		/**
		* Records how many actions are performed by each invocation of {@link #task}.
		* Only the user knows this number, hence it must be specifed by them.
		* <p>
		* The number of actions in each timing measurement is <code>numberActions * {@link #numberExecutions}</code>.
		* <p>
		* <i>The only effect of this value is on the reporting of action execution statistics.</i>
		* <p>
		* An example of why this field is needed comes from measuring array element access times
		* where the <code>task</code> internally loops once over an array of length <code>n</code>.
		* To get the access time per element, <code>numberActions</code> needs to be set equal to the array length.
		* <p>
		* Contract: must be > 0.
		* <p>
		* Default value is 1.
		*/
		protected long numberActions = 1;
		
		/**
		* Specifies whether or not console feedback should be given regarding the benchmarking progress.
		* <p>
		* Default value is <code>true</code>.
		*/
		protected boolean consoleFeedback = true;
		
		/**
		* Specifies whether or not {@link #noiseTask} will be run.
		* <p>
		* Default value is <code>false</code>.
<!--
In my original code, this field was absent but the code behaved as if it were always true (i.e. the noise floor was always measured).
I introduced this field and defaulted it to false because
	a) I ALWAYS seemed to find that the measured noise invalidated all sd measurments (at least for the microbenchmarks I commonly run).
	b) measuring the noise doubles the benchmark time
If ever I get access to a low noise environment (perhaps use real time threads that cannot be context switched?), then maybe I will undo this...
-->
		*/
		protected boolean estimateNoiseFloor = false;
		
		/** Creates a new instance with all fields left at their default initializations. */
		public Params() {}
		
		/**
		* Simply assigns <code>manyExecutions</code> to the corresponding field.
		* All other fields are left at their default initializations.
		*/
		public Params(boolean manyExecutions) {
			setManyExecutions(manyExecutions);
		}
		
		/**
		* Simply assigns <code>numberActions</code> to the corresponding field.
		* All other fields are left at their default initializations.
		* <p>
		* @throws IllegalArgumentException if numberActions violates the field's contract
		*/
		public Params(long numberActions) throws IllegalArgumentException {
			setNumberActions(numberActions);
		}
		
		/**
		* Simply assigns all the parameters to their corresponding fields.
		* All other fields are left at their default initializations.
		* <p>
		* @throws IllegalArgumentException if any paramameter violates the field's contract
		*/
		public Params(boolean manyExecutions, long numberActions) throws IllegalArgumentException {
			setManyExecutions(manyExecutions);
			setNumberActions(numberActions);
		}
		
		/** Accessor for {@link #measureCpuTime}. */
		public boolean getMeasureCpuTime() { return measureCpuTime; }
		
		/** Mutator for {@link #measureCpuTime}. */
		public void setMeasureCpuTime(boolean measureCpuTime) { this.measureCpuTime = measureCpuTime; }
		
		/** Accessor for {@link #manyExecutions}. */
		public boolean getManyExecutions() { return manyExecutions; }
		
		/** Mutator for {@link #manyExecutions}. */
		public void setManyExecutions(boolean manyExecutions) { this.manyExecutions = manyExecutions; }
		
		/** Accessor for {@link #warmupTime}. */
		public double getWarmupTime() { return warmupTime; }
		
		/**
		* Mutator for {@link #warmupTime}.
		* <p>
		* @throws IllegalArgumentException if warmupTime violates its contract
		*/
		public void setWarmupTime(double warmupTime) throws IllegalArgumentException {
			Check.arg().normalPositive(warmupTime);
			
			this.warmupTime = warmupTime;
		}
		
		/** Accessor for {@link #executionTimeGoal}. */
		public double getExecutionTimeGoal() { return executionTimeGoal; }
		
		/**
		* Mutator for {@link #executionTimeGoal}.
		* <p>
		* @throws IllegalArgumentException if executionTimeGoal violates its contract
		*/
		public void setExecutionTimeGoal(double executionTimeGoal) throws IllegalArgumentException {
			Check.arg().normalPositive(executionTimeGoal);
			
			this.executionTimeGoal = executionTimeGoal;
		}
		
		/** Accessor for {@link #numberMeasurements}. */
		public int getNumberMeasurements() { return numberMeasurements; }
		
		/**
		* Mutator for {@link #numberMeasurements}.
		* <p>
		* @throws IllegalArgumentException if numberMeasurements violates its contract
		*/
		public void setNumberMeasurements(int numberMeasurements) throws IllegalArgumentException {
			Check.arg().positive(numberMeasurements);
			
			this.numberMeasurements = numberMeasurements;
		}
		
		/** Accessor for {@link #cleanFractionThreshold}. */
		public double getCleanFractionThreshold() { return cleanFractionThreshold; }
		
		/**
		* Mutator for {@link #cleanFractionThreshold}.
		* <p>
		* @throws IllegalArgumentException if cleanFractionThreshold violates its contract
		*/
		public void setCleanFractionThreshold(double cleanFractionThreshold) throws IllegalArgumentException {
			Check.arg().normalPositive(cleanFractionThreshold);
			
			this.cleanFractionThreshold = cleanFractionThreshold;
		}
		
		/** Accessor for {@link #confidenceLevel}. */
		public double getConfidenceLevel() { return confidenceLevel; }
		
		/**
		* Mutator for {@link #confidenceLevel}.
		* <p>
		* @throws IllegalArgumentException if confidenceLevel violates its contract
		*/
		public void setConfidenceLevel(double confidenceLevel) throws IllegalArgumentException {
			Check.arg().normal(confidenceLevel);
			if ((confidenceLevel <= 0) || (confidenceLevel >= 1)) throw new IllegalArgumentException("confidenceLevel = " + confidenceLevel + " is an illegal value");
			
			this.confidenceLevel = confidenceLevel;
		}
		
		/** Accessor for {@link #sdFractionThreshold}. */
		public double getSdFractionThreshold() { return sdFractionThreshold; }
		
		/**
		* Mutator for {@link #sdFractionThreshold}.
		* <p>
		* @throws IllegalArgumentException if sdFractionThreshold violates its contract
		*/
		public void setSdFractionThreshold(double sdFractionThreshold) throws IllegalArgumentException {
			Check.arg().normalPositive(sdFractionThreshold);
			
			this.sdFractionThreshold = sdFractionThreshold;
		}
		
		/** Accessor for {@link #numberActions}. */
		public long getNumberActions() { return numberActions; }
		
		/**
		* Mutator for {@link #numberActions}.
		* <p>
		* @throws IllegalArgumentException if numberActions violates its contract
		*/
		public void setNumberActions(long numberActions) throws IllegalArgumentException {
			Check.arg().positive(numberActions);
			
			this.numberActions = numberActions;
		}
			
		/** Accessor for {@link #consoleFeedback}. */
		public boolean getConsoleFeedback() { return consoleFeedback; }
		
		/** Mutator for {@link #consoleFeedback}. */
		public void setConsoleFeedback(boolean consoleFeedback) { this.consoleFeedback = consoleFeedback; }
			
		/** Accessor for {@link #estimateNoiseFloor}. */
		public boolean getEstimateNoiseFloor() { return estimateNoiseFloor; }
		
		/** Mutator for {@link #estimateNoiseFloor}. */
		public void setEstimateNoiseFloor(boolean estimateNoiseFloor) { this.estimateNoiseFloor = estimateNoiseFloor; }
		
		/**
		* Contract: returns a new <code>Params</code> instance that contains equivalent data to this instance.
		* Any object fields are deep copied.
		* <i>Every subclass must support cloning and must preserve this deep copy behavior.</i>
		*/
		@Override public Object clone() {
			try {
				return super.clone();	// can get away with this because all the fields here are primitives
			}
			catch (Exception e) {
				throw ThrowableUtil.toRuntimeException(e);
			}
		}
		
		/**
		* Determines equality based on whether or not <code>obj</code>'s <code>Class</code>
		* is the same as this instance's <code>Class</code>,
		* and every field of it equals that of this instance.
		* <p>
		* The choice was made to use the <code>this.getClass() != obj.getClass()</code> idiom
		* instead of the <code>!(obj instanceof Params)</code> idiom
		* in order to allow subclasses to override this method and obey the <code>equals</code> contract.
		* The disadvantage of this idiom is behavior that can surprise users (no polymorphic equivalence is possible).
		* <p>
		* @see "the essay stored in the file equalsImplementation.txt"
		* @see <a href="http://www.artima.com/intv/bloch17.html">Josh Bloch interview</a>
		* @see <a href="http://www.codeproject.com/useritems/EmailWithJoshuaBloch.asp">Josh Bloch email response</a>
		*/
		@Override public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (this.getClass() != obj.getClass()) return false;
			
			Params other = (Params) obj;
			return
				(this.measureCpuTime == other.measureCpuTime)
				&& (this.manyExecutions == other.manyExecutions)
				&& (this.warmupTime == other.warmupTime)
				&& (this.executionTimeGoal == other.executionTimeGoal)
				&& (this.numberMeasurements == other.numberMeasurements)
				&& (this.cleanFractionThreshold == other.cleanFractionThreshold)
				&& (this.confidenceLevel == other.confidenceLevel)
				&& (this.sdFractionThreshold == other.sdFractionThreshold)
				&& (this.numberActions == other.numberActions)
				&& (this.consoleFeedback == other.consoleFeedback)
				&& (this.estimateNoiseFloor == other.estimateNoiseFloor);
		}
		
		/** Returns a value based on all of the non-<code>boolean</code> fields. */
		@Override public int hashCode() {
			return
				//boolean measureCpuTime
				//boolean manyExecutions
				HashUtil.hash(warmupTime)	// needs HashUtil.hash since is a double
				^ HashUtil.hash(executionTimeGoal)	// needs HashUtil.hash since is a double
				^ HashUtil.enhance(numberMeasurements)	// needs HashUtil.enhance even tho an int since is probably a crappy hash by itself
				^ HashUtil.hash(cleanFractionThreshold)	// needs HashUtil.hash since is a double
				^ HashUtil.hash(confidenceLevel)	// needs HashUtil.hash since is a double
				^ HashUtil.hash(sdFractionThreshold)	// needs HashUtil.hash since is a double
				^ HashUtil.hash(numberActions)	// needs HashUtil.hash since is a long
				//boolean consoleFeedback
				//boolean estimateNoiseFloor
				;
		}
		
	}
	
	// -------------------- Measurement (static inner class) --------------------
	
	/** Records information about an execution time measurement. */
	protected static class Measurement {
		
		/**
		* Execution time (in seconds).
		* <p>
		* Contract: is never NaN or infinite and is >= 0.
		*/
		protected double executionTime;
		
		/**
		* Records {@link JvmState} just <i>after</i> the measurement ended.
		* <p>
		* Contract: is never <code>null</code>.
		*/
		protected JvmState jvmState;
		
		/**
		* Constructor.
		* <p>
		* @param executionTime in seconds
		* @param jvmState {@link JvmState} after measurement
		* @throws IllegalArgumentException if executionTime is NaN, infinite, or < 0; jvmState == <code>null</code>
		*/
		protected Measurement(double executionTime, JvmState jvmState) throws IllegalArgumentException {
			Check.arg().normalNotNegative(executionTime);
			Check.arg().notNull(jvmState);
			
			this.executionTime = executionTime;
			this.jvmState = jvmState;
		}
		
	}
	
	// -------------------- JvmState (static inner class) --------------------
	
	/** Records some characteristics of the JVM state. */
	protected static class JvmState {
		
		/** Count of the total number of classes that have been loaded by this JVM. */
		protected long countClassesLoaded;
		
		/** Count of the total number of classes that have been unloaded by this JVM. */
		protected long countClassesUnloaded;
		
		/** Accumlated elapsed time (in milliseconds) spent in compilation. */
		protected long compilationTimeTotal;
		
// +++ maybe another thing that should record is the memory profile (e.g. used, free, max, total), since if the JVM changed that in the middle of benchmarking then should restart?
		
		/** Constructor. */
		protected JvmState() {
			ClassLoadingMXBean loadBean = ManagementFactory.getClassLoadingMXBean();
			countClassesLoaded = loadBean.getTotalLoadedClassCount();
			countClassesUnloaded = loadBean.getUnloadedClassCount();
			
			CompilationMXBean compBean = ManagementFactory.getCompilationMXBean();
			if (compBean.isCompilationTimeMonitoringSupported()) compilationTimeTotal = compBean.getTotalCompilationTime();
			else compilationTimeTotal = -1;
		}
		
		/**
		* Determines equality based on whether or not obj is a <code>JvmState</code> instance
		* whose every field equals that of this instance.
		*/
		@Override public final boolean equals(Object obj) {	// for why is final, see the essay stored in the file equalsImplementation.txt
			if (this == obj) return true;
			if (!(obj instanceof JvmState)) return false;
			
			JvmState other = (JvmState) obj;
			return
				(this.countClassesLoaded == other.countClassesLoaded)
				&& (this.countClassesUnloaded == other.countClassesUnloaded)
				&& (this.compilationTimeTotal == other.compilationTimeTotal);
		}
		
		/** Returns a value based on all of the fields. */
		@Override public final int hashCode() {	// for why is final, see the essay stored in the file equalsImplementation.txt
			return
				HashUtil.hash(countClassesLoaded)
				^ HashUtil.hash(countClassesUnloaded)
				^ HashUtil.hash(compilationTimeTotal);
		}
		
		/**
		* Returns a <code>String</code> report of the differences, if any, between this instance and other.
		* <p>
		* Contract: the result is never <code>null</code>, but will be zero-length if there is no difference.
		*/
		protected String difference(JvmState other) {
			Check.arg().notNull(other);
			
			StringBuilder sb = new StringBuilder(64);
			
			long loadedDiff = this.countClassesLoaded - other.countClassesLoaded;
			if (loadedDiff < 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append("class load count DECREASED by ").append(-loadedDiff).append(" (IS THIS AN ERROR?)");
			}
			else if (loadedDiff > 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(loadedDiff).append(" classes loaded");
			}
			
			long unloadedDiff = this.countClassesUnloaded - other.countClassesUnloaded;
			if (unloadedDiff < 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append("class unload count DECREASED by ").append(-unloadedDiff).append(" (IS THIS AN ERROR?)");
			}
			else if (unloadedDiff > 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(unloadedDiff).append(" classes unloaded");
			}
			
			long compTimeDiff = this.compilationTimeTotal - other.compilationTimeTotal;
			if (compTimeDiff < 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append("compilation time DECREASED by ").append(-compTimeDiff).append(" ms (IS THIS AN ERROR?)");
			}
			else if (compTimeDiff > 0) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(compTimeDiff).append(" ms of compilation occured");
			}
			
			return sb.toString();
		}
		
	}
	
	// -------------------- Stats (static inner class) --------------------
	
	/**
	* Holds execution time statistics.
	* <p>
	* Every time field has these features:
	* <ol>
	*  <li>Units: seconds</li>
	*  <li>Contract: passes {@link #checkTimeValue checkTimeValue}</li>
	* </ol>
	*/
	public static class Stats {
		
		/** Arithmetic mean of the execution time. */
		protected double mean;
		
		/** Lower bound for {@link #mean}'s confidence interval. */
		protected double meanLower;
		
		/** Upper bound for the {@link #mean}'s confidence interval. */
		protected double meanUpper;
		
// +++ any use for the median?  is robust against outliers, plus could see if it differs radically from the mean as a skewness diagnostic?
// PROBLEM: how does it scale, as in, given block statistics, how do you calculate the median of the individual actions?
// Similarly, I thought about reporting the best and worst times as well, but they have the same issue...
		
		/** Standard deviation of the execution time. */
		protected double sd;
		
		/** Lower bound for the {@link #sd standard deviation}'s confidence interval. */
		protected double sdLower;
		
		/** Upper bound for the {@link #sd standard deviation}'s confidence interval. */
		protected double sdUpper;
		
		/**
		* Records any issues with the standard deviation.
		* <p>
		* Contract: either <code>null</code> else is nonblank.
		*/
		protected String sdIssues;
		
		/**
		* Constructor.
		* <p>
		* @throws IllegalStateException if any parameter violates the contract of the field it is assigned to
		*/
		public Stats(double mean, double meanLower, double meanUpper, double sd, double sdLower, double sdUpper, String sdIssues) throws IllegalStateException {
			checkTimeValue(mean, "mean");
			checkTimeValue(meanLower, "meanLower");
			checkTimeValue(meanUpper, "meanUpper");
			checkTimeValue(sd, "sd");
			checkTimeValue(sdLower, "sdLower");
			checkTimeValue(sdUpper, "sdUpper");
			if ((sdIssues != null) && StringUtil.isBlank(sdIssues)) throw new IllegalStateException("sdIssues != null but is blank");
			
			this.mean = mean;
			this.meanLower = meanLower;
			this.meanUpper = meanUpper;
			this.sd = sd;
			this.sdLower = sdLower;
			this.sdUpper = sdUpper;
			this.sdIssues = sdIssues;
		}
		
		/**
		* Checks that t is a valid time value.
		* <p>
		* @throws IllegalStateException if t is NaN, infinite, or < 0
		*/
		protected void checkTimeValue(double t, String name) throws IllegalStateException {
			if (Double.isNaN(t)) throw new IllegalStateException(name + " = " + t + " is NaN");
			if (Double.isInfinite(t)) throw new IllegalStateException(name + " = " + t + " is infinite");
			if (t < 0) throw new IllegalStateException(name + " = " + t + " < 0");
		}
		
		/** Accessor for {@link #mean}. */
		public double getMean() { return mean; }
		
		/** Accessor for {@link #meanLower}. */
		public double getMeanLower() { return meanLower; }
		
		/** Accessor for {@link #meanUpper}. */
		public double getMeanUpper() { return meanUpper; }
		
		/** Accessor for {@link #sd}. */
		public double getSd() { return sd; }
		
		/** Accessor for {@link #sdLower}. */
		public double getSdLower() { return sdLower; }
		
		/** Accessor for {@link #sdUpper}. */
		public double getSdUpper() { return sdUpper; }
		
		/** Accessor for {@link #sdIssues}. */
		public String getSdIssues() { return sdIssues; }
		
		/**
		* Calculates action statistics from block statistics.
		* <i>This method should only be called if this instance does, in fact, represent block statistics.</i>
		* <p>
		* See the "Block statistics versus action statistics" section of the
		* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
		* <p>
		* @throws IllegalArgumentException if a <= 0
		*/
		public Stats forActions(long a) throws IllegalArgumentException {
			Check.arg().positive(a);
			
			double meanFactor = 1.0 / a;
			double sdFactor = 1.0 / Math.sqrt(a);
			return new Stats(
				getMean() * meanFactor, getMeanLower() * meanFactor, getMeanUpper() * meanFactor,
				getSd() * sdFactor, getSdLower() * sdFactor, getSdUpper() * sdFactor,
				diagnoseSdOfActions(a)
			);
		}
		
		/**
		* See the "Standard deviation outlier model" section of the
		* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
		*/
		protected String diagnoseSdOfActions(double a) {	// CRITICAL: note that I am converting "a" from a long to a double in order to ensure that all math below is carried out in double precision, because I have seen errors with integer arithmetic spilling over into negative values when a gets large enough otherwise
			if (a < 16) return null;
			
				// calculate some of the key quantities
			double muB = getMean();
			double sigmaB = getSd();
			if (sigmaB == 0) return null;
			
			double muA = muB / a;
			double sigmaA = sigmaB / Math.sqrt(a);
			
			double tMin = 0;
			double muGMin = (muA + tMin) / 2;
			assert (muGMin >= 0) : "muGMin = " + muGMin + " < 0" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			double sigmaG = Math.min( (muGMin - tMin) / 4, sigmaA );
			assert (sigmaG >= 0) : "sigmaG = " + sigmaG + " < 0" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			
				// calculate cMax:
			long cMax1 = cMaxSolver(a, muB, sigmaB, muA, sigmaG, tMin);
			long cMax2 = cMaxSolver(a, muB, sigmaB, muA, sigmaG, muGMin);
			long cMax = Math.min(cMax1, cMax2);
			if (cMax == 0) return null;
			
				// calculate the minimum variance caused by the outliers:
			double var1 = varianceOutliers(a, sigmaB, sigmaG, 1);
			double var2 = varianceOutliers(a, sigmaB, sigmaG, cMax);
			long cOutMin;
			double varOutMin;
			if (var1 < var2) {
				cOutMin = 1L;
				varOutMin = var1;
			}
			else {
				cOutMin = cMax;
				varOutMin = var2;
			}
			
				// calculate muG and U at cOutMin just for the diagnostics below
			double varBG_outMin = (sigmaB * sigmaB) - ((a - cOutMin) * (sigmaG * sigmaG));
			double muG_outMin = muA - Math.sqrt( (cOutMin * varBG_outMin) / (a * (a - cOutMin)) );
			assert (muA > muG_outMin) : "muA = " + muA + " <= muG_outMin = " + muG_outMin + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			assert (muG_outMin > 0) : "muG_outMin = " + muG_outMin + " <= 0" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			double U_outMin = muA + Math.sqrt( ((a - cOutMin) * varBG_outMin) / (a * cOutMin) );
			assert (U_outMin > muA) : "U_outMin = " + U_outMin + " <= muA = " + muA + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			
				// issue warning if outlier variance contribution exceeds 1%:
			double fractionVarOutlierMin = varOutMin / (sigmaB * sigmaB);
			assert (fractionVarOutlierMin >= 0) : "fractionVarOutlierMin = " + fractionVarOutlierMin + " < 0" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			String msg;
			if (fractionVarOutlierMin < 0.01) msg = null;
			else if (fractionVarOutlierMin < 0.10) msg = "might be somewhat inflated";
			else if (fractionVarOutlierMin < 0.50) msg = "likely INFLATED";
			else msg = "ALMOST CERTAINLY GROSSLY INFLATED";
			if (msg != null) return
				"--action sd values " + msg + " by outliers" + "\n" +
					"--they cause at least " + (100 * fractionVarOutlierMin) + "% of the measured VARIANCE according to a equi-valued outlier model" + "\n" +
					"--model quantities: a = " + a + ", muB = " + muB + ", sigmaB = " + sigmaB + ", muA = " + muA + ", sigmaA = " + sigmaA +
						", tMin = " + tMin + ", muGMin = " + muGMin + ", sigmaG = " + sigmaG +
						", cMax1 = " + cMax1 + ", cMax2 = " + cMax2 + ", cMax = " + cMax +
						", cOutMin = " + cOutMin + ", varOutMin = " + varOutMin +
						", muG(cOutMin) = " + muG_outMin + ", U(cOutMin) = " + U_outMin;
			
			return null;
		}
		
		/**
		* See the "Computer algorithm" subsection of the
		* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
		*/
		protected long cMaxSolver(double a, double muB, double sigmaB, double muA, double sigmaG, double x) {
			double muA_minus_x = muA - x;
			double k2 = sigmaG * sigmaG;
			double k1 = (sigmaB * sigmaB) - (a * sigmaG * sigmaG) + (a * muA_minus_x * muA_minus_x);
			double k0 = -a * a * muA_minus_x * muA_minus_x;
			double determinant = (k1 * k1) - (4 * k2 * k0);
			long cMax = (long) Math.floor( -2 * k0 / (k1 + Math.sqrt(determinant)) );
			assert (cMax >= 0) : "cMax = " + cMax + " < 0" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			if (cMax > 1) {
				assert ((k2 * cMax * cMax) + (k1 * cMax) + k0 < 0) : "calculated cMax = " + cMax + ", but the inequality fails at cMax" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
				assert ((k2 * (cMax + 1) * (cMax + 1)) + (k1 * (cMax + 1)) + k0 >= 0) : "calculated cMax = " + cMax + ", but the inequality succeeds at cMax + 1" + "; getMean() = " + getMean() + ", getSd() = " + getSd() + ", a = " + a;
			}
			return cMax;
		}
		
		/**
		* See the Equation (45) of the
		* <a href="http://www.ellipticgroup.com/html/benchmarkingArticle.html">article supplement</a> for more details.
		*/
		protected double varianceOutliers(double a, double sigmaB, double sigmaG, double c) {
			return 	((a - c) / a) * ((sigmaB * sigmaB) - ((a - c) * (sigmaG * sigmaG)));
		}
		
		@Override public String toString() {
			return
				"mean = " + FormatUtil.toEngineeringTime(getMean(), 3) + toStringCi(getMean(), getMeanLower(), getMeanUpper()) +
				", sd = " + FormatUtil.toEngineeringTime(getSd(), 3) + toStringCi(getSd(), getSdLower(), getSdUpper());
		}
		
		/** Returns a String description of the confidence interval specified by the parameters. */
		protected String toStringCi(double d, double lower, double upper) {
			if ((lower <= d) && (d <= upper)) {	// usual case
				double diffLower = d - lower;
				double diffUpper = upper - d;
				double diffMax = Math.max(diffLower, diffUpper);
				double diffMin = Math.min(diffLower, diffUpper);
				double asymmetry = (diffMax - diffMin) / diffMin;
				if (asymmetry <= 1e-3) {	// lower and upper form an approximately symmetric interval about d, then use simpler combined +- form
					return " (CI deltas: +-" + FormatUtil.toEngineeringTime(diffLower, 3) + ")";
				}
				else {	// is distinctly asymmetric, must use separate - and + form
					return " (CI deltas: -" + FormatUtil.toEngineeringTime(diffLower, 3) + ", +" + FormatUtil.toEngineeringTime(diffUpper, 3) + ")";
				}
			}
			else {	// weird case: d is outside the interval!
				return " (CI: [" + FormatUtil.toEngineeringTime(lower) + ", " + FormatUtil.toEngineeringTime(upper) + "])";
			}
		}
		
	}
	
	// -------------------- LfsrAdvancer (static inner class) --------------------
	
	/**
	* The {@link #run run} method of this class simply advances the internal state of an {@link Lfsr} instance.
	* See the {@link Lfsr#advance Lfsr.advance} javadocs for why this class is particularly suitable for being assigned to {@link #noiseTask}.
	*/
	public static class LfsrAdvancer implements Runnable {
		
		protected static final long numberTransitions = 1L * 1000L * 1000L;	// causes run to take ~10 ms for typical GHz CPUs
		
		protected Lfsr lfsr = new Lfsr(32);
		
		public void run() {
			lfsr.advance(numberTransitions);
		}
		
		@Override public String toString() { return String.valueOf(lfsr.getRegister()); }	// part of DCE prevention
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/**
		* Results on 2009-04-09 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Benchmark correctly detected the following outlier issues:
			--EXECUTION TIMES APPEAR TO HAVE OUTLIERS
			--this was determined using the boxplot algorithm with median = 2.565 s, interquantileRange = 458.222 ms
			--1 are EXTREME (on the low side): #0 = 0.0 s
			--1 are mild (on the low side): #1 = 1.25 s
			--1 are EXTREME (on the high side): #59 = 5.0 s
			--1 are mild (on the high side): #58 = 3.75 s
		</code></pre>
		*/
		@Test public void test_diagnoseOutliers() throws Exception {
			Benchmark b = new Benchmark();	// use the no-arg constructor to avoid doing a benchmark
			
			b.params = new Params();
			double[] data = new double[b.params.getNumberMeasurements()];
			for (int i = 0; i < data.length; i++) data[i] = 2 + Math.random();	// so, the data is random draws from [2,3]; iqr (the interquartile range--see diagnoseOutliers) should be 0.5
			data[0] = 0;	// but lets change the 1st element into an extreme low outlier
			data[1] = 1.25;	// but lets change the 2nd element into mild low outlier
			data[2] = 3.75;	// but lets change the 3rd element into extreme high outlier
			data[3] = 5;	// but lets change the 4th element into mild high outlier
			
			JvmState jvmState = new JvmState();
			
			b.measurements = new Measurement[data.length];
			for (int i = 0; i < data.length; i++) {
				b.measurements[i] = new Measurement(data[i], jvmState);
			}
			
			b.diagnoseOutliers();
			b.clearUserMsgs();
			Assert.assertNotNull("failed to find outliers in a signal known to have it", b.outlierIssues);
			System.out.println("Benchmark correctly detected the following outlier issues:");
			System.out.println(b.outlierIssues);
		}
		
		/**
		* Results on 2009-04-09 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Benchmark correctly detected the following serial correlation issues:
			--EXECUTION TIMES HAVE SERIAL CORRELATION
			--1 of the 15 autocorrelation function coefficients (r[k]) that were computed are expected to fall outside their 95% CI
			--but found these 3: r[1] is 3.7109230528011716 sigma above its mean, r[3] is 2.4956492069489786 sigma above its mean, r[4] is 2.2879399503263147 sigma above its mean
			--the 95% CI for the r[k] was calculated as mean +- 1.96 sigma (i.e. a Gaussian distribution was assumed)
		</code></pre>
		*/
		@Test public void test_diagnoseSerialCorrelation() throws Exception {
			Benchmark b = new Benchmark();	// use the no-arg constructor to avoid doing a benchmark
			
			b.params = new Params();
			double[] data = new double[b.params.getNumberMeasurements()];
			double scale = 1.0 / data.length;	// i.e. want the linear component of the data below to be at least 50% of the value by the end
			for (int i = 0; i < data.length; i++) data[i] = (scale * i) + Math.random();	// so, the data is 2 parts periodic to 20 parts randomness (i.e. mostly random, but should still detect the correlation caused by the periodicity)
			
			JvmState jvmState = new JvmState();
			
			b.measurements = new Measurement[data.length];
			for (int i = 0; i < data.length; i++) {
				b.measurements[i] = new Measurement(data[i], jvmState);
			}
			
			b.diagnoseSerialCorrelation();
			b.clearUserMsgs();
			Assert.assertNotNull("failed to find serial correlation in a signal known to have it", b.serialCorrelationIssues);
			System.out.println("Benchmark correctly detected the following serial correlation issues:");
			System.out.println(b.serialCorrelationIssues);
		}
		
		/**
		* Results on 2009-08-07 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_11 server jvm):
		* <pre><code>
			Thread.sleep: first = 100.549 ms, mean = 100.575 ms (CI deltas: -31.408 us, +8.119 us), sd = 74.747 us (CI deltas: -72.195 us, +48.902 us) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, execution times may have serial correlation, SD VALUES MAY BE INACCURATE
			Benchmark correctly measured a known execution time task.
		</code></pre>
		*/
		@Test public void test_Benchmark() throws Exception {	// do NOT name this benchmark_Thread_sleep as per the other methods below, instead start it's name with test_ so that it will be executed by my normal JUnit automatic suite run
			final int sleepTime = 100;
			final int m = 10;
			Callable<Long> task = new Callable<Long>() {
				public Long call() throws InterruptedException {
					long time = 0;
					for (int i = 0; i < m; i++) {
						Thread.sleep(sleepTime);
						time = System.currentTimeMillis();
					}
					return time;	// needed to prevent DCE since this is a Callable
				}
			};
			
			Params params = new Params();
			params.setNumberMeasurements(10);	// do this mainly to reduce how long it takes to execute...
			params.setNumberActions(m);
			
			Benchmark benchmark = new Benchmark(task, params);
			System.out.println("Thread.sleep: " + benchmark.toString());
			Assert.assertEquals(sleepTime * 1e-3, benchmark.getMean(), 10e-3);
			System.out.println("Benchmark correctly measured a known execution time task.");
		}
		
		/**
		* Results on 2009-08-27 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_16 server jvm):
		* <pre><code>
			System.currentTimeMillis: first = 25.889 ns, mean = 21.924 ns (CI deltas: -6.716 ps, +18.038 ps), sd = 322.107 ns (CI deltas: -169.332 ns, +353.907 ns) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
		</code></pre>
		*/
		@Test public void benchmark_System_currentTimeMillis() throws Exception {
			final int n = 1000 * 1000;
			Callable<Long> task = new Callable<Long>() {
				public Long call() {
					long sum = 0;
					for (int i = 0; i < n; i++) {
						sum += System.currentTimeMillis();
					}
					return sum;	// needed to prevent DCE since this is a Callable
				}
			};
			
			System.out.println("System.currentTimeMillis: " + new Benchmark(task, n));
		}
		
		/**
		* Results on 2009-08-27 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_16 server jvm):
		* <pre><code>
			System.nanoTime: first = 203.424 ns, mean = 199.469 ns (CI deltas: -120.862 ps, +110.777 ps), sd = 1.298 us (CI deltas: -199.032 ns, +286.868 ns) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
		</code></pre>
		*/
		@Test public void benchmark_System_nanoTime() throws Exception {
			final int n = 1000 * 1000;
			Callable<Long> task = new Callable<Long>() {
				public Long call() {
					long sum = 0;
					for (int i = 0; i < n; i++) {
						sum += System.nanoTime();
					}
					return sum;	// needed to prevent DCE since this is a Callable
				}
			};
			
			System.out.println("System.nanoTime: " + new Benchmark(task, n).toStringFull());
		}
		
	}
	
}
