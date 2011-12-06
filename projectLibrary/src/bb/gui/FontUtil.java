/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.gui;

import bb.science.Math2;
import bb.util.Benchmark;
import bb.util.Check;
import bb.util.Execute;
import bb.util.StringUtil;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
* This class provides miscellaneous static Font utility methods.
* <p>
* This class is multithread safe.
* All non-multithread safe fields are only accessed inside synchronized methods.
* <p>
* @author Brent Boyer
*/
public class FontUtil {
	
	// -------------------- constants --------------------
	
	/** The default Font point size. */
	private static final float fontSize_default = 12.0f;
	
	// -------------------- static fields --------------------
	
	/**
	* Records all the Fonts available on this local system.
	* Is initialized/reset by {@link #resetFonts resetFonts}.
	* <p>
	* Contract: once initialized, has a length >= 20, and each Font's size will be {@link #fontSize_default fontSize_default}.
+++ need a sort guarantee
	*/
	private static Font[] fonts = null;
	
	// -------------------- static accessors & mutators --------------------
	
	/**
	* Determines the names of all the Font Families that are available on this system.
	* <p>
	* Contract: the result has a size >= 5, because the JVM guarantees the presence of these logical fonts: Dialog, DialogInput, Monospaced, Serif, and SansSerif.
	* It is sorted by the {@link String#compareTo natural String ordering}.
	*/
	public static SortedSet<String> getFontFamilyNames() throws IllegalStateException {
		String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		return new TreeSet<String>( Arrays.asList(names) );
	}
	
	/**
	* Returns all the Fonts available on this system.
	* <p>
	* <i>The result reflects the state of this system as of the last call to {@link #resetFonts resetFonts}.</i>
	* If resetFonts has never been called, then this method will automaticly call it.
	* So, resetFonts never needs to be explicitly called, <i>unless Fonts are added/removed</i>.
	* <p>
	* Contract: the result will have a length >= 20,
	* and each Font's size will be {@link #fontSize_default fontSize_default}; see {@link #resetFonts resetFonts}.
	* <p>
	* @throws IllegalStateException if some problem in determining the Fonts is encountered
	*/
	public static synchronized Font[] getFonts() throws IllegalStateException {
		if (fonts == null) resetFonts();
		return fonts.clone();
	}
	
	/**
	* Determines all the Fonts which are currently available on the running system and stores them in an internal field.
	* <p>
	* @throws IllegalStateException if some problem in determining the Fonts is encountered
	*/
	public static synchronized void resetFonts() throws IllegalStateException {
		fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
		if (fonts.length < 20) throw new IllegalStateException("GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts().length = " + fonts.length + " which is < 20");	// Note: fonts.length should be at least 20, because getAllFonts should always return, at a minimum, Java's 5 logical Fonts (i.e. Serif, SansSerif, Monospaced, Dialog, DialogInput) as well as the 4 style variants (i.e. plain, bold, italic, bolditalic) of each of those Fonts.
		for (int i = 0; i < fonts.length; i++) {
			fonts[i] = fonts[i].deriveFont(fontSize_default);	// Note: getAllFonts always returns Fonts with a size of 1 point, which is almost invisible on most systems, so must resize them
		}
	}
	
	// -------------------- canHandle, requiresGlyph --------------------
	
