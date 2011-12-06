/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

package bb.science;

import bb.util.Check;
import bb.util.StringUtil;

/**
* Stores all information related to fitting of data samples to some statistical distribution.
* <p>
* <b>Warning on all accessors:</b> the field is directly returned, not a copy, so mutating the result invalidates this instance.
* <i>So, only mutate the result if this instance will no longer be used.</i>
* <p>
* This class is not multithread safe: while its immediate state is immutable, the deep state of its fields is not.
* <p>
* @author Brent Boyer
*/
public class DistributionFit {
	
	// -------------------- fields --------------------
	
	private final String[] paramLabels;
	
	private final double[] params;
	
	private final String[] fitMeasureLabels;
	
	private final double[] fitMeasures;
	
	private final double[] bounds;
	
	private final double[] pdfObserved;
	
	private final double[] pdfTheory;
	
	// -------------------- constructor --------------------
	
	public DistributionFit(String[] paramLabels, double[] params, String[] fitMeasureLabels, double[] fitMeasures, double[] bounds, double[] pdfObserved, double[] pdfTheory) throws IllegalArgumentException {
		Check.arg().notEmpty(paramLabels);
		Check.arg().notEmpty(params);
		Check.arg().isTrue( paramLabels.length == params.length );
		Check.arg().notEmpty(fitMeasureLabels);
		Check.arg().notEmpty(fitMeasures);
		Check.arg().isTrue( fitMeasureLabels.length == fitMeasures.length );
		Check.arg().notEmpty(bounds);
		Check.arg().notEmpty(pdfObserved);
		Check.arg().notEmpty(pdfTheory);
		Check.arg().isTrue( bounds.length == pdfObserved.length );
		Check.arg().isTrue( bounds.length == pdfTheory.length );
/*
hmm, not sure if I should retain this normalization checks or not...
		if (bounds.length >= 2) {
			double width = bounds[1] - bounds[0];
			double sumObs = Math2.normalizationSum(pdfObserved) * width;
			double sumObsErr = Math.abs(sumObs - 1.0);
			double sumTheory = Math2.normalizationSum(pdfTheory) * width;
			double sumTheoryErr = Math.abs(sumTheory - 1.0);
			if ((sumObsErr > 1e-6) || (sumTheoryErr > 1e-6)) throw new IllegalStateException("sumObs = " + sumObs + ", sumTheory = " + sumTheory + ", width = " + width);
		}
*/
		
		this.paramLabels = paramLabels;
		this.params = params;
		this.fitMeasureLabels = fitMeasureLabels;
		this.fitMeasures = fitMeasures;
		this.bounds = bounds;
		this.pdfObserved = pdfObserved;
		this.pdfTheory = pdfTheory;
	}
	
	// -------------------- accessors --------------------
	
	/** Accessor for {@link #paramLabels}. */
	public String[] getParamLabels() {
		return paramLabels;
	}
	
	/** Accessor for {@link #params}. */
	public double[] getParams() {
		return params;
	}
	
	/** Accessor for {@link #fitMeasureLabels}. */
	public String[] getFitMeasureLabels() {
		return fitMeasureLabels;
	}
	
	/** Accessor for {@link #fitMeasures}. */
	public double[] getFitMeasures() {
		return fitMeasures;
	}
	
	/** Accessor for {@link #bounds}. */
	public double[] getBounds() {
		return bounds;
	}
	
	/** Accessor for {@link #pdfObserved}. */
	public double[] getPdfObserved() {
		return pdfObserved;
	}
	
	/** Accessor for {@link #pdfTheory}. */
	public double[] getPdfTheory() {
		return pdfTheory;
	}
	
	// -------------------- toString --------------------
	
	@Override public String toString() {
		StringBuilder sb = new StringBuilder();
		
		sb.append("Parameters:").append("\n");
		append(paramLabels, params, sb);
		
		sb.append("\n");
		sb.append("Goodness of fit measures:").append("\n");
		append(fitMeasureLabels, fitMeasures, sb);
		
		sb.append("\n");
		sb.append("Probability distribution function:").append("\n");
		double[][] matrix = new double[][] { bounds, pdfObserved, pdfTheory };
		sb.append( StringUtil.arraysToTextColumns( matrix, new String[] {"x", "pdfObserved(x)", "pdfTheory(x)"} ) );
		
		return sb.toString();
	}
	
	private void append(String[] labels, double[] values, StringBuilder sb) {
		for (int i = 0; i < labels.length; i++) {
			sb.append( labels[i] ).append("\t").append( values[i] ).append("\n");
		}
	}
	
	// -------------------- convenience methods --------------------
	
// +++ may wish to offer a toPoints method which converts bounds and pdfs into Point2D.Double[]s
// that other Java graphing packages could use, assuming that they take that format
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None: is tested by other classes
	
}
