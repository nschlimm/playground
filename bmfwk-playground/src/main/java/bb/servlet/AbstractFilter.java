/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.servlet;


import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;


/**
* This abstract filter class implements some functionality that will likely be common to all filters:
* a {@link #filterConfig} field,
* as well as {@link #init init}, {@link #destroy destroy}, and {@link #toString toString} methods.
* The {@link #doFilter doFilter} method is left for concrete subclasses to implement.
* <p>
* This class is multithread safe: its only state is the {@link #filterConfig} field which is declared volatile.
* <p>
* @author Brent Boyer
*/
public abstract class AbstractFilter implements Filter {


	// -------------------- instance fields --------------------


	/**
	* The filter configuration object we are associated with.
	* If null, this filter instance is not currently configured.
	*/
	protected volatile FilterConfig filterConfig = null;


	// -------------------- Filter API methods --------------------


	/**
	* Places this filter into service.
	* The implementation here merely stores the filterConfig arg into the filterConfig field.
	* <p>
	* @param filterConfig The filter configuration object
	*/
	public void init(FilterConfig filterConfig) throws ServletException {
		this.filterConfig = filterConfig;
	}


	/**
	* Takes this filter out of service.
	* The implementation here merely sets the filterConfig field to null.
	* Therefore, this instance will be left unconfigured once this method exits.
	*/
	public void destroy() {
		this.filterConfig = null;
	}


	/**
	* To be implemented by a concrete subclass.
	* <p>
	* @param request The servlet request we are processing
	* @param response The servlet response we are creating
	* @param chain The filter chain we are processing
	* @throws IOException if an input/output error occurs
	* @throws ServletException if a servlet error occurs
	*/
	public abstract void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException;


	// -------------------- misc methods --------------------


	/** Returns a String representation of this object. */
	@Override public String toString() {
		String configInfo = (filterConfig != null) ? filterConfig.toString() : "<currently unconfigured>";
		return this.getClass().getName() + "(" + configInfo + ")";
	}


}
