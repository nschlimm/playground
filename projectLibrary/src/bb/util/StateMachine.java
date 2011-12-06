/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*
Programmer notes:

+++ need to migrate all user of the now deprecated State class over to this class
	--will this involve adding functionality to this class?
*/

package bb.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
* Simple implementation of a software <a href="http://en.wikipedia.org/wiki/State_machine">state machine</a>.
* <p>
* Currently, this class merely stores state transition rules, and offers the {@link #isTransitionAllowed isTransitionAllowed} method
* for users to verify transitions at runtime.
* <p>
* This class is multithread safe: it is immutable (both its immediate state, as well as the deep state of its fields).
* <p>
* @author Brent Boyer
* @see <a href="http://books.slashdot.org/comments.pl?sid=141172&cid=11885913">this forum discussion</a>
*/
public class StateMachine<E extends Enum<E>> {
	
	// -------------------- fields --------------------
	
	/**
	* Every key is an initial state, and its corresponding value is the transition rule for that state
	* (i.e. the set of states that are allowed to be transitioned to).
	* <p>
	* Contract: is never null, defines a non-empty transition rule for every value of E, and must never be modified.
	*/
	private final EnumMap<E,EnumSet<E>> transitions;
	
	// -------------------- constructor and helper methods --------------------
	
	/**
	* Fundamental constructor.
	* <p>
	* @throws IllegalArgumentException if the transitions arg violates the contract of the {@link #transitions} field.
	*/
	public StateMachine(EnumMap<E,EnumSet<E>> transitions) throws IllegalArgumentException {
		this.transitions = cloneDeep( checkTransitions(transitions) );
	}
	
	private EnumMap<E,EnumSet<E>> checkTransitions(EnumMap<E,EnumSet<E>> transitions) throws IllegalArgumentException {
		Check.arg().notEmpty(transitions);
		
		E first = transitions.keySet().iterator().next();
		E[] states = ReflectUtil.getEnumValues(first);	// this should return all the values of the enum that first's type is
		for (E state : states) {
			Check.arg().notEmpty( transitions.get(state) );
		}
		
		return transitions;
	}
	
	private EnumMap<E,EnumSet<E>> cloneDeep(EnumMap<E,EnumSet<E>> transitions) {
			// replace with a new EnumMap:
		transitions = new EnumMap<E,EnumSet<E>>( transitions );
		
			// now replace all of the Map's values with clones:
		for (Map.Entry<E,EnumSet<E>> entry : transitions.entrySet()) {
			EnumSet<E> valueOld = entry.getValue();
			EnumSet<E> valueNew = EnumSet.copyOf(valueOld);
			entry.setValue(valueNew);
		}
			// Note: no need to clone the keys, since they are immutable Strings
		
		return transitions;
// +++ hmm, should I wrap the result in Collections.unmodifiableMap to guarantee immutability?  definitely need to if ever expose the field..,
	}
	
	/**
	* Convenience constructor which parses the transition rules from a String.
	* <p>
	* @throws IllegalArgumentException if the parsing fails, or its result violates the contract of the {@link #transitions} field.
	*/
	public StateMachine(Class<E> clazz, String... rules) throws IllegalArgumentException {
		this.transitions = checkTransitions( parseTransitions(clazz, rules) );
	}
	
	/**
	* Parses rules into an EnumMap.
	* <p>
	* Each element of rules is a state transition rule.
	* Its format is stateInitial followed by a "-->" followed by statesFinal, which must be a comma (',') delimited list of states.
	* Optional whitespace may appear around any token in the above format.
	* Every state in the enum referred to by clazz must have one and only one transition rule in rules.
	* <p>
	* @throws IllegalArgumentException if rules {@link Check#notBlank is blank} or some format error occurs
	*/
	private EnumMap<E,EnumSet<E>> parseTransitions(Class<E> clazz, String... rules) throws IllegalArgumentException {
		Check.arg().notEmpty(rules);
		
		EnumMap<E,EnumSet<E>> transitions = new EnumMap<E,EnumSet<E>>(clazz);
		for (String rule : rules) {
			rule = rule.trim();
			
			E stateInitial = parseStateInitial(clazz, rule);
			if (transitions.containsKey(stateInitial)) throw new IllegalArgumentException("stateInitial = " + stateInitial + "\n" + "appears more than once inside rules =" + "\n" + "\t" + StringUtil.toString(rules, "\n\t"));
			
			EnumSet<E> statesFinal = parseStatesFinal(clazz, rule);
			
			transitions.put( stateInitial, statesFinal );
		}
		return transitions;
	}
	
	private E parseStateInitial(Class<E> clazz, String rule) throws IllegalArgumentException {
		int index = rule.indexOf("-->");
		Check.state().notNegative(index);
		String name = rule.substring(0, index).trim();
		return Enum.valueOf(clazz, name);
	}
	
	private EnumSet<E> parseStatesFinal(Class<E> clazz, String rule) throws IllegalArgumentException {
		int index = rule.indexOf("-->");
		Check.state().notNegative(index);
		String[] names = rule.substring(index + "-->".length()).split(",", 0);
		List<E> list = new ArrayList<E>(names.length);
		for (String name : names) {
			name = name.trim();
			list.add( Enum.valueOf(clazz, name) );
		}
		return EnumSet.copyOf(list);
	}
	
	// -------------------- overridden Object methods --------------------
	
	/**
	* Returns a String representation of {@link #transitions}.
	* <p>
	* Contract: the result must be compatible with {@link #parseTransitions}, that is,
	* <code>parseTransitions( stateMachine.toString() )</code> must always succeed.
	*/
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (E stateInitial : transitions.keySet()) {
			String statesFinal = StringUtil.toString( transitions.get(stateInitial), ", " );
			sb.append( stateInitial ).append( " --> " ).append( statesFinal ).append('\n');
		}
		return sb.toString();
	}
	
	// -------------------- new public api --------------------
	
	public boolean isTransitionAllowed(E stateInitial, E stateFinal) throws IllegalArgumentException {
		Check.arg().notNull(stateInitial);
		Check.arg().notNull(stateFinal);
		
		return transitions.get(stateInitial).contains(stateFinal);
	}
	
	// -------------------- UnitTest (static inner class) --------------------
	
	/** See the Overview page of the project's javadocs for a general description of this unit test class. */
	public static class UnitTest {
		
		private static enum Numbers { one, two, three; }
		
		@Test public void test_toString() {
			StateMachine<Numbers> stateMachine = new StateMachine<Numbers>(
				Numbers.class,
				"one --> one",
				"two --> one, two",
				"three --> one, two, three"
			);
			String s = stateMachine.toString();
			System.out.println(s);
			String[] tokens = s.split("\n", 0);
			EnumMap<Numbers,EnumSet<Numbers>> transitionsParsed = stateMachine.parseTransitions(Numbers.class, tokens);
			Assert.assertEquals( stateMachine.transitions, transitionsParsed);
		}
		
	}
	
}
