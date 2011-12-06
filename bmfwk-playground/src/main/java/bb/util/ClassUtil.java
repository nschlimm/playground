/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.util;

import bb.io.StreamUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods that deal with the {@link Class} of an object.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @author Brent Boyer
*/
public final class ClassUtil {
	
	// -------------------- findClassFile --------------------
	
	/**
	* Returns a local file that clazz <i>could have been</i> loaded from.
	* Typically this is either a .class or a .jar file.
	* <p>
	* <b>Warning:</b> because there can be multiple URLs that clazz could be loaded from,
	* including not only local files but network URLs too,
	* there is no guarantee that the result is the actual source of clazz's bytes.
	* <p>
	* @throws IllegalArgumentException if clazz is null
	* @throws RuntimeException if unable to find a local file that clazz could have been loaded from
	* @see <a href="http://www.javaworld.com/javaworld/javaqa/2003-07/01-qa-0711-classsrc.html">this article</a>
	* @see <a href="http://www.techtalkz.com/java/102114-how-find-jar-file-ava-class-all-successorshad-been-loaded.html">this article</a>
	*/
	public static File findClassFile(Class clazz) throws IllegalArgumentException, RuntimeException {
		Check.arg().notNull(clazz);
		
		String path = classFilePath(clazz);
		
/*
+++ I do not like this because CodeSource.getLocation does not return the precise location,
for instance, it will return the class files root directory instead of the precise child in the class directory tree where the .class file is

		try {
			ProtectionDomain pd = clazz.getProtectionDomain();
			if (pd != null) {	// unclear if pd can ever be null, but guard against this just in case
				CodeSource cs = pd.getCodeSource();
				if (cs != null) {	// cs can be null depending on classloader behavior
					URL url = cs.getLocation();
					if (url != null) {
						return new File( url.getPath() );
					}
				}
			}
		}
		catch (Exception e) {
			// ignore, since will try other logic below
		}
*/
		
			// first try clazz's ClassLoader:
		try {
			ClassLoader classLoader = clazz.getClassLoader();
			if (classLoader != null) {
				URL url = findFileOrJarUrl( classLoader.getResources(path) );
				if (url != null) return toFile(url, path);
			}
		}
		catch (Exception e) {
			// ignore, since will try other logic below
		}
		
			// if that fails, try the system ClassLoader,
			// which can find the locations of Classes loaded by the bootstrap class loader (e.g. core Classes like String):
		try {
			URL url = findFileOrJarUrl( ClassLoader.getSystemResources(path) );
			if (url != null) return toFile(url, path);
		}
		catch (Exception e) {
			// ignore, since will try other logic below
		}
		
		throw new RuntimeException("unable to locate a file for clazz = " + clazz.getName());
	}
	
	private static URL findFileOrJarUrl(Enumeration<URL> e) {
		while (e.hasMoreElements()) {
			URL url = e.nextElement();
			if (url.getProtocol().equals("file") || url.getProtocol().equals("jar")) {
				return url;
			}
		}
		return null;
	}
	
	private static File toFile(URL url, String pathClass) {
		if (url.getProtocol().equals("file")) {
			return new File( url.getPath() );
		}
		else if (url.getProtocol().equals("jar")) {
				// To understand the logic below, realise that url.toString for a jar file looks something like
				//	jar:file:/D:/software/java/libShared/javamail-1.4.2.jar!/com/sun/mail/iap/Argument.class
				// The initial
				//	jar
				// part is returned by url.getProtocol.
				// The result of calling url.getPath will be
				//	file:/D:/software/java/libShared/javamail-1.4.2.jar!/com/sun/mail/iap/Argument.class
				// The goal of the logic below is to strip the prefix and suffix of the above to produce
				//	/D:/software/java/libShared/javamail-1.4.2.jar
				
			String pathUrl = url.getPath();
			
			String prefix = "file:";
			if (!pathUrl.startsWith(prefix)) throw new IllegalStateException("url = " + url.toString() + " does not start with " + prefix);
			pathUrl = pathUrl.substring(prefix.length());	// skip over prefix
			
			String suffix = "!/" + pathClass;
			int index = pathUrl.indexOf(suffix);
			if (index == -1) throw new IllegalStateException("url = " + url.toString() + " does not end with " + suffix);
			pathUrl = pathUrl.substring(0, index);	// drop suffix
			
			return new File(pathUrl);
		}
		else {
			throw new IllegalStateException("cannot handle url = " + url.toString());
		}
	}

/*
HOLD ON: WHAT I REALLY WANT is for my java code to extract all the contents of jcl.jar to a tmp dir
	--could then simply use ZipUtil when build the self extracting jar file to include that tmp dir among the other contents
	--oh yeah, but the problem is that i do not know where the jcl.jar is, nor what it could be named
		--this contrasts with the approach that was working on here: it will get the bytes as long as they are somewhere on the classpath

NOTE: will need to get all the inner classes from the JAR file too; will
need to use this method from Class:
	public Class<?>[] getDeclaredClasses() throws SecurityException
return a SortedSet or List <Class> of the tree?
	--oh no, this approach too shall fail, because it only picks up declared classes and misses anonymous inner classes


what should i do?  write the bytes of each to an array of tmp files?
*/
			
/*
HERE IS WHERE I LEFT OFF:
--need to writeup in the jar project HOW could make an executable jar builder method		

see:
can the complete Class tree be found at runtime? 
http://forums.sun.com/thread.jspa?threadID=5430125&tstart=0
*/

	
	private static String classFilePath(Class clazz) {
		return clazz.getName().replaceAll("\\.", "/") + ".class";
	}
	
