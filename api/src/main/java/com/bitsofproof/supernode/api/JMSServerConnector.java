/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Hash;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class JMSServerConnector implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (JMSServerConnector.class);

	private ConnectionFactory connectionFactory;
	private Connection connection;

	private Boolean production = null;

	private final Map<String, MessageDispatcher> messageDispatcher = new HashMap<String, MessageDispatcher> ();

	private long timeout = 10 * 60 * 1000; // 2 min

	public void setTimeout (long timeout)
	{
		this.timeout = timeout;
	}

	private interface ByteArrayMessageListener
	{
		public void onMessage (byte[] array);
	}

	private class MessageDispatcher
	{
		private final Map<Object, ByteArrayMessageListener> wrapperMap = new HashMap<Object, ByteArrayMessageListener> ();

		private final MessageConsumer consumer;

		public MessageDispatcher (MessageConsumer consumer)
		{
			this.consumer = consumer;
			try
			{
				consumer.setMessageListener (new MessageListener ()
				{
					@Override
					public void onMessage (Message message)
					{
						List<ByteArrayMessageListener> listenerList = new ArrayList<ByteArrayMessageListener> ();
						synchronized ( wrapperMap )
						{
							listenerList.addAll (wrapperMap.values ());
						}
						BytesMessage m = (BytesMessage) message;
						byte[] body;
						try
						{
							if ( m.getBodyLength () > 0 )
							{
								body = new byte[(int) m.getBodyLength ()];
								m.readBytes (body);
								for ( ByteArrayMessageListener listener : listenerList )
								{
									listener.onMessage (body);
								}
							}
							else
							{
								for ( ByteArrayMessageListener listener : listenerList )
								{
									listener.onMessage (null);
								}
							}
						}
						catch ( JMSException e )
						{
							log.error ("JMS Error ", e);
						}
					}
				});
			}
			catch ( JMSException e )
			{
				log.error ("Can not attache message listener ", e);
			}
		}

		public void addListener (Object inner, ByteArrayMessageListener listener)
		{
			synchronized ( wrapperMap )
			{
				wrapperMap.put (inner, listener);
			}
		}

		public void removeListener (Object inner)
		{
			synchronized ( wrapperMap )
			{
				wrapperMap.remove (inner);
			}
		}

		public boolean isListened ()
		{
			synchronized ( wrapperMap )
			{
				return !wrapperMap.isEmpty ();
			}
		}

		public MessageConsumer getConsumer ()
		{
			return consumer;
		}
	}

	public void setConnectionFactory (ConnectionFactory connectionFactory)
	{
		this.connectionFactory = connectionFactory;
	}

	private void addTopicListener (String topic, Object inner, ByteArrayMessageListener listener) throws JMSException
	{
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (topic);
			if ( dispatcher == null )
			{
				Session session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
				Destination destination = session.createTopic (topic);
				MessageConsumer consumer = session.createConsumer (destination);
				messageDispatcher.put (topic, dispatcher = new MessageDispatcher (consumer));
			}
			dispatcher.addListener (inner, listener);
		}
	}

	private void removeTopicListener (String topic, Object inner)
	{
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (topic);
			if ( dispatcher != null )
			{
				dispatcher.removeListener (inner);
				if ( !dispatcher.isListened () )
				{
					messageDispatcher.remove (topic);
					try
					{
						dispatcher.getConsumer ().close ();
					}
					catch ( JMSException e )
					{
					}
				}
			}
		}
	}

	public void init ()
	{
		try
		{
			log.debug ("Initialize BCSAPI Bus adaptor");
			connection = connectionFactory.createConnection ();
			connection.start ();
		}
		catch ( Exception e )
		{
			log.error ("Can not create JMS connection", e);
		}
	}

	public void destroy ()
	{
		try
		{
			connection.close ();
		}
		catch ( JMSException e )
		{
		}
	}

	@Override
	public long ping (long nonce) throws BCSAPIException
	{
		Session session = null;
		try
		{
			log.trace ("ping " + nonce);

			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Ping.Builder builder = BCSAPIMessage.Ping.newBuilder ();
			builder.setBcsapiversion (1);
			builder.setNonce (nonce);
			m.writeBytes (builder.build ().toByteArray ());
			MessageProducer pingProducer = session.createProducer (session.createQueue ("ping"));
			byte[] response = synchronousRequest (session, pingProducer, m);
			if ( response != null )
			{
				BCSAPIMessage.Ping echo = BCSAPIMessage.Ping.parseFrom (response);
				if ( echo.getNonce () != nonce )
				{
					throw new BCSAPIException ("Incorrect echo nonce from ping");
				}
				return echo.getNonce ();
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
		return 0;
	}

	@Override
	public void addAlertListener (final AlertListener alertListener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("alert", alertListener, new ByteArrayMessageListener ()
			{
				@Override
				public void onMessage (byte[] body)
				{
					BCSAPIMessage.Alert alert;
					try
					{
						alert = BCSAPIMessage.Alert.parseFrom (body);
						alertListener.alert (alert.getAlert (), alert.getSeverity ());
					}
					catch ( InvalidProtocolBufferException e )
					{
						log.error ("Message format error", e);
					}
				}
			});
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void removeAlertListener (AlertListener listener)
	{
		removeTopicListener ("alert", listener);
	}

	@Override
	public boolean isProduction () throws BCSAPIException
	{
		if ( production != null )
		{
			return production;
		}
		return production = getBlockHeader ("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f") != null;
	}

	@Override
	public void scanTransactionsForAddresses (Set<Address> addresses, UpdateMode mode, long after, TransactionListener listener) throws BCSAPIException
	{
		List<byte[]> al = new ArrayList<> (addresses.size ());
		for ( Address a : addresses )
		{
			al.add (a.toByteArray ());
		}
		scanTransactions (al, mode, after, listener);
	}

	@Override
	public void scanUTXOForAddresses (Set<Address> addresses, UpdateMode mode, long after, TransactionListener listener) throws BCSAPIException
	{
		List<byte[]> al = new ArrayList<> (addresses.size ());
		for ( Address a : addresses )
		{
			al.add (a.toByteArray ());
		}
		scanUTXO (al, mode, after, listener);
	}

	public void scanUTXO (Collection<byte[]> match, UpdateMode mode, long after, TransactionListener listener) throws BCSAPIException
	{
		scanRequest (match, mode, after, listener, "utxoMatchRequest");
	}

	@Override
	public void scanUTXO (ExtendedKey master, int firstIndex, int lookAhead, long after, TransactionListener listener) throws BCSAPIException
	{
		scanRequest (master, firstIndex, lookAhead, after, listener, "utxoAccountRequest");
	}

	public void scanTransactions (Collection<byte[]> match, UpdateMode mode, long after, final TransactionListener listener) throws BCSAPIException
	{
		scanRequest (match, mode, after, listener, "matchRequest");
	}

	@Override
	public void scanTransactions (ExtendedKey master, int firstIndex, int lookAhead, long after, final TransactionListener listener) throws BCSAPIException
	{
		scanRequest (master, firstIndex, lookAhead, after, listener, "accountRequest");
	}

	private void scanRequest (Collection<byte[]> match, UpdateMode mode, long after, final TransactionListener listener, String requestQueue)
			throws BCSAPIException
	{
		Session session = null;
		try
		{
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			BytesMessage m = session.createBytesMessage ();

			MessageProducer exactMatchProducer = session.createProducer (session.createQueue (requestQueue));
			BCSAPIMessage.ExactMatchRequest.Builder builder = BCSAPIMessage.ExactMatchRequest.newBuilder ();
			builder.setBcsapiversion (1);
			builder.setMode (mode.ordinal ());
			for ( byte[] d : match )
			{
				builder.addMatch (ByteString.copyFrom (d));
			}
			if ( after != 0 )
			{
				builder.setAfter (after);
			}
			m.writeBytes (builder.build ().toByteArray ());
			final TemporaryQueue answerQueue = session.createTemporaryQueue ();
			final MessageConsumer consumer = session.createConsumer (answerQueue);
			m.setJMSReplyTo (answerQueue);
			final Semaphore ready = new Semaphore (0);
			consumer.setMessageListener (new MessageListener ()
			{
				@Override
				public void onMessage (Message message)
				{
					BytesMessage m = (BytesMessage) message;
					byte[] body;
					try
					{
						if ( m.getBodyLength () > 0 )
						{
							body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
							t.computeHash ();
							listener.process (t);
						}
						else
						{
							consumer.close ();
							answerQueue.delete ();
							ready.release ();
						}
					}
					catch ( JMSException e )
					{
						log.error ("Malformed message received for scan matching transactions", e);
					}
					catch ( InvalidProtocolBufferException e )
					{
						log.error ("Malformed message received for scan matching transactions", e);
					}
				}
			});

			exactMatchProducer.send (m);
			ready.acquireUninterruptibly ();
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	private void scanRequest (ExtendedKey master, int firstIndex, int lookAhead, long after, final TransactionListener listener, String request)
			throws BCSAPIException
	{
		if ( !master.isReadOnly () )
		{
			master = master.getReadOnly ();
		}
		Session session = null;
		try
		{
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			BytesMessage m = session.createBytesMessage ();

			MessageProducer scanAccountProducer = session.createProducer (session.createQueue (request));
			BCSAPIMessage.AccountRequest.Builder builder = BCSAPIMessage.AccountRequest.newBuilder ();
			builder.setBcsapiversion (1);
			builder.setPublicKey (master.serialize (isProduction ()));
			builder.setLookAhead (lookAhead);
			builder.setAfter (after);
			m.writeBytes (builder.build ().toByteArray ());

			final TemporaryQueue answerQueue = session.createTemporaryQueue ();
			final MessageConsumer consumer = session.createConsumer (answerQueue);
			m.setJMSReplyTo (answerQueue);
			final Semaphore ready = new Semaphore (0);
			consumer.setMessageListener (new MessageListener ()
			{
				@Override
				public void onMessage (Message message)
				{
					BytesMessage m = (BytesMessage) message;
					byte[] body;
					try
					{
						if ( m.getBodyLength () > 0 )
						{
							body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
							t.computeHash ();
							listener.process (t);
						}
						else
						{
							consumer.close ();
							answerQueue.delete ();
							ready.release ();
						}
					}
					catch ( JMSException e )
					{
						log.error ("Malformed message received for account scan transactions", e);
					}
					catch ( InvalidProtocolBufferException e )
					{
						log.error ("Malformed message received for account scan transactions", e);
					}
				}
			});

			scanAccountProducer.send (m);
			ready.acquireUninterruptibly ();
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	@Override
	public void catchUp (String hash, final TrunkListener listener) throws BCSAPIException
	{
		log.trace ("catchUp from " + hash);
		BytesMessage m;
		Session session = null;
		try
		{
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer transactionRequestProducer = session.createProducer (session.createQueue ("catchUpRequest"));

			m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (session, transactionRequestProducer, m);
			if ( response != null )
			{
				TrunkUpdateMessage tu = TrunkUpdateMessage.fromProtobuf (BCSAPIMessage.TrunkUpdate.parseFrom (response));
				if ( tu.getRemoved () != null )
				{
					for ( Block b : tu.getRemoved () )
					{
						b.computeHash ();
					}
				}
				if ( tu.getAdded () != null )
				{
					for ( Block b : tu.getAdded () )
					{
						b.computeHash ();
					}
				}
				listener.trunkUpdate (tu.getRemoved (), tu.getAdded ());
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	@Override
	public void registerTransactionListener (final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("transaction", listener, new ByteArrayMessageListener ()
			{
				@Override
				public void onMessage (byte[] body)
				{
					try
					{
						Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
						t.computeHash ();
						listener.process (t);
					}
					catch ( Exception e )
					{
						log.debug ("Transaction message error", e);
					}
				}
			});
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void removeTransactionListener (TransactionListener listener)
	{
		removeTopicListener ("transaction", listener);
	}

	@Override
	public void registerTrunkListener (final TrunkListener listener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("trunk", listener, new ByteArrayMessageListener ()
			{
				@Override
				public void onMessage (byte[] body)
				{
					try
					{
						TrunkUpdateMessage tu = TrunkUpdateMessage.fromProtobuf (BCSAPIMessage.TrunkUpdate.parseFrom (body));
						if ( tu.getRemoved () != null )
						{
							for ( Block b : tu.getRemoved () )
							{
								b.computeHash ();
							}
						}
						if ( tu.getAdded () != null )
						{
							for ( Block b : tu.getAdded () )
							{
								b.computeHash ();
							}
						}
						listener.trunkUpdate (tu.getRemoved (), tu.getAdded ());
					}
					catch ( Exception e )
					{
						log.debug ("Block message error", e);
					}
				}
			});
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void removeTrunkListener (TrunkListener listener)
	{
		removeTopicListener ("trunk", listener);
	}

	private byte[] synchronousRequest (Session session, MessageProducer producer, Message m) throws BCSAPIException
	{
		TemporaryQueue answerQueue = null;
		MessageConsumer consumer = null;
		try
		{
			answerQueue = session.createTemporaryQueue ();
			m.setJMSReplyTo (answerQueue);
			consumer = session.createConsumer (answerQueue);
			producer.send (m);
			BytesMessage reply = (BytesMessage) consumer.receive (timeout);
			if ( reply == null )
			{
				throw new BCSAPIException ("timeout");
			}
			if ( reply.getBodyLength () == 0 )
			{
				return null;
			}
			else
			{
				byte[] body = new byte[(int) reply.getBodyLength ()];
				reply.readBytes (body);
				return body;
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				if ( consumer != null )
				{
					consumer.close ();
				}
				answerQueue.delete ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	@Override
	public Transaction getTransaction (String hash) throws BCSAPIException
	{
		log.trace ("get transaction " + hash);
		BytesMessage m;
		Session session = null;
		try
		{
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer transactionRequestProducer = session.createProducer (session.createQueue ("transactionRequest"));

			m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (session, transactionRequestProducer, m);
			if ( response != null )
			{
				Transaction t;
				t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (response));
				t.computeHash ();
				return t;
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
		return null;
	}

	@Override
	public Block getBlock (String hash) throws BCSAPIException
	{
		Session session = null;
		try
		{
			log.trace ("get block " + hash);

			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer blockRequestProducer = session.createProducer (session.createQueue ("blockRequest"));

			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (session, blockRequestProducer, m);
			if ( response != null )
			{
				Block b = Block.fromProtobuf (BCSAPIMessage.Block.parseFrom (response));
				b.computeHash ();
				return b;
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
		return null;
	}

	@Override
	public Block getBlockHeader (String hash) throws BCSAPIException
	{
		Session session = null;
		try
		{
			log.trace ("get block header" + hash);

			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer blockHeaderRequestProducer = session.createProducer (session.createQueue ("headerRequest"));

			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (session, blockHeaderRequestProducer, m);
			if ( response != null )
			{
				Block b = Block.fromProtobuf (BCSAPIMessage.Block.parseFrom (response));
				b.computeHash ();
				return b;
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
		return null;
	}

	@Override
	public void sendTransaction (Transaction transaction) throws BCSAPIException
	{
		Session session = null;
		try
		{
			transaction.computeHash ();
			log.trace ("send transaction " + transaction.getHash ());

			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer transactionProducer = session.createProducer (session.createTopic ("newTransaction"));

			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			byte[] reply = synchronousRequest (session, transactionProducer, m);
			if ( reply != null )
			{
				try
				{
					BCSAPIMessage.ExceptionMessage em = BCSAPIMessage.ExceptionMessage.parseFrom (reply);
					throw new BCSAPIException (em.getMessage (0));
				}
				catch ( InvalidProtocolBufferException e )
				{
					throw new BCSAPIException ("Invalid response", e);
				}
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	@Override
	public void sendBlock (Block block) throws BCSAPIException
	{
		Session session = null;
		try
		{
			block.computeHash ();
			log.trace ("send block " + block.getHash ());
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer blockProducer = session.createProducer (session.createTopic ("newBlock"));

			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (block.toProtobuf ().toByteArray ());
			byte[] reply = synchronousRequest (session, blockProducer, m);
			if ( reply != null )
			{
				try
				{
					BCSAPIMessage.ExceptionMessage em = BCSAPIMessage.ExceptionMessage.parseFrom (reply);
					throw new BCSAPIException (em.getMessage (0));
				}
				catch ( InvalidProtocolBufferException e )
				{
					throw new BCSAPIException ("Invalid response", e);
				}
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		finally
		{
			try
			{
				session.close ();
			}
			catch ( JMSException e )
			{
			}
		}
	}
}
