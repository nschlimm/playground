/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ write an EmailHandler impl which automatically emails anything that is WARNING or SEVERE?

+++ tell sun that they need to:
 --vastly improve their javadocs on logging issues:
	discussion of root logger
	naming and inheritance
		some props like Level are pushed down; others like handlers are defined just at that node and rely on usePar to work
	discussion of global Logger
	param substitution and what Object[] params really mean
	key/values that can appear in the logging.properties file
		--their JDK example should cite that the Handler stuff is in the approp Handler javadocs
		--give them my file as a better example
package description needs to be the equiv of hunter article

	--FileHandler autonaming using the pattern does not do what want; what want is the classname/Loggername
		--i was forced to write special code inside nsHandlers, but would not have had to if pattern did not have to be specified at cons

	--Handler (and all of its subclasses, like StreamHandler) bugs:
		--many methods lack synchronization
			--does Sun consider this OK? is it because Sun views Handlers as being configured once upon initialization and thereafter not really supposed to really be changing?
			--Sun DOES claim in the Logger javadocs that that class is supposed to be thread safe
			--there is a bug report; status as of 2009-03-23: "Your report has been assigned an internal review ID of 1484784, which is NOT visible on the Sun Developer Network (SDN)."
			--see my HandlerAbstract and HandlerConsole classes

	--generic Handler bugs:
		setFormatter uses newFormatter.getClass() to check for null instead of == null?
			http://jonasboner.com/2006/04/04/the-optimized-null-check-pattern/

	--MemoryHandler
		javadocs say:
			A subclass overrides the log method
		what log method?  do they mean publish?

	--Logger
		--the method
			private synchronized ResourceBundle findResourceBundle(String name) {
		needs to be public
		--the field
			private boolean anonymous
		needs a way that subclasses can set it, say via a constructor (see comments in Logger2))

		javadocs about subclass need to warn about
			setLevel
		because Logger fails to always use the getLevel accessor
		unlike
		where log(LogRecord record) uses getUseParentHandlers() instead of useParentHandlers

*/

package bb.util.logging;

