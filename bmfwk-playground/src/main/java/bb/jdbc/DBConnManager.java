/*
Copyright © 2008 Brent Boyer

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the Lesser GNU General Public License for more details.

You should have received a copy of the Lesser GNU General Public License along with this program (see the license directory in this project).  If not, see <http://www.gnu.org/licenses/>.
*/


/*

Programmer Notes:

--for how to specify the MySQL jdbcUrl params, see:
	mm.mysql.jdbc-1.2c/doc/mm.doc/c106.html
	http://www.worldserver.com/mm.mysql/doc/mm.doc/c106.htm#AEN118
	
	--if you need to know what domain name to use, try executing
		./bin/mysql mysql
		select * from user
	You may find that the root user has a row with a domain name.

--On connection pooling:
	--good web articles:
		http://www.jguru.com/faq/view.jsp?EID=161
			(their implementation is mediocre):  http://webdevelopersjournal.com/columns/connection_pool.html
	--check out JDBC 1.4: I believe that the latest jdbc spec has some sort of built in connection pool class or something?

+++ CRITICAL FEATURE THAT NEED TO ADD TO THIS CLASS ASAP:
should NOT allow the user to return just any Connection instance to the returnConnection method,
but need to know what instances where created by and only accept back Connections that it gave out

--a more sophisticated pool implementation than what is currently in this class might:
	--have an initial number of connections, grow it if necessary to some max number, and remove connections if they are unused for a long while
	--have a method called shutdown which sets a closed flag, closes all connections currently in the pool, and closes all connections that subsequently get returned
	--specify constants like poolSize and waitLimit in some other less hard-wired way
	--auditing:
		--when a Connection gets checked out, record what statcktrace did the checkout in a Map instance
		--when a Connection is returned, release that information
		--offer an api that can call to see what are the guys who have not released Connections
			--this would allow you to debug guys who are hogging Connections

--NEED TO READ DOUG LEA'S CONCURRENT BOOK; HAS A SECTION ON POOLS; HE PROBABLY HAS SOME IDEAS THAT SHOULD INCORPORATE HERE
+++ actually, see "Java Concurrency in Practice" by Goetz et al

+++ IN FUTURE, should get properties like driver & db url from a properties text file
and/or use JDK1.4's new preferences capability instead of hard coding them as is currently done;
then would not have to recompile each time...
	--would need to add code which confirms that stuff like poolSize, and waitLimit have valid values

*/


package bb.jdbc;


import bb.util.Check;
import bb.util.ThrowableUtil;
import bb.util.logging.LogUtil;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;

/**
* This class manages access to database Connections.
* The public api is just the {@link #getConnection getConnection} and {@link #returnConnection returnConnection} methods.
* <p>
* This class is multithread safe: most of its state is immutable (both its immediate state, as well as the deep state of its fields).
* The sole exception is the {@link #pool} field, which is guarded by synchronized blocks on itself.
* <p>
* @author Brent Boyer
*/
public class DBConnManager {

	
	private static final String jdcDriver = "org.gjt.mm.mysql.Driver";
	//private static final String jdcDriver = "org.apache.derby.jdbc.EmbeddedDriver";
	
	private static final String jdbcUrl = "jdbc:mysql://localhost/EMA?user=root ?default-character-set=big5";
	//private static final String jdbcUrl = "jdbc:derby:" + dbName + ";create=true";
	
	
	private static final int poolSize = 75;
	private static final BlockingQueue<Connection> pool = new ArrayBlockingQueue<Connection>(poolSize);


	/**
	* Static initializer that loads the driver class and adds new Connections to the pool.
	* <p>
	* @throws RuntimeException (or some subclass) if any error occurs; this may merely wrap some other underlying Throwable
	*/
	static {
		try {
			Class.forName(jdcDriver);	 // if have problems, may need to add  .newInstance()  here -- see mm.mysql.jdbc-1.2c/doc/mm.doc/c106.html
			// see also: http://blogs.sun.com/blogAmit/entry/jdbc_driver_loading_with_mustang

			for (int i = 0; i < poolSize; ++i) {
				pool.add( newConnection() );
			}
		}
		catch (Throwable t) {
			throw ThrowableUtil.toRuntimeException(t);
		}
	}


	/**
	* Returns a newly constructed Connection instance.
	* <p>
	* Note: the result has auto commit turned off and (since is new) should have
	* nothing to be committed and no warnings present.
	* <p>
	* Note: this method assumes that the driver class has already been loaded.
	* <p>
	* @throws SQLException if a database access error occurs
	*/
	private static Connection newConnection() throws SQLException {
		Connection connection = DriverManager.getConnection(jdbcUrl);
		connection.setAutoCommit(false);
		return connection;
	}


	/**
	* Returns a Connection instance.
	* <p>
	* Contract: the result is never null, has auto commit turned off, and is clear of all warnings.
	* <p>
	* @throws InterruptedException if the calling Thread is interrupted while waiting for a Connection to become available
	*/
	public static Connection getConnection() throws InterruptedException {
		return pool.take();
	}


	/**
	* Every Connection returned by {@link #getConnection getConnection} must be sent back to this class by calling this method.
	* <p>
	* <b>Failure to call this method will ultimately deprive this class of any Connections to return, which may choke your application</b>.
	* Therefore, the caller of getConnection must ensure that this method receives back all Connections.
	* Here is typical usage:
	* <pre><code>
	*	Connection connection = null;
	*	try {
	*		connection = DBConnManager.getConnection();
	*		// do something with connection
	*	}
	*	finally {
	*		DBConnManager.returnConnection(connection);
	*	}
	* </code></pre>
	* <p>
	* The implementation here immediately returns if connection is null.
	* Otherwise, it adds <code>{@link #ensurePristine ensurePristine}(connection)</code> back to {@link #pool}.
	*/
	public static void returnConnection(Connection connection) {
		if (connection == null) return;
		
		try {
			pool.add( ensurePristine(connection) );
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.SEVERE, "DBConnManager", "returnConnection", "failed to add a Connection back to the pool", t);
		}
	}
	
	
	/**
	* Returns {@link #newConnection newConnection} if connection has already been closed.
	* <p>
	* Otherwise, cleans up connection as follows:
	* <ol>
	*  <li>turns off its auto commit</li>
	*  <li>calls its commit method</li>
	*  <li>clears its warnings</li>
	* </ol>
	* <p>
	* If all these steps succeed, then onnection is returned.
	* Else an attempt is made to close connection and return newConnection.
	* <p>
	* @throws IllegalArgumentException if connection is null
	* @throws SQLException if a database access error occurs
	*/
	private static Connection ensurePristine(Connection connection) throws IllegalArgumentException, SQLException {
		Check.arg().notNull(connection);
		
		if (connection.isClosed()) {
			return newConnection();
		}
		
		try {
			connection.setAutoCommit(false);
			connection.commit();
			connection.clearWarnings();
			return connection;
		}
		catch (Throwable t) {
			LogUtil.getLogger2().logp(Level.SEVERE, "DBConnManager", "ensurePristine", "failed to cleanup a Connection", t);
			
			try {
				connection.close();	// CRITICAL: must close connection before try to return newConnection below, since the database may have a connection limit
			}
			catch (Throwable t2) {
				LogUtil.getLogger2().logp(Level.SEVERE, "DBConnManager", "ensurePristine", "failed to close a Connection", t2);
			}
			
			return newConnection();
		}
	}
	
	
	// -------------------- constructor --------------------
	
	
	/** This sole private constructor suppresses the default (public) constructor, ensuring non-instantiability outside of this class. */
	private DBConnManager() {}
	
	
}
