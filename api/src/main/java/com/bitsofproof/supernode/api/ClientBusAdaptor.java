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

	private final List<TransactionListener> transactionListener = Collections.synchronizedList (new ArrayList<TransactionListener> ());
	private final List<TrunkListener> trunkListener = Collections.synchronizedList (new ArrayList<TrunkListener> ());
	private final Map<String, ArrayList<TransactionListener>> filterListener = new HashMap<String, ArrayList<TransactionListener>> ();

	private MessageProducer inventoryProducer;
	private MessageProducer transactionProducer;
	private MessageProducer blockProducer;
	private MessageProducer blockRequestProducer;
	private MessageProducer transactionRequestProducer;
	private MessageProducer accountRequestProducer;

	private final Map<String, MessageConsumer> filterConsumer = new HashMap<String, MessageConsumer> ();

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

			monitorConfirmations ();
		}
		catch ( JMSException e )
		{
			log.error ("Can not create JMS connection", e);
		}
		catch ( BCSAPIException e )
		{
			log.error ("Can not register trunk listener", e);
		}
	}

	private void monitorConfirmations () throws BCSAPIException
	{
		registerTrunkListener (new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				if ( removed != null )
				{
					for ( Block b : removed )
					{
						b.computeHash ();
						for ( Transaction t : b.getTransactions () )
						{
							synchronized ( filterListener )
							{
								List<TransactionListener> listener = filterListener.get (t.getHash ());
								if ( listener != null )
								{
									for ( TransactionListener l : listener )
									{
										l.orphaned (t.getHash ());
									}
								}
							}
						}
					}
				}
				if ( added != null )
				{
					for ( Block b : added )
					{
						b.computeHash ();
						for ( Transaction t : b.getTransactions () )
						{
							synchronized ( filterListener )
							{
								List<TransactionListener> listener = filterListener.get (t.getHash ());
								if ( listener != null )
								{
									for ( TransactionListener l : listener )
									{
										l.confirmed (t.getHash ());
									}
								}
							}
						}
					}
				}
			}
		});
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
	public void registerTransactionListener (TransactionListener listener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("transaction", new MessageListener ()
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
						synchronized ( transactionListener )
						{
							for ( TransactionListener l : transactionListener )
							{
								l.process (t);
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
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		transactionListener.add (listener);
	}

	@Override
	public void removeTransactionListener (TransactionListener listener)
	{
		transactionListener.remove (listener);
	}

	private class TrunkListenerDispatcher implements MessageListener
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
				synchronized ( trunkListener )
				{
					for ( TrunkListener l : trunkListener )
					{
						l.trunkUpdate (tu.getRemoved (), tu.getAdded ());
					}
				}
			}
			catch ( Exception e )
			{
				log.error ("Block message error", e);
			}
		}
	}

	@Override
	public void registerTrunkListener (TrunkListener listener) throws BCSAPIException
	{
		try
		{
			addTopicListener ("trunk", new TrunkListenerDispatcher ());
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
		synchronized ( trunkListener )
		{
			if ( !trunkListener.contains (listener) )
			{
				trunkListener.add (listener);
			}
		}
	}

	@Override
	public void removeTrunkListener (TrunkListener listener)
	{
		synchronized ( trunkListener )
		{
			trunkListener.remove (listener);
		}
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

	private class ReceiveListener implements MessageListener
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
					for ( String a : o.getAddresses () )
					{
						synchronized ( filterListener )
						{
							List<TransactionListener> listener = filterListener.get (a);
							if ( listener != null )
							{
								for ( TransactionListener l : listener )
								{
									l.process (t);
								}
							}
						}
					}
				}
			}
			catch ( Exception e )
			{
				log.error ("Transaction message error", e);
			}
		}
	}

	private final ReceiveListener receiveListener = new ReceiveListener ();

	@Override
	public void registerAddressListener (Collection<String> addresses, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			for ( String a : addresses )
			{
				synchronized ( filterListener )
				{
					ArrayList<TransactionListener> ll = filterListener.get (a);
					if ( ll == null )
					{
						ll = new ArrayList<TransactionListener> ();
						filterListener.put (a, ll);
					}
					if ( !ll.contains (listener) )
					{
						ll.add (listener);
					}
				}
				String hash = a.substring (a.length () - 2, a.length ());

				MessageConsumer consumer = filterConsumer.get (hash);
				if ( consumer == null )
				{
					consumer = session.createConsumer (session.createTopic ("address" + hash));
					consumer.setMessageListener (receiveListener);
				}
			}
		}
		catch ( JMSException e )
		{
			throw new BCSAPIException (e);
		}
	}

	private class SpendListener implements MessageListener
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
					synchronized ( filterListener )
					{
						ArrayList<TransactionListener> listener = filterListener.get (i.getSourceHash ());
						if ( listener != null )
						{
							for ( TransactionListener l : listener )
							{
								l.process (t);
							}
						}
					}
				}
			}
			catch ( Exception e )
			{
				log.error ("Transaction message error", e);
			}
		}
	}

	private final SpendListener spendListener = new SpendListener ();

	@Override
	public void registerOutputListener (Collection<String> hashes, final TransactionListener listener) throws BCSAPIException
	{
		try
		{
			for ( String h : hashes )
			{
				synchronized ( filterListener )
				{
					ArrayList<TransactionListener> ll = filterListener.get (h);
					if ( ll == null )
					{
						ll = new ArrayList<TransactionListener> ();
						filterListener.put (h, ll);
					}
					if ( !ll.contains (listener) )
					{
						ll.add (listener);
					}
				}
				String hash = h.substring (h.length () - 3, h.length ());

				MessageConsumer consumer = filterConsumer.get (hash);
				if ( consumer == null )
				{
					consumer = session.createConsumer (session.createTopic ("output" + hash));
					consumer.setMessageListener (spendListener);
				}
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
		synchronized ( filterListener )
		{
			for ( String s : filter )
			{
				ArrayList<TransactionListener> ll = filterListener.get (s);
				if ( ll != null )
				{
					ll.remove (listener);
					if ( ll.size () == 0 )
					{
						filterListener.remove (s);
					}
				}
			}
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
}
