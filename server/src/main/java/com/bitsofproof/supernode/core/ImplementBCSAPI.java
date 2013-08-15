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
package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPIMessage;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.ExtendedKey;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkUpdateMessage;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;
import com.bitsofproof.supernode.core.BlockStore.TransactionProcessor;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class ImplementBCSAPI implements TrunkListener, TxListener
{
	private static final Logger log = LoggerFactory.getLogger (ImplementBCSAPI.class);

	private final BitcoinNetwork network;
	private final BlockStore store;
	private final TxHandler txhandler;

	private ConnectionFactory connectionFactory;

	private Connection connection;

	private final ExecutorService requestProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());

	public ImplementBCSAPI (BitcoinNetwork network, TxHandler txHandler)
	{
		this.network = network;
		this.txhandler = txHandler;
		this.store = network.getStore ();

		store.addTrunkListener (this);
		txHandler.addTransactionListener (this);
	}

	public void setConnectionFactory (ConnectionFactory connectionFactory)
	{
		this.connectionFactory = connectionFactory;
	}

	private abstract static class SessionMessageListener implements MessageListener
	{
		private Session session;

		protected void reply (Destination destination, byte[] msg)
		{
			MessageProducer replier = null;
			try
			{
				replier = session.createProducer (destination);
				BytesMessage m = session.createBytesMessage ();
				if ( msg != null )
				{
					m.writeBytes (msg);
				}
				replier.send (m);
			}
			catch ( JMSException e )
			{
				log.trace ("can not reply", e);
			}
			finally
			{
				try
				{
					replier.close ();
				}
				catch ( JMSException e )
				{
				}
			}
		}

		public void setSession (Session session)
		{
			this.session = session;
		}
	}

	private void addTopicListener (String topic, SessionMessageListener listener) throws JMSException
	{
		Session session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
		listener.setSession (session);
		Destination destination = session.createTopic (topic);
		MessageConsumer consumer = session.createConsumer (destination);
		consumer.setMessageListener (listener);
	}

	private void addQueueListener (String queue, SessionMessageListener listener) throws JMSException
	{
		Session session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
		listener.setSession (session);
		Destination destination = session.createQueue (queue);
		MessageConsumer consumer = session.createConsumer (destination);
		consumer.setMessageListener (listener);
	}

	public void init ()
	{
		try
		{
			connection = connectionFactory.createConnection ();
			connection.setClientID ("bitsofproof supernode");
			connection.start ();
			addNewTransactionListener ();
			addNewBlockListener ();
			addBlockrequestListener ();
			addBlockHeaderRequestListener ();
			addTransactionRequestListener ();
			addMatchScanListener ();
			addPingListener ();
			addAccountScanListener ();
		}
		catch ( JMSException e )
		{
			log.error ("Error creating JMS producer", e);
		}
	}

	private void closeSession (Session session)
	{
		if ( session != null )
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

	private void addMatchScanListener () throws JMSException
	{
		addQueueListener ("matchRequest", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message msg)
			{
				BytesMessage o = (BytesMessage) msg;
				byte[] body;
				try
				{
					body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					BCSAPIMessage.ExactMatchRequest request = BCSAPIMessage.ExactMatchRequest.parseFrom (body);
					final Set<ByteVector> match = new HashSet<ByteVector> ();
					for ( ByteString bs : request.getMatchList () )
					{
						match.add (new ByteVector (bs.toByteArray ()));
					}
					final UpdateMode mode = UpdateMode.values ()[request.getMode ()];
					final Destination destination = msg.getJMSReplyTo ();
					final long after = request.hasAfter () ? request.getAfter () : 0;
					log.debug ("matchRequest for ", match.size () + " patterns after " + after);
					requestProcessor.execute (new Runnable ()
					{
						@Override
						public void run ()
						{
							Session session = null;
							try
							{
								session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
								final MessageProducer producer = session.createProducer (destination);
								final Session passInSession = session;
								final AtomicInteger counter = new AtomicInteger (0);

								TransactionProcessor processor = new TransactionProcessor ()
								{
									@Override
									public void process (Tx tx)
									{
										if ( tx != null )
										{
											Transaction transaction = toBCSAPITransaction (tx, false);
											BytesMessage m;
											try
											{

												m = passInSession.createBytesMessage ();
												m.writeBytes (transaction.toProtobuf ().toByteArray ());
												producer.send (m);
												counter.incrementAndGet ();
											}
											catch ( JMSException e )
											{
											}
										}
									}
								};

								store.filterTransactions (match, mode, after, processor);
								int c = counter.get ();
								log.debug ("matchRequest returns " + counter.get () + " transactions from blockchain");

								txhandler.scanUnconfirmedPool (match, mode, processor);
								log.debug ("matchRequest returns " + (counter.get () - c) + " transactions from mempool");

								try
								{
									BytesMessage m = session.createBytesMessage ();
									producer.send (m); // indicate EOF
									producer.close ();
								}
								catch ( JMSException e )
								{
								}
							}
							catch ( ValidationException | JMSException e )
							{
								log.error ("Error while scanning", e);
							}
							finally
							{
								closeSession (session);
							}
						}
					});
				}
				catch ( JMSException e )
				{
					log.error ("invalid matchRequest request", e);
				}
				catch ( InvalidProtocolBufferException e )
				{
					log.error ("invalid matchRequest request", e);
				}
			}
		});
	}

	private void addAccountScanListener () throws JMSException
	{
		addQueueListener ("accountRequest", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message msg)
			{
				BytesMessage o = (BytesMessage) msg;
				byte[] body;
				try
				{
					body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					BCSAPIMessage.AccountRequest request = BCSAPIMessage.AccountRequest.parseFrom (body);
					final ExtendedKey ek = ExtendedKey.parse (request.getPublicKey ());
					final int lookAhead = request.getLookAhead ();
					final long after = request.getAfter ();
					log.debug ("accountRequest for " + ek.serialize (true) + " after " + after);
					final Set<ByteVector> match = new HashSet<ByteVector> ();
					final UpdateMode mode = UpdateMode.all;
					final Destination destination = msg.getJMSReplyTo ();
					final AtomicInteger counter = new AtomicInteger (0);
					requestProcessor.execute (new Runnable ()
					{
						@Override
						public void run ()
						{
							Session session = null;
							try
							{
								session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
								final MessageProducer producer = session.createProducer (destination);
								final Session passInSession = session;
								TransactionProcessor processor = new TransactionProcessor ()
								{
									@Override
									public void process (Tx tx)
									{
										if ( tx != null )
										{
											Transaction transaction = toBCSAPITransaction (tx, false);
											BytesMessage m;

											try
											{
												m = passInSession.createBytesMessage ();
												m.writeBytes (transaction.toProtobuf ().toByteArray ());
												producer.send (m);
												counter.incrementAndGet ();
											}
											catch ( JMSException e )
											{
											}
										}
									}
								};

								store.filterTransactions (match, ek, lookAhead, after, processor);
								int c = counter.get ();
								log.debug ("accountRequest returns " + counter.get () + " transactions from blockchain");

								txhandler.scanUnconfirmedPool (match, mode, processor);
								log.debug ("accountRequest returns " + (counter.get () - c) + " transactions from mempool");
								try
								{
									BytesMessage m = session.createBytesMessage ();
									producer.send (m); // indicate EOF
									producer.close ();
								}
								catch ( JMSException e )
								{
								}
							}
							catch ( ValidationException | JMSException e )
							{
								log.error ("Error while scanning", e);
							}
							finally
							{
								closeSession (session);
							}
						}
					});
				}
				catch ( JMSException e )
				{
					log.error ("invalid filter request", e);
				}
				catch ( InvalidProtocolBufferException e )
				{
					log.error ("invalid filter request", e);
				}
				catch ( ValidationException e )
				{
					log.error ("Invalid scan account request", e);
				}
			}
		});
	}

	private void addTransactionRequestListener () throws JMSException
	{
		addQueueListener ("transactionRequest", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					String hash = new Hash (BCSAPIMessage.Hash.parseFrom (body).getHash (0).toByteArray ()).toString ();
					log.debug ("transactionRequest for " + hash);
					Transaction t = getTransaction (hash);
					if ( t != null )
					{
						reply (o.getJMSReplyTo (), t.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
					log.debug ("transactionRequest for " + hash + " replied");
				}
				catch ( Exception e )
				{
					log.trace ("Rejected invalid transaction request ", e);
				}
			}
		});
	}

	private void addBlockrequestListener () throws JMSException
	{
		addQueueListener ("blockRequest", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					String hash = new Hash (BCSAPIMessage.Hash.parseFrom (body).getHash (0).toByteArray ()).toString ();
					log.debug ("blockRequest for " + hash);
					Block b = getBlock (hash);
					if ( b != null )
					{
						b.setHeight (store.getBlockHeight (b.getHash ()));
						reply (o.getJMSReplyTo (), b.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
					log.debug ("blockRequest for " + hash + " replied");
				}
				catch ( Exception e )
				{
					log.trace ("Rejected invalid block request ", e);
				}
			}
		});
	}

	private void addBlockHeaderRequestListener () throws JMSException
	{
		addQueueListener ("headerRequest", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					String hash = new Hash (BCSAPIMessage.Hash.parseFrom (body).getHash (0).toByteArray ()).toString ();
					log.debug ("headerRequest for " + hash);
					Block b = getBlockHeader (hash);
					if ( b != null )
					{
						b.setHeight (store.getBlockHeight (b.getHash ()));
						reply (o.getJMSReplyTo (), b.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
					log.debug ("headerRequest for " + hash + " replied");
				}
				catch ( Exception e )
				{
					log.trace ("Rejected invalid header block request ", e);
				}
			}
		});
	}

	private void addNewBlockListener () throws JMSException
	{
		addTopicListener ("newBlock", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					Block block = Block.fromProtobuf (BCSAPIMessage.Block.parseFrom (body));
					block.computeHash ();
					log.debug ("newBlock for " + block.getHash ());
					sendBlock (block);
					reply (o.getJMSReplyTo (), null);
					log.debug ("newBlock " + block.getHash () + " accepted");
				}
				catch ( Exception e )
				{
					BCSAPIMessage.ExceptionMessage.Builder builder = BCSAPIMessage.ExceptionMessage.newBuilder ();
					builder.setBcsapiversion (1);
					builder.addMessage (e.getMessage ());
					try
					{
						reply (o.getJMSReplyTo (), builder.build ().toByteArray ());
						log.debug ("newBlock rejected");
					}
					catch ( JMSException e1 )
					{
						log.error ("Can not send reply ", e1);
					}
				}
			}
		});
	}

	private void addPingListener () throws JMSException
	{
		addQueueListener ("ping", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					log.debug ("ping received");
					o.readBytes (body);
					reply (o.getJMSReplyTo (), body);
					log.debug ("ping replied");
				}
				catch ( Exception e )
				{
					log.error ("Exception in ping", e);
				}
			}
		});
	}

	private void addNewTransactionListener () throws JMSException
	{
		addTopicListener ("newTransaction", new SessionMessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					Transaction transaction = Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body));
					transaction.computeHash ();
					log.debug ("newTransaction " + transaction.getHash ());
					sendTransaction (transaction);
					reply (o.getJMSReplyTo (), null);
					log.debug ("newTransaction " + transaction.getHash () + " accepted");
				}
				catch ( Exception e )
				{
					BCSAPIMessage.ExceptionMessage.Builder builder = BCSAPIMessage.ExceptionMessage.newBuilder ();
					builder.setBcsapiversion (1);
					builder.addMessage (e.getMessage ());
					try
					{
						reply (o.getJMSReplyTo (), builder.build ().toByteArray ());
						log.debug ("newTransaction rejected");
					}
					catch ( JMSException e1 )
					{
						log.error ("Can not send reply ", e1);
					}
				}
			}
		});
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

	public Transaction getTransaction (final String hash)
	{
		try
		{
			log.trace ("get transaction " + hash);
			Tx tx = txhandler.getTransaction (hash);
			if ( tx == null )
			{
				Tx t;
				try
				{
					t = store.getTransaction (hash);
					if ( t != null )
					{
						return toBCSAPITransaction (t, false);
					}
				}
				catch ( ValidationException e )
				{
				}
				return null;
			}
			else
			{
				return toBCSAPITransaction (tx, false);
			}
		}
		finally
		{
			log.trace ("get transaction returned " + hash);
		}
	}

	public Block getBlock (final String hash)
	{
		log.trace ("get block " + hash);
		Block block = null;
		String h = hash;
		if ( h.equals (Hash.ZERO_HASH_STRING) )
		{
			h = store.getHeadHash ();
		}
		Blk b;
		try
		{
			b = store.getBlock (h);
			if ( b != null )
			{
				block = toBCSAPIBlock (b);
			}
		}
		catch ( ValidationException e )
		{
		}

		log.trace ("get block returned " + block != null ? hash : "null");
		return block;
	}

	public Block getBlockHeader (final String hash)
	{
		log.trace ("get block header " + hash);
		Block block = null;
		String h = hash;
		if ( h.equals (Hash.ZERO_HASH_STRING) )
		{
			h = store.getHeadHash ();
		}
		Blk b;
		try
		{
			b = store.getBlockHeader (h);
			if ( b != null )
			{
				block = toBCSAPIBlock (b);
			}
		}
		catch ( ValidationException e )
		{
		}

		log.trace ("get block header returned " + block != null ? hash : "null");
		return block;
	}

	private void sendTransaction (Transaction transaction) throws ValidationException
	{
		log.trace ("send transaction " + transaction.getHash ());
		WireFormat.Writer writer = new WireFormat.Writer ();
		transaction.toWire (writer);
		Tx t = new Tx ();
		t.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		txhandler.validateCacheAndSend (t, null);
	}

	private void sendBlock (Block block) throws ValidationException
	{
		log.trace ("send block " + block.getHash ());
		WireFormat.Writer writer = new WireFormat.Writer ();
		block.toWire (writer);
		Blk b = new Blk ();
		b.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		store.storeBlock (b);
		for ( BitcoinPeer p : network.getConnectPeers () )
		{
			BlockMessage bm = (BlockMessage) p.createMessage ("block");
			bm.setBlock (b);
			p.send (bm);
		}
	}

	@Override
	public void process (Tx tx, boolean doubleSpend)
	{
		Session session = null;
		try
		{
			Transaction transaction = toBCSAPITransaction (tx, doubleSpend);
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			MessageProducer transactionProducer = session.createProducer (session.createTopic ("transaction"));
			transactionProducer.send (m);
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
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

	private Transaction toBCSAPITransaction (Tx tx, boolean doubleSpend)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		tx.toWire (writer);
		Transaction transaction = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		if ( tx.getBlockHash () != null )
		{
			transaction.setBlockHash (tx.getBlockHash ());
			transaction.setHeight (store.getBlockHeight (tx.getBlockHash ()));
		}
		if ( doubleSpend )
		{
			transaction.setDoubleSpend (true);
		}
		return transaction;
	}

	private Block toBCSAPIBlock (Blk b)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		b.toWire (writer);
		Block block = Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		block.setHeight (b.getHeight ());
		return block;
	}

	@Override
	public void trunkUpdate (final List<Blk> removed, final List<Blk> extended)
	{
		Session session = null;
		try
		{
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer trunkProducer = session.createProducer (session.createTopic ("trunk"));

			List<Block> r = new ArrayList<Block> ();
			List<Block> a = new ArrayList<Block> ();
			for ( Blk blk : removed )
			{
				r.add (toBCSAPIBlock (blk));
			}
			for ( Blk blk : extended )
			{
				a.add (toBCSAPIBlock (blk));
			}
			TrunkUpdateMessage tu = new TrunkUpdateMessage (a, r);
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (tu.toProtobuf ().toByteArray ());
			trunkProducer.send (m);
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
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
