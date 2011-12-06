/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer Notes:

--see the article "Inspect Your Java Objects" in Java Pro March 2002

+++ another use for this class would be to do generic deep copying of objects?

+++ it would be nice if could write a version of this class which autoparses C structs;
	--see the problem faced by this forum posting: http://forum.java.sun.com/thread.jspa?threadID=5276697&tstart=0

--note that can also access the Abstract Syntax Tree using the new Compiler Tree API to maybe do similar stuff?
	http://today.java.net/pub/a/today/2008/04/10/source-code-analysis-using-java-6-compiler-apis.html
*/

package bb.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/**
* Determines the state of some other object.
* <p>
* The constructor finds the <i>complete</i> state:
* every Field in object's Class, all of its superclasses, and its complete interface hierarchy.
* (Recall: interfaces can declare static final fields,
* altho this is <a href="http://java.sun.com/j2se/1.5.0/docs/guide/language/static-import.html">not a good idea</a>).
* <p>
* By default, the constructor stores all this state,
* however, an optional number of {@link Filter}s may be provided to restrict the state that is stored.
* Many users will find the pre-defined {@link #immediateState} or {@link #immediateInstanceState} fields convenient.
* <p>
* The state of this other object is stored as a Class --> Field[] map.
* Its accessor is {@link #getClassToFields getClassToFields}.
* <p>
* This class offers convenience {@link #toString} and {@link #toStringLabeled} methods,
* which return formatted Strings and make it trivial for any class to write a generic toString/toStringLabeled methods.
* For example,
* <pre><code>
*	&#64;Override public String toString() {
*		return new {@link #ObjectState(Object, Set) ObjectState}( this, {@link ObjectState#immediateInstanceState ObjectState.immediateInstanceState} ).{@link #toString()};
*	}
* </code></pre>
* or
* <pre><code>
*	public String toStringLabeled() {
*		return new {@link #ObjectState(Object, Filter[]) ObjectState}( this ).{@link #toStringLabeled()};
*	}
* </code></pre>
* might work.
* Even more convenient for the casual user is the static {@link #describe} method.
* <p>
* If this generic toString/toStringLabeled approach is inadequate,
* {@link #generateToStringCode generateToStringCode}/{@link #generateToStringLabeledCode generateToStringLabeledCode}
* may be used to automatically generate java source code which can then be customized.
* For example, a call like:
* <pre><code>
*	System.out.println( new {@link #ObjectState(Object, Set) ObjectState}( this, {@link ObjectState#immediateState} ).{@link #generateToStringCode generateToStringCode}() );
* </code></pre>
* possibly followed by some moderate hand editing might be all that is required.
* <p>
* This class is useful in debugging, and is heavily used by the {@link d.p p} and {@link d.g g} classes.
* <p>
* This class uses reflection to get the Field data.
* <i>Since reflection is much slower than direct field access, care should be taken in using this class if high performance is needed.</i>
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class ObjectState {
	
	// -------------------- constants --------------------
	
	/**
	* The {@link #toStringLabeled(String, String)} method can result in recursive calls
	* because of its use of {@link #toStringSmart}.
	* This means that infinite loops can result when applied to certain object graphs
	* with circular reference chains, just as with object serialization.
	* One way to prevent this is to keep track of objects encountered so far,
	* and use a placeholder if ever encounter a known object and stop the recursion there.
	* That is what this field does: it allows the thread executing toStringLabeled to know what objects it has encountered so far.
	* It is a Map from an encountered Object to the number of times that it has been encountered.
	* <p>
	* Contract: is never null.
	* The Map implementation used in the values must use reference equality, not object-equality; see {@link IdentityHashMap}.
	* A given thread's Map must be empty when the topmost toStringLabeled call finally returns.
	* <p>
	* Note that this field must be static and not instance based, because toStringSmart can create new ObjectState intances,
	* therefore the tracking has to be done beyond the instance level.
	*/
	private static final ThreadLocal<Map<Object,Integer>> threadToObjCount = new ThreadLocal<Map<Object,Integer>>() {
		protected synchronized Map<Object,Integer> initialValue() {
			return new IdentityHashMap<Object,Integer>();
		}
	};
	
	/**
	* A Set of {@link Filter}s that acts to only accept fields from the immediate object under consideration
	* (i.e. reject fields from superclasses/interfaces); both static and instance fields are accepted.
	* <p>
	* Contract: is never null.
	* Furthermore, is {@link Collections#unmodifiableSet unmodifiable} in order to preserve encapsulation.
	* It will never be changed after class initialization, so users may safely iterate over it with no fear of {@link ConcurrentModificationException}.
	*/
	public static final Set<Filter> immediateState;
	
	/**
	* A Set of {@link Filter}s that acts to only accept this state:
	* <ol>
	*  <li>fields from the immediate object under consideration (i.e. reject fields from superclasses/interfaces)</li>
	*  <li>instance fields (i.e. reject static fields)</li>
	* </ol>
	* <p>
	* Contract: is never null.
	* Furthermore, is {@link Collections#unmodifiableSet unmodifiable} in order to preserve encapsulation.
	* It will never be changed after class initialization, so users may safely iterate over it with no fear of {@link ConcurrentModificationException}.
	*/
	public static final Set<Filter> immediateInstanceState;
	
	static {
		Set<Filter> set = new HashSet<Filter>( Arrays.asList(
			new AcceptOnlyImmediateClass()
		) );
		immediateState = Collections.unmodifiableSet(set);
		
		set = new HashSet<Filter>( Arrays.asList(
			new AcceptOnlyImmediateClass(),
			new RejectStaticFields()
		) );
		immediateInstanceState = Collections.unmodifiableSet(set);
	}
	
	// -------------------- instance fields --------------------
	
	/**
	* Object that this instance is concerned with.
	* <p>
	* Contract: none; may even be null.
	*/
	private final Object object;
	
	/**
	* Stores all the {@link Filter}s that this instance uses.
	* <p>
	* If multiple elements are present, then each is connected by an implicit AND
	* (i.e. a Class/Field must be accepted by every Filter in order to be be present in {@link #classToFields}).
	* <p>
	* Contract: is never null, but may be empty.
	* Furthermore, is {@link Collections#unmodifiableSet unmodifiable} in order to preserve encapsulation.
	* It will never be changed after construction, so users may safely iterate over it with no fear of {@link ConcurrentModificationException}.
	*/
	private final Set<Filter> filters;
	
	/**
	* For each Class in {@link #object}'s hierarchy (i.e. object's direct Class, any superclasses, any interface hierarchy),
	* stores a mapping from the Class to an array of every declared Field of that class.
	* <p>
	* Contract:
	* <ol>
	*  <li>is never null</li>
	*  <li>is empty if and only if {@link #object} is null</li>
	*  <li>
	*		iteration returns Classes in the following order: first the Class for object itself,
	*		then the Class hierarchy for each interface that is directly implemented by object.
	*		This process is then recursively repeated for each superclass above object.
	*		If an interface appears multiple times in the interface hierarchy,
	*		then it will obviously only appear once as a key in this field,
	*		and its iteration order is determined by its first appearance.
	*  </li>
	*  <li>
	*		each Field[] that is mapped to will a) never be null
	*		but b) will be zero length if the Class/Interface has no fields
	*		and c) is sorted using a {@link ReflectUtil.FieldComparator}.
	*  </li>
	*  <li>
	*		each element of the Field[] that is mapped to is guaranteed to be accessible.
	*		If any element's {@link Field#isAccessible isAccessible} method originally returned false,
	*		then a call to {@link Field#setAccessible field.setAccessible}(true) is made during construction.
	*		This means that all code in this class can count on these elements being accessible
	*		and should never susequently call setAccessible.
	*		<i>It also means that no code in this class should ever assume that an element's isAccessible
	*		result says anything about the original accessibility of the field.</i>
	*  </li>
	*  <li>
	*		is {@link Collections#unmodifiableSet unmodifiable} in order to preserve encapsulation.
	*		It will never be changed after construction, so users may safely iterate over it with no fear of {@link ConcurrentModificationException}.
	*  </li>
	* </ol>
	*/
	private final Map<Class,Field[]> classToFields;
	
	// -------------------- xxxObjectGraph methods --------------------
	
	private static boolean isInObjectGraph(Object obj) {
		return threadToObjCount.get().containsKey(obj);
	}
	
	private static boolean isObjectGraphEmpty() {
		return threadToObjCount.get().isEmpty();
	}
	
	private static void incrementObjectGraph(Object obj) {
		Map<Object,Integer> map = threadToObjCount.get();
		Integer count = map.get(obj);
		if (count == null) {
			map.put(obj, 1);
		}
		else if (count.intValue() <= 0) {
			throw new IllegalStateException("count.intValue() <= 0 when incrementObjectGraph called--this should never happen");
		}
		else {
			map.put(obj, count.intValue() + 1);
		}
	}
	
	private static void decrementObjectGraph(Object obj) {
		Map<Object,Integer> map = threadToObjCount.get();
		Integer count = map.get(obj);
		if (count == null) {
			throw new IllegalStateException("count == null when decrementObjectGraph called--this should never happen");
		}
		else if (count.intValue() <= 0) {
			throw new IllegalStateException("count.intValue() <= 0 when decrementObjectGraph called--this should never happen");
		}
		else if (count.intValue() == 1) {
			map.remove(obj);	// CANNOT HAVE 0 counts in map in order for the logic inside isInObjectGraph/isObjectGraphEmpty to work
		}
		else {
			map.put(obj, count.intValue() - 1);
		}
	}
	
	// -------------------- describe --------------------
	
	/** Returns <code>{@link #describe(Object, String) describe}( obj, null)</code>. */
	public static String describe(Object obj) throws RuntimeException {
		return describe(obj, null);
	}
	
	/**
	* Returns <code>new {@link #ObjectState(Object, Set) ObjectState}( obj, {@link ObjectState#immediateInstanceState immediateInstanceState} ).{@link #toStringLabeled}( prefix, "\t" )</code>.
	* Is simply a convenience method that covers a conmmon usage scenario.
	* <p>
	* @param obj the object to get a report on its immediate instance state
	* @throws RuntimeException (or some subclass) if any problem occurs; this RuntimeException may wrap some underlying Throwable
	*/
	public static String describe(Object obj, String prefix) throws RuntimeException {
		return new ObjectState( obj, immediateInstanceState ).toStringLabeled( prefix, "\t" );
	}
	
	// -------------------- toStringSmart --------------------
	
	/**
	* Returns a String which describes the state of obj.
	* This method is noteworthy because of how it tries to intelligently handle certain scenarios for obj
	* in a way that many users will find convenient.
	* <p>
	* The following special cases are checked in the order listed:
	* <ol>
	*  <li>if obj is null, then "&lt;NULL>" is returned</li>
	*  <li>if obj is an array of any primitive type, then a comma separated list containing each element is returned</li>
	*  <li>
	*		if obj is an Object array, {@link Collection}, or {@link Map}, then each element is printed on its own indented line
	*		(altho if it is empty, then the text "&lt;EMPTY (XXX has no elements)>" is returned,
	*		where XXX is either array, Collection, or Map as appropriate)
	*  </li>
	*  <li>
	*		if obj is in the <code>java.lang</code> package
	*		and/or is an instance of {@link CharSequence},
	*		then obj.toString() is returned
	*		(the idea here being that the toString method for these types is likely to produce a simple 1 line result)
	*  </li>
	* </ol>
	* For all other classes, this method returns a newline followed by
	* <code>new ObjectState( obj, {@link #getFilters} ).toStringLabeled(prefix, indent)</code>
	* (i.e. a recursive call).
	* <p>
	* @throws RuntimeException (or some subclass) if any problem occurs; this RuntimeException may wrap some underlying Throwable, so be sure to inspect its cause
	*/
	public static String toStringSmart(Object obj, String prefix, String indent, Set<Filter> filters) throws RuntimeException {
		if (prefix == null) prefix = "";
		Check.arg().notNull(indent);
		
		if (obj == null) return "<NULL>";
		
		String separatorMultiLine = "\n" + prefix;
		if (obj.getClass().isArray()) {
			if (obj.getClass().getComponentType().isPrimitive()) {
				if (obj.getClass().getComponentType() == boolean.class) {
					boolean[] booleans = (boolean[]) obj;
					if (booleans.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(booleans, ", ");
				}
				if (obj.getClass().getComponentType() == byte.class) {
					byte[] bytes = (byte[]) obj;
					if (bytes.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(bytes, ", ");
				}
				if (obj.getClass().getComponentType() == char.class) {
					char[] chars = (char[]) obj;
					if (chars.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(chars, ", ");
				}
				if (obj.getClass().getComponentType() == double.class) {
					double[] doubles = (double[]) obj;
					if (doubles.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(doubles, ", ");
				}
				else if (obj.getClass().getComponentType() == float.class) {
					float[] floats = (float[]) obj;
					if (floats.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(floats, ", ");
				}
				else if (obj.getClass().getComponentType() == int.class) {
					int[] ints = (int[]) obj;
					if (ints.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(ints, ", ");
				}
				else if (obj.getClass().getComponentType() == long.class) {
					long[] longs = (long[]) obj;
					if (longs.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(longs, ", ");
				}
				else if (obj.getClass().getComponentType() == short.class) {
					short[] shorts = (short[]) obj;
					if (shorts.length == 0) return "<EMPTY (array has no elements)>";
					return StringUtil.toString(shorts, ", ");
				}
					// boy does the above make me hate Java's mixed type system--get rid of primitives!
				else {
					throw new IllegalStateException("unable to handle the new Java primitive type " + obj.getClass().getComponentType());	// this should never happen
				}
			}
			else {
				return separatorMultiLine + StringUtil.toString((Object[]) obj, separatorMultiLine);
			}
		}
		else if (obj instanceof Collection) {
			Collection collection = (Collection) obj;
			if (collection.size() == 0) return "<EMPTY (Collection has no elements)>";
			return separatorMultiLine + StringUtil.toString(collection, separatorMultiLine);
		}
		else if (obj instanceof Map) {
			Map map = (Map) obj;
			if (map.size() == 0) return "<EMPTY (Map has no elements)>";
			return separatorMultiLine + StringUtil.toString(map, separatorMultiLine);
		}
		
		if (
			obj.getClass().getName().startsWith("java.lang") ||	// among others, this will pick up the primitive wrapper classes plus fundamental ones like Class, Object, String, StringBuffer, StringBuilder, Thread, etc
			(obj instanceof CharSequence)
		) {
			return obj.toString();
		}
		
		return "\n" + (new ObjectState( obj, filters ).toStringLabeled(prefix, indent));
	}
	
	// -------------------- constructors and helper methods --------------------
	
	/** Calls the {@link #ObjectState(Object, Set) fundamental constructor} after converting filters into a non-null Set. */
	public ObjectState(Object object, Filter... filters) throws SecurityException {
		this( object, (filters != null) ? new HashSet<Filter>( Arrays.asList(filters) ) : new HashSet<Filter>() );
	}
	
	/**
	* Constructor.
	* Determines object's state as restricted by filters, and places the result into {@link #classToFields}.
	* <p>
	* @param object assigned to {@link #object}, so must satisfy the contract
	* @param filters assigned to {@link #filters}, so must satisfy the contract
	* @throws SecurityException if {@link Class#getDeclaredFields Class.getDeclaredFields} has an isssue
	*/
	public ObjectState(Object object, Set<Filter> filters) throws SecurityException {
		Check.arg().notNull(filters);
		
		this.object = object;
		this.filters = Collections.unmodifiableSet( new HashSet<Filter>(filters) );
		
		Map<Class,Field[]> map = new LinkedHashMap<Class,Field[]>();
		if (object != null) {
			for (Class c = object.getClass(); c != null; c = c.getSuperclass()) {
				handleClass(c, object, this.filters, map);
			}
		}
		this.classToFields = Collections.unmodifiableMap(map);
	}
	
	private void handleClass(Class c, Object object, Set<Filter> filters, Map<Class,Field[]> map) {
		if (classAccepted(c, object, filters)) {
			map.put( c, extractFields(c, object, filters) );
		}
		
		for (Class interf : c.getInterfaces()) {
			handleClass(interf, object, filters, map);
		}
	}
	
	private boolean classAccepted(Class c, Object object, Set<Filter> filters) {
		for (Filter filter : filters) {
			if (!filter.acceptClass(c, object)) return false;
		}
		return true;
	}
	
	private Field[] extractFields(Class c, Object object, Set<Filter> filters) throws SecurityException {
		Field[] fields = c.getDeclaredFields();		// according to its contract, getDeclaredFields should never return null
		Arrays.sort(fields, ReflectUtil.FieldComparator.getInstance());
		
		List<Field> list = new ArrayList<Field>( fields.length );
		for (Field field : fields) {
			if (fieldAccepted(field, object, filters)) {
				if (!field.isAccessible()) field.setAccessible(true);	// see classToFields javadocs
				list.add(field);
			}
		}
		return list.toArray( new Field[list.size()] );
	}
	
	private boolean fieldAccepted(Field field, Object object, Set<Filter> filters) {
		for (Filter filter : filters) {
			if (!filter.acceptField(field, object)) return false;
		}
		return true;
	}
	
	// -------------------- accessors and mutators --------------------
	
	/** Accessor for {@link #object}. */
	public Object getObject() { return object; }
	
	/** Accessor for {@link #filters}. */
	public Set<Filter> getFilters() { return filters; }	// safe to directly return filters since it is unmodifiable
	
	/** Accessor for {@link #classToFields}. */
	public Map<Class,Field[]> getClassToFields() { return classToFields; }	// safe to directly return filters since it is unmodifiable
	
	// -------------------- convenience methods --------------------
	
	/**
	* Returns {@link #getObject getObject}'s class name.
	* In general, the result is getObject's class name, however, the following are special cases:
	* <ol>
	*  <li>if getObject returns null, then the String "&lt;NO TYPE: null Object reference>" is returned</li>
	*  <li>
	*		if getObject returns an array, then the result is formatted just like it would be declared in source code
	*		(i.e. as the element type followed by braces, e.g. "byte[]";
	*		this contrasts with the class name produced by the JVM for a byte array, which is the cryptic "[B")
	*  </li>
	* </ol>
	*/
	public String getType() {
		if (getObject() == null) return "<NO TYPE: null Object reference>";
		if (getObject().getClass().isArray()) return getObject().getClass().getComponentType().getName() + "[]";
		
		return getObject().getClass().getName();
	}
	
	/**
	* Returns 0 if <code>{@link #getObject}</code> returns null.
	* Else returns <code>getObject().hashCode()</code>.
	*/
	public int getHashCode() {
		if (getObject() == null) return 0;
		
		return getObject().hashCode();
	}
	
	/**
	* Returns the total number of fields stored in all the values of {@link #classToFields}.
	* <p>
	* @throws IllegalStateException if {@link #getClassToFields getClassToFields} raises it
	*/
	public int getNumberOfFields() throws IllegalStateException {
		int numberFields = 0;
		for (Class c : getClassToFields().keySet()) {
			numberFields += getClassToFields().get(c).length;
		}
		return numberFields;
	}
	
	// -------------------- toString and helper methods --------------------
	
	/** Returns <code>{@link #toString(String) toString}("\t")</code>. */
	@Override public String toString() throws RuntimeException {
		return toString("\t");
	}
	
	/**
	* Returns a simple String which barely describes the data in this class compared to {@link #toStringLabeled(String, String) toStringLabeled}.
	* <p>
	* The result always consists of but a single line which has all the fields of {@link #object} recorded by this instance.
	* These fields appear only as their values (e.g. no field name labels are present),
	* with the value's toString method (from whatever class it belongs to) being used to generate the String form of the value.
	* If a null reference is ever encountered, it is represented by the text "null".
	* <p>
	* The result is likely to be very confusing unless {@link #filters} is very restrictive
	* (e.g. is something like {@link #immediateInstanceState}).
	* In contrast, toStringLabeled can handle any scenario, since it labels its result.
	* <p>
	* Contract: the result is never blank and only ends with a newline if that newline is from the last field's value.
	* <p>
	* @param separator text used to separate the fields inthe result; may not be null
	* @throws RuntimeException (or some subclass) if any problem occurs; this RuntimeException may wrap some underlying Throwable
	*/
	public String toString(String separator) throws RuntimeException {
		try {
			Check.arg().notNull(separator);
			
			StringBuilder sb = new StringBuilder( getNumberOfFields() * 16 );	// guesstimate that each field averages 16 chars in length
			if (getObject() == null) {
				sb.append("null");
			}
			else {
				for (Field[] fields : getClassToFields().values()) {
					for (Field field : fields) {
						if (sb.length() > 0) sb.append(separator);
						Object value = field.get( getObject() );
						String valueAsString = (value != null) ? value.toString() : "null";
						sb.append( valueAsString );
					}
				}
			}
			return sb.toString();
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- toStringLabeled and helper methods --------------------
	
	/** Returns <code>{@link #toStringLabeled(int) toStringLabeled}(0)</code>. */
	public String toStringLabeled() throws RuntimeException {
		return toStringLabeled(0);
	}
	
	/** Returns <code>{@link #toStringLabeled(String, String) toStringLabeled}( {@link StringUtil#getTabs StringUtil.getTabs}.(indentLevel), "\t" )</code>. */
	public String toStringLabeled(int indentLevel) throws RuntimeException {
		// indentLevel checked by getTabs below
		
		return toStringLabeled( StringUtil.getTabs(indentLevel), "\t" );
	}
	
	/**
	* Returns a complex String which more fully describes the data in this class compared to {@link #toString(String)}.
	* <p>
	* The result always consists of multiple lines, with a given line dedicated to each piece of information.
	* The first line describes the calling thread.
	* The second line reports {@link #getHashCode}.
	* Finally, the remaining lines list all the fields of {@link #object} recorded by this instance.
	* These fields appear in the form "name = value",
	* with {@link #toStringSmart} being used to generate the String form of the value.
	* The class that a field was declared in actually appears first on its own line before its declared fields are listed.
	* <p>
	* Contract: the result is never blank and always ends with a newline.
	* <p>
	* @param prefix optional text that appears at the start of every line in the result; may be null
	* @param indent text used to indent by one level; may not be null
	* @throws RuntimeException (or some subclass) if any problem occurs; this RuntimeException may wrap some underlying Throwable, so be sure to inspect its cause
	*/
	public String toStringLabeled(String prefix, String indent) throws RuntimeException {
		try {
			if (prefix == null) prefix = "";
			Check.arg().notNull(indent);
			
			if (isInObjectGraph(getObject())) {
				return prefix + "<OBJECT ALREADY ENCOUNTERED; see hashCode = " + getHashCode() + ">" + '\n';
			}
			
			StringBuilder sb = new StringBuilder( 64 + (getNumberOfFields() * 32) );	// guesstimate that each field averages 32 chars in length
			
/*
decided to remove this for now...

			if (isObjectGraphEmpty()) {
				sb.append(prefix).append("calling thread = ").append( Thread.currentThread().toString() ).append('\n');
			}
*/
			
			try {
				incrementObjectGraph( getObject() );
				
				if (getObject() == null) {
					sb.append(prefix).append("<NULL object reference>").append('\n');
				}
				else {
					boolean first = true;
					for (Class c : getClassToFields().keySet()) {
						if (first) {
							sb.append(prefix).append( c.getName() ).append(", hashCode = ").append( getHashCode() ).append('\n');
							first = false;
						}
						else {
							sb.append(prefix).append( c.getName() ).append('\n');
						}
						appendFields(c, sb, prefix + indent, indent);	// + indent because want the field lines to be indented relative to the type line
					}
				}
			}
			finally {
				decrementObjectGraph( getObject() );
			}
			
			return sb.toString();
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	private void appendFields(Class c, StringBuilder sb, String prefix, String indent) throws Exception {
		Field[] fields = getClassToFields().get(c);
		if (fields.length == 0) {
			sb.append(prefix).append("<NO FIELDS (either none declared, or none pass all the filters)>").append('\n');
			return;
		}
		
		for (Field field : fields) {
			appendField(field, sb, prefix, indent);
		}
	}
	
	private void appendField(Field field, StringBuilder sb, String prefix, String indent) throws Exception {
		sb.append(prefix).append( field.getName() ).append(" = ");
		Object value = field.get( getObject() );
		String valueAsString = toStringSmart(value, prefix + indent, indent, getFilters());	// prefix + indent because if need to use multilines, then want them indented relative to the field name line
		sb.append( valueAsString );
		if (!valueAsString.endsWith("\n")) sb.append('\n');
	}
	
	// -------------------- generateToStringCode, generateToStringLabeledCode --------------------
	
	/**
	* Returns a String which is valid java source code for a toString implementation for {@link #object}.
	* <p>
	* The result contains every field present in {@link #classToFields}.
	* Each such field is given its own line in the source code,
	* but the result that the source code produces has every field in a single tab delimited line.
	* <p>
	* If a field's access level is such that its direct access will cause an IllegalAccessException,
	* then the line for that field is actually commented out and a diagnostic error line is printed beneath it
	* which explains the issue and possibly offers resolution (the programmer will have to manually intervene).
	* <p>
	* If a field is a static, then a warning comment is placed at the end of its line
	* suggesting that the line be removed.
	* <p>
	* The programer is highly likely to need to hand edit the result before using it in an actual class:
	* the warnings and errors mentioned above should be dealt with,
	* custom decisions may need be made about how to handle individual fields, etc.
	* This method merely helps automate the process of writing a custom toString method
	* for someone who does not want to use the generic {@link #toString(String) toString}
	* (e.g. to obtain more customization or performance).
	* <p>
	* Contract: the result is never blank and always ends with a newline.
	*/
	public String generateToStringCode() {
		return generateCode(false);
	}
	
	/**
	* Returns a String which is valid java source code for a toStringLabeled implementation for {@link #object}.
	* <p>
	* The result contains every field present in {@link #classToFields}.
	* Each such field is given its own line in the result,
	* both in the source code as well as the result that the source code produces.
	* <p>
	* If a field's access level is such that its direct access will cause an IllegalAccessException,
	* then the line for that field is actually commented out and a diagnostic error line is printed beneath it
	* which explains the issue and possibly offers resolution (the programmer will have to manually intervene).
	* <p>
	* If a field is a static, then a warning comment is placed at the end of its line
	* suggesting that the line be removed.
	* <p>
	* The programer is highly likely to need to hand edit the result before using it in an actual class:
	* the warnings and errors mentioned above should be dealt with,
	* custom decisions may need be made about how to handle individual fields, etc.
	* This method merely helps automate the process of writing a custom toStringLabeled method
	* for someone who does not want to use the generic {@link #toStringLabeled(String, String) toStringLabeled}
	* (e.g. to obtain more customization or performance).
	* <p>
	* Contract: the result is never blank and always ends with a newline.
	*/
	public String generateToStringLabeledCode() {
		return generateCode(true);
	}

	private String generateCode(boolean labeled) {
		StringBuilder sb = new StringBuilder( 128 + (getNumberOfFields() * 32) );	// guesstimate that each field averages 32 chars in length
		
		if (labeled) sb.append("\t").append("public String toStringLabeled() {").append('\n');
		else sb.append("\t").append("public String toString() {").append('\n');
		
		if (getNumberOfFields() == 0) {
			sb.append("\t\t").append("return \"\";").append("// Note: this object either has no state, or none that pass the current filters").append('\n');
		}
		else {
			sb.append("\t\t").append("return").append('\n');
			AtomicBoolean first = new AtomicBoolean(true);	// use AtomicBoolean not for its atomicity, but for its mutability
			for (Class c : getClassToFields().keySet()) {
				appendCodeForClass(c, sb, first, labeled);
			}
			sb.append("\t\t").append(';').append('\n');
			sb.append("// +++ HAND EDIT: it would be more conventional to put the semicolon above at the end of the final output line").append('\n');
		}
		
		sb.append("\t").append("}").append('\n');
		
		return sb.toString();
	}
	
	private void appendCodeForClass(Class c, StringBuilder sb, AtomicBoolean first, boolean labeled) {
		sb.append("\t\t\t\t").append("// state from ").append( c.getName() ).append(':').append('\n');
		
		Field[] fields = getClassToFields().get(c);
		if (fields.length == 0) {
			sb.append("\t\t\t").append("// NONE: this class either declares no state, or declares none that pass the current filters").append('\n');
			return;
		}
		
		for (Field field : fields) {
			String errors = fieldErrors(field);
			if (errors.length() > 0) {
				sb.append("//");
			}
			
			sb.append( lineStart(first, labeled) );
			if (labeled) sb.append("\"").append( field.getName() ).append(" = \" + ");
			sb.append( field.getName() );
			if (labeled) sb.append(" + ").append("\"\\n\"");
			sb.append( fieldWarnings(field) ).append('\n');
			
			if (errors.length() > 0) {
				sb.append( errors );
			}
		}
	}
	
	private String lineStart(AtomicBoolean first, boolean labeled) {
		if (first.get()) {
			first.set(false);
			return "\t\t\t";
		}
		else {
			if (labeled) return "\t\t\t" + " + ";
			else return "\t\t\t" + " + " + "\"\\t\"" + " + ";	// want toString's code to tab delimit the fields on a single line
		}
	}
	
	private String fieldErrors(Field field) {
		StringBuilder sb = null;
		
		if (field.getDeclaringClass() != getObject().getClass()) {
			if (
				ReflectUtil.isDefault(field) &&
				!field.getDeclaringClass().getPackage().equals(object.getClass().getPackage())
			) {
				if (sb == null) sb = new StringBuilder();
				sb.append("// +++ INACCESSABLE: field above has default access and belongs to a different Class that is in a different package than object's");
			}
			else if (ReflectUtil.isPrivate(field)) {
				if (sb == null) sb = new StringBuilder();
				sb.append("// +++ INACCESSABLE: field above has private access and belongs to a different Class than object's");
			}
			
			if (sb != null) sb.append("; usual fix: replace direct field access with an accessor method").append('\n');
		}
		
		return (sb != null) ? sb.toString() : "";
	}
	
	private String fieldWarnings(Field field) {
		StringBuilder sb = null;
		
		if (ReflectUtil.isStatic(field)) {
			if (sb == null) sb = new StringBuilder();
			sb.append('\t').append("// WARNING: field is static; may wish to skip it");
		}
		
		return (sb != null) ? sb.toString() : "";
	}
	
	// -------------------- serializeState, deserializeState, checkForChanges --------------------
	
// +++ write code which checks to see if a class has changed its state
// probably need 3 methods:
//	1: serializeState
//		serializes classToFields to an OutputStream
//	2: deserializeState
//		deserializes classToFields from an InputStream
//	2: checkForChanges
//		compares 2 classToFields (e.g. a previously serialzed one and the current one); need to
//		a) find all classes/interfaces which are in one but not the other,
//		and on classes/interfaces whose modifiers have changed
//		b) for classes/interfaces which are in both, need to report on fields which are in one but not the other,
//		and on fields whose modifiers and types changed

// When write the 2 methods above, should change generateToStringLabeledCode above
// so that it the first time that it is executed, it makes calls to deserializeState and checkForChanges
// which detect if it is now obsolete
	
	// -------------------- Filter and some standard implementations (static inner interface and classes) --------------------
	
	/**
	* Interface for types which accept/reject {@link Class Classes}/{@link Field Fields}.
	* One use is to limit what gets put into {@link #classToFields} during construction.
	* Implementations must be multithread safe.
	*/
	public static interface Filter {
		
		/** Returns true if this instance accepts clazz for object. */
		boolean acceptClass(Class clazz, Object object);
		
		/** Returns true if this instance accepts field for object. */
		boolean acceptField(Field field, Object object);
		
	}
	
	// Class-only filters:
	
	/** Base class for Filters which care only about Classes. */
	public abstract static class ClassFilterAbstract implements Filter {
		
		/** Always returns true. */
		public boolean acceptField(Field field, Object object) { return true; }
		
	}
	
	/** Accepts a Class only if it is object's class.  Rejects everything else. This class is multithread safe: it is stateless. */
	public static class AcceptOnlyImmediateClass extends ClassFilterAbstract {
		
		/** Returns <code>clazz.equals(object.getClass())</code>. */
		public boolean acceptClass(Class clazz, Object object) { return clazz.equals(object.getClass()); }
		
	}
	
	/** Rejects a Class if it is Object.class.  Accepts everything else. This class is multithread safe: it is stateless. */
	public static class RejectObjectClass extends ClassFilterAbstract {
		
		/** Returns <code>!clazz.equals(Object.class)</code>. */
		public boolean acceptClass(Class clazz, Object object) { return !clazz.equals(Object.class); }
		
	}
	
	/** Rejects a Class if it is an interface.  Accepts everything else. This class is multithread safe: it is stateless. */
	public static class RejectInterfaces extends ClassFilterAbstract {
		
		/** Returns <code>!clazz.isInterface()</code>. */
		public boolean acceptClass(Class clazz, Object object) { return !clazz.isInterface(); }
		
	}
	
	// Field-only filters:
	
	/** Base class for Filters which care only about Fields. */
	public abstract static class FieldFilterAbstract implements Filter {
		
		/** Always returns true. */
		public boolean acceptClass(Class clazz, Object object) { return true; }
		
	}
	
	/** Accepts a Field only if it is public or protected.  Rejects everything else. This class is multithread safe: it is stateless. */
	public static class AcceptOnlyPublicProtectedFields extends FieldFilterAbstract {
		
		/** Returns <code>!{@link ReflectUtil#isPublic ReflectUtil.isPublic}(field)</code>. */
		public boolean acceptField(Field field, Object object) { return ReflectUtil.isPublic(field) || ReflectUtil.isProtected(field); }
		
	}
	
	/** Rejects a Field if it is static.  Accepts everything else. This class is multithread safe: it is stateless. */
	public static class RejectStaticFields extends FieldFilterAbstract {
		
		/** Returns <code>!{@link ReflectUtil#isStatic ReflectUtil.isStatic}(field)</code>. */
		public boolean acceptField(Field field, Object object) { return !ReflectUtil.isStatic(field); }
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_describe() {
			System.out.println();
			System.out.println("Here is is the immediate state of a String:");
			System.out.println( describe("ijvvoklvllbvmdfmdfmb", "\t") );
		}
		
		@Test public void test_toString() {
			System.out.println();
			System.out.println("Here is how null is handled:");
			System.out.println( new ObjectState(null).toString() );
			
			System.out.println();
			System.out.println("Here is how an object with no fields is handled:");
			System.out.println( new ObjectState( new NoFields() ).toString() );
			
			System.out.println();
			System.out.println("Here is how a complex class is handled:");
			System.out.println( new ObjectState( new SuperClass() ).toString() );
			
			System.out.println();
			System.out.println("Here is how an object with a complex superclass is handled; note that separator is 2 spaces:");
			System.out.println( new ObjectState( new SubClass() ).toString("  ") );
			
			System.out.println();
			System.out.println("Here is how an object which has fields that are non-trivial types is handled:");
			System.out.println( new ObjectState( new CompoundClass() ).toString() );
			
			ReferringClass num1 = new ReferringClass();
			ReferringClass num2 = new ReferringClass();
			ReferringClass num3 = new ReferringClass();
			num1.other = num2;
			num2.other = num3;
			num3.other = num1;
			System.out.println();
			System.out.println("Here is how an object whose object graph has a circular chain of references is handled:");
			System.out.println( new ObjectState(num1).toString() );
			
			System.out.println();
			System.out.println("Here is how an object with a complex interface hierarchy  is handled:");
			System.out.println( new ObjectState( new InterfaceImplementingClass() ).toString() );
		}
		
		@Test public void test_toStringLabeled() {
			System.out.println();
			System.out.println("Here is how null is handled:");
			System.out.println( new ObjectState(null).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is how an object with no fields is handled:");
			System.out.println( new ObjectState( new NoFields() ).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is how a complex class is handled:");
			System.out.println( new ObjectState( new SuperClass() ).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is how an object with a complex superclass is handled; note that prefix is 2 dashes, and indent is 2 spaces:");
			System.out.println( new ObjectState( new SubClass() ).toStringLabeled("--", "  ") );
			
			System.out.println();
			System.out.println("Here is how an object which has fields that are non-trivial types is handled:");
			System.out.println( new ObjectState( new CompoundClass() ).toStringLabeled(1) );
			
			ReferringClass num1 = new ReferringClass();
			ReferringClass num2 = new ReferringClass();
			ReferringClass num3 = new ReferringClass();
			num1.other = num2;
			num2.other = num3;
			num3.other = num1;
			System.out.println();
			System.out.println("Here is how an object whose object graph has a circular chain of references is handled:");
			System.out.println( new ObjectState(num1).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is how an object with a complex interface hierarchy  is handled:");
			System.out.println( new ObjectState( new InterfaceImplementingClass() ).toStringLabeled(1) );
		}
		
		@Test public void test_generateToStringCode() {
			System.out.println();
			System.out.println("Here is the output of generateToStringCode for a complex class:");
			System.out.println( new ObjectState( new SubClass() ).generateToStringCode() );
		}
		
		@Test public void test_generateToStringLabeledCode() {
			System.out.println();
			System.out.println("Here is the output of generateToStringLabeledCode for a complex class:");
			System.out.println( new ObjectState( new SubClass() ).generateToStringLabeledCode() );
		}
// +++ both test_generateXXX methods above have no assertions, which is not so good; it relies on manual inspection at the moment;
// should I compare literal string output agreement?  or is that too rigid?
		
		@Test public void test_Filters() {
			System.out.println();
			System.out.println("Here is the effect of the AcceptOnlyImmediateClass filter:");
			System.out.println( new ObjectState( new SubClass(), new AcceptOnlyImmediateClass() ).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is the effect of the RejectObjectClass filter:");
			System.out.println( new ObjectState( new NoFields(), new RejectObjectClass() ).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is the effect of the RejectInterfaces filter:");
			System.out.println( new ObjectState( new InterfaceImplementingClass(), new RejectInterfaces() ).toStringLabeled(1) );
			
			System.out.println();
			System.out.println("Here is the effect of the AcceptOnlyPublicProtectedFields plus the RejectStaticFields filter:");
			System.out.println( new ObjectState( new SuperClass(), new AcceptOnlyPublicProtectedFields(), new RejectStaticFields() ).toStringLabeled(1) );
		}
		
		
// +++ the classes below offer a decent variety of challenges for my code,
// but what really ought to do in order to stress test ObjectState
// is to randomly generate Classes and interfaces and fields with all permutations of modifiers and types
// and then confirm that no bugs are present (how? see if can recreate the original source code?).
// Not sure how to create Java classes on the fly
//	--need to look into the new compiler api
//	--or try hotpatching: http://www.fasterj.com/articles/hotpatch1.shtml
		
		private static class NoFields {}
		
		/**
		* Has fields which provide complete coverage of:
		* <pre><code>
			all modifier permutations
				public protected <none> private
				static
				final
			many field types
				primitives
				arrays, Collection, Map
				java.lang and CharSequence
		* </code></pre>
		* Also, all the values are distinct, both within this class and from {@link SubClass} below.
		*/
		private static class SuperClass {
			public static final boolean boolean_1 = false;
			protected static final byte byte_1 = (byte) (1*100 + 1);
			static final char char_1 = (char) (1*100 + 2);
			private static final double double_1 = (double) (1*100 + 3);
			
			public static float float_1 = (float) (1*100 + 4);
			protected static int int_1 = 1*100 + 5;
			static long long_1 = (long) (1*100 + 6);
			private static short short_1 = (short) (1*100 + 7);
			
			public final double[] doubleArray_1 = new double[] {(double) (1*110 + 1), (double) (1*110 + 2), (double) (1*110 + 3)};
			protected final Object[] ObjectArray_1 = new Object[] {new Object(), new Object()};
			final Collection<Object> ObjectCollection_1 = Arrays.asList( new Object(), new Object() );
			private final Map<Integer,Character> intsToCharsMap_1 = new TreeMap<Integer,Character>();
			{
				intsToCharsMap_1.put(1*120 + 1, 'a');
				intsToCharsMap_1.put(1*120 + 2, 'b');
			}
			
			public Class Class_1 = SuperClass.class;
			protected Object Object_1 = null;
			String String_1 = "String_1";
			private StringBuffer StringBuffer_1 = new StringBuffer("StringBuffer_1");
		}
		
		/** Has fields which provide similar complete coverage and distinct values as {@link SuperClass}. */
		private static class SubClass extends SuperClass {
			public static final boolean boolean_2 = true;
			protected static final byte byte_2 = (byte) (2*100 + 1);
			static final char char_2 = (char) (2*100 + 2);
			private static final double double_2 = (double) (2*100 + 3);
			
			public static float float_2 = (float) (2*100 + 4);
			protected static int int_2 = 2*100 + 5;
			static long long_2 = (long) (2*100 + 6);
			private static short short_2 = (short) (2*100 + 7);
			
			public final float[] floatArray_2 = new float[0];
			protected final Object[] ObjectArray_2 = null;
			final Collection<Object> ObjectCollection_2 = new LinkedList<Object>();
			private final Map<Integer,Character> intsToCharsMap_2 = new TreeMap<Integer,Character>();
			
			public Class Class_2 = SubClass.class;
			protected Object Object_2 = new Object();
			String String_2 = "String_2";
			private StringBuffer StringBuffer_2 = new StringBuffer("StringBuffer_2");
		}
		
		private static class CompoundClass {
			static InnerClass1 innerClass1 = new InnerClass1();
			InnerClass2 innerClass2 = new InnerClass2();
			
			static class InnerClass1 {
				Object field1 = new Object();
			}
			
			class InnerClass2 {
				Object field2 = new Object();
			}
		}
		
		private static class ReferringClass {
			private Object other;
		}
		
		private static interface SuperInterface1 {
			Object interfaceConstant1 = new Object();
		}
		
		private static interface SubInterface2 extends SuperInterface1 {
			Object interfaceConstant1 = new Object();
		}
		
		private static interface SuperInterface3 extends SuperInterface1 {	// NOTE: am doing extends SuperInterface1 so that SuperInterface1 appears twice in the interface hierarchy of InterfaceImplementingClass below in order to test how this situation is handled (SuperInterface1 should only appear once in the printout, just after SubInterface2)
			Object interfaceConstant3 = new Object();
		}
		
		private static interface SubInterface4 extends SuperInterface3 {
			Object interfaceConstant4 = new Object();
		}
		
		private static class InterfaceImplementingClass implements SubInterface2, SubInterface4 {}
		
	}
	
}
