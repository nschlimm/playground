/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ are the toEngineeringXXX names too long?  Should I truncate to toEngXXX?

+++ add other toEngineeringXXX methods (e.g. frequency) as need arises?  or is it too much of a pain for all the different combinations?

--this class was written before JDK 1.5's formatted printing functionality
	--it is not exactly obsoleted by this because it offers methods like toEngineeringXXX that are absent from formatted printing
+++ should use the new Formatter class to add toScientific methods to this class; consider this test code:
	java.util.Formatter formatter = new java.util.Formatter();
	double num = 1;
	formatter.format("%1.3e%n", num);
	num = 0.1;
	formatter.format("%1.3e%n", num);
	num = 999;
	formatter.format("%1.3e%n", num);
	num = .000123456;
	formatter.format("%1.3e%n", num);
	num = 123456;
	formatter.format("%1.3e%n", num);
	d.p.s( formatter.toString() );
It outputs:
	1.000e+00
	1.000e-01
	9.990e+02
	1.235e-04
	1.235e+05
as expected.
*/

package bb.science;

import bb.util.Check;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.Assert;
import org.junit.Test;

/**
* Provides static utility methods to help in formatting/scaling of quantities.
* <p>
* This class is multithread safe: it is stateless.
* <p>
* @see Prefix
* @author Brent Boyer
*/
public final class FormatUtil {
	
	// -------------------- toEngineeringString --------------------
	
	/**
	* Returns <code>{@link #toEngineeringString(double, Unit, int) toEngineeringString}(quantity, unit, -1)</code>
	* (i.e. all digits retained).
	* <p>
	* @param quantity the number that is to be formatted
	* @param unit the relevant Unit instance
	* @throws IllegalArgumentException if unit == null
	*/
	public static String toEngineeringString(double quantity, Unit unit) throws IllegalArgumentException {
		return toEngineeringString(quantity, unit, -1);
	}
	
	/**
	* Returns <code>{@link #toEngineeringString(BigDecimal, Unit, int) toEngineeringString}(quantity, unit, -1)</code>
	* (i.e. all digits retained).
	* <p>
	* @param quantity the number that is to be formatted
	* @param unit the relevant Unit instance
	* @throws IllegalArgumentException if unit == null
	*/
	public static String toEngineeringString(BigDecimal quantity, Unit unit) throws IllegalArgumentException {
		return toEngineeringString(quantity, unit, -1);
	}
	
	/**
	* If quantity is NaN or infinite, then simply returns <code>quantity + " " + unit.getSymbol()</code>.
	* Else simply returns <code>{@link #toEngineeringString(BigDecimal, Unit, int) toEngineeringString}({@link BigDecimal#valueOf(double) BigDecimal.valueOf}(quantity), unit, numberDigitsFractional)</code>.
	* <p>
	* @param quantity the number that is to be formatted
	* @param unit the relevant Unit instance
	* @param numberDigitsFractional is either -1 (meaning retain all digits),
	* or else is some value >= 0 which specifies how many digits after the decimal point are to be retained
	* @throws IllegalArgumentException if unit == null; numberDigitsFractional < -1
	*/
	public static String toEngineeringString(double quantity, Unit unit, int numberDigitsFractional) throws IllegalArgumentException {
		if (Double.isNaN(quantity) || Double.isInfinite(quantity)) return quantity + " " + unit.getSymbol();	// must intercept these special cases because BigDecimal cannot represent them
		
		return toEngineeringString(BigDecimal.valueOf(quantity), unit, numberDigitsFractional);
	}
	
	/**
	* Returns quantity expressed in enginnering notation
	* with the exponent accounted for by the appropriate prefix of unit.
	* Here is what is returned:
	* <ol>
	*  <li>the numerical value of quantity, scaled by that power of 10 which puts it in the range [1, 1000)</li>
	*  <li>a space char</li>
	*  <li>the SI prefix symbol for the scaling factor (e.g. m for milli if the scaling factor was 10^-3)</li>
	*  <li>unit's symbol (e.g. s if unit is {@link Unit#second})</li>
	* </ol>
	* {@link Prefix#getScalePrefix Prefix.getScalePrefix} is used to determine the scaling factor.
	* <p>
	* @param quantity the number that is to be formatted
	* @param unit the relevant Unit instance
	* @param numberDigitsFractional is either -1 (meaning retain all digits),
	* or else is some value >= 0 which specifies how many digits after the decimal point are to be retained
	* @throws IllegalArgumentException if unit == null; numberDigitsFractional < -1
	*/
	public static String toEngineeringString(BigDecimal quantity, Unit unit, int numberDigitsFractional) throws IllegalArgumentException {
		Check.arg().notNull(unit);
		if (numberDigitsFractional < -1) throw new IllegalArgumentException("numberDigitsFractional = " + numberDigitsFractional + " < -1");
		
		Prefix prefix = Prefix.getScalePrefix(quantity.doubleValue());
		
			// scale quantity by prefix:
		quantity = quantity.scaleByPowerOfTen( -prefix.getExponent() );
		
			// retain only a certain number of the fractional digits:
		if (numberDigitsFractional != -1) {	// confirm this has been specified
			if (quantity.scale() >= 0) {	// confirm that scaling took place
				int numberDigitsIntegral = quantity.precision() - quantity.scale();
				int numberDigitsRetain = numberDigitsIntegral + numberDigitsFractional;
				quantity = quantity.round( new MathContext(numberDigitsRetain, RoundingMode.HALF_EVEN) );
			}
		}
		
		return quantity.toString() + " " + prefix.getSymbol() + unit.getSymbol();
	}
	
