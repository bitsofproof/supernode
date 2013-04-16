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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Exchanger;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class ClientBusAdaptor implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);

	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;

	private String clientId;

	private MessageProducer inventoryProducer;
	private MessageProducer transactionProducer;
	private MessageProducer blockProducer;
	private MessageProducer blockRequestProducer;
	private MessageProducer transactionRequestProducer;
	private MessageProducer accountRequestProducer;
	private MessageProducer colorProducer;
	private MessageProducer colorRequestProducer;
	private MessageProducer filterRequestProducer;

	private final Map<String, MessageDispatcher> messageDispatcher = Collections.synchronizedMap (new HashMap<String, MessageDispatcher> ());

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
						for ( MessageListener listener : wrapperMap.values () )
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
			wrapperMap.put (inner, listener);
		}

		public void removeListener (Object inner)
		{
			wrapperMap.remove (inner);
		}

		public boolean isListened ()
		{
			return !wrapperMap.isEmpty ();
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

			inventoryProducer = session.createProducer (session.createTopic ("inventory"));
			transactionProducer = session.createProducer (session.createTopic ("newTransaction"));
			blockProducer = session.createProducer (session.createTopic ("newBlock"));
			blockRequestProducer = session.createProducer (session.createTopic ("blockRequest"));
			transactionRequestProducer = session.createProducer (session.createTopic ("transactionRequest"));
			accountRequestProducer = session.createProducer (session.createTopic ("accountRequest"));
			colorProducer = session.createProducer (session.createTopic ("newColor"));
			colorRequestProducer = session.createProducer (session.createTopic ("colorRequest"));
			filterRequestProducer = session.createProducer (session.createTopic ("filterRequest"));
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
	public void registerFilteredListener (BloomFilter filter, final TransactionListener listener) throws BCSAPIException
	{
		BCSAPIMessage.FilterRequest.Builder builder = BCSAPIMessage.FilterRequest.newBuilder ();
		builder.setFilter (ByteString.copyFrom (filter.getFilter ()));
		builder.setHashFunctions ((int) filter.getHashFunctions ());
		builder.setTweak ((int) filter.getTweak ());
		builder.setMode (filter.getUpdateMode ().ordinal ());

		try
		{
			log.trace ("register bloom filter");
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (builder.build ().toByteArray ());
			synchronized ( messageDispatcher )
			{
				MessageDispatcher dispatcher = messageDispatcher.get (filter.getNonUniqueName ());
				if ( dispatcher == null )
				{
					TemporaryQueue answerQueue = session.createTemporaryQueue ();
					MessageConsumer consumer = session.createConsumer (answerQueue);
					dispatcher = new MessageDispatcher (consumer);
					dispatcher.setTemporaryQueue (answerQueue);
					messageDispatcher.put (filter.getNonUniqueName (), dispatcher);
				}
				m.setJMSReplyTo (dispatcher.getTemporaryQueue ());
				dispatcher.addListener (listener, new MessageListener ()
				{
					@Override
					public void onMessage (Message message)
					{
						BytesMessage m = (BytesMessage) message;
						byte[] body;
						try
						{
							body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
							t.computeHash ();
							listener.process (t);
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
			filterRequestProducer.send (m);
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void removeFilteredListener (BloomFilter filter, TransactionListener listener)
	{
		synchronized ( messageDispatcher )
		{
			MessageDispatcher dispatcher = messageDispatcher.get (filter.getNonUniqueName ());
			if ( dispatcher != null )
			{
				dispatcher.removeListener (listener);
				if ( !dispatcher.isListened () )
				{
					messageDispatcher.remove (filter.getNonUniqueName ());
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
	public AccountStatement getAccountStatement (Collection<String> addresses, long from) throws BCSAPIException
	{
		try
		{
			log.trace ("get account statement");
			Queue replyQueue = session.createTemporaryQueue ();

			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.AccountRequest.Builder ab = BCSAPIMessage.AccountRequest.newBuilder ();
			ab.setBcsapiversion (1);
			for ( String a : addresses )
			{
				ab.addAddress (a);
			}
			ab.setFrom ((int) from);
			m.writeBytes (ab.build ().toByteArray ());
			byte[] response = synchronousRequest (accountRequestProducer, m, replyQueue);
			if ( response != null )
			{
				AccountStatement s = AccountStatement.fromProtobuf (BCSAPIMessage.AccountStatement.parseFrom (response));
				if ( s.getUnconfirmedSpend () != null )
				{
					for ( Transaction t : s.getUnconfirmedSpend () )
					{
						t.computeHash ();
					}
				}
				if ( s.getUnconfirmedReceive () != null )
				{
					for ( Transaction t : s.getUnconfirmedReceive () )
					{
						t.computeHash ();
					}
				}
				return s;
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
	public void registerAddressListener (Collection<String> addresses, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			for ( final String a : addresses )
			{
				String topic = "address" + a.substring (a.length () - 2, a.length ());
				addTopicListener (topic, listener, new MessageListener ()
				{
					@Override
					public void onMessage (Message message)
					{
						BytesMessage m = (BytesMessage) message;
						try
						{
							byte[] body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
							t.computeHash ();
							for ( TransactionOutput o : t.getOutputs () )
							{
								if ( o.getAddresses ().contains (a) )
								{
									listener.process (t);
								}
							}
						}
						catch ( Exception e )
						{
							log.error ("Transaction message error", e);
						}
					}
				});
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void registerOutputListener (Collection<String> hashes, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			for ( final String h : hashes )
			{
				String topic = "output" + h.substring (h.length () - 3, h.length ());
				addTopicListener (topic, listener, new MessageListener ()
				{
					@Override
					public void onMessage (Message message)
					{
						BytesMessage m = (BytesMessage) message;
						try
						{
							byte[] body = new byte[(int) m.getBodyLength ()];
							m.readBytes (body);
							Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
							t.computeHash ();
							for ( TransactionInput i : t.getInputs () )
							{
								if ( i.getSourceHash ().equals (h) )
								{
									listener.process (t);
								}
							}
						}
						catch ( Exception e )
						{
							log.error ("Transaction message error", e);
						}
					}
				});
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void removeFilteredListener (Collection<String> filter, TransactionListener listener)
	{
		for ( String s : filter )
		{
			removeTopicListener ("output" + s.substring (s.length () - 3, s.length ()), listener);
			removeTopicListener ("address" + s.substring (s.length () - 2, s.length ()), listener);
		}
	}

	@Override
	public List<String> getBlocks () throws BCSAPIException
	{
		try
		{
			log.trace ("get blocks ");
			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (Hash.ZERO_HASH.toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (inventoryProducer, m);
			if ( response != null )
			{
				List<String> chain = new ArrayList<String> ();
				BCSAPIMessage.Hash b = BCSAPIMessage.Hash.parseFrom (response);
				for ( ByteString bs : b.getHashList () )
				{
					chain.add (new Hash (bs.toByteArray ()).toString ());
				}
				return chain;
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
	public KeyGenerator createKeyGenerator (int size, int addressFlag) throws BCSAPIException
	{
		try
		{
			return DefaultKeyGenerator.createKeyGenerator (size, addressFlag);
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public KeyGenerator getKeyGenerator (ExtendedKey master, int nextKeySequence, int addressFlag) throws BCSAPIException
	{
		try
		{
			return new DefaultKeyGenerator (master, nextKeySequence, addressFlag);
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public AccountManager createAccountManager (KeyGenerator generator)
	{
		DefaultAccountManager manager = new DefaultAccountManager ();
		manager.setApi (this);
		manager.track (generator);
		return manager;
	}

}
