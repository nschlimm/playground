/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/**
<p>
	Provides classes and interfaces for GUI applications.
</p>
<p>
	Concerning multithread safety, many of the classes in this package have a javadoc comment like
	<q>Like typical Java GUI code, most of this class's methods are not multithread safe...</q>.
	Here are some references which discuss this matter:
</p>
<ol>
	<li><a href="http://java.sun.com/javase/6/docs/api/javax/swing/package-summary.html#package_description">javax.swing package summary</a></li>
	<li><a href="http://java.sun.com/docs/books/tutorial/uiswing/concurrency/index.html">Concurrency in Swing</a></li>
	<li><a href="http://java.sun.com/developer/JDCTechTips/2003/tt1208.html#1">JDCTechTips: Multithreading In Swing</a></li>
	<li><a href="http://java.sun.com/developer/JDCTechTips/2004/tt0611.html#1">JDCTechTips: More Multithreading In Swing</a></li>
</ol>
<p>
	Because of this threading limitation, most public methods in this package which deal with components
	require the calling thread to be the event dispatch thread.
</p>


<!--
For a discussion of other things that could put here, see
	http://java.sun.com/j2se/javadoc/writingdoccomments/index.html#packagecomments
	http://java.sun.com/j2se/javadoc/writingdoccomments/package-template
-->
*/
package bb.gui;
