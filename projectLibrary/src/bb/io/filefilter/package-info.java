/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/**
<p>
	Provides classes that may be used for file filtering.
</p>

<p>
	To understand this package, first examine the {@link bb.io.filefilter.BaseFilter} class,
	since almost every file filter implementation here suclasses it.
	(One exception is {@link bb.io.filefilter.CompoundFilter}).
	Then examine these important subclasses of BaseFilter: {@link bb.io.filefilter.RegexFilter}, and its subclass {@link bb.io.filefilter.SuffixFilter}.
</p>

<p>
	The reason why so many specific SuffixFilter subclasses exist
	(e.g. {@link bb.io.filefilter.ClassFilter}, {@link bb.io.filefilter.JarFilter}, {@link bb.io.filefilter.JavaFilter}, etc) is twofold.
	First, these filters are common enough that it is nice to not require users to implement them every time.
	Second, several of my classes allow file filters to be specified as command line arguments,
	and it is very convenient to only have to specify the filter class name with no other parameters.
	(The alternative, if specified SuffixFilter's class name along with a list of suffixes,
	would require custom parsing code to be written.)
</p>

<!--
For a discussion of other things that could put here, see
	http://java.sun.com/j2se/javadoc/writingdoccomments/index.html#packagecomments
	http://java.sun.com/j2se/javadoc/writingdoccomments/package-template
-->
*/
package bb.io.filefilter;
