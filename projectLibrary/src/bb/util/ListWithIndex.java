/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.util;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;


/**
* Contains an internal List, as well as keeps track of the current position (index) in the List.
* Elements are gotten, added, or removed from the current index.
* The index is -1 if the List has size 0.
* <p>
* This component is a Java Bean, and supports "index" as a bound property.
* See <a href="http://developer.java.sun.com/developer/onlineTraining/Beans/beans02/page3.html">the Sun tutorial</a> for details.
* <b>Implementation note: must always use the setIndex method to change the index field to guarantee proper index event firing.</b>
* <p>
* This class is not multithread safe.
*/
public class ListWithIndex {


	// -------------------- instance variables --------------------


	private final List<Object> list = new ArrayList<Object>();

	private final PropertyChangeSupport changes = new PropertyChangeSupport(this);

	private int index = -1;


	// -------------------- constructor --------------------


	public ListWithIndex() {}


	// -------------------- List methods --------------------


	/**
	* Returns the element at index.
	* There are no side effects of calling this method (i.e. index is left unchanged).
	* <p>
	* @throws IllegalStateException if list.size() == 0
	*/
	public Object get() throws IllegalStateException {
		Check.state().notEmpty(list);

		return list.get(index);
	}

	/**
	* Returns the element at index + offset.
	* There are no side effects of calling this method (i.e. index is left unchanged).
	* <p>
	* @throws IllegalStateException if hasAtOffset(offset) returns false
	*/
	public Object getAtOffset(int offset) throws IllegalStateException {
		if ( !hasAtOffset(offset) ) throw new IllegalStateException("hasAtOffset(" + offset + ") returns false");

		return list.get(index + offset);
	}


	/**
	* Inserts obj into the List right after the element that index currently points to.
	* Side effect: index is incremented (so that it now points to obj).
	* Contract: index will never become an illegal value by calling this method.
	* <p>
	* @param obj any Object, including null
	*/
	public void add(Object obj) {
		int indexNew = index + 1;
		list.add(indexNew, obj);
		setIndex(indexNew);
	}


	/**
	* Removes the element at index from the list.
	* Side effect: if, after removal of the element, index now points outside the List
	* (i.e. it has the value list.size()), it will be decremented.
	* Contract: index will never become an illegal value by calling this method.
	* <p>
	* @return the element that was removed from the list
	* @throws IllegalStateException if list.size() == 0
	*/
	public Object remove() throws IllegalStateException {
		Check.state().notEmpty(list);

		Object element = list.remove(index);
		if (index == list.size()) setIndex(index - 1);
		return element;
	}


	/** Returns the size of the internal List. */
	public int size() { return list.size(); }


	/** Returns a new List instance which contains all the elements currently in the internal List. */
	public List<Object> getList() { return new ArrayList<Object>(list); }


	// -------------------- index methods --------------------


	/** Returns the current value of index. Will be -1 if list.size() == 0. */
	public int getIndex() { return index; }

	/**
	* Assigns the current value of index.
	* <p>
	* @throws IllegalStateException if list.size() == 0 && index != -1;
	* list.size() > 0 && index < 0; index >= list.size()
	*/
	public void setIndex(int index) throws IllegalStateException {
		if (list.size() == 0) {
			if (index != -1) throw new IllegalStateException("index = " + index + " is != -1 while list.size() == 0");
		}
		else {
			if (index < 0) throw new IllegalStateException("index = " + index + " is < 0 but list.size() = " + list.size() + " is > 0");
			if (index >= list.size()) throw new IllegalStateException("index = " + index + " is >= list.size() = " + list.size());
		}

		int indexOld = this.index;
		this.index = index;
		changes.firePropertyChange("index", indexOld, index);
	}


	/**
	* Changes index to index + offset.
	* <p>
	* @throws IllegalStateException if setIndex objects to index + offset as a new index
	*/
	public void offsetIndex(int offset) throws IllegalStateException {
		setIndex(index + offset);
	}

	/**
	* If hasNext() returns true, then this method increments index.
	* Otherwise this method leaves index unchanged.
	* Contract: index will never become an illegal value by calling this method.
	*/
	public void incrementIndex() { if (hasNext()) setIndex(index + 1); }

	/**
	* If hasPrevious() returns true, then this method decrements index.
	* Otherwise this method leaves index unchanged.
	* Contract: index will never become an illegal value by calling this method.
	*/
	public void decrementIndex() { if (hasPrevious()) setIndex(index - 1); }


	// -------------------- hasXXX --------------------


	/** Reports whether or not there is an element before the current value of index. */
	public boolean hasPrevious() { return (index > 0); }


	/** Reports whether or not there is an element after the current value of index. */
	public boolean hasNext() { return (index < list.size() - 1); }	// note: this correctly returns false even when list.size() == 0, as index should equal -1 then


	/** Reports whether or not there is an element at index + offset. */
	public boolean hasAtOffset(int offset) {
		int desiredIndex = index + offset;
		return (0 <= desiredIndex) && (desiredIndex < list.size());
	}


	// -------------------- xxxPropertyChangeListener --------------------


	public void addPropertyChangeListener(PropertyChangeListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);

		changes.addPropertyChangeListener(listener);
	}


	public void removePropertyChangeListener(PropertyChangeListener listener) throws IllegalArgumentException {
		Check.arg().notNull(listener);

		changes.removePropertyChangeListener(listener);
	}


}