	/**
	* Determines whether or not the {@link Component#getFont component's Font}
	* is capable of displaying all of the chars of s that {@link #requiresGlyph require a glyph}.
	* <p>
	* This method is needed because the {@link Font#canDisplayUpTo Font.canDisplayUpTo} method
	* does not exclude chars that are highly unlikely to have a glyph in any Font.
	* <p>
	* To see why this method is needed,
	* suppose you want to determine if a JTextArea, say, is currently set to a Font
	* that is capable of displaying some text that contains line returns.
	* Here, you do not want the line returns to count in the Font determination because they will require no glyphs.
	* <p>
	* @throws IllegalArgumentException if component == null; s == null
	*/
	public static boolean canHandle(Component component, String s) throws IllegalArgumentException {
		Check.arg().notNull(component);
		Check.arg().notNull(s);
		
		Font font = component.getFont();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (requiresGlyph(c) && !font.canDisplay(c)) return false;
		}
		return true;
	}
	
	/**
	* Determines whether or not c requires a glyph from a Font or not.
	* This method assumes that all chars which are not {@link Character#isWhitespace whitespace characters} or
	* {@link Character#isISOControl control characters} require glyphs.
	*/
	public static boolean requiresGlyph(char c) {
		return
			!Character.isWhitespace(c) &&	// test for whitespace first, as is more common so can benefit from short-circuit evaluation
			!Character.isISOControl(c);
	}
	
	// -------------------- findBestFontFamily, findBestFont, rankFonts --------------------
	
	/**
	* Returns the first element of namesDesired which is actually available on this system.
	* <p>
	* @throws IllegalArgumentException if every element of namesDesired is unavailable on this machine;
	* in order to avoid this, it is highly recommended that the user supply one of the 5 logical font family names that the JVM guarantees to be present
	* (e.g. Serif, SansSerif, Monospaced, Dialog, or DialogInput)
	*/
	public static String findBestFontFamily(String... namesDesired) throws IllegalArgumentException {
		SortedSet<String> namesPresent = getFontFamilyNames();
		for (String name : namesDesired) {
			if (namesPresent.contains(name)) return name;
		}
		throw new IllegalArgumentException("every element of namesDesired is unavailable on this machine");
	}
	
	/**
	* Determines that Font in the local system which can display the highest percentage of s's chars.
	* In the event of ties (e.g. several Fonts can display 100% of s's chars),
	* it returns that Font which was encountered first.
	* <p>
	* Contract: the result is never null, and will have a Font size of {@link #fontSize_default}.
	* <p>
	* @throws IllegalArgumentException if s == null
	* @throws IllegalStateException if GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts().length < 20
	*/
	public static Font findBestFont(String s) throws IllegalArgumentException, IllegalStateException {
		//return rankFonts(s)[0].getFont();	// elegant, but lacks the early return possibility of the code below which can be an enormous speed improvement (see benchmark results in UnitTest)
		
		Font bestFont = null;
		double bestRank = -1.0;
		for (Font font : getFonts()) {
			double rank = findDisplayableCharPercent(font, s);
			if (rank == 1.0) return font;	// can immediately return if find a font that can display 100% of s's chars
			if (rank > bestRank) {
				bestFont = font;
				bestRank = rank;
			}
		}
		if (bestFont == null) throw new IllegalStateException("failed to find a best Font; this should never happen");
		return bestFont;
	}
	
	/**
	* Retrieves every Font in the local system by calling {@link #getFonts getFonts}.
	* Determines the {@link FontRank} for each
	* using {@link FontUtil.FontRank#FontUtil.FontRank(Font, String) this constructor}.
	* Returns these FontRanks as an array, sorted by FontRank's {@link FontRank#compareTo natural ordering}.
	* <p>
	* Contract: see getFonts for guarantees on the Fonts in the result.
	* <p>
	* @throws IllegalArgumentException if s == null
	* @throws IllegalStateException if GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts().length < 20
	*/
	public static FontRank[] rankFonts(String s) throws IllegalArgumentException, IllegalStateException {
		Check.arg().notNull(s);
		
		SortedSet<FontRank> fontRanks = new TreeSet<FontRank>();
		for (Font font : getFonts()) {
			fontRanks.add( new FontRank(font, s) );
		}
		return fontRanks.toArray( new FontRank[fontRanks.size()] );
	}
	
	// -------------------- findDisplayableCharPercent --------------------
	
	/**
	* Returns the percent of chars in the supplied String that can be displayed by the supplied Font.
	* <p>
	* @throws IllegalArgumentException if font == null; s == null or s.length() == 0
	*/
	public static double findDisplayableCharPercent(Font font, String s) throws IllegalArgumentException {
		Check.arg().notNull(font);
		Check.arg().notNull(s);
		if (s.length() == 0) throw new IllegalArgumentException("s.length() == 0");
		
		int displayCount = 0;
		for (int i = 0; i < s.length(); i++) {
			if (font.canDisplay( s.charAt(i) )) ++displayCount;
		}
		
		return displayCount / ((double) s.length());
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private FontUtil() {}
	
	// -------------------- FontRank (static inner class) --------------------
	
	/**
	* The main purpose of this class is simply to record a Font and some associated rank of that Font.
	* The semantic meaning of that rank is determined by the user.
	* <p>
	* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
<!--
+++ umm, is that deep state comment really true for Font?  Its javadocs never state this...
On the other hand, it should be effectively immutable if the user never explicitly does anything to mutate it.
-->
	*/
	public static class FontRank implements Comparable<FontRank> {
		
		private final Font font;
		private final double rank;
		
		/**
		* Simply calls <code>this( font, {@link #findDisplayableCharPercent findDisplayableCharPercent}(font, s) )</code>.
		* In other words, it ranks font based on what percent of characters it can display.
		*/
		public FontRank(Font font, String s) {
			this( font, findDisplayableCharPercent(font, s) );
		}
		
		/**
		* Constructs a new FontRank instance from the supplied arguments.
		* The font arg may be any Font value except null; the rank arg may be any double value (including NaN and infinity).
		* <p>
		* @throws IllegalArgumentException if font is null
		*/
		public FontRank(Font font, double rank) throws IllegalArgumentException {
			Check.arg().notNull(font);
			
			this.font = font;
			this.rank = rank;
		}
		
		public Font getFont() { return font; }
		
		public double getRank() { return rank; }
		
		/**
		* Only returns true if obj is another FontRank instance whose font and rank fields equal this.
		* <p>
		* @throws IllegalStateException if obj is another FontRank instance whose font field equals this, but whose rank field differs; this is meant to detect subtle bugs
		*/
		@Override public final boolean equals(Object obj) throws IllegalStateException {	// for why is final, see the essay stored in the file equalsImplementation.txt
			if (this == obj) return true;
			if (!(obj instanceof FontRank)) return false;
			
			FontRank other = (FontRank) obj;
			if (this.font.equals(other.font)) {
				if (this.rank != other.rank) throw new IllegalStateException("this.font.equals(other.font) = true, but this.rank != other.rank");
				return true;
			}
			else {
				return false;
			}
		}
		
		@Override public final int hashCode() {	// for why is final, see the essay stored in the file equalsImplementation.txt
			return font.hashCode();	// just font suffices
		}
		
		@Override public String toString() { return "font = " + getFont().toString() + ", rank = " + getRank(); }
		
		/**
		* Tries to order this and other by their ranks:
		* returns <code>Double.compare( other.rank, this.rank )</code> if that result is != 0.
		* <p>
		* Else tries to order this and other by their names:
		* returns <code>this.font.getName().compareTo( other.font.getName() )</code> if that result is != 0.
		* <p>
		* Else tries to order this and other by their sizes:
		* returns <code>{@link Math2#compare Math2.compare}( this.font.getSize(), other.font.getSize() )</code> if that result is != 0.
		* <p>
		* Else tries to order this and other by their styles:
		* returns <code>{@link Math2#compare Math2.compare}( this.font.getStyle() - other.font.getStyle() )</code> if that result is != 0.
		* <p>
		* Else returns 0 if <code>this.{@link #equals equals}(other)</code> is true.
		* This is the only circumstance in which 0 will ever be returned, thus,
		* <i>this ordering is consistent with equals</i> (see {@link Comparable} for more discussion).
		* <p>
		* Else throws an IllegalStateException.
		* <p>
		* @throws IllegalArgumentException if other is null
		* @throws IllegalStateException if run out of criteria to order this and other
		*/
		public int compareTo(FontRank other) throws IllegalArgumentException, IllegalStateException {	// see the file compareImplementation.txt for more discussion
			Check.arg().notNull(other);
			
			int rankComparison = Double.compare( other.rank, this.rank );	// CRITICAL: note the argument order inside Double.compare; also, use Double.compare because of how it handle special values
// +++ shouldd I use my Math2.compare method instead?  Note: it bombs on NaN values...
			if (rankComparison != 0) return rankComparison;
			
			int nameComparison = this.font.getName().compareTo( other.font.getName() );
			if (nameComparison != 0) return nameComparison;
			
			int sizeComparison = Math2.compare( this.font.getSize(), other.font.getSize() );
			if (sizeComparison != 0) return sizeComparison;
			
			int styleComparison = Math2.compare( this.font.getStyle(), other.font.getStyle() );
			if (styleComparison != 0) return styleComparison;
			
			// if look inside Font.equals, there are some other attributes that could possibly test, but will give up for now...
			
			//int identityHashComparison = Math2.compare( System.identityHashCode(this), System.identityHashCode(other) );
			//if (identityHashComparison != 0) return identityHashComparison;
			
			if (this.equals(other)) return 0;
			
			throw new IllegalStateException("ran out of criteria to order this = " + this + " and other = " + other);
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		/** The first letters of the English alphabet. */
		//private static final String englishLettersFirst = "abcd";
		
		/**
		* The unique letters of the German alphabet (relative to English).
		* <p>
		* @see <a href="http://german.about.com/library/anfang/blanfang_abc.htm">German alphabet</a>
		* @see <a href="http://www.unicode.org/charts/PDF/U0080.pdf">The relevant Unicode code chart</a>
		*/
		//private static final String germanLettersUnique = "\u00c4\u00d6\u00df\u00dc";
		
		/**
		* The first letters of the Chinese-Japanese-Korean (unified ideographs) alphabet.
		* <p>
		* @see <a href="http://www.unicode.org/charts/PDF/U4E00.pdf">The relevant Unicode code chart</a>
		*/
		//private static final String cjkLettersFirst = "\u4e00\u4e01\u4e02\u4e03";
		
		/** Concatentation of {@link #englishLettersFirst}, {@link #germanLettersUnique}, {@link #cjkLettersFirst}. */
		//private static final String charsFromManyLanguages = englishLettersFirst + germanLettersUnique + cjkLettersFirst;
		
		/** Contains all possible char values, in sequence, from {@link Character#MIN_VALUE} to {@link Character#MAX_VALUE}. */
		private static final String charsAll;
		static {
			StringBuilder sb = new StringBuilder();
			for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) sb.append( (char) c );	// CRITICAL: c must be an int, not a char, for the c++ in the loop not to cause a problem at MAX_VALUE
			charsAll = sb.toString();
		}
		
		/**
		* Tests the parent class.
		* <p>
		* If this method is this Java process's entry point (i.e. first <code>main</code> method),
		* then its final action is a call to {@link System#exit System.exit}, which means that <i>this method never returns</i>;
		* its exit code is 0 if it executes normally, 1 if it throws a Throwable (which will be caught and logged).
		* Otherwise, this method returns and leaves the JVM running.
		*/
		public static void main(final String[] args) {
			Execute.thenExitIfEntryPoint( new Callable<Void>() { public Void call() throws Exception {
				Check.arg().empty(args);
				
				test_getFontFamilyNames();
				test_getFonts();
// +++ also need to test canHandle, requiresGlyph
				test_findDisplayableCharPercent();
				test_findBestFontFamily();
				test_findBestFont();
//				benchmark_findBestFont();
				test_rankFonts();
//				benchmark_rankFonts();
				
				return null;
			} } );
		}
		
		/**
		* Results on 2009-08-21 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_15 server jvm):
		* <pre><code>

		* </code></pre>
		*/
		private static void test_getFontFamilyNames() {
			System.out.println();
			System.out.println("test_getFontFamilyNames:");
			
			System.out.println( StringUtil.toString( getFontFamilyNames(), ", " ) );
		}
		
		/**
		* Results on 2009-08-21 (2.5 GHz Xeon E5420 desktop, jdk 1.6.0_15 server jvm):
		* <pre><code>

		* </code></pre>
		*/
		private static void test_getFonts() {
			System.out.println();
			System.out.println("test_getFonts:");
			
			System.out.println( StringUtil.toString( getFonts(), ", " ) );
		}
// +++ what really should do is display in a JTextPane or something a list of all the font names using the font itself!
		
		/**
		* Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			findDisplayableCharPercent(aLogicalFont, charsAll) = 0.565673828125
		* </code></pre>
		*/
		private static void test_findDisplayableCharPercent() {
			System.out.println();
			System.out.println("test_findDisplayableCharPercent:");
			
			Font aLogicalFont = new Font("SansSerif", Font.PLAIN, 12);
			System.out.println("findDisplayableCharPercent(aLogicalFont, charsAll) = " + findDisplayableCharPercent(aLogicalFont, charsAll) );
		}
		
		/**
		* Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			findBestFontFamily("Courier New", "Courier", "Terminal", "FixedSys", "Andale Mono", "Lucida Console", "Monaco", "Monospaced"): Courier New
		* </code></pre>
		*/
		private static void test_findBestFontFamily() throws Exception {
			System.out.println();
			System.out.println("test_findBestFontFamily:");
			
			System.out.println( "findBestFontFamily(\"Courier New\", \"Courier\", \"Terminal\", \"FixedSys\", \"Andale Mono\", \"Lucida Console\", \"Monaco\", \"Monospaced\"): " + findBestFontFamily("Courier New", "Courier", "Terminal", "FixedSys", "Andale Mono", "Lucida Console", "Monaco", "Monospaced") );
		}
		
		/**
		* Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			findBestFont(charsAll): java.awt.Font[family=Arial Unicode MS,name=Arial Unicode MS,style=plain,size=12]
		* </code></pre>
		*/
		private static void test_findBestFont() throws Exception {
			System.out.println();
			System.out.println("test_findBestFont:");
			
			System.out.println( "findBestFont(charsAll): " + findBestFont(charsAll) );
		}
		
		/**
		* Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			findBestFont: first = 2.089 s, mean = 2.083 s (CI deltas: -568.671 us, +587.207 us), sd = 2.294 ms (CI deltas: -335.538 us, +446.633 us) WARNING: execution times have mild outliers, SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		private static void benchmark_findBestFont() throws Exception {
			System.out.println();
			System.out.println("benchmark_findBestFont:");
			
			Runnable task = new Runnable() { public void run() { findBestFont(charsAll); } };
			System.out.println("findBestFont: " + new Benchmark(task));
		}
		
		/**
		* (Abbreviated) Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			rankFonts(charsFromManyLanguages):
				font = java.awt.Font[family=Arial Unicode MS,name=Arial Unicode MS,style=plain,size=12], rank = 0.5940704345703125
				font = java.awt.Font[family=Dialog,name=Dialog.bold,style=plain,size=12], rank = 0.565673828125
				font = java.awt.Font[family=Dialog,name=Dialog.plain,style=plain,size=12], rank = 0.565673828125
				font = java.awt.Font[family=SansSerif,name=SansSerif.bold,style=plain,size=12], rank = 0.565673828125
				...
				font = java.awt.Font[family=SimSun-PUA,name=SimSun-PUA,style=plain,size=12], rank = 0.0017547607421875
				font = java.awt.Font[family=MT Extra,name=MT Extra,style=plain,size=12], rank = 9.765625E-4
				font = java.awt.Font[family=Marlett,name=Marlett,style=plain,size=12], rank = 8.392333984375E-4
				font = java.awt.Font[family=MS Outlook,name=MS Outlook,style=plain,size=12], rank = 5.035400390625E-4
		* </code></pre>
		*/
		private static void test_rankFonts() throws Exception {
			System.out.println();
			System.out.println("test_rankFonts:");
			
			System.out.println("rankFonts(charsAll):");
			for (FontRank fontRank : rankFonts(charsAll)) {
				System.out.println("\t" + fontRank);
			}
			
// +++ maybe should eliminate the bold/italic/bolditalic variants to reduce the results?
//	--and maybe could print a warning if they differ in coverage from the base font...
		}
		
		/**
		* Results on 2009-02-16 on my 2.0 GHz Core 2 Duo laptop (server jvm):
		* <pre><code>
			rankFonts: first = 2.091 s, mean = 2.082 s (CI deltas: -969.569 us, +3.074 ms), sd = 6.229 ms (CI deltas: -4.050 ms, +7.450 ms) WARNING: EXECUTION TIMES HAVE EXTREME OUTLIERS, SD VALUES MAY BE INACCURATE
		* </code></pre>
		*/
		private static void benchmark_rankFonts() throws Exception {
			System.out.println();
			System.out.println("benchmark_rankFonts:");
			
			Runnable task = new Runnable() { public void run() { rankFonts(charsAll); } };
			System.out.println("rankFonts: " + new Benchmark(task));
		}
		
		/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
		private UnitTest() {}
		
	}
	
}