import bb.io.DirUtil;
import bb.io.StreamUtil;
import bb.util.Check;
import bb.util.DateUtil;
import bb.util.ReflectUtil;
import bb.util.ThrowableUtil;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
* Provides static constants and utility methods relating to the <code>java.util.logging</code> package.
* <p>
* The most important functionality offered by this class is the various {@link #makeLogger2(String, String) makeLogger2} methods.
* A default Logger2 may be obtained by calling {@link #getLogger2 getLogger2}.
* The remaining public methods offer all kinds of other services.
* <p>
* The makeLogger2 methods are especially powerful because they can draw on new logging properties supported by this class.
* The next sections of documentation explain the background, motivation, and behavior of these new properties.

* <h4>JDK logging background</h4>

* These resources provide an introduction to the JDK logging facility:
* <ul>
*  <li><a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">Java Logging Technology</a></li>
*  <li><a href="http://java.sun.com/javase/6/docs/technotes/guides/logging/overview.html">Java Logging Overview</a></li>
*  <li><a href="http://java.sun.com/javase/6/docs/api/java/util/logging/package-summary.html">API Specification: java.util.logging</a></li>
*  <li><a href="http://www.oracle.com/technology/pub/articles/hunter_logging.html">Jason Hunter's JDK logging tutorial</a></li>
* </ul>
* <p>
* One characteristic of the <code>java.util.logging</code> package
* is that all named (i.e. non-anonymous) Loggers form a parent/child hierarchy.
* From the {@link LogManager} javadocs:
* <blockquote>
*	Loggers are organized into a naming hierarchy based on their dot separated names. Thus "a.b.c" is a child of "a.b", but "a.b1" and a.b2" are peers.
* </blockquote>
* One reason for this hierarchy is that some behavior (the <code>level</code> and <code>handlers</code> properties)
* in child Loggers can be inherited in some sense from its parents.
* <p>
* Another characteristic of the <code>java.util.logging</code> package
* is that much of this behavior can be specified outside of code.
* In particular, the <code>java.util.logging.config.file</code> {@link System#getProperty(String) System property}
* may be used to point to a logging properties file that specifies default behaviors.
* Consult {@link LogManager} as well as the links above for details.
* A sample logging properties file is the <code>.../script/logging.properties</code> file in this project.

* <h4>Problems with the JDK logging properties</h4>

* First note that the <code>useParentHandlers</code> property is never inherited at all.
* A Logger is by default created with <code>useParentHandlers</code> set to true.
* To set <code>useParentHandlers</code> set to false requires explicit configuration
* (i.e. the logging properties file must have a <code>&lt;logger>.level</code> entry
* where <code>&lt;logger></code> exactly matches the name of the Logger,
* or else there must be Java code somewhere which calls {@link Logger#setUseParentHandlers setUseParentHandlers}).
* <p>
* Unfortunately, even the "inheritable" properties (<code>level</code> and <code>handlers</code>) have issues.
* From <a href="http://www.oracle.com/technology/pub/articles/hunter_logging.html">Jason Hunter's JDK logging tutorial</a>:
* <blockquote>
*	A child inherits the properties of its parent, or its parents parent, going to the root for global default if necessary.
*	Inheritance works a little differently than in object-oriented programs...
*	The tricky part of parentage is understanding that setting a parent property doesn't push the setting on its children.
*	Child loggers have their own settings but at runtime look up the tree for a value if they ever have an unset property.
* </blockquote>
* <p>
* This form of "inheritance" is not a problem for the logging {@link Level}.
* For example, suppose your logging properties file only specifies the property <code>com.level = INFO</code>.
* Next suppose you subsequently only create a Logger named <code>com.xyz</code>,
* and rely only on the logging properties file for configuration (i.e. no explicit configuration in code).
* Then because there is no <code>com.xyz.level</code> property,
* the <code>com.xyz</code> Logger will initially have null for its Level,
* which means that every log request will cause a search up the Logger hierarchy until a Logger with a non-null Level is found.
* (Note that when LogManager registers a Logger,
* it will also create all parent Loggers which have a Level specified in the properties file.)
* Thus, the net effect is that the <code>com.xyz</code> Logger will at runtime always search for its Level,
* which it will find in its immediate parent, the <code>com</code> Logger.
* <p>
* In contrast, this form of "inheritance" may be a huge problem with certain logging Handlers.
* For instance, suppose the <code>com.xyz</code> package in some Java codebase has a bunch of classes inside it,
* and each class needs its own dedicated Logger in order to classify events and so simplify analysis.
* Furthermore, suppose that each of these per class Loggers needs a {@link FileHandler} to persist all logs to a file.
* Now if your logging properties file merely contained the entry <code>com.xyz.handlers = java.util.logging.FileHandler</code>
* what would happen is that <i>only</i> the parent Logger (i.e. the one named <code>com.xyz</code>)
* would have that FileHandler.
* All the child Loggers (e.g. <code>com.xyz.foo</code>, <code>com.xyz.bar</code>, etc)
* would use the same FileHandler instance in their parent (assuming their <code>useParentHandlers</code> is true).
* This fails the per class FileHandler requirement.
* You could achieve it by having dedicated property entries
* (e.g. <code>com.xyz.foo.handlers = java.util.logging.FileHandler</code>, <code>com.xyz.bar.handlers = java.util.logging.FileHandler</code>, etc)
* but this is very cumbersome and bug prone (e.g. if you refactor a class name, you have to remember to change the logging properties file too).

* <h4>New properties supported by this class</h4>

* This class supports these new logging properties:
* <ol>
*  <li>
*		A property named <code>logDirectory-NS</code>.
*		Defines the path of the parent directory of all log files created by this class.
*		This directory will be created if currently unexisting.
*		Log files created by this class include those explicitly created by {@link #makeLogFile makeLogFile},
*		as well as files implicitly created by {@link FileHandler}s
*		specified by the <code>&lt;logger>.handlers-NS</code> property below.
*  </li>
*  <li>
*		A property named <code>&lt;logger>.level-NS</code>.
*		Analogous to the existing <code>&lt;logger>.level</code> property
*		defined in {@link LogManager} but with differences described below.
*  </li>
*  <li>
*		A property named <code>&lt;logger>.handlers-NS</code>.
*		Analogous to the existing <code>&lt;logger>.handlers</code> property
*		defined in {@link LogManager} but with differences described below.
*  </li>
*  <li>
*		A property named <code>&lt;logger>.useParentHandlers-NS</code>.
*		Analogous to the existing <code>&lt;logger>.useParentHandlers</code> property
*		defined in {@link LogManager} but with differences described below.
*  </li>
* </ol>
* <p>
* These properties must be defined in the same logging properties file
* that is used to specify the <code>java.util.logging</code> properties for your project.
* These property names all end with <code>-NS</code> because they are <i>non-standard</i> properties:
* the JDK logging code (e.g. LogManager) does not understand them, only a special class like this one does.
* This class's static initializer will reread any logging properties file and process the above properties.
* Only Loggers created by the {@link #makeLogger2(String) makeLogger2} method
* will have these non-standard properties applied to them.
* <p>
* The <code>&lt;logger>.level-NS</code>, <code>&lt;logger>.handlers-NS</code>, and <code>&lt;logger>.useParentHandlers-NS</code>
* properties were introduced in order to fix the defects with the JDK's logging property "inheritance" noted above.
* These properties behave as follows:
* <ol>
*  <li>
*		<i>the <code>&lt;logger></code> part is actually a name prefix</i>
*		(i.e. the <code>&lt;logger></code> pattern is assumed to end with an implicit * wildcard).
*		For example, specifying <code>com.level-NS = ...</code> will cause both a Logger named <code>com</code>
*		as well as Loggers named <code>com123</code> and <code>com.xyz</code> to match the pattern and be assigned the Level.
*		This wildcard matching thus transends Logger parent/child boundaries.
*		Continuing the example, the <code>com</code> Logger is the parent of the <code>com.xyz</code> Logger
*		but is not the parent of the <code>com123</code> Logger (its parent is the root "" Logger).
*		This contrasts with the existing JDK logging properties, where the <code>&lt;logger></code> part is the full name of some Logger.
*  </li>
*  <li>
*		<i>the <code>&lt;logger></code> part may be a special universal match value of last resort.</i>
*		The value "+" matches any non-empty Logger name if no other prefix matches.
*		For example, if your logging property file only defines <code>+.level-NS = FINE</code>,
*		then every Logger (except the root Logger which has the empty name "")
*		will have FINE as its Level.
*		This pattern allows convenient setting of general behavior.
*  </li>
*  <li>
*		<i>they are inherited by explicit assignment.</i>
*		This is a consequence of the prefix matching described above:
*		every Logger name which matches a prefix has that property applied to it.
*  </li>
* </ol>
* This behavior allows properties for many differently named Loggers
* to all be set at once by specifying the properties of some common name prefix.
* Furthermore, because these properties are explicitly assigned to matching Loggers,
* you can now easily solve the log file per class problem described above.
* <p>
* One major issue with using a name prefix,however, is how to handle multiple matches.
* For example, if only the properties <code>com.level-NS = INFO</code> and <code>com.xyz.level-NS = FINE</code>
* are defined in the logging properties file, which one does a Logger named <code>com.xyz.foo</code> receive?
* After all, both the <code>com</code> and <code>com.xyz</code> prefixes match in this case.
* This class's policy is to use the longest length matching prefix, since that is likely to be the most specific.
* Continuing the example, the <code>com.xyz.foo</code> Logger would be assigned the FINE Level
* since <code>com.xyz</code> is longer than <code>com</code>.
* In the case of multiple "best matches", one is arbitrarily chosen.
* Note that the "+" universal match value mentioned above is only used if no other name prefix matched.

* <h4>Comment on param substitution and reducing logging impact</h4>

* <a href="http://www.oracle.com/technology/pub/articles/hunter_logging.html">Jason Hunter's JDK logging tutorial</a>
*  notes the following:
* <blockquote>
*	In Java we first saw variable substitution used for internationalization and localization,
*	because in those cases simple string concatentation isn't possible.
*	With simple log() methods you could rely on string concatentation like this:
*	<code style="display: block; margin-left: 2em">logger.log(Level.INFO, "Your file was " + size + " bytes long");</code>
*	The problem with this approach is that the runtime must perform the string concatentation
*	before executing the log() method call.
*	That wastes CPU cycles and memory when the logger's going to reject the message
*	for being at too low a level.
*	The solution is to use variable substitution and avoid all concatentation:
*	<code style="display: block; margin-left: 2em">logger.log(Level.INFO, "Your file was {0} bytes long", size);</code>
*	This call executes directly and the substitution only happens after the message
*	has passed the logger's quick level check.
* </blockquote>
* This seems like a really clever use of param substitution,
* but it is not obvious that will actually reduce the cpu impact of logging very much.
* Reason: it is almost impossible to use params in one of the log/logp methods of Logger
* without causing an Object[] to be created.
* (The sole exception is the rare situation when all the params that you need are already in an array.)
* Thus, you always take a hit from creating that Object[], which is about as bad as creating a new String.
* Furthermore, if the level check is passed, then the work that needs to be done to actually carry out
* the param substitution is somewhat greater than doing simple String concatenation.
* So, you may not gain much performance.
* Since param substitution makes your code look even worse,
* you should only use param substitution for what it was originally designed for,
* namely, to support pre-localized generic messages that can subsequently be localized
* and then have params substituted in.
* This is discussed further <a href="http://blogs.sun.com/CoreJavaTechTips/entry/logging_localized_message">here</a>.

* <h4>Multithread safety</h4>

* This class is multithread safe: most of its immediate state is immutable,
* and the deep state of its fields is also either immutable or is multithread safe.
* The sole mutable field is {@link #logger2_default}, which is protected by synchronized access.
* <p>
* @author Brent Boyer
*/
public final class LogUtil {
	
	// -------------------- constants --------------------
	
	private static final Level[] levels;
	static {
		levels = new Level[] {
			Level.ALL, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST,
			Level.INFO, Level.OFF, Level.SEVERE, Level.WARNING
		};
		Arrays.sort(levels, new Comparator<Level>() {
			public int compare(Level a, Level b) {
				return a.intValue() - b.intValue();
			}
		} );
// +++ in the future, if can get sun to make Level implement Comparable, could simply do Arrays.sort(levels)...
// see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6405743
	}
	
	private static final String logDirectory_key = "logDirectory-NS";
	
	private static final String logDirectory_valueDefault = "../log/";
	
	/**
	* Directory where log files are to be stored.
	* <p>
	* Internally used by this class's {@link #getPath(String)} method,
	* and therefore implicitly used by the various makeLogger2 methods.
	* May be used by external users in arbitrary ways.
	* <p>
	* Contract: is never null, and always exists.
	*/
	private static final File logDirectory;
	
	/**
	* Serves as the universal matching pattern of last resort.
	* See the class javadocs concerning the <code>-NS</code> properties as well as {@link NodeProp#findBest findBest}.
	*/
	private static final String nsPatternUniversalMatch = "+";
	
	private static final String nsSuffix = "-NS";
	
	private static final ConcurrentMap<String,NodeProp> nameToNodeProps = new ConcurrentHashMap<String,NodeProp>();
	
	// -------------------- static fields --------------------
	
	/**
	* A default Logger2 instance that any user of this class may draw on via {@link #getLogger2 getLogger2}
	* if they do not want to bother creating their own Logger.
	* This Logger always has the name "default".
	* <p>
	* Note: while {@link Logger#global} is a similar default Logger,
	* there are several reasons why this field was introduced:
	* <ol>
	*  <li>
	*		as of JDK 1.5, sun, in their infinite wisdom, chose to not initialize Logger.global:
	*		the programmer must manually initialize it via a call to Logger.getLogger("global") which is annoying;
	*		in contrast, getLogger2 lazy initializes this field
<!--
+++ Note: jdk 1.6 deprecates Logger.glogal and jdk 1.7 introduces Logger.getGlobal():
	http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6476146
so eliminate this when 1.7 comes out?
-->
	*  </li>
	*  <li>
	*		since getLogger2 initializes this field by a call to <code>{@link #makeLogger2 makeLogger2}("default")</code>,
	*		this means that the logging properties file can specify <code>-NS</code> properties
	*		for this field (use the Logger name "default");
	*		this is particularly valuable if want it to write to a log file
	*		located in {@link #logDirectory}, and with a timestamp in its file name.
	*  </li>
	*  <li>this field is a Logger2 instance because of that subclass's advanced features.</li>
	* </ol>
	*/
	private static Logger2 logger2_default;
	
	// -------------------- static initializer and helper methods: loadProperties, extract_logDirectory, init_nameToNodeProps --------------------
	
	static {
		try {
			Properties properties = loadProperties();
			logDirectory = extract_logDirectory(properties);
			init_nameToNodeProps(properties);
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	private static Properties loadProperties() throws Exception {
		InputStream in = null;
		try {
			Properties properties = new Properties();
			
			String configFile = System.getProperty("java.util.logging.config.file");
			if (configFile != null) {
				File file = new File(configFile);
				Check.state().validFile(file);
				in = new BufferedInputStream( new FileInputStream(file) );
				properties.load(in);
			}
			
			return properties;
		}
		finally {
			StreamUtil.close(in);
		}
	}
	
	private static File extract_logDirectory(Properties properties) throws SecurityException, IllegalStateException {
		String path = properties.getProperty(logDirectory_key, logDirectory_valueDefault);
		File directory = new File( path );
		DirUtil.ensureExists(directory);
		return directory;
	}
	
	private static void init_nameToNodeProps(Properties properties) throws IllegalStateException, ClassNotFoundException {
		for (Object obj : properties.keySet()) {
			String key = (String) obj;
			if (key.equals(logDirectory_key)) continue;
			if (!key.endsWith(nsSuffix)) continue;
			
			int lengthRetain = key.length() - nsSuffix.length();
			String keyEssential = key.substring(0, lengthRetain);	// strip off nsSuffix
			if (properties.containsKey(keyEssential)) throw new IllegalStateException("duplication: properties contains key = " + key + ", which has keyEssential = " + keyEssential + ", which is already defined in properties");
			
			int indexLastDot = keyEssential.lastIndexOf('.');
			if (indexLastDot == -1) throw new IllegalStateException("the logging properties specifies the key " + key + " which fails to contain a period inside it");
			String name = keyEssential.substring(0, indexLastDot);
			String type = keyEssential.substring(indexLastDot + 1);
			
			NodeProp nodeProp = nameToNodeProps.get(name);
			if (nodeProp == null) {
				nodeProp = new NodeProp();
				nameToNodeProps.put(name, nodeProp);
			}
			
			if (type.equals("handlers")) {
				String value = properties.getProperty(key).trim();
				String[] classNames = value.split("(\\s+|\\s*,\\s*)", -1);
				Set<Class<? extends Handler>> classes = new LinkedHashSet<Class<? extends Handler>>( classNames.length );
				for (String className : classNames) {
					boolean added = classes.add( Class.forName(className).asSubclass(Handler.class) );
					if (!added) throw new IllegalStateException("className = " + className + " was specified more than once in the list for " + key + " in the logging properties");
				}
				nodeProp.handlerClasses = classes;
			}
			else if (type.equals("level")) {
				String value = properties.getProperty(key).trim();
				nodeProp.level = parseLevel(value);
			}
			else if (type.equals("useParentHandlers")) {
				String value = properties.getProperty(key).trim();
				nodeProp.useParentHandlers = Boolean.valueOf(value);
			}
			else throw new IllegalStateException("the logging properties specifies the key " + key + " whose final type part (" + type + ") is an illegal value");
		}
	}
	
	// -------------------- accessors --------------------
	
	/**
	* Returns an array of all the possible Levels, sorted into ascending order (according to {@link Level#intValue Level.intValue}).
	* <p>
	* Contract: the result is a clone of an internal field, so the caller may safely modify the result.
	*/
	public static Level[] getLevels() { return levels.clone(); }
	
// +++ in the future, add a getConfiguration method like the currentConfiguration method described here:
// http://www.forward.com.au/javaProgramming/javaGuiTips/javaLoggingDebugging.html
	
	/** Accessor for {@link #logDirectory}. */
	public static File getLogDirectory() { return logDirectory; }
	
	/**
	* Accessor for {@link #logger2_default}.
	* Lazy initializes it if necessary.
	* <p>
	* @throws IllegalStateException if a Logger with the name "default" already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static synchronized Logger2 getLogger2() throws IllegalStateException, SecurityException, RuntimeException {
		if (logger2_default == null) logger2_default = makeLogger2("default");
		return logger2_default;
	}
	
	// -------------------- makeLogFile, makeLogWriter --------------------
	
	/**
	* Returns a new File instance which points to a location inside the {@link LogUtil#getLogDirectory log directory}.
	* <p>
	* <i>The childPath arg is interpreted as a path relative to the log directory.</i>
	* It can be either a simple file name, or it can include one or more subdirtories before the file name.
	* The only restriction in the latter case is that the canonical path that it resolves to must be inside the log directory
	* (i.e. using ".." to move out of the log directory will be dtected and rejected).
	* <p>
	* @return a new File constructed from childPath
	* @throws IllegalArgumentException if childPath is {@link Check#notBlank blank}; childPath resolves to a path that falls outside the log directory
	* @throws SecurityException if a security manager exists and denies read access to the file
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static File makeLogFile(String childPath) throws IllegalArgumentException, SecurityException, RuntimeException {
		try {
			Check.arg().notBlank(childPath);
			
			File file = new File( logDirectory, childPath );
			if (!file.getCanonicalPath().startsWith(logDirectory.getCanonicalPath())) throw new IllegalArgumentException("childPath = " + childPath + " produces the canonical path " + file.getCanonicalPath() + " which lies outside " + logDirectory.getCanonicalPath());
			return file;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	/** Returns <code>{@link #makeLogWriter(String, boolean) makeLogWriter}(prefix, false)</code> (i.e. no auto flushing). */
	public static PrintWriter makeLogWriter(String prefix) throws IllegalArgumentException, RuntimeException {
		return makeLogWriter(prefix, false);
	}
	
	/** Returns <code>{@link #makeLogWriter(File, String, boolean) makeLogWriter}({@link #logDirectory}, prefix, autoFlush)</code> (i.e. the default log directory is used). */
	public static PrintWriter makeLogWriter(String prefix, boolean autoFlush) throws IllegalArgumentException, RuntimeException {
		return makeLogWriter(logDirectory, prefix, false);
	}
	
	/**
	* Returns a new PrintWriter that ultimately writes to a file located in directory.
	* <p>
	* The PrintWriter immediately writes to a BufferedWriter to ensure top performance,
	* and that in turn writes to an OutputStreamWriter which uses the platform's default Charset.
	* <p>
	* The file's name starts with prefix,
	* and ends with an underscore ('_') followed by a timestamp followed by the extension ".txt".
	* So, the complete filename is typically unique each time that this method is called,
	* which obviates the need for a file append mode (this method always overwrites the file if it already exists).
	* <p>
	* @param directory the parent directory of the file that will be written to by the result
	* @param prefix the prefix of the filename that the result will write to
	* @param autoFlush specifies wheter or not automatic flushing is enabled in the result
	* @throws IllegalArgumentException if prefix is {@link Check#notBlank blank}
	* @throws RuntimeException (or some subclass) if any other probolem occurs
	*/
	public static PrintWriter makeLogWriter(File directory, String prefix, boolean autoFlush) throws IllegalArgumentException, RuntimeException {
		Check.arg().validDirectory(directory);
		Check.arg().notBlank(prefix);
		
		try {
			String suffix = "_" + DateUtil.getTimeStampForFile() + ".txt";
			File file = new File( directory, prefix + suffix );
			return new PrintWriter( new BufferedWriter( new OutputStreamWriter( new FileOutputStream(file) ) ), autoFlush );	// the default buffer size of 8192 appears to be optimal; see .../filePrograms/src/BenchmarkFileWriting.java
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- makeLogger2 --------------------
	
	/**
	* Returns <code>{@link #makeLogger2(Class, String) makeLogger2}(c, null) )</code>.
	* <p>
	* @throws IllegalArgumentException if c == null
	* @throws IllegalStateException if a Logger with getName(c, null) already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 makeLogger2(Class c) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		return makeLogger2(c, null);
	}
	
	/**
	* Returns <code>{@link #makeLogger2(String) makeLogger2}( {@link #getName getName}(c, suffix) )</code>.
	* <p>
	* @throws IllegalArgumentException if c == null
	* @throws IllegalStateException if a Logger with getName(c, suffix) already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 makeLogger2(Class c, String suffix) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		return makeLogger2( getName(c, suffix) );
	}
	
	/**
	* Returns <code>{@link #makeLogger2(String, String) makeLogger2}( name, null )</code>.
	* <p>
	* @throws IllegalArgumentException if name is blank
	* @throws IllegalStateException if a Logger with name already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 makeLogger2(String name) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		return makeLogger2( name, null );
	}
	
	/**
	* Returns a new Logger2 created by a call to
	* <code>{@link Logger2#getLogger2(String, String) Logger2.getLogger2}(name, resourceBundleName)</code>.
	* So, only if this is the first Logger with name known by LogManager is it created.
	* The result is a {@link Logger2} instance because of that subclass's advanced features.
	* <p>
	* If the Logger2 is created, then {@link #nsLevel nsLevel},
	* {@link #nsHandlers nsHandlers}, and {@link #nsUseParentHandlers nsUseParentHandlers} are called on it.
	* <p>
	* No other configuration is subsequently performed
	* (e.g. for the Level or Formatter of Logger2's handlers; all are left with their default settings,
	* such as whatever has been specified by some logging properties file).
	* <p>
	* This method only throws unchecked Exceptions so that it can be used to initialize a field at its declaration.
	* <p>
	* @throws IllegalArgumentException if name is blank
	* @throws IllegalStateException if a Logger with name already exists
	* @throws SecurityException if a security manager exists and if the caller does not have LoggingPermission("control")
	* @throws RuntimeException (or some subclass) if any other error occurs; this may merely wrap some other underlying Throwable
	*/
	public static Logger2 makeLogger2(String name, String resourceBundleName) throws IllegalArgumentException, IllegalStateException, SecurityException, RuntimeException {
		// name, resourceBundleName checked by new Logger2 below
		
		try {
			Logger2 logger2 = Logger2.getLogger2(name, resourceBundleName);
			nsLevel(logger2);
			nsHandlers(logger2);
			nsUseParentHandlers(logger2);
			return logger2;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- getName, getPath --------------------
	
	/**
	* Determines an appropriate Logger name for the arguments.
	* The fully qualified name of c (i.e. package included) always appears first.
	* If suffix is non-null, then a '_' followed by suffix is appended.
	* Examples:
	* <ul>
	*  <li>c = java.lang.Double and suffix = null ==> "java.lang.Double"</li>
	*  <li>c = java.lang.Float and suffix = "diagnostics" ==> "java.lang.Float_diagnostics"</li>
	* </ul>
	* <p>
	* Motivation: Loggers are often static fields of a class,
	* so it is convenient to simply supply the Class object and have this method determine the name.
	* The suffix arg allows optional further discrimination
	* (e.g. a class may have multiple loggers for different kinds of events,
	* or, if each instance has its own Logger, then each one's name should have some instance specific part).
	* <p>
	* @throws IllegalArgumentException if c == null
	*/
	public static String getName(Class c, String suffix) throws IllegalArgumentException {
		Check.arg().notNull(c);
		
		if (suffix != null) return c.getName() + "_" + suffix;
		else return c.getName();
	}
	
	/**
	* Returns <code>{@link #getPath getPath}( {@link #getName getName}(c, suffix) )</code>.
	* <p>
	* @throws IllegalArgumentException if c == null
	* @throws SecurityException if a security manager exists and its SecurityManager.checkWrite method does not permit directoryLogDefault and all necessary parent directories to be created
	* @throws IllegalStateException if an attempt to create directoryLogDefault is made but fails
	*/
	public static String getPath(Class c, String suffix) throws IllegalArgumentException, SecurityException, IllegalStateException {
		return getPath( getName(c, suffix) );
	}
	
	/**
	* Returns an appropriate path to a log file for name.
	* <p>
	* This is typically used with {@link #makeLogger2(Class, String)}.
	* The result is located in the directory returned by {@link #getLogDirectory getLogDirectory}
	* (see javadocs for possible side effects), and consists of name, a '_', a timestamp, and then ".log".
	* <p>
	* @throws IllegalArgumentException if name is blank
	* @throws SecurityException if a security manager exists and its SecurityManager.checkWrite method does not permit directoryLogDefault and all necessary parent directories to be created
	* @throws IllegalStateException if an attempt to create directoryLogDefault is made but fails
	*/
	public static String getPath(String name) throws IllegalArgumentException, SecurityException, IllegalStateException {
		Check.arg().notBlank(name);
		
		String filename = name + "_" + DateUtil.getTimeStampForFile() + ".log";
		File path = new File(getLogDirectory(), filename);
		return path.getPath();
	}
	
	// -------------------- nsXXX --------------------
	
	/**
	* Calls {@link NodeProp#findBest NodeProp.findBest} to get the NodeProp
	* whose associated key  best matches logger's name and whose level field is non-null.
	* If a non-null result is found, then its level field is assigned to logger.
	* <p>
	* @throws IllegalArgumentException if logger == null
	* @throws Exception (or some subclass) if some other problem occurs
	*/
	public static void nsLevel(Logger logger) throws IllegalArgumentException, Exception {
		Check.arg().notNull(logger);
		
		NodeProp nodeProp = NodeProp.findBest( logger.getName(), "level" );
		if (nodeProp == null) return;
		
		logger.setLevel( nodeProp.level );
	}
	
	/**
	* Calls {@link NodeProp#findBest NodeProp.findBest} to get the NodeProp
	* whose associated key  best matches logger's name and whose handlerClasses field is non-null.
	* If a non-null result is found, then its handlerClasses field is used to create new Handler instances that are added to logger.
	* <p>
	* Special case: if a {@link FileHandler} Class is encountered,
	* its String arg constructor is called instead of its default constructor.
	* The param passed to that constructor is a call to {@link #getPath getPath}( logger.getName() ).
	* <p>
	* @throws IllegalArgumentException if logger == null
	* @throws Exception (or some subclass) if some other problem occurs
	*/
	public static void nsHandlers(Logger logger) throws IllegalArgumentException, Exception {
		Check.arg().notNull(logger);
		
		NodeProp nodeProp = NodeProp.findBest( logger.getName(), "handlerClasses" );
		if (nodeProp == null) return;
		
		for (Class<? extends Handler> c : nodeProp.handlerClasses) {
			if (c == FileHandler.class) {
				String path = getPath( logger.getName() );
				Constructor<? extends Handler> constructor = c.getConstructor(String.class);
				logger.addHandler( constructor.newInstance(path) );
			}
			else {
				logger.addHandler( c.newInstance() );
			}
		}
	}
	
	/**
	* Calls {@link NodeProp#findBest NodeProp.findBest} to get the NodeProp
	* whose associated key  best matches logger's name and whose useParentHandlers field is non-null.
	* If a non-null result is found, then its useParentHandlers field is assigned to logger.
	* <p>
	* If an appropriate match is found, then its useParentHandlers boolean value is assigned to logger.
	* <p>
	* @throws IllegalArgumentException if logger == null
	* @throws Exception (or some subclass) if some other problem occurs
	*/
	public static void nsUseParentHandlers(Logger logger) throws IllegalArgumentException, Exception {
		Check.arg().notNull(logger);
		
		NodeProp nodeProp = NodeProp.findBest( logger.getName(), "useParentHandlers" );
		if (nodeProp == null) return;
		
		logger.setUseParentHandlers( nodeProp.useParentHandlers );
	}
	
	// -------------------- parseLevel --------------------
	
	/**
	* Returns the Level that corresponds to s.
	* <p>
	* @throws IllegalArgumentException if s is blank or no Level corresponds to s
	*/
	public static Level parseLevel(String s) throws IllegalArgumentException {
		Check.arg().notBlank(s);
		
		for (int i = 0; i < levels.length; i++) {
			if (s.equals(levels[i].toString())) return levels[i];
		}
		throw new IllegalArgumentException("s = " + s + " does not correspond to any known Level instance");
	}
	
	// -------------------- close, flush, removeHandlers --------------------
	
	/**
	* Immediately returns if logger == null.
	* Otherwise, retrieves all of logger's Handlers and closes them.
	* <p>
	* Contract: this method should never throw a Throwable.
	* Any Throwable that is raised is caught and {@link Logger2#log(LogRecord) logged robustly} to the {@link #getLogger2 default Logger}.
	*/
	public static void close(Logger logger) {
		try {
			if (logger == null) return;
			
			for (Handler handler : logger.getHandlers()) handler.close();
		}
		catch (Throwable t) {
			getLogger2().logp(Level.SEVERE, "LogUtil", "close", "unexpected Throwable caught", t);
		}
	}
	
	/**
	* Immediately returns if logger == null.
	* Otherwise, retrieves all of logger's Handlers and flushes them.
	* <p>
	* Contract: this method should never throw a Throwable.
	* Any Throwable that is raised is caught and {@link Logger2#log(LogRecord) logged robustly} to the {@link #getLogger2 default Logger}.
	*/
	public static void flush(Logger logger) throws IllegalArgumentException {
		try {
			if (logger == null) return;
			
			for (Handler handler : logger.getHandlers()) handler.flush();
		}
		catch (Throwable t) {
			getLogger2().logp(Level.SEVERE, "LogUtil", "flush", "unexpected Throwable caught", t);
		}
	}
	
	/**
	* Adds every element of handlers to logger.
	* <p>
	* @throws IllegalArgumentException if handlers is null; logger is null
	*/
	public static void addHandlers(Handler[] handlers, Logger logger) throws IllegalArgumentException {
		Check.arg().notNull(handlers);
		Check.arg().notNull(logger);
		
		for (Handler handler : handlers) logger.addHandler( handler );
	}
	
	/**
	* Removes all of logger's Handlers.
	* <p>
	* @return all the Handlers that were removed from logger
	* @throws IllegalArgumentException if logger == null
	*/
	public static Handler[] removeHandlers(Logger logger) throws IllegalArgumentException {
		Check.arg().notNull(logger);
		
		Handler[] handlers = logger.getHandlers();
		for (Handler handler : handlers) logger.removeHandler( handler );
		return handlers;
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private LogUtil() {}
	
	// -------------------- NodeProp (static inner class) --------------------
	
	private static class NodeProp {
		
		private Level level;
		private Set<Class<? extends Handler>> handlerClasses;
		private Boolean useParentHandlers;	// has to be Boolean and not boolean because need to detect null
		
		/**
		* Searches every key of {@link #nameToNodeProps} to find the best match to loggerName.
		* Here, "match" means that loggerName starts with key.
		* Furthermore, the "best match" (in the event that multiple keys match loggerName)
		* is the longest (in number of chars) such matching key, since that one is assumed to be the most specific.
		* If there are multiple "best matching" keys, then one is arbitrarily chosen.
		* <p>
		* During this search, a key is rejected from consideration if its associated NodeProp does not have the required field set.
		* Specifically, the NodeProp must have a field whose name is given by fieldName,
		* and that field must have a non-null value if the key is to be considered.
		* <p>
		* Finally, if no matching key for loggerName whatsoever is found,
		* and if loggerName is non-empty,
		* and if nameToNodeProps contains the key {@link #nsPatternUniversalMatch},
		* and if the NodeProp corresponding to nsPatternUniversalMatch has the field set,
		* then that NodeProp is returned.
<!--
+++ would really like to add true regular expression support instead of this universal match business,
but am not sure how, if multiple regular expressions match, you can determine which is the best match
(the longest key algorithm now seems less useful).

You face the same problem if you broaden the pattern into a substring instead of a prefix
(i.e. that it is implicitly *SSS* instead of just SSS*).

Note: the LogManager javadocs state:
	Levels are applied in the order they are defined in the properties file. Thus level settings for child nodes in the tree should come after settings for their parents. The property name ".level" can be used to set the level for the root of the tree.
That could be one way of handling ties.
Oh, the above LogManager javadocs are likely wrong, by the way; see this bug report:
	Your report has been assigned an internal review ID of 1495184, which is NOT visible on the Sun Developer Network (SDN).
-->
		* <p>
		* See the class javadocs concerning the <code>-NS</code> properties for more discussion.
		* <p>
		* @throws IllegalArgumentException if loggerName == null; fieldName is {@link Check#notBlank blank}
		* @throws Exception (or some subclass) if some other problem occurs
		*/
		private static NodeProp findBest(String loggerName, String fieldName) throws IllegalArgumentException, Exception {
			Check.arg().notNull(loggerName);
			Check.arg().notBlank(fieldName);
			
			String keyBest = null;
			for (String key : nameToNodeProps.keySet()) {
				if (!loggerName.startsWith(key)) continue;
				
				NodeProp nodeProp = nameToNodeProps.get(key);
				Object fieldValue = ReflectUtil.get( nodeProp, fieldName );
				if (fieldValue == null) continue;
				
				if (keyBest == null) keyBest = key;
				else if (keyBest.length() < key.length()) keyBest = key;
			}
			
			if (keyBest == null) {
				if (loggerName.length() == 0) return null;
				
				NodeProp nodeProp = nameToNodeProps.get(nsPatternUniversalMatch);
				if (nodeProp == null) return null;
				
				Object fieldValue = ReflectUtil.get( nodeProp, fieldName );
				if (fieldValue == null) return null;

				return nodeProp;
			}
			
			return nameToNodeProps.get(keyBest);
		}
		
		/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
		private NodeProp() {}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_getLogger2() {
			Logger2 logger = null;
			try {
				logger = getLogger2();
				logger.logp(Level.WARNING, "LogUtil.UnitTest", "test_getLogger2", null, new Exception("test if LogUtil.getLogger2() can handle a null message; if see this in a log entry, then it does"));
				logger.logp(Level.INFO, "LogUtil.UnitTest", "test_getLogger2", "test if LogUtil.getLogger2() can handle a null Throwable; if see this in a log entry, then it does", (Throwable) null);
			}
			finally {
				flush(logger);
			}
		}
		
		@Test public void test_makeLogger2() {
			Logger2 logger = null;
			try {
				logger = makeLogger2( LogUtil.UnitTest.class, "test_makeLogger2" );
				
				logger.entering("LogUtil.UnitTest", "test_makeLogger2");
				logger.entering("LogUtil.UnitTest", "test_makeLogger2", new Object[] {"param1", "param2", "param3"});	// do this to test and see how method params can be printed using the entering method
				logger.exiting("LogUtil.UnitTest", "test_makeLogger2");
				logger.throwing("LogUtil.UnitTest", "test_makeLogger2", new Exception("deliberately generated Exception", new Exception("with a cause too")));
				
				logger.finest("step #1");
				logger.finer("step #2");
				logger.fine("step #3");
				logger.config("step #4");
				logger.info("step #5");
				logger.warning("step #6");
				logger.severe("step #7");
			}
			finally {
				close(logger);
			}
		}
		
		/**
		* Must run with a logging.properties file that only contains:
		* <pre><code>
			com.xyz.level = FINE
			com.level = INFO
		* </code></pre>
		* Note how the child Logger's level is defined first.
		*/
		@Ignore("Need to custom set the logging.properties file for this test to pass, as per this method's javadocs; cannot use the normal one")
		@Test public void test_LogManagerBug() {
			Logger loggerParent = Logger.getLogger("com");
			Assert.assertEquals( Level.INFO, loggerParent.getLevel() );
			
			Logger loggerChild = Logger.getLogger("com.xyz");
			Assert.assertEquals( Level.FINE, loggerChild.getLevel() );
		}
		
	}
	
}
