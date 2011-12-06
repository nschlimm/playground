/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

--JUnit javadocs:
	http://junit.org/junit/javadoc/4.5/
	http://junit.sourceforge.net/javadoc_40/index.html

--issues with JUnit that affect this class:
	RunListener.testStarted(Description description) inadequate
	http://sourceforge.net/tracker/index.php?func=detail&aid=2090298&group_id=15278&atid=365278
	
	javadoc for RunListener.testIgnored needs clarification
	http://sourceforge.net/tracker/index.php?func=detail&aid=2093264&group_id=15278&atid=365278
	
	RunListener.testIgnored needs to supply the reason
	http://sourceforge.net/tracker/index.php?func=detail&aid=2094290&group_id=15278&atid=365278
	
	javadoc for RunListener.testFailure needs clarification
	http://sourceforge.net/tracker/index.php?func=detail&aid=2093267&group_id=15278&atid=365278
	
	need details about run start/stop for timing purposes
	http://sourceforge.net/tracker/index.php?func=detail&aid=2093247&group_id=15278&atid=365278
	
	Description.getChildren return type not generic
	http://sourceforge.net/tracker/index.php?func=detail&aid=2090304&group_id=15278&atid=365278
	
	Failure.getException misnamed
	http://sourceforge.net/tracker/index.php?func=detail&aid=2094304&group_id=15278&atid=365278
	
	Request.filterWith has bizarre behavior
	http://sourceforge.net/tracker/index.php?func=detail&aid=2094316&group_id=15278&atid=365278
	
	Filter.describe is unnecessary; should be toString
	http://sourceforge.net/tracker/index.php?func=detail&aid=2094343&group_id=15278&atid=365278
	
	Assert.assertArrayEquals for doubles needed
	http://sourceforge.net/tracker/index.php?func=detail&aid=2095139&group_id=15278&atid=365278
	
+++ email junit guys:
	--need a way to support multiple failures per test method
	(e.g. if loop thru several tests, each of which should fails;
	the tests may use common resources that are annoying to have to put into fields)
	--@Test(expected=Exception.class) only covers failure of the entire method
	--adding Assert.assertFails(Callable) would work, altho this would mean packaging all code into a Callable
	--not sure of a good solution besides doing the annoying old fashioned
		@Test public void aTestMethod() {
			for (...) {
				try {
					...
					Assert.fail(...);
				}
				catch (Exception e) {
					// ignore, since expected it to occur
				}
			}
		}

+++ WARNING: I think that this class fails to handle parameterized tests correctly, that is, the new
	@RunWith(value = Parameterized.class)
functionality added to JUnit 4

+++ use this project to speed up the tests:
	https://parallel-junit.dev.java.net/
Problem: doing things in parallel would screw up the benchmarking test methods, and maybe others too...
*/

package bb.util;

import bb.gui.Sounds;
import bb.io.ConsoleUtil;
import bb.io.DirUtil;
import bb.io.FileUtil;
import bb.io.filefilter.ClassFilter;
import bb.science.FormatUtil;
import bb.util.logging.LogUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
* Discovers and autoexecutes all the <a href="http://junit.sourceforge.net">JUnit 4</a> test methods
* (i.e. those annotated <code>{@link Test}</code>) found in the class files of some directory tree.
* Offers filtering to fine tune which packages, classes, and methods
* in that directory tree are actually run.
* <p>
* <b>Note:</b> you should use at least JUnit 4.5 with this class,
* as there were bugs in earlier versions that were finally fixed in 4.5
* (Early incarnations of this class coded around these bugs with hack code,
* but that hack code has now been removed, which leaves you vulnerable if you use older JUnit versions.)
* <p>
* While executing test methods, a {@link PrintStreamFeedback} instance
* and a {@link AudioFeedback} are used to provide feedback.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
*/
public class JUnitExecutor {
	
	// -------------------- switch constants --------------------
	
	private static final String root_key = "-root";
	private static final String packages_key = "-packages";
	private static final String classes_key = "-classes";
	private static final String methods_key = "-methods";
	
	/** Specifies all the switch keys which can legally appear as command line arguments to {@link #main main}. */
	private static final List<String> keysLegal = Arrays.asList(
		root_key, packages_key, classes_key, methods_key
	);
	
	// -------------------- misc constants --------------------
	
	private static final File testOutputDirectory = new File(LogUtil.getLogDirectory(), "testOutput");
	static {
		DirUtil.ensureExists(testOutputDirectory);
	}
	
	// -------------------- instance fields --------------------
	
	private final FindTestEvents findTestEvents = new FindTestEvents();
	private final TestRunEvents testRunEvents = new TestRunEvents();
	
	// -------------------- main and helper methods --------------------
	