	// -------------------- toEngineeringTime --------------------
	
	/**
	* Convenience method that simply returns <code>{@link #toEngineeringTime(double, int) toEngineeringTime}(time, -1)</code>.
	* <p>
	* @param time the time that is to be formatted; <i>the units of this quantity must be in seconds</i>
	*/
	public static String toEngineeringTime(double time) {
		return toEngineeringTime(time, -1);
	}
	
	/**
	* Convenience method that simply returns <code>{@link #toEngineeringString(double, Unit, int) toEngineeringString}(time, Unit.second, numberDigitsFractional)</code>.
	* <p>
	* @param time the time that is to be formatted; <i>the units of this quantity must be in seconds</i>
	* @param numberDigitsFractional is either -1 (meaning retain all digits),
	* or else is some value >= 0 which specifies how many digits after the decimal point are to be retained
	* @throws IllegalArgumentException if numberDigitsFractional < -1
	*/
	public static String toEngineeringTime(double time, int numberDigitsFractional) throws IllegalArgumentException {
		return toEngineeringString(time, Unit.second, numberDigitsFractional);
	}
	
	// -------------------- constructor --------------------
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private FormatUtil() {}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		@Test public void test_toEngineeringString() {
			String[][] scenarios = new String[][] {
					// normal cases:
				{"3.681614468957996E-24", "3.681614468957996 ym"},
				{"9.496980860617553E-23", "94.96980860617553 ym"},
				{"4.196024695885741E-22", "419.6024695885741 ym"},
				{"6.0835901262750846E-21", "6.0835901262750846 zm"},
				{"9.65325849780029E-20", "96.5325849780029 zm"},
				{"7.318944200843247E-19", "731.8944200843247 zm"},
				{"1.786319549181626E-18", "1.786319549181626 am"},
				{"6.62168207687131E-17", "66.2168207687131 am"},
				{"6.442399096857892E-16", "644.2399096857892 am"},
				{"1.6631961306305176E-15", "1.6631961306305176 fm"},
				{"4.1805997476068685E-14", "41.805997476068685 fm"},
				{"2.157416569565775E-13", "215.7416569565775 fm"},
				{"5.077150926335776E-12", "5.077150926335776 pm"},
				{"2.838249519705646E-11", "28.38249519705646 pm"},
				{"5.596898258451314E-10", "559.6898258451314 pm"},
				{"5.771215952444123E-9", "5.771215952444123 nm"},
				{"9.577758136586389E-8", "95.77758136586389 nm"},
				{"6.941450454474366E-7", "694.1450454474366 nm"},
				{"4.681710281064776E-6", "4.681710281064776 um"},
				{"1.877619370460786E-5", "18.77619370460786 um"},
				{"6.041917189925801E-4", "604.1917189925801 um"},
				{"0.00918101955672535", "9.18101955672535 mm"},
				{"0.02184070855990232", "21.84070855990232 mm"},
				{"0.6579747453797774", "657.9747453797774 mm"},
				{"3.727471620736689", "3.727471620736689 m"},
				{"81.96143467784137", "81.96143467784137 m"},
				{"243.72622234488227", "243.72622234488227 m"},
				{"6356.609817093072", "6.356609817093072 km"},
				{"41663.23732685342", "41.66323732685342 km"},
				{"356936.13937249174", "356.93613937249174 km"},
				{"7309435.699228829", "7.309435699228829 Mm"},
				{"4.408850410886773E7", "44.08850410886773 Mm"},
				{"5.012352626692337E8", "501.2352626692337 Mm"},
				{"3.4647526395771317E9", "3.4647526395771317 Gm"},
				{"4.406504948675454E10", "44.06504948675454 Gm"},
				{"4.3059038717777136E11", "430.59038717777136 Gm"},
				{"6.151322452883016E12", "6.151322452883016 Tm"},
				{"3.4709296247123836E13", "34.709296247123836 Tm"},
				{"4.0889855324174975E14", "408.89855324174975 Tm"},
				{"5.80117170435937E15", "5.80117170435937 Pm"},
				{"7.5822112616502176E16", "75.822112616502176 Pm"},
				{"5.279608215499783E17", "527.9608215499783 Pm"},
				{"2.29902765143837491E18", "2.29902765143837491 Em"},
				{"5.81170778962884E19", "58.1170778962884 Em"},
				{"9.717084733433081E20", "971.7084733433081 Em"},
				{"9.78852994137227E21", "9.78852994137227 Zm"},
				{"4.284708562690263E22", "42.84708562690263 Zm"},
				{"2.5950373366370774E23", "259.50373366370774 Zm"},
				{"2.0405875017055503E24", "2.0405875017055503 Ym"},
				{"2.581768759109869E25", "25.81768759109869 Ym"},
				{"2.05572152242281E26", "205.572152242281 Ym"},
					// exponents outside normal case:
				{"3.098114734707732E-25", "3.098114734707732E-25 m"},
				{"8.520316081367575E27", "8.520316081367575E+27 m"},
					// special cases:
				{"Infinity", "Infinity m"},
				{"-Infinity", "-Infinity m"},
				{"NaN", "NaN m"}
			};
			
			for (String[] pair : scenarios) {
				double number = Double.parseDouble(pair[0]);
				String resultExpected = pair[1];
				Assert.assertEquals( resultExpected, toEngineeringString(number, Unit.meter) );
			}
		}
		
		@Test public void test_toEngineeringTime() {
			Assert.assertEquals( "1.2 s", toEngineeringTime(1.23, 1) );
			Assert.assertEquals( "4.57 s", toEngineeringTime(4.567, 2) );
		}
		
	}
	
}
