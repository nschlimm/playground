/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.science;

/**
* Subclass of DistributionFit that is customized for the fitting of a Gaussian distribution to data
* as performed by {@link Math2#gaussianFit Math2.gaussianFit}.
* <p>
* This class adds no new instance state to its superclass.
* Instead, it merely offers a more convenient constructor and accesor methods.
* <p>
* @author Brent Boyer
*/
public class GaussianFit extends DistributionFit {
	
	// -------------------- constants --------------------
	
	private static final String[] paramLabels = new String[] {"mean", "sd"};
	
	private static final String[] fitMeasureLabels = new String[] {"Anderson–Darling test statistic", "Kolmogorov-Smirnov test statistic"};
	
	// -------------------- constructor --------------------
	
	public GaussianFit(double mean, double sd, double andersonDarling, double kolmogorovSmirnov, double[] bounds, double[] pdfObserved, double[] pdfTheory) throws IllegalArgumentException {
		super(paramLabels, new double[] {mean, sd}, fitMeasureLabels, new double[] {andersonDarling, kolmogorovSmirnov}, bounds, pdfObserved, pdfTheory);
	}
	
	// -------------------- accessors --------------------
	
	/** Returns the mean parameter of the fitted Gaussian. */
	public double getMean() {
		return getParams()[0];
	}
	
	/** Returns the sd parameter of the fitted Gaussian. */
	public double getSd() {
		return getParams()[1];
	}
	
	/** Returns the {@link Math2#gaussianAndersonDarling Anderson-Darling} goodness of fit measure. */
	public double getAndersonDarling() {
		return getFitMeasures()[0];
	}
	
	/** Returns the {@link Math2#gaussianKolmogorovSmirnov Kolmogorov-Smirnov} goodness of fit measure. */
	public double getKolmogorovSmirnov() {
		return getFitMeasures()[1];
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None: is tested by other classes
	
}
