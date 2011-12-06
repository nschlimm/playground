/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ this class is now obsolete:
	http://www.jscience.org/
	http://www.jcp.org/en/jsr/detail?id=275
*/


package bb.science;


import bb.util.Check;
import org.junit.Assert;
import org.junit.Test;


/**
* This class models the state corresponding to a prefix for a scientific unit of measurement.
* In particular, it has name, symbol, (power of 10) exponent, and scaleFactor properties.
* <p>
* This class also offers static constants which are Prefix instances that model the standard SI unit prefixes.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see <a href="http://physics.nist.gov/cuu/Units/prefixes.html">SI prefixes</a>
* @see <a href="http://www.ex.ac.uk/cimt/dictunit/dictunit.htm#prefixes">The Prefixes of the SI</a>
* @see <a href="http://www.encyclopedia.com/html/m1/metricT1A1B1L1E1.asp">Prefixes for Basic Metric Units</a>
* @see Unit
* @author Brent Boyer
*/
public class Prefix {


	// -------------------- constants: SI standard prefixes --------------------


	/** Models the SI prefix for 10<sup>24</sup>, the yotta. */
	public static final Prefix yotta = new Prefix("yotta", "Y", 24);

	/** Models the SI prefix for 10<sup>21</sup>, the zetta. */
	public static final Prefix zetta = new Prefix("zetta", "Z", 21);

	/** Models the SI prefix for 10<sup>18</sup>, the exa. */
	public static final Prefix exa = new Prefix("exa", "E", 18);

	/** Models the SI prefix for 10<sup>15</sup>, the peta. */
	public static final Prefix peta = new Prefix("peta", "P", 15);

	/** Models the SI prefix for 10<sup>12</sup>, the tera. */
	public static final Prefix tera = new Prefix("tera", "T", 12);

	/** Models the SI prefix for 10<sup>9</sup>, the giga. */
	public static final Prefix giga = new Prefix("giga", "G", 9);

	/** Models the SI prefix for 10<sup>6</sup>, the mega. */
	public static final Prefix mega = new Prefix("mega", "M", 6);

	/** Models the SI prefix for 10<sup>3</sup>, the kilo. */
	public static final Prefix kilo = new Prefix("kilo", "k", 3);

	/** Models the SI prefix for 10<sup>2</sup>, the hecto. */
	public static final Prefix hecto = new Prefix("hecto", "h", 2);

	/** Models the SI prefix for 10<sup>1</sup>, the deka. */
	public static final Prefix deka = new Prefix("deka", "da", 1);

	/** Models the "null" prefix (i.e. when there is no visible prefix, that is, for 10<sup>0</sup> = 1). */
	public static final Prefix nullPrefix = new Prefix();

	/** Models the SI prefix for 10<sup>-1</sup>, the deci. */
	public static final Prefix deci = new Prefix("deci", "d", -1);

	/** Models the SI prefix for 10<sup>-2</sup>, the centi. */
	public static final Prefix centi = new Prefix("centi", "c", -2);

	/** Models the SI prefix for 10<sup>-3</sup>, the milli. */
	public static final Prefix milli = new Prefix("milli", "m", -3);

	/** Models the SI prefix for 10<sup>-6</sup>, the micro. */
//	public static final Prefix micro = new Prefix("micro", "µ", -6);
// +++ it would be nice if could use µ instead of u here, but so many platforms (e.g. dos) will not handle correctly...
public static final Prefix micro = new Prefix("micro", "u", -6);

	/** Models the SI prefix for 10<sup>-9</sup>, the nano. */
	public static final Prefix nano = new Prefix("nano", "n", -9);

	/** Models the SI prefix for 10<sup>-12</sup>, the pico. */
	public static final Prefix pico = new Prefix("pico", "p", -12);

	/** Models the SI prefix for 10<sup>-15</sup>, the femto. */
	public static final Prefix femto = new Prefix("femto", "f", -15);

	/** Models the SI prefix for 10<sup>-18</sup>, the atto. */
	public static final Prefix atto = new Prefix("atto", "a", -18);

	/** Models the SI prefix for 10<sup>-21</sup>, the zepto. */
	public static final Prefix zepto = new Prefix("zepto", "z", -21);

	/** Models the SI prefix for 10<sup>-24</sup>, the yocto. */
	public static final Prefix yocto = new Prefix("yocto", "y", -24);


	// -------------------- instance fields --------------------


	/** The full name of the prefix (e.g. milli, kilo, mega etc). */
	private final String name;

	/** The symbol that identifies this prefix (e.g. m, k, M etc). */
	private final String symbol;

	/** The exponent for the power of 10 that is associated with this prefix (e.g. -3, 3, 6 etc). */
	private final int exponent;