	/**
	* Creates a new <code>JUnitExecutor</code> instance and calls its <code>{@link #run run}</code> method.
	* <p>
	* The <code>root</code> param in that call to <code>run</code>
	* is specified as a key/value pair in <code>args</code> of the form
	* <code>-root <i>pathToRootDirectory</i></code>.
	* <p>
	* The <code>pcmFilter</code> param in that call to <code>run</code>
	* is specified using any combination of these filters:
	* <ol>
	*  <li>
	*		a key/value pair in <code>args</code> of the form
	*		<code>-packages <i>listOfPackageNames</i></code>,
	*		for example <code>-packages bb.io,bb.util</code> or <code>-packages bb.*</code>,
	*		to restrict the running of test methods to only classes whose packages are in the list
	*  </li>
	*  <li>
	* 		a key/value pair in <code>args</code> of the form
	*		<code>-classes <i>listOfSimpleClassNames</i></code>,
	*		for example <code>-classes Benchmark,Bootstrap</code> or
	*		<code>-classes ArrayUtil\$UnitTest,Benchmark\$UnitTest</code> or
	*		<code>-classes B.*</code>,
	*		to restrict the running of test methods to only classes whose <i>simple</i> names (no package part)
	*		are in the list
	*  </li>
	*  <li>
	* 		a key/value pair in <code>args</code> of the form
	*		<code>-methods <i>listOfMethodNames</i></code>,
	*		for example <code>-classes test_concatenate,test_shuffle</code> or <code>-classes te.*</code>,
	*		to restrict the running of test methods to only those whose names
	*		are in the list
	*  </li>
	* </ol>
	* A given test method is only run if it passes every filter (i.e. there is an implied AND of all the filters).
	* <p>
	* For each filter, the value must be a single comma separated list with no spaces in the list.
	* The list is parsed into elements, <i>each of which is interpreted as a
	* <a href="http://java.sun.com/javase/6/docs/api/java/util/regex/Pattern.html#sum">java regular expression</a></i>.
	* So, wildcards can be used as in the examples above.
	* A given test method is run if it matches at least one element in the list (i.e. there is an implied OR of all the list elements).
	* <p>
	* If this method is this Java process's entry point (i.e. first <code>main</code> method),
	* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
	* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
	* Otherwise, this method returns and leaves the JVM running.
	*/
	public static void main(final String[] args) throws Exception {
		Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
			Check.arg().notEmpty(args);
			
			Properties2 switches = new Properties2(args).checkKeys(keysLegal);
			
			String root = switches.getProperty(root_key);
			Set<Pattern> packageReqs = parseList(packages_key, switches);
			Set<Pattern> classReqs = parseList(classes_key, switches);
			Set<Pattern> methodReqs = parseList(methods_key, switches);
			PcmFilter pcmFilter = new PcmFilter(packageReqs, classReqs, methodReqs);
			
			JUnitExecutor junitExecutor = new JUnitExecutor();
			
			System.out.println();
			System.out.println("========== Miscellaneous console output (typical sources: test run events, various ConsoleLoggers, etc) ==========");
			System.out.println();
			junitExecutor.run(root, pcmFilter);
			
			System.out.println();
			System.out.println("========== Summary of all find test events ==========");
			System.out.println();
			System.out.println( junitExecutor.findTestEvents.getEvents() );
			
			System.out.println();
			System.out.println("========== Summary of all test run events ==========");
			System.out.println();
			System.out.println( junitExecutor.testRunEvents.getEvents() );
			
			return null;
		} } );
	}
	
	private static Set<Pattern> parseList(String key, Properties2 switches) throws Exception {
		Set<Pattern> set = null;
		if (switches.containsKey(key)) {
			set = new LinkedHashSet<Pattern>();	// use LinkedHashSet to guarantee that the iteration order is the same order as what the user specified on the command line
			String[] requirements = switches.getProperty(key).split(",", -1);
			for (String regex : requirements) {
				Pattern pattern = Pattern.compile(regex);
				set.add(pattern);
			}
		}
		return set;
	}
	
	// -------------------- constructor --------------------
	
	/** Constructor. */
	public JUnitExecutor() {}
	
	// -------------------- run and helper methods --------------------
	
	/**
	* Determines all the classes underneath <code>root</code> which contain JUnit 4 test methods
	* (i.e. those which are annotated <code>@{@link Test}</code>)
	* that pass <code>pcmFilter</code>.
	* Then runs those tests using <code>JUnit 4</code>.
	* <p>
	* <i>Note that <code>root</code> must be the sole directory which is the package root;
	* it cannot be just any directory which contains the classes.</i>
	* For example, suppose that the directory <code>D:/software/java/projectsMine/bbLibrary/class</code>
	* is the package root which contains class files like <code>A.class</code> and <code>B.class</code>
	* (these would be in the default, unnamed package)
	* as well as subdirectories that contain more class files
	* (these would be in named packages).
	* Then, <code>root</code> must have the value <code>D:/software/java/projectsMine/bbLibrary/class</code>.
	* You would not use higher level directories like
	* <code>D:/software/java/projectsMine/bbLibrary</code> or <code>D:/software/java/projectsMine</code>
	* in this case because they are not the package root.
	* <p>
	* @throws Exception of some kind if any is raised
	*/
	public void run(String root, PcmFilter pcmFilter) throws Exception {
		JUnitCore jUnitCore = new JUnitCore();
		jUnitCore.addListener( new AudioFeedback() );
// +++ write a GuiFeedback?
		jUnitCore.addListener( new PrintStreamFeedback(System.out) );
		jUnitCore.addListener( new StdStreamSwapper() );
		jUnitCore.addListener( testRunEvents );
// +++ require the user to specify all the RunListeners that he wants attached as args to main?
		
		Class[] classesWithTests = findClassesWithJUnitTests(root, pcmFilter);
		Request request = Request.classes(classesWithTests);	// old String 1st param: "classes with Tests in root = " + root,
		request = request.filterWith(pcmFilter);	// Note that pcmFilter is used both here, to cause the JUnit framework to reject methods that should not be run, as well as in the call to findClassesWithJUnitTests above
		
		jUnitCore.run(request);
	}
	
	private Class[] findClassesWithJUnitTests(String root, PcmFilter pcmFilter) throws Exception {
		Set<Class> result = new LinkedHashSet<Class>();
		String pathRoot = getPath( new File(root) );
		File[] classFiles = DirUtil.getFilesInTree( new File(pathRoot), new ClassFilter() );
		try {
			for (File classFile : classFiles) {
				ConsoleUtil.overwriteLine("findClassesWithJUnitTests: " + classFile.getPath());
				
				String packageName = extractPackageName(classFile, pathRoot);
				String className = extractClassName(classFile);
				if (!pcmFilter.shouldRun(packageName, className, null)) continue;
				
				Class c = loadClass(classFile, pathRoot);
				if (c == null) continue;
				
				if (containsJUnitTests(c, pcmFilter)) {
					boolean added = result.add(c);
					if (!added) throw new IllegalStateException("failed to add a Class when expected to");
				}
				else if (needsJUnitTests(c)) {
					findTestEvents.needsTests(c);	// only add if is a top level class
				}
			}
		}
		finally {
			ConsoleUtil.eraseLine();
		}
		return result.toArray( new Class[result.size()] );
	}
	
	/** Returns a standard form for file's path: the separator is always '/' and directories end with a '/'. */
	private String getPath(File file) {
		String path = file.getPath().replace('\\', '/');
		if (file.isDirectory() && !path.endsWith("/")) path += "/";
		return path;
	}
	
	/** Note: returns an empty String if classFile is in the default (unnamed) package. */
	private String extractPackageName(File classFile, String pathRoot) throws IllegalStateException {
		String path = getPath(classFile);
		if (!path.startsWith(pathRoot)) throw new IllegalStateException("path = " + path + " !startsWith pathRoot = " + pathRoot);
		path = path.substring( pathRoot.length() );
		
		int i = path.lastIndexOf('/');
		if (i == -1) return "";
		else if (i == 0) throw new IllegalStateException("classFile = " + classFile.getPath() + " has a path after pathRoot = " + pathRoot + " has been removed which starts with '/' which is illegal");
		else if (i > 0) return path.substring(0, i).replace('/', '.');
		else throw new IllegalStateException("i = " + i + "; happened for classFile = " + classFile.getPath() + " and pathRoot = " + pathRoot);
	}
	
	private String extractClassName(File classFile) {
		return FileUtil.getNameMinusExtension(classFile);
	}
	
	private Class loadClass(File classFile, String pathRoot) throws Exception {
		String path = getPath(classFile);
		if (!path.startsWith(pathRoot)) throw new IllegalStateException("path = " + path + " !startsWith pathRoot = " + pathRoot);
		if (!path.endsWith(".class")) throw new IllegalStateException("path = " + path + " !endsWith .class");
		String classNameFull = path.replace(pathRoot, "").replace(".class", "").replace('/', '.');
		try {
			StdStreams.swap();	// CRITICAL: must call this because have seen class loading cause resources like Loggers to be initialized, and if those Loggers use a ConsoleHandler then they must be initialized with the standard streams swapped to what they will be when PrintStreamFeedback is used below
			return Class.forName(classNameFull);
		}
		catch (Throwable t) {
			findTestEvents.classLoadFailed(classFile, t);
			return null;
		}
		finally {
			StdStreams.unswap(null, null);
		}
	}
	
	private boolean containsJUnitTests(Class c, PcmFilter pcmFilter) {
		for (Method method : c.getMethods()) {
			if (!pcmFilter.shouldRun(null, null, method.getName())) continue;
			
			for (Annotation annotation : method.getAnnotations()) {
				if (annotation instanceof Test) return true;
			}
		}
		return false;
	}
	
	private boolean needsJUnitTests(Class c) {
return false;
// +++ the code below works for MY needs (how I use the UnitTest inner class approach),
// but will not work for people who make separate top level test classes, plus it produces too much output to regularly use.
// Fix this in the future...
/*
		if (c.isInterface()) return false;	// interfaces obviously never need test methods
		if (c.getName().contains("$")) return false;	// inner classes are never considered to need test methods
		for (Class inner : c.getDeclaredClasses()) {
			for (Method method : inner.getMethods()) {
				for (Annotation annotation : method.getAnnotations()) {
					if (annotation instanceof Test) return false;
				}
			}
		}
		return true;
*/
	}
	
	// -------------------- PcmFilter (static inner class) --------------------
	
	/**
	* Filters on the basis of <i>p</i>ackage, <i>c</i>lass, and <i>m</i>ethod names (pcm).
	* <p>
	* This class is multithread safe: every public method is synchronized.
	*/
	private static class PcmFilter extends Filter implements Serializable {
		
		private static final long serialVersionUID = 1;
		
		/** @serial */
		private final Set<Pattern> packageReqs;
		
		/** @serial */
		private final Set<Pattern> classReqs;
		
		/** @serial */
		private final Set<Pattern> methodReqs;
		
		private PcmFilter(Set<Pattern> packageReqs, Set<Pattern> classReqs, Set<Pattern> methodReqs) {
			this.packageReqs = packageReqs;
			this.classReqs = classReqs;
			this.methodReqs = methodReqs;
		}
		
		public synchronized String describe() { return toString(); }
		
		@Override public synchronized String toString() {
			return
				((packageReqs != null) ? "Accepts every package whose name matches all these regular expressions: " + StringUtil.toString(packageReqs, ", ") : "Accepts every package")
				+ ((classReqs != null) ? "; accepts every class whose (simple) name matches all these regular expressions: " + StringUtil.toString(classReqs, ", ") : "; accepts every class")
				+ ((methodReqs != null) ? "; accepts every method whose name matches all these regular expressions: " + StringUtil.toString(methodReqs, ", ") : "; accepts every method");
		}
		
		public synchronized boolean shouldRun(Description description) {
			return shouldRun( new DescriptionFields(description) );
		}
		
		public synchronized boolean shouldRun(DescriptionFields descriptionFields) {
			return shouldRun( descriptionFields.packageName, descriptionFields.className, descriptionFields.methodName );
		}
		
		private boolean shouldRun(String packageName, String className, String methodName) {
			//if ((methodName != null) && methodName.equals("initializationError0")) return true;	// this wass a hack to deal with a JUnit bug that was present UNTIL junit 4.5; removing it for now
			return
				matches(packageName, packageReqs) &&
				matches(className, classReqs) &&
				matches(methodName, methodReqs);
		}
		
		private boolean matches(String s, Set<Pattern> requirements) {
			if (s == null) return true;
			if (requirements == null) return true;
			
			for (Pattern p : requirements) {
				if (p.matcher(s).matches()) return true;
			}
			
			return false;
		}
		
	}
		
	// -------------------- FindTestEvents (static inner class) --------------------
	
	/**
	* Stores all events that occur while finding classes with JUnit tests, from which it can generate a report.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class FindTestEvents extends RunListenerAbstract {
		
		private final Set<Class> classesNeedTests = new LinkedHashSet<Class>();
		private final SortedSet<String> loadFailures = new TreeSet<String>();	// use SortedSet because loadClass will start each entry with the class file's path, so it would be nice if they were listed in order
		
		private FindTestEvents() {}
		
		private synchronized void needsTests(Class c) {
			classesNeedTests.add(c);
		}
		
		private synchronized void classLoadFailed(File classFile, Throwable t) {
			LogUtil.getLogger2().logp(Level.WARNING, "JUnitExecutor.FindTestEvents", "classLoadFailed", "unexpected Throwable caught while trying to load " + classFile.getPath(), t);
			loadFailures.add( classFile.getPath() + ": " + ThrowableUtil.getTypeAndMessage(t) );
		}
		
		private synchronized String getEvents() {
			String events = "";
			if (classesNeedTests.size() > 0) {
				events +=
					"\n"
					+ "The following " + classesNeedTests.size() + " top level classes lack an inner class which has JUnit test methods:" + "\n"
					+ "\t" + StringUtil.toString(classesNeedTests, "\n\t") + "\n";
			}
			if (loadFailures.size() > 0) {
				events +=
					"\n"
					+ "The following " + loadFailures.size() + " Throwables were encountered that caused the corresponding class files to be skipped (their stack traces are above):" + "\n"
					+ "\t" + StringUtil.toString(loadFailures, "\n\t") + "\n";
			}
			return events;
		}
		
	}
	
	// -------------------- AudioFeedback (static inner class) --------------------
	
	/**
	* Provides audio feedback for test events.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class AudioFeedback extends RunListener {
		
		private AudioFeedback() {}
		
		// -------------------- RunListener methods --------------------
		
		public synchronized void testRunStarted(Description description) {}
		
		public synchronized void testStarted(Description description) {
			Sounds.playNotify(false);
		}
		
		public synchronized void testIgnored(Description description) {}
		
		public synchronized void testFailure(Failure failure) {
			Sounds.playErrorMinor();
		}
		
		public synchronized void testFinished(Description description) {}
		
		public synchronized void testRunFinished(Result result) {
			if (result.wasSuccessful()) {
				Sounds.playNotify();
			}
			else {
				Sounds.playErrorMajor();
			}
		}
		
	}
	
	// -------------------- PrintStreamFeedback (static inner class) --------------------
	
	/**
	* Provides console feedback for test events.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class PrintStreamFeedback extends RunListener {
		
		private final PrintStream ps;
		
		private PrintStreamFeedback(PrintStream ps) {
			Check.arg().notNull(ps);
			
			this.ps = ps;
		}
		
		// -------------------- RunListener methods --------------------
		
		public synchronized void testRunStarted(Description description) {
			ps.println("JUnit test run started: " + DescriptionFields.toString(description));
			ps.flush();
		}
		
		public synchronized void testStarted(Description description) {
			ps.println("JUnit test started: " + DescriptionFields.toString(description));
			ps.flush();
		}
		
		public synchronized void testIgnored(Description description) {
// +++ need to find a way to get any message text of the @Ignore
			ps.println("JUnit test ignored: " + DescriptionFields.toString(description));
			ps.flush();
		}
		
		public synchronized void testFailure(Failure failure) {
			ps.println("JUnit test FAILURE:");
			failure.getException().printStackTrace(ps);
			ps.flush();
		}
		
		public synchronized void testFinished(Description description) {
			ps.println("JUnit test finished: " + DescriptionFields.toString(description));
			ps.flush();
		}
		
		public synchronized void testRunFinished(Result result) {
			ps.println("JUnit test run finished: " + result);
			ps.flush();
		}
		
	}
	
	// -------------------- RunListenerAbstract (static abstract inner class) --------------------
	
	/**
	* Defines some common functionality used by a couple of (otherwise very different) RunListener implementations.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private abstract static class RunListenerAbstract extends RunListener {
		
		protected String className;
		protected String methodName;
		
		protected RunListenerAbstract() {}
		
		// -------------------- RunListener methods --------------------
		
		// NONE: each subclass must fully define
		
		// -------------------- helper methods --------------------
		
		/**
		* Parses the class name and method name out of description
		* and assigns the results to {@link #className} and {@link #methodName}.
		* <p>
		* @return true if className is the same as before, false otherwise
		*/
		protected synchronized boolean parseNames(Description description) throws IllegalStateException {
			String classNamePrevious = className;
			DescriptionFields descriptionFields = new DescriptionFields(description);
			className = descriptionFields.getClassNameFull();
			methodName = descriptionFields.methodName;
			return className.equals(classNamePrevious);
		}
		
	}
	
	// -------------------- StdStreamSwapper (static inner class) --------------------
	
	/**
	* Every time that a test method is about to be executed,
	* this intercepts the standard streams (out and err) and captures any output.
	* The last line of each stream's output is continously overwritten to the console
	* to let the user know what the test method is generating.
	* Each stream's complete output is saved to a dedicated file once the test method finishes.
	* This both reduces the console clutter
	* as well as makes it extremely easy to find the output from a given test method.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class StdStreamSwapper extends RunListenerAbstract {
		
		private File classDir;
		private File fileOut;
		private File fileErr;
		
		private StdStreamSwapper() {}
		
		// -------------------- RunListener methods --------------------
		
		public synchronized void testRunStarted(Description description) {
			try {
				className = null;
				methodName = null;
				fileOut = null;
				fileErr = null;
			}
			catch (Throwable t) {
				onProblem("An unexpected Throwable was caught", t);
			}
		}
		
		public synchronized void testStarted(Description description) {
			try {
				boolean sameClass = parseNames(description);
				if (!sameClass) {
					classDir = DirUtil.ensureExists( new File(testOutputDirectory, className) );
				}
				fileOut = new File(classDir, methodName + ".stdOut.txt");
				fileErr = new File(classDir, methodName + ".stdErr.txt");
				StdStreams.swap();	// CRITICAL: after call this method, no point in the code below should use the std streams until testFinished/onProblem calls restoreStdStreams
			}
			catch (Throwable t) {
				onProblem("An unexpected Throwable was caught", t);
			}
		}
		
		/**
		* Note: the Junit 4 javadocs do not describe this method well (as of 2008/5/8),
		* but it currently is called <i>instead of</i> {@link #testStarted testStarted}
		* if the method is annotated with both @Ignore and @Test.
		* In this case, <i>there will be no subsequent call</i> to {@link #testFinished testFinished};
		* only this method is called.
		*/
		public synchronized void testIgnored(Description description) {
			try {
				testStarted(description);
				testFinished(description);
			}
			catch (Throwable t) {
				onProblem("An unexpected Throwable was caught", t);
			}
		}
		
		public synchronized void testFailure(Failure failure) {}
		
		public synchronized void testFinished(Description description) {
			try {
				StdStreams.unswap(fileOut, fileErr);
			}
			catch (Throwable t) {
				onProblem("An unexpected Throwable was caught", t);
			}
		}
		
		public synchronized void testRunFinished(Result result) {}
		
		// -------------------- helper methods --------------------
		
		/**
		* Handles problems generated by the JUnit framework itself (including this class),
		* as opposed to Throwables raised by the test methods
		* (which should get caught by the JUnit framework and passed to {@link #testFailure testFailure}).
		* <p>
		* This method simply calls {@link StdStreams#unswap StdStreams.unswap}
		* and then throws a new RuntimeException made from message and t.
		*/
		private synchronized void onProblem(String message, Throwable t) throws RuntimeException {
			try {
				StdStreams.unswap(fileOut, fileErr);	// CRITICAL: must call this since have no idea what state are in when called
			}
			catch (Throwable t2) {
				LogUtil.getLogger2().logp(Level.SEVERE, "JUnitExecutor.StdStreamSwapper", "onProblem", "unexpected Throwable caught while calling StdStreams.unswap", t2);
			}
			
			throw new RuntimeException(message, t);
		}
		
	}
		
	// -------------------- TestRunEvents (static inner class) --------------------
	
	/**
	* Stores all events which occur during a test run, from which it can generate a report.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class TestRunEvents extends RunListenerAbstract {
		
		private String events;
		
		private long timeRunStartNs;	// keep track ourselves because Result.getRunTime() does not use the high resolution System.nanoTime
		private long timeTestStartNs;
		
		private boolean ignored;
		private Failure failure;
		
		private final SortedMap<String,SortedSet<String>> classToMethodsFailed = new TreeMap<String,SortedSet<String>>();
		
		private TestRunEvents() {}
		
		// -------------------- RunListener methods --------------------
		
		public synchronized void testRunStarted(Description description) {
			events = "JUnit 4 test run started for: " + description.getDisplayName() + "\n";
			className = null;
			methodName = null;
			timeRunStartNs = System.nanoTime();
		}
		
		public synchronized void testStarted(Description description) {
			boolean sameClass = parseNames(description);
			if (!sameClass) {
				events += "--------------------------------------------------" + "\n";
				events += className + "\n";
			}
			events += "\t" + methodName + ": ";
			ignored = false;
			failure = null;
			timeTestStartNs = System.nanoTime();
		}
		
		/**
		* Note: the Junit 4 javadocs do not describe this method well (as of 2008/5/8),
		* but it currently is called <i>instead of</i> {@link #testStarted testStarted}
		* if the method is annotated with both @Ignore and @Test.
		* In this case, <i>there will be no subsequent call</i> to {@link #testFinished testFinished};
		* only this method is called.
		*/
		public synchronized void testIgnored(Description description) {
			testStarted(description);
// +++ need to find a way to get any message text of the @Ignore
			ignored = true;
			testFinished(description);
		}
		
		/**
		* Called <i>in between</i> calls to {@link #testStarted testStarted}
		* and {@link #testFinished testFinished} if a test method fails.
		*/
		public synchronized void testFailure(Failure failure) {
			this.failure = failure;
			
			SortedSet<String> methodsFailed = classToMethodsFailed.get(className);
			if (methodsFailed == null) {
				methodsFailed = new TreeSet<String>();
				classToMethodsFailed.put(className, methodsFailed);
			}
			methodsFailed.add(methodName);
			
			LogUtil.getLogger2().logp(Level.SEVERE, "JUnitExecutor.TestRunEvents", "testFailure", "JUnit test FAILURE:", failure.getException());
		}
		
		public synchronized void testFinished(Description description) {
			long timeTestStopNs = System.nanoTime();
			
			if (!ignored && (failure == null)) events += ": PASSED";
			else if (!ignored && (failure != null)) events += ": FAILED: " + failure.getException().getClass().getSimpleName() + "; full stack trace in default log file" ;
			else if (ignored && (failure == null)) events += ": IGNORED";
			else throw new IllegalStateException("the combination ignored = " + ignored + " && failure = " + failure + " should never happen");
			
			double timeExecS = (timeTestStopNs - timeTestStartNs) * 1e-9;	// e-9 converts ns to s as required below
			events += "; execution time = " + FormatUtil.toEngineeringTime(timeExecS) + "\n";
		}
		
		public synchronized void testRunFinished(Result result) {
			long timeRunStopNs = System.nanoTime();
			double timeRunExecS = (timeRunStopNs - timeRunStartNs) * 1e-9;	// e-9 converts ns to s as needed by FormatUtil.toEngineeringTime below
			String execTime = "; total execution time = " + FormatUtil.toEngineeringTime(timeRunExecS);
			
			events += "--------------------------------------------------" + "\n";
			
			if (result.wasSuccessful()) {
				events += "JUnit 4 test run was successful: all " + result.getRunCount() + " tests passed (" + result.getIgnoreCount() + " tests were ignored)" + execTime + "\n";
			}
			else {
				events += "JUnit 4 test run FAILED: " + result.getFailureCount() + "/" + result.getRunCount() + " tests failed (" + result.getIgnoreCount() + " tests were ignored)" + execTime + "\n";	// WARNING JUnit 4.5 has a nasty bug (fixed in 4.6) in which getRunCount is wrong when tests are ignored: http://sourceforge.net/tracker/index.php?func=detail&aid=2106324&group_id=15278&atid=115278
				events += "\n";
				events += "Summary of each class which failed, followed by a list of that class's failing methods:" + "\n";
				events += "\t" + StringUtil.toString(classToMethodsFailed, "\n\t") + "\n";
			}
			
			if (testOutputDirectory.exists()) {
				try {
					int numberOutputFiles = DirUtil.getContents(testOutputDirectory).length;
					if (numberOutputFiles > 0) {
						events += "\n";
						events += "Note: there are " + numberOutputFiles + " test output files inside " + testOutputDirectory.getPath() + "\n";
					}
				}
				catch (Throwable t) {
					throw ThrowableUtil.toRuntimeException(t);
				}
			}
		}
		
		// -------------------- getEvents --------------------
		
		/**
		* Returns a summary of all the events that have been recorded since the last call of {@link #testRunStarted testRunStarted}.
		* May be called while in the middle of a run to get all the events that have happened so far,
		* or after a run has finished to get a report of all the events.
		* <p>
		* The result is never null or empty.
		* A typical result (once a run has finished) looks like:
		* <pre><code>
			JUnit 4 run started for: All
			--------------------------------------------------
			somepackage.SomeClass$UnitTest
					test_method1: PASSED; execution time = 1 ms
					test_method2: IGNORED; execution time = 2 us
					test_method3: FAILED: java.lang.RuntimeException: unexpected error happened; execution time = 3 ms
			--------------------------------------------------
			anotherpackage.AnotherClass$UnitTest
					test_methodA: PASSED; execution time = 4 ms
					test_methodB: IGNORED; execution time = 5 us
					test_methodC: FAILED: java.lang.RuntimeException: unexpected error happened; execution time = 6 ms
			--------------------------------------------------
			JUnit 4 run FAILED: 2/4 tests failed (2 tests were ignored); total execution time = 100 ms
			
			Note: there are 4 test output files inside ..\log\testOutput
		* </code></pre>
		* <p>
		* @throws IllegalStateException if testRunStarted has never been called
		*/
		private synchronized String getEvents() throws IllegalStateException {
			Check.state().notNull(events);
			
			return events;
		}
		
	}
	
	// -------------------- StdStreams (static inner class) --------------------
	
	/**
	* Swaps and unswaps the standard streams (System.out and System.err)
	* for a pair of {@link BufferWithEcho} instances.
	* <p>
	* This class guarantees that the same replacement streams are always used
	* across all calls to {@link #swap swap} and {@link #unswap unswap}.
	* In the context of the containing class, this behavior is required in order to coordinate
	* the use of this class by {@link #loadClass loadClass} and {@link StdStreamSwapper}.
	* This class satisfies this guarantee by being entirely static based,
	* since that is an easy way to assure singleton behavior.
	* <p>
	* This class is multithread safe: every method is synchronized.
	*/
	private static class StdStreams {
		
		private static final PrintStream stdOutOriginal = System.out;
		private static final PrintStream stdErrOriginal = System.err;
		private static final BufferWithEcho stdOutBuffer = new BufferWithEcho(System.out);
		private static final BufferWithEcho stdErrBuffer = new BufferWithEcho(System.err);

		private static synchronized void swap() {
			if (System.out != stdOutOriginal) throw new IllegalStateException("System.out != stdOutOriginal");
			if (System.err != stdErrOriginal) throw new IllegalStateException("System.err != stdErrOriginal");
			
			stdOutBuffer.reset();	// CRITICAL: must do this before reuse it
			System.setOut( new PrintStream(stdOutBuffer, true) );
			
			stdErrBuffer.reset();	// CRITICAL: must do this before reuse it
			System.setErr( new PrintStream(stdErrBuffer, true) );
		}
		
		/**
		* First unswaps the standard streams to their original objects,
		* then writes any contents of the {@link BufferWithEcho} instances to either the corresponding File args
		* (fileOut and fileErr), if non-null, or else to the original standard stream.
		* <p>
		* @throws IllegalStateException if System.out == stdOutOriginal; System.err == stdErrOriginal
		* @throws Exception if one or more Throwables are raised during execution; the message will contain the stack traces of each one
		*/
		private static synchronized void unswap(File fileOut, File fileErr) throws IllegalStateException, Exception {
			if (System.out == stdOutOriginal) throw new IllegalStateException("System.out == stdOutOriginal");
			if (System.err == stdErrOriginal) throw new IllegalStateException("System.err == stdErrOriginal");
			
			StringBuilder sb = new StringBuilder();
			
			try {
				System.out.flush();	// CRITICAL: only flush, do NOT close, since may be used again
				System.setOut(stdOutOriginal);
			}
			catch (Throwable t) {
				sb.append( ThrowableUtil.toString(t) );
			}
			
			try {
				System.err.flush();	// CRITICAL: only flush, do NOT close, since may be used again
				System.setErr(stdErrOriginal);
			}
			catch (Throwable t) {
				sb.append( ThrowableUtil.toString(t) );
			}
			
			try {
				stdOutBuffer.eraseLine();	// clear any output that was on the ORIGINAL std out from the calls to BufferWithEcho.echoLastLine (which are implicitly done by every call to its write method)
				if (stdOutBuffer.size() > 0) {
					if (fileOut != null) {
						FileUtil.writeBytes( stdOutBuffer.toByteArray(), fileOut, false );
					}
					else {
						stdOutOriginal.print( new String( stdOutBuffer.toByteArray() ) );
					}
				}
			}
			catch (Throwable t) {
				sb.append( ThrowableUtil.toString(t) );
			}
			
			try {
				stdErrBuffer.eraseLine();	// clear any output that was on the ORIGINAL std err from the calls to BufferWithEcho.echoLastLine (which are implicitly done by every call to its write method)
				if (stdErrBuffer.size() > 0) {
					if (fileErr != null) {
						FileUtil.writeBytes( stdErrBuffer.toByteArray(), fileErr, false );
					}
					else {
						stdErrOriginal.print( new String( stdErrBuffer.toByteArray() ) );
					}
				}
			}
			catch (Throwable t) {
				sb.append( ThrowableUtil.toString(t) );
			}
			
			if (sb.length() > 0) throw new Exception("The following problem(s) occured:" + sb.toString());
		}
		
		/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
		private StdStreams() {}
		
	}
	
	// -------------------- BufferWithEcho (static inner class) --------------------
	
	/**
	* Modifies superclass to echo the last line stored in the buffer to the console
	* after byte(s) are written to it.
	* This gives the user some amount of feedback as to what is going on.
	* <p>
	* The console output is continuously overwritten on the same line in order to reduce clutter,
	* just like the {@link ConsoleUtil} class does.
	* <p>
	* <i>It is critical that this stream be closed when its use is over,</i>
	* since the {@link #close close} method has been overridden to erase the console line
	* that it has been writing to.
	* <p>
	* This class is multithread safe: every public method is synchronized.
	*/
	private static class BufferWithEcho extends ByteArrayOutputStream {
		
		private final PrintStream stdStreamOriginal;
		
		private BufferWithEcho(PrintStream stdStreamOriginal) {
			this.stdStreamOriginal = stdStreamOriginal;
		}
		
		@Override public synchronized void write(int b) {	// Note on this and all other synchronized methods: synchronize on this, because our superclass does
			super.write(b);
			echoLastLine();
		}
		
		@Override public synchronized void write(byte[] b, int off, int len) {
			super.write(b, off, len);
			echoLastLine();
		}
		
		private void echoLastLine() {
			assert (count > 0) : "echoLastLine called when count = " + " is not > 0";
			
			int end = count - 1;	// end is the ending index (non-inclusive) of the last line in buf, and does not include the end of line char(s)
			for ( ; end >= 0; end--) {
				if (!CharUtil.isLineEnd(buf[end])) break;
			}
			++end;	// restore end to the previous line end index (which may be the implicit one just beyond the last valid index of buf)
			
			int start = end - 1;	// start is the starting index (inclusive) of the last line in buf
			for ( ; start >= 0; start--) {
				if (CharUtil.isLineEnd(buf[start])) break;
			}
			++start;	// restore start to the previous NON line end index
			
			byte[] bytes = Arrays.copyOfRange(buf, start, end);
			writeLine( new String(bytes) );
		}
		
		private void eraseLine() {
			writeLine("");
		}
		
		private void writeLine(String line) {
			stdStreamOriginal.print( ConsoleUtil.clearLine );
			stdStreamOriginal.print( StringUtil.keepWithinLength(line, ConsoleUtil.maxCharsPerLine) );
			stdStreamOriginal.flush();
		}
		
		/** Overrides superclass method to additionally erase the console line that this instance has been writing to. */
		@Override public synchronized void close() throws IOException {
			super.close();
			eraseLine();
		}
		
	}
	
	// -------------------- DescriptionFields (static inner class) --------------------
	
	/**
	* Parses and stores fields from a {@link Description}.
	* This class was only introduced because Description fails to offer the necessary API.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
	*/
	private static class DescriptionFields {
		
		private final String packageName;
		private final String className;
		private final String methodName;
		
		public static String toString(Description description) throws IllegalArgumentException {
			return new DescriptionFields(description).toString();
		}
		
		private DescriptionFields(Description description) throws IllegalArgumentException {
			Check.arg().notNull(description);
			
			String s = description.getDisplayName();
			String pkgClName;
			if (s.contains("(")) {
				if (!s.contains(")")) throw new IllegalStateException( "description.getDisplayName() = " + s + " contains'(' but does not contain ')'" );
				String[] tokens = s.split("\\(|\\)", 0);	// matches the chars '(' or ')'; CRITICAL: must use 0 to drop the final empty token that is otherwise produced (e.g. if use -1)
				Check.state().hasSize(tokens, 2);
				pkgClName = tokens[1];
				methodName = tokens[0];
			}
			else {
				pkgClName = s;
				methodName = null;
			}
			int i = pkgClName.lastIndexOf('.');
			if (i == -1) {
				packageName = null;
				className = pkgClName;
			}
			else {
				packageName = pkgClName.substring(0, i);
				className = pkgClName.substring(i + 1);
			}
		}
		
		private String getClassNameFull() {
			if (packageName == null) return className;
			return packageName + "." + className;
		}
		
		public String toString() {
			return
				((packageName != null) ? packageName + "." : "")
				+ className
				+ ((methodName != null) ? "." + methodName : "");
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed--is tested by main
	
}
