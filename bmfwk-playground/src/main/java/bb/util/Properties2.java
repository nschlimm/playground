/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer Notes:

--why did Sun have a separate defaults field in their Properties implementation?
	http://forums.sun.com/thread.jspa?threadID=5369431

+++ this class should probably implement just ConcurrentNavigableMap, as that combines the two interfaces
	--was simply too lazy last time that I worked on this class to do all the method delegations...

+++ add support for property expansion, using the ant ${...} mechanism, as well as file inclusion
	--how would this work?
		--would you read in a properties file, say, and if encounter a ${someName} then would look in the existing properties for someName and substitute its value for the complete ${someName} part (and would crash if there were no value associated with someName)
		--I can see how there could be circular dependencies, infinite loops, etc...
			--in theory, these cycles can be detected via a graph theory library which should have a method to find cycles in graphs
			--might be able to get away with a simpler solution of using a Set to store visited nodes and bomb if a node is re-encountered; possible code:
				process(File file, Set filesVisited) throws IllegalStateException {
					boolean added = filesVisited.add(file);
					if (!added) throw new IllegalStateException("file = " + file.gethPath() + " has already been encountered, therefore there is a cycle in the graph of includes");
					
					String contents = FileUtil.readString(file);
					String[] lines = contents.split("\\n", 0);	// CRITICAL: use 0 and not -1 since want to discard trailing empty strings (last line will still have a \n)
					for (String line : lines) {
						if (isFileInclude(line)) {
							process(parseFile(line), filesVisited);	// recursively call
						}
						else {
							[read in the name/value pairs or whatever]
						}
					}
					
					boolean removed = filesVisited.remove(file);
					if (!removed) throw new IllegalStateException("file = " + file.gethPath() + " was not removed from filesVisited; this should never happen");
				}
			
			After writing the above, I did some websearching and found that someone else has also thought of it:
				If you can't add a "visited" property to the nodes, use a set (or map) and just add all visited nodes to the set unless they are already in the set. Use a unique key or the address of the objects as the "key".
				This also gives you the information about the "root" node of the cyclic dependency which will come in handy when a user has to fix the problem."
				http://stackoverflow.com/questions/261573/best-algorithm-for-detecting-cycles-in-a-directed-graph
				(see this comment: answered Nov 4 '08 at 12:15)
			
			See also
				http://en.wikipedia.org/wiki/Cycle_detection
				

+++ in the future add support for reporting which line number in the properties file/stream a given property is defined at
	--this would allow you have a protocol for resolving multiple key matches that the last key in the file which matches is what is used
	--one place where I could use this functionality is in my LogUtil.nsHandlers method (see notes there)
*/

package bb.util;

import bb.io.StreamUtil;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* This class is what {@link Properties} <i>ought</i> to have been, namely,
* a <code>Map&lt;String, String></code> implementation with additional constraints and convenience methods.
* <p>
* Properties has issues:
* <ol>
*  <li>it lacks necessary constraints, for instance, it allows generic Objects to be used as keys and/or values</li>
*  <li>it imposes no ordering on its keys (it's nice to have them iterated over in alphabetical, instead of random, order)</li>
*  <li>
*		it subclasses {@link Hashtable} (it should have used composition instead of inheritance,
*		that is, have a field of some Map type that it delegates to)
*  </li>
* </ol>
* This class fixes all these issues: it implements the {@link SortedMap} and {@link ConcurrentMap} <i>interfaces</i>
* (so it supports all that functionality, but is not commited to a particular implementation),
* plus it constrains {@link #checkKey keys} and {@link #checkKey values}.
* <p>
* This class also offers much additional functionality that is not present in Properties.
* <p>
* First, it offers several convenience constructors for initializing name/value pairs from
* {@link #Properties2(File[]) Files}, {@link #Properties2(Map) Maps}, and {@link #Properties2(String[]) Strings}.
* <p>
* Second, it adds many convenience <code>getXXX</code> methods.
* With these methods, the user specifies whether mappings are mandatory or are optional.
* Specificly, every <code>getXXX</code> method comes in one-arg and two-arg versions.
* The one-arg versions take only a String key param, and have mandatory mapping semantics:
* each will throw an IllegalStateException if key is not mapped to a value
* (e.g. {@link #getProperty(String) getProperty(key)}, {@link #getDouble(String) getDouble(key)}, etc).
* The two-arg versions take both a String key param and a String default value param, and have optional mapping semantics:
* each will return the default value if key is not mapped to a value
* (e.g. {@link #getProperty(String, String) getProperty(key, valueDefault)}, {@link #getDouble(String, double) getDouble(key, valueDefault)}, etc).
* Note that the default value is never constrained, except for type correctness (e.g. it may be null).
* <p>
* Third, this class supports {@link #checkKeys valid key checking}.
* <p>
* Fourth, this class supports the notion of effectively only a key being present;
* see {@link #isKeyOnlyPresent isKeyOnlyPresent} and {@link #putKeyOnly putKeyOnly}.
* This is useful for dealing with command line switches where only the key part is present;
* see the {@link #Properties2(String[]) String[]-arg constructor}.
* <p>
* Fifth, this class supports auditing.
* It is not unusual in a large project for a Properties instance to be mutated at multiple points in the codebase,
* and this mutation includes not only adding new key/value pairs, but also overriding existing key/value pairs.
* If some piece of this configuration code is buggy, it can be very difficult to find the offender.
* (This same issue, by the way, affects the {@link System#getProperties System properties}.)
* To ease this type of debugging, this class logs whenever its properties are mutated.
* It not only logs the event and the state that changed, but it also logs the full stack trace of the calling thread.
* <p>
* Sixth, this class supports {@link #freeze freezing its state}.
* This allows initialization code to populate an instance, and then make it effectively immutable.
* As with auditing, this can be invaluable in large codebases.
* <i>Be aware that freezing an instance has some subtle consequences.</i>
* In particular, after freezing, {@link #entrySet entrySet}, {@link #keySet keySet}, and {@link #values values}
* can no loner be called because all are potentially mutative
* (if their results are mutated, the change is propogated to this class).
* Users who need a view of the keys and values of this class after freezing should call {@link #copy copy}.
* <p>
* Since this class is not an instance of Properties, it offers interoperability support.
* For converting Properties into Properties2, the {@link #toSortedMap toSortedMap} method
* will convert a Properties instance into a Map instance
* which can then be passed to the {@link #Properties2(Map) Map-arg constructor} or to {@link #putAll putAll}.
* This technique is used by the {@link #Properties2(File[]) File[]-arg constructor}
* to support legacy properties files.
* For converting Properties2 into Properties, the {@link #toProperties toProperties} method may be called.
* This is useful when interacting with old APIs that require Properties instances.
* <p>
* Here is an example of a subclass which relaxes this class's restriction on values being non-blank.
* The subclass below allows values to be any non-null String (i.e. empty or all whitespace Strings are additionally allowed):
* <pre><code>
	private static class Properties3 extends Properties2 {
		// probably want to have more than the default no-arg constructor...
		
		protected String checkValue(Object obj) throws IllegalArgumentException {
			Check.arg().notNull(obj);
			if (!(obj instanceof String)) throw new IllegalArgumentException("arg " + obj.toString() + " is of type " + obj.getClass().getName() + " which is not an instance of String");
			return (String) obj;
		}
	}
* </code></pre>
* <p>
* This class is multithread safe: every public method is synchronized.
* <p>
* @author Brent Boyer
*/
public class Properties2 implements SortedMap<String, String>, ConcurrentMap<String, String> {
	
	// -------------------- constants --------------------
	
	/**
	* The next Properties2 instance's {@link #instanceId} field.
	* <p>
	* Contract: is initialized to 0, and if this field has that value, it means that no instances have been created.
	*/
	private static final AtomicLong instanceIdNext = new AtomicLong();
	
	/** @see #getValueUndefined getValueUndefined */
	private static final String valueUndefined = "placeholderForUndefinedValue";
	
	// -------------------- fields --------------------
	
	/** This instance's Id. */
	private final long instanceId = instanceIdNext.incrementAndGet();
	
	/** Holds all this instance's property data. */
	private final SortedMap<String, String> sortedMap = new TreeMap<String, String>();
	
	private final Logger2 logger2 = LogUtil.makeLogger2( getClass(), "#" + instanceId );
	
	private int auditDepth = 0;
	
	private boolean frozen = false;
	
	// -------------------- static methods --------------------
	
	/**
	* Parses a new {@link Properties} instance from the contents of file.
	* The format of file must be compatible with {@link Properties#load(Reader) Properties.load}.
	* <p>
	* @throws RuntimeException (or some subclass) if any error occurs; this may merely wrap some other underlying Throwable
	*/
	public static synchronized Properties parseProperties(File file) throws RuntimeException {
		// file checked by FileReader below
		
		Reader reader = null;
		try {
			reader = new FileReader(file);
			Properties properties = new Properties();
			properties.load(reader);
			return properties;
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
		finally {
			StreamUtil.close(reader);
		}
	}
	
	/**
	* Converts properties into a <code>SortedMap<String, String></code>.
	* <p>
	* The keys are obtained by calling {@link Properties#stringPropertyNames}.
	* So, only those key/value pairs where both are String instances are in the result.
	* Furthermore, in addition to the String keys immediately in properties,
	* the chain of {@link Properties#defaults default properties} is drawn on as well.
	* <p>
	* @throws IllegalArgumentException if properties is null
	*/
	public static synchronized SortedMap<String, String> toSortedMap(Properties properties) throws IllegalArgumentException {
		Check.arg().notNull(properties);
		
		SortedMap<String, String> m = new TreeMap<String, String>();
		for (String key : properties.stringPropertyNames()) {
			String value = properties.getProperty(key);
			m.put(key, value);
		}
		return m;
	}
	
	// -------------------- constructors --------------------
	
	/**
	* Constructs a new instance with key/value pairs parsed from files.
	* <p>
	* Each element of files is processed in sequence as follows:
	* <ol>
	*  <li>its contents are {@link #parseProperties parsed} into a new {@link Properties} instance</li>
	*  <li>that Properties is {@link #toSortedMap converted} into a {@link SortedMap}</li>
	*  <li>that SortedMap is passed to {@link #putAll putAll}</li>
	* </ol>
	* <p>
	* This method never throws a checked Exception in order to support convenient one-line field assignments like
	* <blockquote><code>private final int port = new Properties2(file).getInt("Port", 7462);</code></blockquote>
	* <p>
	* @throws RuntimeException (or some subclass) if any error occurs; this may merely wrap some other underlying Throwable
	*/
	public Properties2(File... files) throws IllegalArgumentException, SecurityException, RuntimeException {
		Check.arg().notEmpty(files);
		// each element of files checked by parseProperties below
		
		for (File file : files) {
			putAll( toSortedMap( parseProperties(file) ) );
		}
	}
	
	/**
	* Constructs a new instance with initial key/value pairs drawn from m.
	* <p>
	* @throws IllegalArgumentException if m is null;
	* m contains a key which fails {@link #checkKey checkKey};
	* m contains a value which fails {@link #checkValue checkValue}
	*/
	public Properties2(Map<String, String> m) throws IllegalArgumentException {
		this();
		
		// m checked by putAll below
		
		putAll(m);
	}
	
	/**
	* Constructs a new instance with initial key/value pairs parsed from args.
	* <p>
	* The key/value pairs in args must be a series of <i>command line switches</i>
	* (since args is typically supplied on the command line to a program's main).
	* The precise format is:
	* <ol>
	*  <li>a command line switch is a key, optionally followed by a value</li>
	*  <li>every key must pass {@link #isSwitchKey isSwitchKey}</li>
	*  <li>every value must <i>fail</i> isSwitchKey</li>
	*  <li>each key and (optional) value is a separate consecutive element of args</li>
	*  <li>a value element of args must always be preceded by an element of args that is its key</li>
	*  <li>corollary: args[0] must be a switch key</li>
	*  <li>since switch values are optional, there may be multiple consecutive elements of args which are just keys</li>
	*  <li>if a switch lacks a value element, then the switch's key is added using a call to {@link Properties2#putKeyOnly}</li>
	*  <li>a given switch key may only appear once (it is an error to have duplicate switches)</li>
	* </ol>

<!--
this website discusses the POSIX conventions for command line arguments:
	http://www.iam.ubc.ca/guides/javatut99/essential/attributes/_posix.html
should i support these instead?  they are more sophisticated and useful...
-->

	* <p>
	* A common use case for this constructor is to parse the command line arguments passed to some class's <code>main</code> method.
	* Here is an example which illustrates how this class can parse, verify, and exploit command line arguments:
	* <pre><code>
	*	public class Operations
	*		private static final String operation_key = "-operation";
	*		private static final String n_key = "-n";
	*		private static final List<String> keysLegal = Arrays.asList( operation_key, numberLoops_key );
	*
	*		public static void main(String[] args) {
	*			// may also want to use bb.util.Execute.XXX here...
	*
	*			Check.arg().notEmpty(args);
	*
	*			Properties2 properties = new Properties2(args).checkKeys(keysLegal);	// parses args into name/value pairs and verifies that only legal keys present
	*			String operation = properties.getProperty(operation_key);	// gets a mandatory property
	*			int n = properties.getInt(n_key, 10);	// gets an optional property as an int, and uses a default value if absent
	*			...
	*		}
	*	}
	* </code></pre>
	* This class might be executed on a command line like
	* <pre><code>
	*	java  Operations  -operation add  -a 1  -b 2
	* </code></pre>
	* <p>
	* @throws IllegalArgumentException if args is null or violates a format rule
	* @see <a href="http://www.scit.wlv.ac.uk/cbook/chap9.command.line.html">Command line programs</a>
	*/
	public Properties2(String[] args) throws IllegalArgumentException {
		this();
		
		Check.arg().notNull(args);
		
		for (int i = 0; i < args.length; i++) {	// NOTE: the loop below expects i to always index the next switch key
			String key = args[i];
			if (!isSwitchKey(key)) throw new IllegalArgumentException("format error: args[" + i + "] = " + key + " is not switch key.  Here are all the elements of args:" + "\n\t" + StringUtil.toString(args, "\n\t"));
			if (containsKey(key)) throw new IllegalArgumentException("format error: args[" + i + "] = " + key + " has already been used as a key earlier in args.  Here are all the elements of args:" + "\n\t" + StringUtil.toString(args, "\n\t"));
			
			String value = null;
			int nextIndex = i + 1;
			if (nextIndex < args.length) {	// if there is at least one more String after current i
				if (!isSwitchKey(args[nextIndex])) {	// and it is not a switch key
					value = args[nextIndex];	// then it is this switch's value
					i = nextIndex;	// must set i to the value index, so that the loop increment will advance it past
				}
			}
			
			if (value == null) putKeyOnly(key);
			else put(key, value);
		}
	}
	
	/** Constructs a new instance which contains no key/value pairs. */
	public Properties2() {
		checkValue(getValueUndefined());	// CRITICAL: make sure that no subclass has overriden checkValue and/or getValueUndefined in an incompatible way
	}
	
	// -------------------- isSwitchKey, checkKey, checkValue --------------------
	
	/**
	* Determines whether or not s is a key for a <i>command line switch</i>.
	* Returns true if and only if s is not null and starts with a hyphen (i.e. '-') char.
	*/
	protected boolean isSwitchKey(String s) {
		return (s != null) && s.startsWith("-");
	}
	
	/**
	* Checks that obj is a valid key.
	* <p>
	* This class requires keys to be String instances which are not blank.
	* Subclasses may override to implement their own key constraints.
	* <p>
	* @return obj cast to a String
	* @throws IllegalArgumentException if obj is null, is not an instance of String, or is {@link Check#notBlank blank}
	*/
	protected String checkKey(Object obj) throws IllegalArgumentException {	// NOTE: the reason why that an Object param instead of a String is because this method must be used from some of the Map methods like get(Object key) (note sure why these methods have to take a generic Object instead of the K type that the sortedMap is declared with, which would be String in this case...)
		Check.arg().notNull(obj);
		if (!(obj instanceof String)) throw new IllegalArgumentException("arg " + obj.toString() + " is of type " + obj.getClass().getName() + " which is not an instance of String");
		Check.arg().notBlank( (String) obj );
		return (String) obj;
	}
	
	/**
	* Checks that obj is a valid value.
	* <p>
	* This class imposes the same requirements on values as keys,
	* so the implementation here simply calls <code>{@link #checkKey checkKey}(obj)</code>.
	* Subclasses may override to implement their own value constraints.
	* <p>
	* @return obj cast to a String
	* @throws IllegalArgumentException if obj is null, is not an instance of String, or is {@link Check#notBlank blank}
	*/
	protected String checkValue(Object obj) throws IllegalArgumentException {
		return checkKey(obj);
	}
	
	/**
	* Checks if this instance contains any keys which are not elements of keysLegal.
	* <p>
	* A common use for this method is verifying command line arguments,
	* as illustrated in the {@link #Properties2(String[]) String[]-arg constructor} javadocs.
	* <p>
	* @return this instance
	* @throws IllegalArgumentException if keysLegal is null
	* @throws IllegalStateException if this instance contains any keys which are not elements of keysLegal
	*/
	public synchronized Properties2 checkKeys(Collection<String> keysLegal) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(keysLegal);
		
		SortedSet<String> keysPresentSet = new TreeSet<String>(keySet());	// must wrap with a TreeSet because, unfortunately, the result of calling keySet even on a SortedMap instance is a Set; see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4912961
		SortedSet<String> keysLegalSet = new TreeSet<String>(keysLegal);
		SortedSet<String> keysBad = SetUtil.difference( keysPresentSet, keysLegalSet );
		if (keysBad.size() > 0) {
			throw new IllegalStateException(
				"This instance contains the following illegal key(s):" + "\n"
				+ "\t" + StringUtil.toString(keysBad, "\n\t") + "\n"
				+ "The set of legal key(s) are:" + "\n"
				+ "\t" + StringUtil.toString(keysLegalSet, "\n\t") + "\n"
			);
		}
		
		return this;
	}
// +++ this checking is fairly primitive; would be better if instead of keysLegal being a Collection
//	it was a Map from key to specification, where the spec would include things like mandatory or optional, possibly the type (or maybe not, since subsequentprocessign usually handles that)
	
	// -------------------- audit --------------------
	
	private void audit(Level level, String methodName, String msg) {
		if (auditDepth < 1) throw new IllegalStateException("auditDepth = " + auditDepth + " < 1");
		else if (auditDepth == 1) logger2.logp(level, "Properties2", methodName, msg + "\n" + "Call stack trace:" + "\n" + ThreadUtil.getStackTraceString());
	}
	
	// -------------------- freeze, isFrozen, checkMutate --------------------
	
	/**
	* Freezes this instances state to its present values.
	* This is accomplished by setting a field that will cause any future calls to mutative methods to fail.
	* Accessor methods, however, may still be called.
	*/
	public synchronized void freeze() { frozen = true; }
	
	/** Reports whether or not this instance's state has been {@link #freeze frozen}.*/
	public synchronized boolean isFrozen() { return frozen; }
	
	private void checkMutate(String msg) throws IllegalStateException {
		if (isFrozen()) throw new IllegalStateException(msg);
	}
	
	// -------------------- Map methods --------------------
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized void clear() throws IllegalStateException {
		checkMutate("cannot call clear now because it is a mutative method and this instance's state has been frozen");
		try {
			++auditDepth;	// CRITICAL: must be the first line inside this block, since must guarantee to be executed
			sortedMap.clear();
			audit(Level.WARNING, "clear", "All mappings removed");
		}
		finally {
			--auditDepth;
		}
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	@Override public synchronized boolean containsKey(Object key) throws IllegalArgumentException {
		return sortedMap.containsKey( checkKey(key) );
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalArgumentException if value fails {@link #checkValue checkValue}
	*/
	@Override public synchronized boolean containsValue(Object value) throws IllegalArgumentException {
		return sortedMap.containsValue( checkValue(value) );
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized Set<Map.Entry<String,String>> entrySet() throws IllegalStateException {
		checkMutate("cannot call entrySet now because it is a POTENTIALLY mutative method (via the setValue method of the Map.Entry elements in the result) and this instance's state has been frozen");
		return sortedMap.entrySet();
	}
	
	@Override public synchronized boolean equals(Object obj) {
		return sortedMap.equals(obj);
	}
	
	/**
	* Returns the String value to which key is mapped, or null if this instance contains no mapping for key.
	* <p>
	* Contract: the result is <i>only</i> null if key has no mapping, since this class does not permit null keys or values.
	* Furthermore, if non-null, then the result is guaranteed to pass {@link #checkValue checkValue}.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @see #getProperty(String)
	* @see #getProperty(String, String)
	*/
	@Override public synchronized String get(Object key) throws IllegalArgumentException, IllegalStateException {
		return sortedMap.get( checkKey(key) );	// Note: no need to check result, because put/putAll enforce all constraints
	}
	
	@Override public synchronized int hashCode() {
		return sortedMap.hashCode();
	}
	
	@Override public synchronized boolean isEmpty() {
		return sortedMap.isEmpty();
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized Set<String> keySet() throws IllegalStateException {
		checkMutate("cannot call keySet now because it is a POTENTIALLY mutative method (the result can be mutated and is backed up by this instance) and this instance's state has been frozen");
		return sortedMap.keySet();
	}
	
	/**
	* Associates key with value, replacing any previous mapping of key.
	* <p>
	* @return the previous value associated with key, or null if there was no mapping for key
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}; value fails {@link #checkValue checkValue}
	*/
	@Override public synchronized String put(String key, String value) throws IllegalStateException, IllegalArgumentException {
		checkMutate("cannot call put now because it is a mutative method and this instance's state has been frozen");
		try {
			++auditDepth;	// CRITICAL: must be the first line inside this block, since must guarantee to be executed
			
			String valueOld = sortedMap.put( checkKey(key), checkValue(value) );
			
			Level level;
			String msg;
			if (valueOld == null) {
				level = Level.CONFIG;
				msg = "New mapping: " + key + " --> " + value;
			}
			else {
				level = Level.INFO;
				msg = "OVERRIDING mapping: " + key + " --> " + value + " (PREVIOUS value: " + valueOld + ")";
			}
			audit(level, "put", msg);
			
			return valueOld;
		}
		finally {
			--auditDepth;
		}
	}
	
	/**
	* Copies all of the mappings from m to this instance.
	* The effect of this call is equivalent to that of calling put(k, v) on this instance
	* once for each mapping from key k to value v in m.
	* The behavior of this operation is undefined if m is modified while the operation is in progress.
	* <p>
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if m is null;
	* m contains a key which fails {@link #checkKey checkKey};
	* m contains a value which fails {@link #checkValue checkValue}
	*/
	@Override public synchronized void putAll(Map<? extends String, ? extends String> m) throws IllegalStateException, IllegalArgumentException {
		checkMutate("cannot call putAll now because it is a mutative method and this instance's state has been frozen");
		Check.arg().notNull(m);
		for (Map.Entry<? extends String, ? extends String> entry : m.entrySet()) {
			checkKey( entry.getKey() );
			checkValue( entry.getValue() );
		}
			
		try {
			++auditDepth;	// CRITICAL: must be the first line inside this block, since must guarantee to be executed
			
			Level level = null;
			StringBuilder sb = new StringBuilder( m.size() * 64 );
			for (Map.Entry<? extends String, ? extends String> entry : m.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				String valueOld = sortedMap.get(key);
				if (valueOld == null) {
					if ((level == null) || (level.intValue() < Level.CONFIG.intValue())) level = Level.CONFIG;
					if (sb.length() > 0) sb.append("\n");
					sb.append( "New mapping: " ).append( key ).append( " --> " ).append( value );
				}
				else {
					if ((level == null) || (level.intValue() < Level.INFO.intValue())) level = Level.INFO;
					if (sb.length() > 0) sb.append("\n");
					sb.append( "OVERRIDING mapping: " ).append( key ).append( " --> " ).append( value ).append( " (PREVIOUS value: " ).append( valueOld ).append( ")" );
				}
			}
			
			sortedMap.putAll(m);	// decided NOT to do individual put calls in the loop above, because putAll may be optimized to reduce the number of hash table resizings
			
			audit(level, "putAll", sb.toString());
		}
		finally {
			--auditDepth;
		}
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized String remove(Object key) throws IllegalStateException {
		checkMutate("cannot call putAll remove because it is a mutative method and this instance's state has been frozen");
		try {
			++auditDepth;	// CRITICAL: must be the first line inside this block, since must guarantee to be executed
			
			String valueOld = sortedMap.remove( checkKey(key) );
			
			Level level;
			String msg;
			if (valueOld == null) {
				level = Level.WARNING;
				msg = "FAILED to remove the mapping associated with " + key + " because that key is absent";
			}
			else {
				level = Level.CONFIG;
				msg = "Removed the mapping: " + key + " --> " + valueOld;
			}
			audit(level, "remove", msg);
			
			return valueOld;
		}
		finally {
			--auditDepth;
		}
	}
	
	@Override public synchronized int size() {
		return sortedMap.size();
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized Collection<String> values() throws IllegalStateException {
		checkMutate("cannot call values now because it is a POTENTIALLY mutative method (the result can be mutated and is backed up by this instance) and this instance's state has been frozen");
		return sortedMap.values();
	}
	
	// -------------------- additional SortedMap methods --------------------
	
	@Override public synchronized Comparator<? super String> comparator() {
		return sortedMap.comparator();
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized SortedMap<String, String> subMap(String fromKey, String toKey) throws IllegalStateException {
		checkMutate("cannot call subMap now because it is a POTENTIALLY mutative method (the result can be mutated and is backed up by this instance) and this instance's state has been frozen");
		return sortedMap.subMap(fromKey, toKey);
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized SortedMap<String, String> headMap(String toKey) throws IllegalStateException {
		checkMutate("cannot call headMap now because it is a POTENTIALLY mutative method (the result can be mutated and is backed up by this instance) and this instance's state has been frozen");
		return sortedMap.headMap(toKey);
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	*/
	@Override public synchronized SortedMap<String, String> tailMap(String fromKey) throws IllegalStateException {
		checkMutate("cannot call tailMap now because it is a POTENTIALLY mutative method (the result can be mutated and is backed up by this instance) and this instance's state has been frozen");
		return sortedMap.tailMap(fromKey);
	}
	
	@Override public synchronized String firstKey() {
		return sortedMap.firstKey();
	}
                     
	@Override public synchronized String lastKey() {
		return sortedMap.lastKey();
	}
	
	// -------------------- additional ConcurrentMap methods --------------------
	
	/*
	Notes:
	1) all these methods have code that was adapted from the javadocs of the corresponding methods of ConcurrentMap
	2) none of these methods, even tho all are potential mutators, need to explicitly call the checkMutate method because they can rely on lower level methods like put and remove
	3) for the same reason, none of these methods need to explicitly call the audit method
	*/
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}; value fails {@link #checkValue checkValue}
	*/
	@Override public synchronized String putIfAbsent(String key, String value) throws IllegalStateException, IllegalArgumentException {
		if (!sortedMap.containsKey(key)) {
			return sortedMap.put(key, value);
		}
		else {
			return sortedMap.get(key);
		}
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}; value fails {@link #checkValue checkValue}
	*/
	@Override public synchronized boolean remove(Object key, Object value) throws IllegalStateException, IllegalArgumentException {
		if (sortedMap.containsKey(key) && sortedMap.get(key).equals(value)) {
			sortedMap.remove(key);
			return true;
		}
		else {
			return false;
		}
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}; value fails {@link #checkValue checkValue}
	*/
	@Override public synchronized String replace(String key, String value) throws IllegalStateException, IllegalArgumentException {
		if (sortedMap.containsKey(key)) {
			return sortedMap.put(key, value);
		}
		else {
			return null;
		}
	}
	
	/**
	* {@inheritDoc}
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}; valueOld or valueNew fail {@link #checkValue checkValue}
	*/
	@Override public synchronized boolean replace(String key, String valueOld, String valueNew) throws IllegalStateException, IllegalArgumentException {
		if (sortedMap.containsKey(key) && sortedMap.get(key).equals(valueOld)) {
			sortedMap.put(key, valueNew);
			return true;
		}
		else {
			return false;
		}
	}
	
	// -------------------- convenience single arg getXXX methods (all have mandatory kep mapping semantics) --------------------
	
	// NOTE: none of the methods in this section need to check their key arg, because these methods all ultimately call get which will check it
	
	/**
	* Returns the value associated with key.
	* <p>
	* Is identical to {@link #get get} except that null is never returned (an IllegalStateException is thrown instead).
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	*/
	public synchronized String getProperty(String key) throws IllegalArgumentException, IllegalStateException {
		String value = get(key);
		Check.state().notNull(value);
		return value;
	}
	
	/**
	* Returns <code>Boolean.parseBoolean( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	*/
	public synchronized boolean getBoolean(String key) throws IllegalArgumentException, IllegalStateException {
		return Boolean.parseBoolean( getProperty(key) );
	}
	
	/**
	* Returns <code>Byte.parseByte( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable byte
	*/
	public synchronized byte getByte(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Byte.parseByte( getProperty(key) );
	}
	
	// No need for a getChar version!
	
	/**
	* Returns <code>Double.parseDouble( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable double
	*/
	public synchronized double getDouble(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Double.parseDouble( getProperty(key) );
	}
	
	/**
	* Returns <code>new File( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	*/
	public synchronized File getFile(String key) throws IllegalArgumentException, IllegalStateException {
		return new File( getProperty(key) );
	}
	
	/**
	* Returns <code>Float.parseFloat( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable float
	*/
	public synchronized float getFloat(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Float.parseFloat( getProperty(key) );
	}
	
	/**
	* Returns <code>Integer.parseInt( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable int
	*/
	public synchronized int getInt(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Integer.parseInt( getProperty(key) );
	}
	
	/**
	* Returns <code>Long.parseLong( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable long
	*/
	public synchronized long getLong(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Long.parseLong( getProperty(key) );
	}
	
	/**
	* Returns <code>Short.parseShort( {@link #getProperty(String) getProperty}(key) )</code>.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws IllegalStateException if key is not associated with any value
	* @throws NumberFormatException if the value is not a parsable short
	*/
	public synchronized short getShort(String key) throws IllegalArgumentException, IllegalStateException, NumberFormatException {
		return Short.parseShort( getProperty(key) );
	}
	
	// -------------------- convenience double arg getXXX methods (all have optional kep mapping semantics) --------------------
	
	// NOTE: none of the methods in this section need to check their key arg, because these methods all call get which will check it
	
	/**
	* If key has an associated value, then returns that value.
	* Otherwise, returns valueDefault.
	* <p>
	* Is identical to {@link #get get} except that null is never returned (valueDefault is returned instead).
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	public synchronized String getProperty(String key, String valueDefault) throws IllegalArgumentException {
		String value = get(key);
		return (value != null) ? value : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Boolean.parseBoolean(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	public synchronized boolean getBoolean(String key, boolean valueDefault) throws IllegalArgumentException {
		String value = get(key);
		return (value != null) ? Boolean.parseBoolean(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Byte.parseByte(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable byte
	*/
	public synchronized byte getByte(String key, byte valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Byte.parseByte(value) : valueDefault;
	}
	
	// No need for a getChar version!
	
	/**
	* If key has an associated value, then returns <code>Double.parseDouble(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable double
	*/
	public synchronized double getDouble(String key, double valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Double.parseDouble(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>new File(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	public synchronized File getFile(String key, File valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? new File(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Float.parseFloat(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable float
	*/
	public synchronized float getFloat(String key, float valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Float.parseFloat(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Integer.parseInt(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable int
	*/
	public synchronized int getInt(String key, int valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Integer.parseInt(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Long.parseLong(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable long
	*/
	public synchronized long getLong(String key, long valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Long.parseLong(value) : valueDefault;
	}
	
	/**
	* If key has an associated value, then returns <code>Short.parseShort(value)</code>.
	* Otherwise, returns valueDefault.
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	* @throws NumberFormatException if the value is not a parsable short
	*/
	public synchronized short getShort(String key, short valueDefault) throws IllegalArgumentException, NumberFormatException {
		String value = get(key);
		return (value != null) ? Short.parseShort(value) : valueDefault;
	}
	
	// -------------------- isKeyOnlyPresent, putKeyOnly, getValueUndefined --------------------
	
	/**
	* Reports whether or not only key is effectively in this instance (i.e. it has no substantial associated value).
	* <p>
	* In principle, this method should only return true if <code>{@link #putKeyOnly putKeyOnly}(key)</code> was the last mapping done on key.
	* So, this method is implemented as <code>return {@link #getValueUndefined} == {@link #get get}(key)</code>
	* (note that the <code>==</code> operator, not the <code>equals</code> method, is used).
	* <p>
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	public synchronized boolean isKeyOnlyPresent(String key) throws IllegalArgumentException {
		// key checked by get below
		
		return getValueUndefined() == get(key);
	}
	
	/**
	* Associates key with the result of {@link #getValueUndefined getValueUndefined}.
	* This special value is meant for situations in which only the presence of key in this instance matters
	* and its associated value is irrelevant.
	* For example, if this instance represents a set of command line switches,
	* it is often the case that many of the switches just have a key part and no associated value.
	* <p>
	* @throws IllegalStateException if {@link #isFrozen isFrozen} returns true
	* @throws IllegalArgumentException if key fails {@link #checkKey checkKey}
	*/
	public synchronized String putKeyOnly(String key) throws IllegalStateException, IllegalArgumentException {
		// key checked by put below
		checkMutate("cannot call putKeyOnly now because it is a mutative method and this instance's state has been frozen");
		
		return put(key, getValueUndefined());
	}
	
	/**
	* Returns the value associated with every key added by {@link #putKeyOnly putKeyOnly}.
	* <p>
	* Contract: the result must be the same String instance for a given Properties2 instance.
	* (This class satisfies this by returning a static constant, however, subclasses may behave differently.)
	* Furthermore, the result must pass {@link #checkValue checkValue}.
	*/
	protected String getValueUndefined() {
		return valueUndefined;
	}
		
	// -------------------- copy, toProperties --------------------
	
	/**
	* Copies this instance into a new ConcurrentNavigableMap, which is then returned.
	* This method is an alternative to {@link #clone clone}.
	* The result is not backed by this instance (so mutating the result does not affect this instance).
	* <p>
	* Motivation: because instances can be {@link #freeze frozen},
	* this method was written to get a view of this instance whose keys and values can always be interrogated.
	* Recall that {@link #entrySet entrySet}, {@link #keySet keySet}, and {@link #values values}
	* are potentially mutative methods, and so they will throw an IllegalStateException if called after an instance has been frozen.
	*/
	public synchronized ConcurrentNavigableMap<String, String> copy() {
		return new ConcurrentSkipListMap<String, String>(sortedMap);
	}
	
	/**
	* Converts this instance into a new Properties instance, which is then returned.
	* This method is useful when dealing with old APIs that require Properties instances
	* (e.g. {@link System#setProperties System.setProperties}).
	*/
	public synchronized Properties toProperties() {
		Properties result = new Properties();
		result.putAll(sortedMap);
		return result;
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_constructor_StringArray_pass() {
			Properties2 propertiesExpected = new Properties2();
			propertiesExpected.put("-a", "a's value");
			propertiesExpected.put("-b", "b's value");
			propertiesExpected.put("-c", propertiesExpected.getValueUndefined());
			
			String[] args = new String[] {"-a", "a's value", "-b", "b's value", "-c"};
			Properties2 propertiesParsed = new Properties2(args);
			
			Assert.assertEquals(propertiesExpected, propertiesParsed);
			
			System.out.println();
			System.out.println("test_constructor_StringArray_pass:" + "\n\t" + StringUtil.toString(propertiesParsed, "\n\t") );
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_constructor_StringArray_fail1() {
			String[] args = new String[] {"a's value", "-b", "b's value", "-c"};	// does not start with a key
			new Properties2(args);
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_constructor_StringArray_fail2() {
			String[] args = new String[] {"-a", "a's value", "b's value", "-c"};	// is missing a key (2 values in a row occur)
			new Properties2(args);
		}
		
		@Test(expected=IllegalArgumentException.class) public void test_constructor_StringArray_fail3() {
			String[] args = new String[] {"-a", "-a"};	// duplicates a key
			new Properties2(args);
		}
		
		@Test public void test_checkKeys_pass() {
			Set<String> keysLegal = new HashSet<String>( Arrays.asList("-a", "-b", "-c") );
			Properties2 propertiesParsed = new Properties2( new String[] {"-a", "-b", "-c"} );
			propertiesParsed.checkKeys(keysLegal);
		}
		
		@Test(expected=IllegalStateException.class) public void test_checkKeys_fail() {
			Set<String> keysLegal = new HashSet<String>( Arrays.asList("-a", "-b", "-c") );
			Properties2 propertiesParsed = new Properties2( new String[] {"-d", "-e", "-f"} );
			propertiesParsed.checkKeys(keysLegal);
		}
		
		/**
		* Calls all the methods which should use {@link #audit audit} to log a mutation.
		* Need to manually examine the log file to see if the expected events are present.
		*/
		@Test public void test_audit() {
			Properties2 properties2 = new Properties2();
			
			properties2.clear();
			
			properties2.put("key1", "value1a");	// new mapping
			properties2.put("key1", "value1b");	// overriding mapping
			
			SortedMap<String, String> m = new TreeMap<String, String>();
			m.put("key1", "value1c");	// overriding mapping
			m.put("key2", "value2");	// new mapping
			m.put("key3", "value3");	// new mapping
			m.put("key4", "value4");	// new mapping
			properties2.putAll(m);
			
			properties2.remove("key1");	// remove key that should be present
			properties2.remove("keyAbsent");	// remove key that should be absent
		}
		
		@Test(expected=IllegalStateException.class) public void test_freeze_fail() {
			Properties2 p2 = new Properties2();
			p2.freeze();
			p2.put("key", "value");
		}
		
		@Test public void test_freeze_copy() {
			Properties2 p2 = new Properties2();
			p2.put("key1", "value1");
			p2.freeze();
			System.out.println( StringUtil.toString(p2.copy(), "\n") );	// must use copy because p2 has been frozen
		}
		
	}
	
}
