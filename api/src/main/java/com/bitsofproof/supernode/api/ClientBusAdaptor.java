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
package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class ClientBusAdaptor implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);

	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;

	private String clientId;

	private final List<TransactionListener> transactionListener = Collections.synchronizedList (new ArrayList<TransactionListener> ());
	private final List<TrunkListener> trunkListener = Collections.synchronizedList (new ArrayList<TrunkListener> ());
	private final List<TemplateListener> blockTemplateListener = Collections.synchronizedList (new ArrayList<TemplateListener> ());

	private MessageProducer inventoryProducer;
	private MessageProducer transactionProducer;
	private MessageProducer blockProducer;
	private MessageProducer blockRequestProducer;
	private MessageProducer transactionRequestProducer;
	private MessageProducer accountRequestProducer;

	private final Map<String, MessageConsumer> filterConsumer = new HashMap<String, MessageConsumer> ();
	private final Set<String> listenAddresses = new HashSet<String> ();
	private final Set<String> listenTransactions = new HashSet<String> ();
	private final Map<String, Integer> monitorConfirmations = new HashMap<String, Integer> ();
	private Map<String, Integer> blocks = null;

	public void setClientId (String clientId)
	{
		this.clientId = clientId;
	}

	public void setConnectionFactory (ConnectionFactory connectionFactory)
	{
		this.connectionFactory = connectionFactory;
	}

	private void addTopicListener (String topic, MessageListener listener) throws JMSException
	{
		Destination destination = session.createTopic (topic);
		MessageConsumer consumer = session.createConsumer (destination);
		consumer.setMessageListener (listener);
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
	public void registerTransactionListener (TransactionListener listener) throws JMSException
	{
		addTopicListener ("transaction", new MessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				for ( TransactionListener l : transactionListener )
				{
					try
					{
						byte[] body = new byte[(int) o.getBodyLength ()];
						o.readBytes (body);
						l.validated (Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body)));
					}
					catch ( Exception e )
					{
						log.error ("Transaction message error", e);
					}
				}
			}
		});
		transactionListener.add (listener);
	}

	@Override
	public void registerTrunkListener (TrunkListener listener) throws JMSException
	{
		addTopicListener ("trunk", new MessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				try
				{
					BytesMessage m = (BytesMessage) arg0;
					byte[] body = new byte[(int) m.getBodyLength ()];
					m.readBytes (body);
					TrunkUpdateMessage tu = TrunkUpdateMessage.fromProtobuf (BCSAPIMessage.TrunkUpdate.parseFrom (body));
					for ( TrunkListener l : trunkListener )
					{
						l.trunkUpdate (tu.getRemoved (), tu.getAdded ());
					}
				}
				catch ( Exception e )
				{
					log.error ("Block message error", e);
				}
			}
		});
		trunkListener.add (listener);
	}

	private byte[] synchronousRequest (MessageProducer producer, Message m)
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

	private byte[] synchronousRequest (MessageProducer producer, Message m, Queue answerQueue)
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
						log.trace ("Can not parse reply", e);
					}
				}
			});
			producer.send (m);
			try
			{
				result = exchanger.exchange (null);
			}
			catch ( InterruptedException e )
			{
			}
		}
		catch ( JMSException e )
		{
			log.error ("Can not send request", e);
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
	public Transaction getTransaction (String hash)
	{
		try
		{
			log.trace ("get transaction " + hash);
			BytesMessage m = session.createBytesMessage ();
			BCSAPIMessage.Hash.Builder builder = BCSAPIMessage.Hash.newBuilder ();
			builder.setBcsapiversion (1);
			builder.addHash (ByteString.copyFrom (new Hash (hash).toByteArray ()));
			m.writeBytes (builder.build ().toByteArray ());
			byte[] response = synchronousRequest (transactionRequestProducer, m);
			if ( response != null )
			{
				Transaction t = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (response));
				t.computeHash ();
				return t;
			}
		}
		catch ( Exception e )
		{
			log.error ("Can not get transaction", e);
		}
		return null;
	}

	@Override
	public Block getBlock (String hash)
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
		catch ( Exception e )
		{
			log.error ("Can not get block", e);
		}
		return null;
	}

	@Override
	public AccountStatement getAccountStatement (List<String> addresses, long from)
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
				return AccountStatement.fromProtobuf (BCSAPIMessage.AccountStatement.parseFrom (response));
			}
		}
		catch ( Exception e )
		{
			log.error ("Can not register account", e);
		}
		return null;
	}

	@Override
	public void sendTransaction (Transaction transaction)
	{
		transaction.computeHash ();
		log.trace ("send transaction " + transaction.getHash ());
		try
		{
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			transactionProducer.send (m);
		}
		catch ( Exception e )
		{
			log.error ("Can not send transaction", e);
		}
	}

	@Override
	public void sendBlock (Block block)
	{
		block.computeHash ();
		log.trace ("send block " + block.getHash ());
		try
		{
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (block.toProtobuf ().toByteArray ());
			blockProducer.send (m);
		}
		catch ( JMSException e )
		{
			log.error ("Can not send transaction", e);
		}
	}

	@Override
	public void registerAddressListener (List<String> addresses, final TransactionListener listener)
	{
		try
		{
			for ( String a : addresses )
			{
				listenAddresses.add (a);
				String hash = a.substring (a.length () - 2, a.length ());

				MessageConsumer consumer = filterConsumer.get (hash);
				if ( consumer == null )
				{
					consumer = session.createConsumer (session.createTopic ("filter" + hash));
					consumer.setMessageListener (new MessageListener ()
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
								for ( TransactionOutput o : t.getOutputs () )
								{
									for ( String a : o.getAddresses () )
									{
										if ( listenAddresses.contains (a) )
										{
											listener.received (t);
										}
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
		}
		catch ( Exception e )
		{
			log.error ("Can not register address", e);
		}
	}

	@Override
	public void registerTransactionListener (List<String> hashes, final TransactionListener listener)
	{
		try
		{
			for ( String h : hashes )
			{
				listenTransactions.add (h);
				String hash = h.substring (h.length () - 3, h.length ());

				MessageConsumer consumer = filterConsumer.get (hash);
				if ( consumer == null )
				{
					consumer = session.createConsumer (session.createTopic ("filter" + hash));
					consumer.setMessageListener (new MessageListener ()
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
								if ( listenTransactions.contains (t.getHash ()) )
								{
									listener.spent (t);
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
		}
		catch ( Exception e )
		{
			log.error ("Can not register transaction listener", e);
		}
	}

	private Set<String> confirmationUpdate (List<Block> removed, List<Block> added)
	{
		Set<String> confirmed = new HashSet<String> ();
		for ( Map.Entry<String, Integer> entry : monitorConfirmations.entrySet () )
		{
			if ( entry.getValue () > 0 )
			{
				entry.setValue (Math.max (entry.getValue () - removed.size (), 0));
				confirmed.add (entry.getKey ());
			}
		}
		for ( Block b : added )
		{
			b.computeHash ();
			Set<String> hashes = new HashSet<String> ();
			for ( Transaction t : b.getTransactions () )
			{
				hashes.add (t.getHash ());
			}
			for ( Map.Entry<String, Integer> entry : monitorConfirmations.entrySet () )
			{
				if ( hashes.contains (entry.getKey ()) )
				{
					entry.setValue (1);
					confirmed.add (entry.getKey ());
				}
				else
				{
					if ( entry.getValue () != 0 )
					{
						entry.setValue (entry.getValue () + 1);
						confirmed.add (entry.getKey ());
					}
				}
			}
		}
		return confirmed;
	}

	@Override
	public void registerConfirmationListener (List<String> hashes, final TransactionListener listener) throws JMSException
	{
		if ( monitorConfirmations.size () == 0 )
		{
			int i = 0;
			blocks = new HashMap<String, Integer> ();
			for ( String h : getBlocks () )
			{
				blocks.put (h, i);
				++i;
			}
			registerTrunkListener (new TrunkListener ()
			{
				@Override
				public void trunkUpdate (List<Block> removed, List<Block> added)
				{
					for ( Block b : removed )
					{
						b.computeHash ();
						blocks.remove (b.getHash ());
					}
					for ( Block b : added )
					{
						b.computeHash ();
						blocks.put (b.getHash (), blocks.size ());
					}
					Set<String> confirmed = confirmationUpdate (removed, added);
					for ( String hash : monitorConfirmations.keySet () )
					{
						if ( confirmed.contains (hash) )
						{
							listener.confirmed (hash, monitorConfirmations.get (hash));
						}
					}
				}
			});
		}
		for ( String hash : hashes )
		{
			Transaction t = getTransaction (hash);
			if ( t != null )
			{
				if ( t.getBlockHash () != null )
				{
					listener.confirmed (hash, blocks.size () - blocks.get (t.getBlockHash ()));
				}
				Integer conf = monitorConfirmations.get (hash);
				if ( conf == null )
				{
					monitorConfirmations.put (hash, new Integer (0));
				}
			}
		}
	}

	@Override
	public List<String> getBlocks ()
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
		catch ( Exception e )
		{
			log.error ("Can not get block", e);
		}
		return null;
	}
}