	// -------------------- getBytes --------------------
	
	/**
	* Returns the bytes of clazz.
	* This is accomplished by {@link Class#getClassLoader getting clazz's classloader}
	* and {@link ClassLoader#getResourceAsStream draining the bytes} for clazz.
	* If clazz was originally loaded from some .class file
	* then the result should be exactly equal to the bytes in that file,
	* regardless of whether that file is local or remote.
	* <p>
	* @throws IllegalArgumentException if clazz is null
	* @throws IOException if any I/O problem occurs
	*/
	public static byte[] getBytes(Class clazz) throws IllegalArgumentException, IOException {
		Check.arg().notNull(clazz);
		
		InputStream is = clazz.getClassLoader().getResourceAsStream( classFilePath(clazz) );
		return StreamUtil.drain(is);
	}
	
	// -------------------- getNameSimple --------------------
	
	/**
	* Returns <code>{@link #getNameSimple(Class) getNameSimple}( obj.getClass() )</code>.
	* <p>
	* @throws IllegalArgumentException if obj is null
	*/
	public static String getNameSimple(Object obj) throws IllegalArgumentException {
		Check.arg().notNull(obj);
		
		return getNameSimple( obj.getClass() );
	}
	
	/**
	* Returns <code>{@link #getNameSimple(String) getNameSimple}( clazz.getName() )</code>.
	* <p>
	* The result is usually identical to what {@link Class#getSimpleName clazz.getSimpleName()} returns.
	* The sole exception that it does not return an empty String for anonymous classes,
	* but instead returns whatever name the JVM uses for such classes.
	* <p>
	* @throws IllegalArgumentException if clazz is null
	*/
	public static String getNameSimple(Class clazz) throws IllegalArgumentException {
		Check.arg().notNull(clazz);
		
		return getNameSimple( clazz.getName() );
	}
	
	/**
	* Returns the "simple" name of the fully quallified class name that is stored in nameFull,
	* that is, any leading package info is removed.
	* <p>
	* Algorithm: if nameFull contains at least one period (i.e. '.') char,
	* then every up thru the last period is stripped and everything that comes after is returned.
	* Otherwise nameFull in its entirety is returned.
	* For example, if nameFull equals "java.lang.Object" then "Object" is returned.
	* <p>
	* @throws IllegalArgumentException if nameFull is {@link Check#notBlank blank}
	*/
	public static String getNameSimple(String nameFull) throws IllegalArgumentException {
		Check.arg().notBlank(nameFull);
		
		int index = nameFull.lastIndexOf('.');
		return (index > -1) ? nameFull.substring(index + 1) : nameFull;
	}
	
	// -------------------- getPackage --------------------
	
	/**
	* Returns <code>{@link #getPackage(Class) getPackage}( obj.getClass() )</code>.
	* <p>
	* @throws IllegalArgumentException if obj is null
	*/
	public static String getPackage(Object obj) throws IllegalArgumentException {
		Check.arg().notNull(obj);
		
		return getPackage( obj.getClass() );
	}
	
	/**
	* Returns <code>{@link #getPackage(String) getPackage}( clazz.getName() )</code>.
	* <p>
	* The result is usually identical to what {@link Class#getSimpleName clazz.getSimpleName()} returns.
	* The sole exception that it does not return an empty String for anonymous classes,
	* but instead returns whatever name the JVM uses for such classes.
	* <p>
	* @throws IllegalArgumentException if clazz is null
	*/
	public static String getPackage(Class clazz) throws IllegalArgumentException {
		Check.arg().notNull(clazz);
		
		return getPackage( clazz.getName() );
	}
	
	
	/**
	* Returns the package name of the fully quallified class name that is stored in nameFull.
	* In other words, the "simple" name at the end of nameFull is removed.
	* For example, if nameFull equals "java.lang.Object" then "java.lang" is returned.
	* If nameFull represents a class in the default (unnamed) package, then null is returned.
	* <p>
	* @throws IllegalArgumentException if nameFull is {@link Check#notBlank blank}
	*/
	public static String getPackage(String nameFull) throws IllegalArgumentException {
		Check.arg().notBlank(nameFull);
		
		int index = nameFull.lastIndexOf('.');
		return (index > -1) ? nameFull.substring(0, index) : null;
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private ClassUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_findClassFile_getBytes() throws Exception {			
			File fileClassOrig = findClassFile(ClassUtil.class);
			byte[] bytesOrig = StreamUtil.drain( new FileInputStream(fileClassOrig) );
			byte[] bytesLoaded = getBytes(ClassUtil.class);
			Assert.assertArrayEquals( bytesOrig, bytesLoaded );
		}
		
		@Test public void test_getNameSimple() {
			Assert.assertEquals( "Object", getNameSimple( new Object() ) );
			Assert.assertEquals( "Object", getNameSimple( Object.class ) );
			Assert.assertEquals( "Object", getNameSimple("java.lang.Object") );
		}
		
		@Test public void test_getPackage() {
			Assert.assertEquals( "java.lang", getPackage( new Object() ) );
			Assert.assertEquals( "java.lang", getPackage( Object.class ) );
			Assert.assertEquals( "java.lang", getPackage("java.lang.Object") );
			Assert.assertEquals( null, getPackage("ClassInDefaultPackage") );
		}
		
	}
	
}
