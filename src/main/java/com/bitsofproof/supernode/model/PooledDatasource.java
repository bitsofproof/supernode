/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.model;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PooledDatasource implements DataSource
{
	private static final Logger log = LoggerFactory.getLogger (PooledDatasource.class);
	private ConnectionPoolDataSource dataSource;

	public ConnectionPoolDataSource getDataSource ()
	{
		return dataSource;
	}

	public void setDataSource (ConnectionPoolDataSource dataSource)
	{
		this.dataSource = dataSource;
	}

	@Override
	public PrintWriter getLogWriter () throws SQLException
	{
		return dataSource.getLogWriter ();
	}

	@Override
	public int getLoginTimeout () throws SQLException
	{
		return dataSource.getLoginTimeout ();
	}

	@Override
	public void setLogWriter (PrintWriter arg0) throws SQLException
	{
		dataSource.setLogWriter (arg0);
	}

	@Override
	public void setLoginTimeout (int arg0) throws SQLException
	{
		dataSource.setLoginTimeout (arg0);
	}

	@Override
	public boolean isWrapperFor (Class<?> arg0) throws SQLException
	{
		return false;
	}

	@Override
	public <T> T unwrap (Class<T> arg0) throws SQLException
	{
		return null;
	}

	private final Set<MyPooledConnection> pool = Collections.synchronizedSet (new HashSet<MyPooledConnection> ());

	private class MyPooledConnection implements PooledConnection
	{
		PooledConnection delegate;

		public MyPooledConnection (PooledConnection delegate)
		{
			this.delegate = delegate;
			final MyPooledConnection thisConnection = this;
			delegate.addConnectionEventListener (new ConnectionEventListener ()
			{

				@Override
				public void connectionClosed (ConnectionEvent arg0)
				{
					pool.add (thisConnection);
				}

				@Override
				public void connectionErrorOccurred (ConnectionEvent arg0)
				{
				}
			});
		}

		@Override
		public void addConnectionEventListener (ConnectionEventListener listener)
		{
			delegate.addConnectionEventListener (listener);
		}

		@Override
		public void addStatementEventListener (StatementEventListener listener)
		{
			delegate.addStatementEventListener (listener);
		}

		@Override
		public void close () throws SQLException
		{
			delegate.close ();
		}

		@Override
		public Connection getConnection () throws SQLException
		{
			return delegate.getConnection ();
		}

		@Override
		public void removeConnectionEventListener (ConnectionEventListener listener)
		{
			delegate.removeConnectionEventListener (listener);
		}

		@Override
		public void removeStatementEventListener (StatementEventListener listener)
		{
			delegate.removeStatementEventListener (listener);
		}
	}

	@Override
	public Connection getConnection () throws SQLException
	{
		MyPooledConnection c = null;
		synchronized ( pool )
		{
			if ( pool.isEmpty () )
			{
				log.trace ("creating new connection ");
				c = new MyPooledConnection (dataSource.getPooledConnection ());
			}
			else
			{
				c = pool.iterator ().next ();
				pool.remove (c);
			}
		}
		return c.getConnection ();
	}

	@Override
	public Connection getConnection (String arg0, String arg1) throws SQLException
	{
		throw new SQLException ("not implemented");
	}

	public java.util.logging.Logger getParentLogger () throws SQLFeatureNotSupportedException {
		return java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
	}

}