	/**
	* Convenience field that equals 10<sup>exponent</sup>.
	* <p>
	* <b>Warning: this value may have roundoff error</b>, so use at your own discretion.
	* This always occurs whenever exponent is negative
	* (because the base 2 system used by computers can never perfectly represent negative powers of 10).
	* It will also occur if exponent is a sufficiently large positive value
	* (because the limited precision of floating point numbers becomes insufficient to perfectly represent really large integers);
	* with the 64-bit IEEE floating point implementation used by Java's double type,
	* which uses 52 bits in the fractional part (appended after an implicit leading 1);
	* this will happen for integers greater than 2^53 - 1 = 9,007,199,254,740,991 (i.e. powers of 10 greater than 15).
	* <p>
	* @see <a href="http://www.psc.edu/general/software/packages/ieee/ieee.html">The IEEE standard for floating point arithmetic</a>
	*/
	private final double scaleFactor;


	// -------------------- static methods --------------------


	/** Returns an array of all the standard SI prefixes in order from {@link #yotta} down to {@link #yocto}. */
	public static Prefix[] getSiPrefixes() {
		return new Prefix[] {
			yotta, zetta, exa, peta, tera, giga, mega, kilo, hecto, deka,
			nullPrefix,
			deci, centi, milli, micro, nano, pico, femto, atto, zepto, yocto
		};
	}


	/**
	* This method attempts to return a Prefix instance which will "best scale" the supplied quantity.
	* Such a Prefix has the property that when its scaleFactor divides the quantity arg,
	* it produces a number in the range [1, 1000).
	* For example, the best Prefix for 2,000 would be kilo, and the best Prefix for 0.00001 would be micro.
	* <p>
	* The Prefixes returned are only the "standard" ones -- that is, those whose exponents are multiples of 3
	* (e.g. n = 10<sup>-6</sup>, m = 10<sup>-3</sup>, k = 10<sup>3</sup>, G = 10<sup>9</sup>, etc).
	* Other prefixes (e.g. c = 10<sup>-2</sup>) are never returned.
	* <p>
	* If the quantity arg is either too large or small to be scaled using a standard SI prefix,
	* or has some other problem (e.g. is NaN or infinite),
	* then this method simply returns {@link #nullPrefix}.
	* <p>
	* @param quantity the number that is to be scaled by a Prefix
	*/
	public static Prefix getScalePrefix(double quantity) {
		switch ( Math2.orderOfMagnitude(quantity) ) {
			case 26:
			case 25:
			case 24:
				return yotta;
			case 23:
			case 22:
			case 21:
				return zetta;
			case 20:
			case 19:
			case 18:
				return exa;
			case 17:
			case 16:
			case 15:
				return peta;
			case 14:
			case 13:
			case 12:
				return tera;
			case 11:
			case 10:
			case 9:
				return giga;
			case 8:
			case 7:
			case 6:
				return mega;
			case 5:
			case 4:
			case 3:
				return kilo;
			case 2:
			case 1:
			case 0:
				return nullPrefix;
			case -1:
			case -2:
			case -3:
				return milli;
			case -4:
			case -5:
			case -6:
				return micro;
			case -7:
			case -8:
			case -9:
				return nano;
			case -10:
			case -11:
			case -12:
				return pico;
			case -13:
			case -14:
			case -15:
				return femto;
			case -16:
			case -17:
			case -18:
				return atto;
			case -19:
			case -20:
			case -21:
				return zepto;
			case -22:
			case -23:
			case -24:
				return yocto;
			default:
				return nullPrefix;
		}
	}


	// -------------------- constructors --------------------


	/**
	* This special protected constructor is used solely to make a "null" Prefix instance.
	* <p>
	* @see #nullPrefix
	*/
	protected Prefix() {
		this.name = "<null prefix>";
		this.symbol = "";
		this.exponent = 0;
		scaleFactor = 1.0;
	}


	/**
	* Constructs a new Prefix instance for the supplied name, symbol, and exponent.
	* <p>
	* @throws IllegalArgumentException if name or symbol are either null or all whitespace
	*/
	public Prefix(String name, String symbol, int exponent) throws IllegalArgumentException {
		Check.arg().notBlank(name);
		Check.arg().notBlank(symbol);
// +++ any tests on exponent?  like if is really really large?

		this.name = name;
		this.symbol = symbol;
		this.exponent = exponent;

		scaleFactor = Math2.power10(exponent);
	}


	// -------------------- accessors --------------------


	/** Returns the full name of the prefix (e.g. milli, kilo, mega etc). */
	public String getName() { return name; }

	/** Returns the symbol that identifies this prefix (e.g. m, k, M etc). */
	public String getSymbol() { return symbol; }

	/** Returns the exponent for the power of 10 that is associated with this prefix (e.g. -3, 3, 6 etc). */
	public int getExponent() { return exponent; }

	/** Convenience method that returns the value of the {@link #scaleFactor} field. */
	public double getScaleFactor() { return scaleFactor; }


	// -------------------- toString --------------------


	@Override public String toString() { return getName() + " (" + getSymbol() + "): 10^" + getExponent() + " ~= " + getScaleFactor(); }


	// -------------------- UnitTest (static inner class) --------------------


	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_toString() {
			for (Prefix prefix : getSiPrefixes()) {
				System.out.println( prefix.toString() );
			}
		}
		
