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
import java.util.concurrent.Exchanger;
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
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BloomFilter.UpdateMode;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class ClientBusAdaptor implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);

	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;

	private String clientId;

	private MessageProducer transactionProducer;
	private MessageProducer blockProducer;
	private MessageProducer blockRequestProducer;
	private MessageProducer blockHeaderRequestProducer;
	private MessageProducer transactionRequestProducer;
	private MessageProducer colorProducer;
	private MessageProducer colorRequestProducer;
	private MessageProducer filterRequestProducer;
	private MessageProducer scanRequestProducer;
	private MessageProducer exactMatchProducer;

	private final Map<String, MessageDispatcher> messageDispatcher = new HashMap<String, MessageDispatcher> ();

	private class MessageDispatcher
	{
		private final Map<Object, MessageListener> wrapperMap = new HashMap<Object, MessageListener> ();

		private final MessageConsumer consumer;
		private TemporaryQueue temporaryQueue;

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
						List<MessageListener> listenerList = new ArrayList<MessageListener> ();
						synchronized ( wrapperMap )
						{
							listenerList.addAll (wrapperMap.values ());
						}
						for ( MessageListener listener : listenerList )
						{
							listener.onMessage (message);
						}
					}
				});
			}
			catch ( JMSException e )
			{
				log.error ("Can not attache message listener ", e);
			}
		}

		public void addListener (Object inner, MessageListener listener)
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

		public TemporaryQueue getTemporaryQueue ()
		{
			return temporaryQueue;
		}

		public void setTemporaryQueue (TemporaryQueue temporaryQueue)
		{
			this.temporaryQueue = temporaryQueue;
		}

	}

	public void setClientId (String clientId)
	{
		this.clientId = clientId;
	}

	public void setConnectionFactory (ConnectionFactory connectionFactory)
	{
		this.connectionFactory = connectionFactory;
	}

	private void addTopicListener (String topic, Object inner, MessageListener listener) throws JMSException
	{
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (topic);
			if ( dispatcher == null )
			{
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
					messageDispatcher.remove (dispatcher);
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
			connection = connectionFactory.createConnection ();
			connection.setClientID (clientId);
			connection.start ();
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);

			transactionProducer = session.createProducer (session.createTopic ("newTransaction"));
			blockProducer = session.createProducer (session.createTopic ("newBlock"));
			blockRequestProducer = session.createProducer (session.createTopic ("blockRequest"));
			blockHeaderRequestProducer = session.createProducer (session.createTopic ("headerRequest"));
			transactionRequestProducer = session.createProducer (session.createTopic ("transactionRequest"));
			colorProducer = session.createProducer (session.createTopic ("newColor"));
			colorRequestProducer = session.createProducer (session.createTopic ("colorRequest"));
			filterRequestProducer = session.createProducer (session.createTopic ("filterRequest"));
			scanRequestProducer = session.createProducer (session.createTopic ("scanRequest"));
			exactMatchProducer = session.createProducer (session.createTopic ("matchRequest"));
		}
		catch ( JMSException e )
		{
			log.error ("Can not create JMS connection", e);
		}
	}

	public void destroy ()
	{
		try
		{
			session.close ();
			connection.close ();
		}
		catch ( JMSException e )
		{
		}
	}

	@Override
	public void scanTransactions (Collection<byte[]> match, UpdateMode mode, long after, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			BytesMessage m = session.createBytesMessage ();

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

	}

	@Override
	public void registerFilteredListener (final BloomFilter filter, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			BytesMessage m = compileFilterFeedRequest (filter, listener);

			filterRequestProducer.send (m);
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void scanTransactions (BloomFilter filter, TransactionListener listener) throws BCSAPIException
	{
		try
		{
			BytesMessage m = compileFilterFeedRequest (filter, listener);

			scanRequestProducer.send (m);
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	private BytesMessage compileFilterFeedRequest (final BloomFilter filter, final TransactionListener listener) throws JMSException
	{
		BytesMessage m = session.createBytesMessage ();
		BCSAPIMessage.FilterRequest.Builder builder = BCSAPIMessage.FilterRequest.newBuilder ();
		builder.setBcsapiversion (1);
		builder.setFilter (ByteString.copyFrom (filter.getFilter ()));
		builder.setHashFunctions ((int) filter.getHashFunctions ());
		builder.setTweak ((int) filter.getTweak ());
		builder.setMode (filter.getUpdateMode ().ordinal ());

		m.writeBytes (builder.build ().toByteArray ());
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (filter.toString ());
			if ( dispatcher == null )
			{
				TemporaryQueue answerQueue = session.createTemporaryQueue ();
				MessageConsumer consumer = session.createConsumer (answerQueue);
				dispatcher = new MessageDispatcher (consumer);
				dispatcher.setTemporaryQueue (answerQueue);
				messageDispatcher.put (filter.toString (), dispatcher);
				dispatcher.addListener (listener, new MessageListener ()
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
								listener.process (null);
							}
						}
						catch ( JMSException e )
						{
							log.error ("Malformed message received for filter", e);
						}
						catch ( InvalidProtocolBufferException e )
						{
							log.error ("Malformed message received for filter", e);
						}
					}
				});
			}
			m.setJMSCorrelationID (listener.toString ());
			m.setJMSReplyTo (dispatcher.getTemporaryQueue ());
		}
		return m;
	}

	@Override
	public void removeFilteredListener (BloomFilter filter, TransactionListener listener)
	{
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (filter.toString ());
			if ( dispatcher != null )
			{
				dispatcher.removeListener (listener);
				if ( !dispatcher.isListened () )
				{
					messageDispatcher.remove (filter.toString ());
					try
					{
						dispatcher.getConsumer ().close ();
						dispatcher.getTemporaryQueue ().delete ();
					}
					catch ( JMSException e )
					{
					}
				}
			}
		}
	}

	@Override
	public void registerTransactionListener (final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("transaction", listener, new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					BytesMessage o = (BytesMessage) arg0;
					try
					{
						byte[] body = new byte[(int) o.getBodyLength ()];
						o.readBytes (body);
						Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
						t.computeHash ();
						listener.process (t);
					}
					catch ( Exception e )
					{
						log.error ("Transaction message error", e);
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
			addTopicListener ("trunk", listener, new MessageListener ()
			{
				@Override
				public void onMessage (Message message)
				{
					try
					{
						BytesMessage m = (BytesMessage) message;
						byte[] body = new byte[(int) m.getBodyLength ()];
						m.readBytes (body);
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
						log.error ("Block message error", e);
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

	private byte[] synchronousRequest (MessageProducer producer, Message m) throws BCSAPIException
	{
		TemporaryQueue answerQueue = null;
		try
		{
			try
			{
				answerQueue = session.createTemporaryQueue ();
			}
			catch ( JMSException e )
			{
				return null;
			}
			return synchronousRequest (producer, m, answerQueue);
		}
		finally
		{
			try
			{
				answerQueue.delete ();
			}
			catch ( JMSException e )
			{
			}
		}
	}

	private byte[] synchronousRequest (MessageProducer producer, Message m, Queue answerQueue) throws BCSAPIException
	{
		byte[] result = null;
		final Exchanger<byte[]> exchanger = new Exchanger<byte[]> ();

		MessageConsumer consumer = null;

		try
		{
			m.setJMSReplyTo (answerQueue);
			consumer = session.createConsumer (answerQueue);
			consumer.setMessageListener (new MessageListener ()
			{
				@Override
				public void onMessage (Message message)
				{
					BytesMessage m = (BytesMessage) message;
					byte[] body;
					try
					{
						if ( m.getBodyLength () == 0 )
						{
							try
							{
								exchanger.exchange (null);
							}
							catch ( InterruptedException e )
							{
							}
						}
						else
						{
							body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							try
							{
								exchanger.exchange (body);
							}
							catch ( InterruptedException e )
							{
							}
						}
					}
					catch ( JMSException e )
					{
						log.error ("Can not parse reply", e);
					}
				}
			});
			producer.send (m);
			try
			{
				result = exchanger.exchange (null);
				consumer.close ();
			}
			catch ( InterruptedException e )
			{
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
			}
			catch ( JMSException e )
			{
			}
		}
		return result;
	}

	@Override
	public Transaction getTransaction (String hash) throws BCSAPIException
	{
		log.trace ("get transaction " + hash);
		BytesMessage m;
		try
		{
			m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (transactionRequestProducer, m);
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
		return null;
	}

	@Override
	public Block getBlock (String hash) throws BCSAPIException
	{
		try
		{
			log.trace ("get block " + hash);
			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (blockRequestProducer, m);
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
		return null;
	}

	@Override
	public Block getBlockHeader (String hash) throws BCSAPIException
	{
		try
		{
			log.trace ("get block header" + hash);
			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (blockHeaderRequestProducer, m);
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
		return null;
	}

	@Override
	public void sendTransaction (Transaction transaction) throws BCSAPIException
	{
		try
		{
			transaction.computeHash ();
			log.trace ("send transaction " + transaction.getHash ());
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			byte[] reply = synchronousRequest (transactionProducer, m);
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
	}

	@Override
	public void sendBlock (Block block) throws BCSAPIException
	{
		try
		{
			block.computeHash ();
			log.trace ("send block " + block.getHash ());
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (block.toProtobuf ().toByteArray ());
			byte[] reply = synchronousRequest (blockProducer, m);
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
	}

	@Override
	public void issueColor (Color color) throws BCSAPIException
	{
		try
		{
			log.trace ("issue color " + color.getTerms ());
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (color.toProtobuf ().toByteArray ());
			byte[] reply = synchronousRequest (colorProducer, m);
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
	}

	@Override
	public Color getColor (String digest) throws BCSAPIException
	{
		log.trace ("get color " + digest);
		BytesMessage m;
		try
		{
			m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (digest).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (colorRequestProducer, m);
			if ( response != null )
			{
				Color c;
				c = Color.fromProtobuf (BCSAPIMessage.Color.parseFrom (response));
				return c;
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
		return null;
	}

	@Override
	public Wallet getWallet (String fileName, String passphrase) throws BCSAPIException
	{
		SerializedWallet wallet = SerializedWallet.read (fileName, passphrase);
		wallet.setApi (this);
		return wallet;
	}

}
