/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/

/*
Programmer notes:

+++ should this class implement some more detailed interface, like Collection/List/Deque?

--this guy has the same idea as me:
	http://objectmix.com/c/312022-std-container-fixed-size-drop-oldest-function.html
*/

package bb.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
* Simple data structure whose maximum number of elements stored is fixed to a constant value.
* This size limit is enforced via a "drop oldest" policy; see {@link #add add}.
* <p>
* Motivation: this class is useful when memory consumption must be bounded and old events may be safely discarded.
* <p>
* This class is not multithread safe.
* <p>
* @author Brent Boyer
*/
public class BufferFixed<E> {
	
	// -------------------- constants --------------------
	
	/** Default value for {@link #sizeMax}. */
	private static final int sizeMax_default = 1000;
	
	// -------------------- instance fields --------------------
	
	/**
	* Maximum number of elements that can be stored in {@link #deque}.
	* <p>
	* Contract: is > 0.
	*/
	private final int sizeMax;
	
	/**
	* Underlying data structure that holds elements.
	* <p>
	* Contract: is never null.
	*/
	private Deque<E> deque;
	
	/**
	* Number of old elements that were dropped from {@link #deque} <i>since the last call to {@link #getAndResetState getAndResetState}</i>.
	* <p>
	* Contract: is either >= 0 if is a legit value, or else is pegged at {@link Long#MIN_VALUE Long.MIN_VALUE} if overflow occurred.
	*/
	private long numDropped = 0;
	
	// -------------------- constructors --------------------
	
	/** Calls <code>{@link #BufferFixed(int) this}({@link #sizeMax_default})</code>. */
	public BufferFixed() {
		this(sizeMax_default);
	}
	
	/**
	* Constructor.
	* <p>
	* @throws IllegalArgumentException if sizeMax <= 0
	*/
	public BufferFixed(int sizeMax) throws IllegalArgumentException {
		Check.arg().positive(sizeMax);
		
		this.sizeMax = sizeMax;
		this.deque = new ArrayDeque<E>(sizeMax);
	}
	
	// -------------------- public api: add, getAndResetState, size --------------------
	
	/**
	* Adds element to an internal queue.
	* If that queue size already equals {@link #sizeMax},
	* then first removes the oldest item from the queue before adding element.
	* <p>
	* @throws IllegalArgumentException if element is null
	*/
	public void add(E element) throws IllegalArgumentException {
		Check.arg().notNull(element);
		
		if (deque.size() == sizeMax) {
			if (numDropped >= 0) ++numDropped;	// if wrapped around to Long.MIN_VALUE, then must keep it pegged there as per field contract
			deque.removeFirst();	// first is oldest
		}
		
		deque.addLast(element);	// last is newest
	}
	
	/**
	* Returns a new {@link State} instance which holds the complete state of this instance.
	* The caller is free to manipulate the result in any way (e.g. mutate the {@link State#deque} field, such as remove undesired elements).
	* <p>
	* <b>Side effect:</b> before return, resets all this instance's state to its initial condition.
	*/
	public State<E> getAndResetState() {
		try {
			return new State<E>(this);
		}
		finally {
			deque = new ArrayDeque<E>(sizeMax);	// CRITICAL: must create a new instance, since the old one is present in the result
			numDropped = 0;
		}
	}
	
	/** Returns {@link #deque}.{@link Deque#size size}. */
	public int size() {
		return deque.size();
	}
	
	// -------------------- State (static inner class) --------------------
	
	/**
	* Used to record a snapshot of the state of a {@link BufferFixed} instance.
	* <p>
	* This class is not multithread safe.
	* (Its immediate state is immutable, however, the {@link #deque} field is mutable.)
	*/
	public static class State<E> {
		
		/** Copy of the {@link BufferFixed#sizeMax sizeMax} field. */
		public final int sizeMax;

		/** Copy of the {@link BufferFixed#deque deque} field. */
		public final Deque<E> deque;

		/** Copy of the {@link BufferFixed#numDropped numDropped} field. */
		public final long numDropped;
		
		private State(BufferFixed<E> buffer) {
			this.sizeMax = buffer.sizeMax;
			this.deque = buffer.deque;
			this.numDropped = buffer.numDropped;
		}
		
		/**
		* Returns a short description of the number of items received and possibly dropped.
		* <p>
		* Contract: the result is never {@link Check#notBlank blank}, and never ends with a newline sequence (altho it may internally contain newlines).
		*/
		public String getDescription() {
			if (numDropped == 0) {
				return "All " + deque.size() + " of the items that were received over the course of the last snapshot appear below (in ascending order of occurrence):";
			}
			else {
				return
					"The " + deque.size() + " newest items that were received over the course of the last snapshot appear below (in ascending order of occurrence)" + "\n"
					+ "(WARNING: the " + numDropped + " oldest ones were dropped in order to limit memory use):";
			}
		}
		
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	// None needed--is tested by HandlerGui.UnitTest
	
}
