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
may wish to rework the code below to use the above...
*/


package bb.science;


import bb.util.Check;
import org.junit.Test;


/**
* Models a unit of measurement.
* It has name, symbol, and description properties.
* <p>
* This class also offers static constants which are Unit instances that model the SI base units.
* (Exception: we take the gram (instead of the kilogram) to be our base unit of mass,
* as this works much better with the {@link Prefix} class.)
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @see <a href="http://physics.nist.gov/cuu/Units/units.html">SI base units</a>
* @author Brent Boyer
*/
public class Unit {


	// -------------------- constants: SI base units --------------------


	/** Models the SI base unit of length, the meter. */
	public static final Unit meter = new Unit("meter", "m", "SI base unit of length");

	/**
	* Models the SI unit of mass, the gram.
	* (We use this as our base unit of mass, and not the kg as the SI does, in order to work with our Prefix class.)
	*/
	public static final Unit gram = new Unit("gram", "g", "SI unit of mass");

	/** Models the SI base unit of time, the second. */
	public static final Unit second = new Unit("second", "s", "SI base unit of time");

	/** Models the SI base unit of electric current, the ampere. */
	public static final Unit ampere = new Unit("ampere", "s", "SI base unit of electric current");

	/** Models the SI base unit of thermodynamic temperature, the kelvin. */
	public static final Unit kelvin = new Unit("kelvin", "K", "SI base unit of thermodynamic temperature");

	/** Models the SI base unit of amount of substance, the mole. */
	public static final Unit mole = new Unit("mole", "mol", "SI base unit of amount of substance");

	/** Models the SI base unit of luminous intensity, the candela. */
	public static final Unit candela = new Unit("candela", "cd", "SI base unit of luminous intensity");


	// -------------------- instance fields --------------------


	/** The full name of the unit (e.g. meter, gram). */
	protected final String name;

	/** The symbol for the unit (e.g. m, g, etc). */
	protected final String symbol;

	/** A description of the underlying quantity associated with the unit (e.g. length, mass, etc). */
	protected final String description;


	// -------------------- static methods --------------------
	
	
	/** Returns an array of all the SI base units. */
	public static final Unit[] getSiBaseUnits() {
		return new Unit[] { meter, gram, second, ampere, kelvin, mole, candela };
	}
	
	
	// -------------------- constructor --------------------


	/**
	* Constructs a new Unit instance for the supplied name, symbol, and description.
	* <p>
	* @throws IllegalArgumentException if any arg is either null or all whitespace
	*/
	public Unit(String name, String symbol, String description) throws IllegalArgumentException {
		Check.arg().notBlank(name);
		Check.arg().notBlank(symbol);
		Check.arg().notBlank(description);

		this.name = name;
		this.symbol = symbol;
		this.description = description;
	}


	// -------------------- accessors --------------------


	/** Returns the full name of the unit (e.g. meter, gram). */
	public String getName() { return name; }

	/** Returns the symbol for the unit (e.g. m, g, etc). */
	public String getSymbol() { return symbol; }

	/** Returns a description of the underlying quantity associated with the unit (e.g. length, mass, etc). */
	public String getDescription() { return description; }


	// -------------------- toString --------------------


	@Override public String toString() { return getName() + " (" + getSymbol() + "): " + getDescription(); }


	// -------------------- UnitTest (static inner class) --------------------


	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_toString() {
			for (Unit unit : getSiBaseUnits()) {
				System.out.println( unit.toString() );
			}
		}
		
	}


}