		@Test public void test_getScalePrefix() {
			String[][] scenarios = new String[][] {
				{"8.324694030187705E-25", "<null prefix> (): 10^0 ~= 1.0"},
				{"7.712108442510397E-24", "yocto (y): 10^-24 ~= 1.0E-24"},
				{"5.208102662152832E-23", "yocto (y): 10^-24 ~= 1.0E-24"},
				{"6.110882342826844E-22", "yocto (y): 10^-24 ~= 1.0E-24"},
				{"5.733438788436381E-21", "zepto (z): 10^-21 ~= 1.0E-21"},
				{"2.2838109784165325E-20", "zepto (z): 10^-21 ~= 1.0E-21"},
				{"9.572630159975141E-19", "zepto (z): 10^-21 ~= 1.0E-21"},
				{"1.9624632250751015E-18", "atto (a): 10^-18 ~= 1.0E-18"},
				{"6.393408037203187E-17", "atto (a): 10^-18 ~= 1.0E-18"},
				{"5.306135845104571E-16", "atto (a): 10^-18 ~= 1.0E-18"},
				{"4.526414064949297E-15", "femto (f): 10^-15 ~= 1.0E-15"},
				{"9.703241917526118E-14", "femto (f): 10^-15 ~= 1.0E-15"},
				{"4.357809096047562E-13", "femto (f): 10^-15 ~= 1.0E-15"},
				{"9.874590373222371E-12", "pico (p): 10^-12 ~= 1.0E-12"},
				{"6.8579632129077E-11", "pico (p): 10^-12 ~= 1.0E-12"},
				{"4.979128031588321E-10", "pico (p): 10^-12 ~= 1.0E-12"},
				{"2.7636551325241573E-9", "nano (n): 10^-9 ~= 1.0E-9"},
				{"9.166917403100136E-8", "nano (n): 10^-9 ~= 1.0E-9"},
				{"3.2388005211699345E-7", "nano (n): 10^-9 ~= 1.0E-9"},
				{"6.752766922195284E-6", "micro (u): 10^-6 ~= 1.0E-6"},
				{"6.668350368485299E-5", "micro (u): 10^-6 ~= 1.0E-6"},
				{"1.132360632501786E-4", "micro (u): 10^-6 ~= 1.0E-6"},
				{"0.0018035725236191062", "milli (m): 10^-3 ~= 0.0010"},
				{"0.06481922969460731", "milli (m): 10^-3 ~= 0.0010"},
				{"0.6126993298611677", "milli (m): 10^-3 ~= 0.0010"},
				{"3.689019452519547", "<null prefix> (): 10^0 ~= 1.0"},
				{"77.84976216022754", "<null prefix> (): 10^0 ~= 1.0"},
				{"338.82408780980444", "<null prefix> (): 10^0 ~= 1.0"},
				{"9129.076453463327", "kilo (k): 10^3 ~= 1000.0"},
				{"62191.14926361363", "kilo (k): 10^3 ~= 1000.0"},
				{"908830.0773357926", "kilo (k): 10^3 ~= 1000.0"},
				{"5798105.839697947", "mega (M): 10^6 ~= 1000000.0"},
				{"7.678309138071488E7", "mega (M): 10^6 ~= 1000000.0"},
				{"1.481098135748597E8", "mega (M): 10^6 ~= 1000000.0"},
				{"9.746043730395565E9", "giga (G): 10^9 ~= 1.0E9"},
				{"3.882048998002922E10", "giga (G): 10^9 ~= 1.0E9"},
				{"4.9955696600852014E11", "giga (G): 10^9 ~= 1.0E9"},
				{"1.6678769919421553E12", "tera (T): 10^12 ~= 1.0E12"},
				{"4.7799756141825555E13", "tera (T): 10^12 ~= 1.0E12"},
				{"6.245120910341465E14", "tera (T): 10^12 ~= 1.0E12"},
				{"6.552540697461903E15", "peta (P): 10^15 ~= 1.0E15"},
				{"8.232041654708744E16", "peta (P): 10^15 ~= 1.0E15"},
				{"8.116192709447113E17", "peta (P): 10^15 ~= 1.0E15"},
				{"9.727884218714399E18", "exa (E): 10^18 ~= 1.0E18"},
				{"1.4023665426807192E19", "exa (E): 10^18 ~= 1.0E18"},
				{"7.27182307153596E20", "exa (E): 10^18 ~= 1.0E18"},
				{"6.848106981048721E21", "zetta (Z): 10^21 ~= 1.0E21"},
				{"3.466148143928037E22", "zetta (Z): 10^21 ~= 1.0E21"},
				{"1.0637068730642214E23", "zetta (Z): 10^21 ~= 1.0E21"},
				{"2.725612709937673E24", "yotta (Y): 10^24 ~= 1.0E24"},
				{"1.3229952879654654E25", "yotta (Y): 10^24 ~= 1.0E24"},
				{"2.727303555648123E26", "yotta (Y): 10^24 ~= 1.0E24"},
				{"8.035858761523489E27", "<null prefix> (): 10^0 ~= 1.0"}
			};
			
			for (String[] pair : scenarios) {
				double number = Double.parseDouble(pair[0]);
				String resultExpected = pair[1];
				Assert.assertEquals( resultExpected, getScalePrefix(number).toString() );
			}
		}
		
	}
	
	
}
