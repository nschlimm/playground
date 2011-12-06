/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


package bb.io;

import java.io.InputStream;

/**
* Defines methods for a type that can be used to drain InputStreams.
* This type extends Runnable because a dedicated thread is typically used to call {@link #run run}.
* <p>
* <i>A given implementation instance may only be used once to drain but a single InputStream</i>
* (which should have nothing else reading it).
* Therefore, it is an error to call {@link #init init} or run multiple times).
* <p>
* Some implementations store the bytes drained from the source InputStream,
* in which case this data may be read by calling {@link #getBytes getBytes}.
* Other implementations immediately process the bytes that the drain in some other way.
* <p>
* Implementations must be multithread safe, because run may be called by one thread
* while other threads may call other methods (e.g. getBytes).
* <p>
* @author Brent Boyer
*/
public interface StreamDrainer extends Runnable {
	
	/**
	* Assigns the InputStream that this instance must drain.
	* <p>
	* Note that some implementations may have a constructor which takes an InputStream arg.
	* In these cases, that constructor must call this method and the user should not subsequently call it again.
	* <p>
	* @throws IllegalArgumentException if in is null
	* @throws IllegalStateException if called more than once
	*/
	void init(InputStream in) throws IllegalArgumentException, IllegalStateException;
	
	/**
	* Drains the InputStream supplied to {@link #init init}.
	* <i>The fate of the drained bytes is implementation dependent</i> (see {@link #getBytes getBytes}).
	* <p>
	* Other than the IllegalStateException described below,
	* this method guarantees to never throw any Throwable once draining has started.
	* Instead, if a Throwable is thrown that cannot be internally handled,
	* this method guarantees to store it for future retrieval by {@link #getThrowable getThrowable} before aborting execution.
	* <p>
	* @throws IllegalStateException if init has not been called yet; this method is called more than once
	*/
	void run() throws IllegalStateException;
	
	/**
	* Returns all the bytes that have been drained by {@link #run run}
	* <i>and</i> stored by this instance
	* since the last time this method was called.
	* <p>
	* Side effect: any stored bytes are cleared upon return.
	* <p>
	* <i>Note: implementations are not required to store drained bytes</i>
	* (e.g. because they may immediately process them some other way, such as log them).
	* <p>
	* Assume that the implementation stores bytes.
	* One usage scenario is that while one thread executes run, another thread periodically calls this method to poll for data.
	* Alternatively, that other thread may simply wait for the thread executing run to finish, and then call this method to get all the data at once.
	* <p>
	* @return all the bytes drained by run and stored since the last call of this method; the result is never null, but may be zero-length
	* @throws IllegalStateException if run has never been called
	*/
	byte[] getBytes() throws IllegalStateException;
	
	/**
	* Returns any Throwable caught by {@link #run run} while it was draining that it could not handle.
	* <p>
	* @return the Throwable which aborted the draining; is null if no such Throwable occurred
	* @throws IllegalStateException if run has never been called
	*/
	Throwable getThrowable() throws IllegalStateException;
	
}
