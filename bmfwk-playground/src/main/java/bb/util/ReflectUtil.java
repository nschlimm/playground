/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

--several of the methods in this class (get, set, getMethod) subvert the normal JVM access protections
	--the essential technique is a call to Field.setAccessible(true)
		--this technique is known to the JUnit community for accessing private members for testing.
			They developed classes like
				PrivilegedAccessor (is what works with newer 1.3+ jvms?)
				http://jsourcery.com/output/objectweb/c-jdbc/2.0-rc1/org/objectweb/cjdbc/scenario/tools/util/PrivilegedAccessor.source.html#770000921
			and
				PrivateAccessor(only works with old 1.2- jvms?)
				http://www.devx.com/Java/Article/9614
	--SEE ALSO the ObjectState class, which has some powerful functionality for getting descriptions of an arbitrary Object's state.
	It includes a Filter inner interface which can be used to eliminate undesired classes/fields from consideration.
*/

package bb.util;

import bb.io.StreamUtil;
import bb.misc.MiscFactory;
import bb.util.logging.LogUtil;
import bb.util.logging.Logger2;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods related to reflection.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class ReflectUtil {
	
	// -------------------- constants --------------------
	
	/** An empty Class[] that caches the signature used by {@link #callLogError(Object, String)}. */
	private static final Class[] noArgSignature = new Class[0];
	
	/** An empty Object[] that caches the parameter used by {@link #callLogError(Object, String)}. */
	private static final Object[] noArgParameters = new Object[0];
	
	// -------------------- enum methods: getEnumValues --------------------
	
	/**
	* Uses reflection to find and execute a static method named "values" that takes no arguments.
	* Such a method is guaranteed to be injected into every Java enum by the compiler.
	* <p>
	* You can always easily get all the values of an enum if you know the specific class name at compile time.
	* For example, if you have an enum named <code>Planet</code>, then your can call <code>Planet.values()</code>.
	* The purpose of this method, however, is to get all the values of an enum when you do not know its specific classname,
	* but are working with a type parameter that merely says that it is an enum.
	* <p>
	* @throws RuntimeException should actually be one of:
	* <ol>
	*  <li>IllegalArgumentException if e is null</li>
	*  <li>SecurityException see {@link Class#getMethod Class.getMethod}</li>
	* </ol>
	* (this method declares that it throws RuntimeException not only for simplicity,
	* but also because otherwise would be forced by the compiler to declare a bunch of other checked exception types that should never be thrown here)
	*/
	@SuppressWarnings("unchecked")
	public static <E extends Enum<E>> E[] getEnumValues(E e) throws RuntimeException {
		Check.arg().notNull(e);
		
		try {
			Class<E> enumType = e.getDeclaringClass();
			Method method = enumType.getMethod("values", noArgSignature);
			return (E[]) method.invoke( null, noArgParameters);	// supply null because the values() method of an enum is static; see http://java.sun.com/docs/books/tutorial/java/javaOO/enum.html
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- field methods: get, set, diagnoseGetProblem, fieldsDeclaredReport --------------------
	
	/**
	* Accesses the value of the field of obj which is named fieldName.
	* <p>
	* The implementation here suppresses all Java access checks,
	* which means that it can be used to return the values of, say, private fields.
	* <b>Warning:</b> this check suppression fails with certain classes (e.g. security sensitive ones from the JDK).
	* <p>
	* @throws Exception should actually be one of:
	* <ol>
	*  <li>IllegalArgumentException if obj is null; fieldName is {@link Check#notBlank blank}</li>
	*  <li>NoSuchFieldException if a field with fieldName is not found</li>
	*  <li>NullPointerException if fieldName is null</li>
	*  <li>SecurityException see {@link Class#getDeclaredField Class.getDeclaredField}; {@link Field#setAccessible(boolean) Field.setAccessible}</li>
	* </ol>
	* (this method declares that it throws Exception not only for simplicity,
	* but also because otherwise would be forced by the compiler to declare a bunch of other checked exception types that should never be thrown here)
	*/
	public static Object get(Object obj, String fieldName) throws Exception {
		Check.arg().notNull(obj);
		Check.arg().notBlank(fieldName);
		
		Field field = obj.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);	// CRITICAL: this is what allows us to subvert access controls; do not need to undo, because it is a local variable and setAccessible only affects this instance
		return field.get(obj);
	}
	
// +++ append "Field" to the names of the above and below?
	
	/**
	* Mutates the value of the field of obj which is named fieldName.
	* <p>
	* The implementation here suppresses all Java access checks,
	* which means that it can be used to set the values of, say, private and/or final fields.
	* <b>Warning:</b> this check suppression fails with certain classes (e.g. security sensitive ones from the JDK).
	* <p>
	* @throws Exception should actually be one of:
	* <ol>
	*  <li>IllegalArgumentException if obj is null; fieldName is {@link Check#notBlank blank}</li>
	*  <li>NoSuchFieldException if a field with fieldName is not found</li>
	*  <li>SecurityException see {@link Class#getDeclaredField Class.getDeclaredField}; {@link Field#setAccessible(boolean) Field.setAccessible}</li>
	* </ol>
	* (this method declares that it throws Exception not only for simplicity,
	* but also because otherwise would be forced by the compiler to declare a bunch of other checked exception types that should never be thrown here)
	*/
	public static void set(Object obj, String fieldName, Object value) throws Exception {
		Check.arg().notNull(obj);
		Check.arg().notBlank(fieldName);
		// NOTE: no checks on value (it can even be null)
		
		Field field = obj.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);	// CRITICAL: this is what allows us to subvert access controls; do not need to undo, because it is a local variable and setAccessible only affects this instance
		field.set(obj, value);
	}
	
	/**
	* Returns a useful diagnostic message for a Throwable generated when calling {@link Field#get Field.get}.
	* <p>
	* @throws IllegalArgumentException if t is null
	*/
	public static String diagnoseGetProblem(Throwable t) throws IllegalArgumentException {
		Check.arg().notNull(t);
		
		if (t instanceof IllegalAccessException) return "Field is inaccessible";
		else if (t instanceof IllegalArgumentException) return "the Object supplied to the Field's get method is not an instance of the class or interface corresponding to the Field";
		else if (t instanceof NullPointerException) return "the Object supplied to the Field's get method is null, but the Field is an instance member";
		else if (t instanceof ExceptionInInitializerError) return "the initialization provoked by the Field's get method failed";
		else return "an unexpected problem," + ThrowableUtil.getTypeAndMessage(t) + ", occurred";
	}
	
	/**
	* Returns a String describing all of obj's declared fields
	* (i.e. just those fields declared in the obj's class itself, whether static or instance, regardless of access level,
	* but never including fields from superclasses/superinterfaces).
	* Each field's description starts with the result of calling {@link Field#toString} and ends with its value in obj.
	* The fields are ordered by {@link FieldComparator} instance.
	* <p>
	* @throws RuntimeException should actually be one of:
	* <ol>
	*  <li>IllegalArgumentException if obj is null</li>
	*  <li>SecurityException see {@link Class#getDeclaredField Class.getDeclaredField}; {@link Field#setAccessible(boolean) Field.setAccessible}</li>
	* </ol>
	* This method ensures that it only throws RuntimeException by catching all Throwables
	* and wrapping them in a RuntimeException if not already one before rethrowing them.
	* This is done not only for the convenience of the caller,
	* but also because otherwise would be forced by the compiler to declare a bunch of checked exceptions that should never be thrown here.
	*/
	public static String fieldsDeclaredReport(Object obj) throws RuntimeException {
		try {
			Check.arg().notNull(obj);
			
			StringBuilder sb = new StringBuilder();
			Field[] fields = obj.getClass().getDeclaredFields();
			Arrays.sort( fields, FieldComparator.getInstance() );
			for (int i = 0; i < fields.length; i++) {
				fields[i].setAccessible(true);	// CRITICAL: this is what allows us to subvert access controls; do not need to undo, because it is a local variable and setAccessible only affects this instance
				sb.append( "fields[" ).append( i ).append( "]: " ).append( fields[i].toString() ).append( ", value = " ).append( fields[i].get(obj) ).append( '\n' );
			}
			return sb.toString();
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}
	
	// -------------------- method methods: getMethod, callLogError --------------------
	
	/**
	* Uses reflection to find a method named methodName with a signature given by paramClasses on obj.
	* First searches for a matching public method that is declared in obj's Class, or else one of its ancestors.
	* As a fallback if that search fails, searches for any (non-public) matching method that is declared only in obj's Class.
	* <p>
	* Contract: the result is never null, and is always marked as accessible by a call to {@link Method#setAccessible setAccessible}(true).
	* <p>
	* @throws Exception should actually be one of:
	* <ol>
	*  <li>IllegalArgumentException if obj is null; methodName is blank</li>
	*  <li>SecurityException see {@link Class#getMethod Class.getMethod}; {@link Class#getDeclaredMethod Class.getDeclaredMethod}</li>
	*  <li>NoSuchMethodException if a matching method is not found</li>
	* </ol>
	*/
	public static Method getMethod(Object obj, String methodName, Class[] paramClasses) throws Exception {
		Check.arg().notNull(obj);
		Check.arg().notBlank(methodName);
		// no check on paramClasses, may be null
		
		Method method = null;
		try {
			method = obj.getClass().getMethod(methodName, paramClasses);
		}
		catch (Throwable t) {
			method = obj.getClass().getDeclaredMethod(methodName, paramClasses);
		}
		method.setAccessible(true);	// CRITICAL: this is what allows us to subvert access controls; do not need to undo, because it is a local variable and setAccessible only affects this instance
		return method;
	}
	
	/**
	* Returns <code>{@link #callLogError(Object, String, Class[], Object[])}(obj, methodName, {@link #noArgSignature}, {@link #noArgParameters})</code>.
	* This is a convenience method for calling no-arg methods.
	*/
	public static Object callLogError(Object obj, String methodName) {
		return callLogError(obj, methodName, noArgSignature, noArgParameters);
	}
	
	/**
	* Uses {@link #getMethod getMethod} to find a method named methodName with a signature given by paramClasses on obj,
	* and then invokes that method on obj using paramValues and returns the result.
	* <p>
	* Contract: this method should never throw a Throwable.
	* Any Throwable that is raised is caught and {@link Logger2#log(LogRecord) logged robustly} to the {@link LogUtil#getLogger2 default Logger}.
	* An example of such a Throwable is a NoSuchMethodException if obj does not actually have the specified method.
	* If such a Throwable is caught, the result of this method is null.
	* <b>Warning:</b> null could also be the normal result of the method invocation, so it is imposible to use that to determine if a problem happened.
	* <p>
	* Motivation: this method was originally written to support resource closing methods of objects inside finally blocks
	* (e.g. this method is used by {@link StreamUtil#close(Object)}).
	* The issue here is that the finally block may need multiple such methods to be called,
	* so none should not throw a Throwable because that would stop subsequent resources from being closed.
	* Using this method allows each to be conveniently called with its own error handling,
	* as opposed to wrapping each method inside its own dedicated try-catch block.
	*/
	public static Object callLogError(Object obj, String methodName, Class[] paramClasses, Object[] paramValues) {
		try {
			return getMethod(obj, methodName, paramClasses).invoke(obj, paramValues);
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.SEVERE, "ReflectUtil", "callLogError", "caught an unexpected Throwable", t);
			return null;
		}
	}
	
	// -------------------- modifier methods: isXXX --------------------
	
	/** Returns <code>{@link Modifier#isPublic Modifier.isPublic}( f.{@link Field#getModifiers} )</code>. */
	public static boolean isPublic(Field f) { return Modifier.isPublic( f.getModifiers() ); }
	
	/** Returns <code>{@link Modifier#isProtected Modifier.isProtected}( f.{@link Field#getModifiers} )</code>. */
	public static boolean isProtected(Field f) { return Modifier.isProtected( f.getModifiers() ); }
	
	/**
	* Returns true if f has default (unnamed) access.
	* This is determined by the logic <code>!{@link #isPublic isPublic}(f) && !{@link #isProtected isProtected}(f) && >!{@link #isPrivate isPrivate}(f)</code>.
	*/
	public static boolean isDefault(Field f) { return !isPublic(f) && !isProtected(f) && !isPrivate(f); }
	
	/** Returns <code>{@link Modifier#isPrivate Modifier.isPrivate}( f.{@link Field#getModifiers} )</code>. */
	public static boolean isPrivate(Field f) { return Modifier.isPrivate( f.getModifiers() ); }
	
	/** Returns <code>{@link Modifier#isStatic Modifier.isStatic}( f.{@link Field#getModifiers} )</code>. */
	public static boolean isStatic(Field f) { return Modifier.isStatic( f.getModifiers() ); }
	
	/** Returns <code>{@link Modifier#isFinal Modifier.isFinal}( f.{@link Field#getModifiers} )</code>. */
	public static boolean isFinal(Field f) { return Modifier.isFinal( f.getModifiers() ); }
	
// +++ there are more Modifiers to consider...
	
	// -------------------- misc methods: findSignature --------------------
	
	/**
	* Returns a Class[] where each element is the Class of the corresponding Object in the arguments array.
	* If arguments is null, a zero-element array is returned.
	*/
	public static Class[] findSignature(Object[] arguments) {
		if (arguments == null)
			return new Class[0];
// +++ this is probably wrong--null in should return null back...
		else {
			Class[] signature = new Class[arguments.length];
			for (int i = 0; i < signature.length; i++) {
				signature[i] = arguments[i].getClass();
			}
			return signature;
		}
	}
	
	// -------------------- constructor --------------------
	
	/** This private constructor suppresses the default (public) constructor, ensuring non-instantiability. */
	private ReflectUtil() {}
	
	// -------------------- FieldComparator (static inner class) --------------------
	
	/**
	* Imposes an ordering on Fields that is <i>consistent with equals</i>; see {@link #compare compare} for details.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
	*/
	public static class FieldComparator implements Comparator<Field>, Serializable {	// see the file compareImplementation.txt for more discussion
		
		private static final FieldComparator instance = new FieldComparator();
		
		private static final long serialVersionUID = 1;
		
		/**
		* Since this class has no instance state, there is no point in creating multiple instances:
		* one instance is the same as another, may be fully concurrently used, etc.
		* So, for top performance, users should call this method instead of creating new instances.
		*/
		public static FieldComparator getInstance() { return instance; }
		
		/**
<!--
		* Tries to order f1 and f2 by static versus instance:
		* if f1 is static and f2 is not, then f1 is taken as less than f2 (so -1 is returned), and vice versa (so 1 is returned).
		* <p>
		* Else tries to order f1 and f2 by final versus mutable:
		* if f1 is final and f2 is not, f1 is taken as less than f2 (so -1 is returned), and vice versa (so 1 is returned).
-->
		* <p>
		* Tries to order f1 and f2 by their names (case irrelevant):
		* returns <code>f1.getName().toLowerCase().compareTo( f2.getName().toLowerCase() )</code> if that result is != 0.
		* <p>
		* Else tries to order f1 and f2 by their class names:
		* returns <code>f1.getDeclaringClass().getName().compareTo( f2.getDeclaringClass().getName() )</code> if that result is != 0.
		* <p>
		* Else returns 0 if <code>f1.{@link Field#equals equals}(f2)</code> is true.
		* This is the only circumstance in which 0 will ever be returned, thus,
		* <i>this Comparator is consistent with equals</i> (see {@link Comparator} for more discussion).
		* <p>
		* Else throws an IllegalStateException.
		* <p>
		* @throws IllegalArgumentException if f1 or f2 is null
		* @throws IllegalStateException if run out of criteria to order f1 and f2
		*/
		public final int compare(Field f1, Field f2) throws IllegalArgumentException, IllegalStateException {
			Check.arg().notNull(f1);
			Check.arg().notNull(f2);
			
/*
			if (isStatic(f1) && !isStatic(f2)) return -1;
			if (!isStatic(f1) && isStatic(f2)) return 1;
			
			if (isFinal(f1) && !isFinal(f2)) return -1;
			if (!isFinal(f1) && isFinal(f2)) return 1;
*/
			
			int nameComparison = f1.getName().toLowerCase().compareTo( f2.getName().toLowerCase() );
			if (nameComparison != 0) return nameComparison;
			
			int classNameComparison = f1.getDeclaringClass().getName().compareTo( f2.getDeclaringClass().getName() );
			if (classNameComparison != 0) return classNameComparison;
			
			if (f1.equals(f2)) return 0;
			
			throw new IllegalStateException("ran out of criteria to order f1 = " + f1 + " and f2 = " + f2);
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static enum Planet { mercury, venus, earth };
		
		@Test public void test_getEnumValues() throws Exception {
			Planet[] planetsReflected = getEnumValues( Planet.mercury );
			Assert.assertArrayEquals( Planet.values(), planetsReflected );
		}
		
		@Test public void test_get_set() throws Exception {
			Object ctro = MiscFactory.createClassToReflectOn();
			
				// must comment out the line below in order for this class to compile--this proves that the ClassToReflectOn.private_field field is access protected against normal Java code:
			//System.out.println("ctro.private_field = " + cwpf.private_field);
			
				// now use set and get to confirm that can mutate/access a private final field:
			Object value = new Object();
			set(ctro, "private_final_field", value);
			Object valueFromGet = get(ctro, "private_final_field");
			Assert.assertEquals( value, valueFromGet );
			
				// prove that String.substring uses the same underlying char[] (in the value field) as its parent String:
			String s1 = "abcdef";
			String s2 = s1.substring(3);
			Assert.assertSame( get(s1, "value"), get(s2, "value") );
				// now examine some of the substring's fields, just to show that can access:
			System.out.println("s2.length() = " + s2.length() );
			System.out.println("s2.value.length = " + ((char[]) get(s2, "value")).length );
			System.out.println("s2.offset = " + get(s2, "offset") );
			System.out.println("s2.count = " + get(s2, "count") );
		}
		
		@Test public void test_diagnoseGetProblem() throws Exception {
			try {
				Object ctro = MiscFactory.createClassToReflectOn();
				Field field = ctro.getClass().getDeclaredField("private_final_field");
				field.get(ctro);	// this should crash because the field is private and access control has not been disabled
				throw new Exception("field.get failed to crash as expected");
			}
			catch (Throwable t) {
				System.out.println( diagnoseGetProblem(t) );
			}
		}
		
		@Test public void test_fieldsDeclaredReport() throws Exception {
			Object ctro = MiscFactory.createClassToReflectOn();
			System.out.println( fieldsDeclaredReport(ctro) );
		}
		
		@Test public void test_callLogError() {
			Object ctro = MiscFactory.createClassToReflectOn();
			String methodName = "echoMethodName";
			Assert.assertEquals( methodName, callLogError(ctro, methodName) );
		}
		
		@Test public void test_isXXX() throws Exception {
			Object ctro = MiscFactory.createClassToReflectOn();
			
			Field public_static_field = ctro.getClass().getDeclaredField("public_static_field");
			Assert.assertTrue( isPublic(public_static_field) );
			Assert.assertFalse( isProtected(public_static_field) );
			Assert.assertFalse( isDefault(public_static_field) );
			Assert.assertFalse( isPrivate(public_static_field) );
			Assert.assertTrue( isStatic(public_static_field) );
			Assert.assertFalse( isFinal(public_static_field) );
			
			Field protected_field = ctro.getClass().getDeclaredField("protected_field");
			Assert.assertFalse( isPublic(protected_field) );
			Assert.assertTrue( isProtected(protected_field) );
			Assert.assertFalse( isDefault(protected_field) );
			Assert.assertFalse( isPrivate(protected_field) );
			Assert.assertFalse( isFinal(protected_field) );
			Assert.assertFalse( isStatic(protected_field) );
			
			Field default_field = ctro.getClass().getDeclaredField("default_field");
			Assert.assertFalse( isPublic(default_field) );
			Assert.assertFalse( isProtected(default_field) );
			Assert.assertTrue( isDefault(default_field) );
			Assert.assertFalse( isPrivate(default_field) );
			Assert.assertFalse( isFinal(default_field) );
			Assert.assertFalse( isStatic(default_field) );
			
			Field private_final_field = ctro.getClass().getDeclaredField("private_final_field");
			Assert.assertFalse( isPublic(private_final_field) );
			Assert.assertFalse( isProtected(private_final_field) );
			Assert.assertFalse( isDefault(private_final_field) );
			Assert.assertTrue( isPrivate(private_final_field) );
			Assert.assertTrue( isFinal(private_final_field) );
			Assert.assertFalse( isStatic(private_final_field) );
		}
		
		@Test public void test_findSignature() {
			Class[] classesExpected = new Class[] {Object.class, String.class, Integer.class};
			Class[] classesFound = findSignature( new Object[] {new Object(), "abcd", new Integer(0)} );
			Assert.assertArrayEquals( classesExpected, classesFound );
		}
		
	}
	
}
