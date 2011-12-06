/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer notes:

+++ this class needs to be deprecated because the new StateMachine class in this package should replace it
	--need to migrate all current users of this class over
	--possibly need to rip some of this class's code out into StateMachine?
	--here are the old todos for this class:
		+++ WRITE A TEST CLASS: THIS CLASS IS TOO IMPORTANT TO HAVE ANY ERRORS

		+++ allow states to be Objects instead of Strings for more generality
			--this would even cover enums, which user may want to use, since they could then use them in switch statements
			--will have to introduce generics to specify the type

		+++ constructor should be varargs for states instead of []

		--should there be a name associated with the State instance itself?
*/


package bb.util;

import java.util.HashSet;
import java.util.Set;

/**
* This class is meant to represent named states.
* An instance is constructed with an array of all possible states that it can be in,
* and it can be assigned any state in this array by various method calls.
* It is useful for writing simple software state machines.
* <p>
* This class is multithread safe: all public methods are synchronized.
* <p>
* @author Brent Boyer
*/
public class State {
	
	// -------------------- instance fields --------------------
	
	/** Contract: is never null, zero length, contains a blank element, or contains duplicate elements. */
	protected final String[] states;
// +++ an array is optimal for a small number of states, but if have a really large number, then need a SortedMap where the key is the state number or a Set?

	protected int index = -1;	// -1 is an deliberately invalid value which must be reassigned in a constructor
	
	// -------------------- constructors --------------------
	
	public State(String[] states) throws IllegalArgumentException {
		this(states, 0);
	}
	
	public State(String[] states, int index) throws IllegalArgumentException {
		Check.arg().notEmpty(states);
			// check for bad elements inside states:
		Set<String> set = new HashSet<String>();
		for (int i = 0; i < states.length; i++) {
			Check.arg().notBlank(states[i]);
			if (!set.add(states[i])) throw new IllegalArgumentException("states contains duplicate elements, with the first duplicate being states[" + i + "] = " + states[i]);
		}
		// index is checked in the call to set below
		
		this.states = states.clone();	// clone to ensure that our states cannot be modified by anyone else; since Strings are immutable, no need to do a deep clone
		set(index);
	}
	
	// -------------------- Object methods --------------------
	
	@Override public String toString() {
		return get();
	}
	
	// -------------------- public api --------------------
	
	/** Returns whether or not this instance knows of state as a valid state that it can reach. */
	public synchronized boolean knows(String state) {
		return (findIndex(state) != -1);
	}
	
	/** Returns whether or not state equals this instance's current state. */
	public synchronized boolean is(String state) {
		return get().equals(state);
	}
	
	/** Returns the current state. */
	public synchronized String get() {
		return states[index];
	}
	
	/** Returns a clone of all the potential states of this State instance. */
	public synchronized String[] getStates() {
		return states.clone();	// clone to ensure that our states cannot be modified by anyone else
	}
	
	/**
	* Adjusts the internal index to the specified index, which changes the state to the one at that index.
	* <p>
	* This is the fundamental state changing method.
	* Subclasses may override in order to enforce custom state transition rules.
	* <p>
	* @throws IllegalArgumentException if indexNew is not a valid value
	*/
	public synchronized void set(int indexNew) throws IllegalArgumentException {
		if ( (indexNew <= -1) || (states.length <= indexNew) ) throw new IllegalArgumentException("indexNew = " + indexNew + " is outside the valid range [0, " + (states.length - 1) + "]");
		
		index = indexNew;
	}
	
	/**
	* Moves to the specified state.
	* <p>
	* @throws IllegalArgumentException if state is not a valid state for this State instance
	*/
	public synchronized void set(String state) throws IllegalArgumentException {
		int indexState = findIndex(state);
		if (indexState == -1) throw new IllegalArgumentException("state = " + state + " is not a valid state for this State instance");
		set( indexState );	// CRITICAL: always go thru the lower level set(int) method so that way if subclasses have special state transition rules, they need only override set(int)
	}
	
	/** Moves to the next state (wrapping around to the beginning of the states array if are currently at end). */
	public synchronized void increment() {
		set( (index + 1) % states.length );	// CRITICAL: always go thru the lower level set(int) method so that way if subclasses have special state transition rules, they need only override set(int)
	}
	
	/** Moves to the previous state (wrapping around to the end of the states array if are currently at beginning). */
	public synchronized void decrement() {
		set( (index - 1) % states.length );	// CRITICAL: always go thru the lower level set(int) method so that way if subclasses have special state transition rules, they need only override set(int)
	}
	
	// -------------------- utility methods --------------------
	
	/**
	* Returns the index of the element within states which equals state,
	* else returns -1 if no such element exists.
	*/
	protected int findIndex(String state) {
		for (int i = 0; i < states.length; i++) {
			if (states[i].equals(state)) return i;
		}
		return -1;
	}
	
}
