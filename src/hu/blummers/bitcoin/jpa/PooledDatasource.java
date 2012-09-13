package hu.blummers.bitcoin.jpa;

import hu.blummers.bitcoin.core.Chain;

import java.io.PrintWriter;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PooledDatasource implements DataSource {
	private static final Logger log = LoggerFactory.getLogger(PooledDatasource.class);
	private ConnectionPoolDataSource dataSource;
	
	public ConnectionPoolDataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(ConnectionPoolDataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return dataSource.getLogWriter();
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return dataSource.getLoginTimeout();
	}

	@Override
	public void setLogWriter(PrintWriter arg0) throws SQLException {
		dataSource.setLogWriter(arg0);
	}

	@Override
	public void setLoginTimeout(int arg0) throws SQLException {
		dataSource.setLoginTimeout(arg0);
	}

	@Override
	public boolean isWrapperFor(Class<?> arg0) throws SQLException {
		return false;
	}

	@Override
	public <T> T unwrap(Class<T> arg0) throws SQLException {
		return null;
	}
	
	private Set<MyPooledConnection> pool = Collections.synchronizedSet(new HashSet<MyPooledConnection> ());
	
	private class MyPooledConnection implements PooledConnection
	{
		PooledConnection delegate;
		
		public MyPooledConnection (PooledConnection delegate)
		{
			this.delegate = delegate;
			final MyPooledConnection thisConnection = this;
			delegate.addConnectionEventListener (new ConnectionEventListener (){

				@Override
				public void connectionClosed(ConnectionEvent arg0) {
					pool.add(thisConnection);
				}

				@Override
				public void connectionErrorOccurred(ConnectionEvent arg0) {
				}});
		}

		@Override
		public void addConnectionEventListener(ConnectionEventListener listener) {
			delegate.addConnectionEventListener(listener);
		}

		@Override
		public void addStatementEventListener(StatementEventListener listener) {
			delegate.addStatementEventListener(listener);
		}

		@Override
		public void close() throws SQLException {
			delegate.close();
		}
		
		@Override
		public Connection getConnection() throws SQLException {
			return delegate.getConnection();
		}

		@Override
		public void removeConnectionEventListener(ConnectionEventListener listener) {
			delegate.removeConnectionEventListener(listener);
		}

		@Override
		public void removeStatementEventListener(StatementEventListener listener) {
			delegate.removeStatementEventListener(listener);
		}		
	}
	
	@Override
	public Connection getConnection() throws SQLException {
		MyPooledConnection c = null;
		synchronized ( pool )
		{
			if ( pool.isEmpty() )
			{
				log.info("creating new connection ");
				c = new MyPooledConnection (dataSource.getPooledConnection());				
			}
			else
			{
				c = pool.iterator().next();
				pool.remove(c);
			}
		}
		return c.getConnection();
	}

	@Override
	public Connection getConnection(String arg0, String arg1) throws SQLException {
		throw new SQLException ("not implemented");
	}

}
