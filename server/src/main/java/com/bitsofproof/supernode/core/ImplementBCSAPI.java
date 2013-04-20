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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.BCSAPIMessage;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.BloomFilter;
import com.bitsofproof.supernode.api.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.api.Color;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkUpdateMessage;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.core.BlockStore.TransactionProcessor;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.StoredColor;
import com.bitsofproof.supernode.model.Tx;
import com.google.protobuf.InvalidProtocolBufferException;

public class ImplementBCSAPI implements TrunkListener, TxListener
{
	private static final Logger log = LoggerFactory.getLogger (ImplementBCSAPI.class);

	private final BitcoinNetwork network;
	private final BlockStore store;
	private final TxHandler txhandler;

	private PlatformTransactionManager transactionManager;

	private ConnectionFactory connectionFactory;

	private Connection connection;
	private Session session;

	private MessageProducer transactionProducer;
	private MessageProducer trunkProducer;
	private final Map<BloomFilter, MessageProducer> bloomFilterProducer = Collections.synchronizedMap (new HashMap<BloomFilter, MessageProducer> ());

	private final ExecutorService requestProcessor = Executors.newFixedThreadPool (Runtime.getRuntime ().availableProcessors ());

	public ImplementBCSAPI (BitcoinNetwork network, TxHandler txHandler)
	{
		this.network = network;
		this.txhandler = txHandler;
		this.store = network.getStore ();

		store.addTrunkListener (this);
		txHandler.addTransactionListener (this);
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void setConnectionFactory (ConnectionFactory connectionFactory)
	{
		this.connectionFactory = connectionFactory;
	}

	private void addMessageListener (String topic, MessageListener listener) throws JMSException
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
			connection.setClientID ("bitsofproof supernode");
			connection.start ();
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			transactionProducer = session.createProducer (session.createTopic ("transaction"));
			trunkProducer = session.createProducer (session.createTopic ("trunk"));
			addNewTransactionListener ();
			addNewBlockListener ();
			addBlockrequestListener ();
			addTransactionRequestListener ();
			addColorRequestListener ();
			addNewColorListener ();
			addBloomFilterListener ();
			addBloomScanListener ();
		}
		catch ( JMSException e )
		{
			log.error ("Error creating JMS producer", e);
		}
	}

	private void addBloomFilterListener () throws JMSException
	{
		addMessageListener ("filterRequest", new MessageListener ()
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
					BCSAPIMessage.FilterRequest request = BCSAPIMessage.FilterRequest.parseFrom (body);
					byte[] data = request.getFilter ().toByteArray ();
					long hashFunctions = request.getHashFunctions ();
					long tweak = request.getTweak ();
					UpdateMode updateMode = UpdateMode.values ()[request.getMode ()];
					BloomFilter filter = new BloomFilter (data, hashFunctions, tweak, updateMode);
					MessageProducer producer = session.createProducer (msg.getJMSReplyTo ());
					bloomFilterProducer.put (filter, producer);
				}
				catch ( JMSException e )
				{
					log.error ("invalid filter request", e);
				}
				catch ( InvalidProtocolBufferException e )
				{
					log.error ("invalid filter request", e);
				}
			}
		});
	}

	private void addBloomScanListener () throws JMSException
	{
		addMessageListener ("scanRequest", new MessageListener ()
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
					BCSAPIMessage.FilterRequest request = BCSAPIMessage.FilterRequest.parseFrom (body);
					byte[] data = request.getFilter ().toByteArray ();
					long hashFunctions = request.getHashFunctions ();
					long tweak = request.getTweak ();
					UpdateMode updateMode = UpdateMode.values ()[request.getMode ()];
					final BloomFilter filter = new BloomFilter (data, hashFunctions, tweak, updateMode);
					final MessageProducer producer = session.createProducer (msg.getJMSReplyTo ());
					requestProcessor.execute (new Runnable ()
					{
						@Override
						public void run ()
						{
							try
							{
								store.scan (filter, new TransactionProcessor ()
								{
									@Override
									public void process (Tx tx)
									{
										if ( tx != null )
										{
											Transaction transaction = toBCSAPITransaction (tx);
											BytesMessage m;
											try
											{
												m = session.createBytesMessage ();
												m.writeBytes (transaction.toProtobuf ().toByteArray ());
												producer.send (m);
											}
											catch ( JMSException e )
											{
											}
										}
										else
										{
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
									}
								});
							}
							catch ( ValidationException e )
							{
								log.error ("Error while scanning", e);
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
			}
		});
	}

	private void addNewColorListener () throws JMSException
	{
		addMessageListener ("newColor", new MessageListener ()
		{
			@Override
			public void onMessage (Message arg0)
			{
				BytesMessage o = (BytesMessage) arg0;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					Color color = Color.fromProtobuf (BCSAPIMessage.Color.parseFrom (body));
					StoredColor sc = new StoredColor ();
					sc.setFungibleName (color.getFungibleName ());
					sc.setExpiryHeight (color.getExpiryHeight ());
					sc.setPubkey (color.getPubkey ());
					sc.setSignature (color.getSignature ());
					sc.setTerms (color.getTerms ());
					sc.setUnit (color.getUnit ());
					sc.setTxHash (color.getTransaction ());
					store.issueColor (sc);
					reply (o.getJMSReplyTo (), null);
				}
				catch ( Exception e )
				{
					BCSAPIMessage.ExceptionMessage.Builder builder = BCSAPIMessage.ExceptionMessage.newBuilder ();
					builder.setBcsapiversion (1);
					builder.addMessage (e.getMessage ());
					try
					{
						reply (o.getJMSReplyTo (), builder.build ().toByteArray ());
					}
					catch ( JMSException e1 )
					{
						log.error ("Can not send reply ", e1);
					}
				}
			}
		});
	}

	private void addColorRequestListener () throws JMSException
	{
		addMessageListener ("colorRequest", new MessageListener ()
		{
			@Override
			public void onMessage (Message message)
			{
				BytesMessage o = (BytesMessage) message;
				try
				{
					byte[] body = new byte[(int) o.getBodyLength ()];
					o.readBytes (body);
					String hash = new Hash (BCSAPIMessage.Hash.parseFrom (body).getHash (0).toByteArray ()).toString ();
					Color c = getColor (hash);
					if ( c != null )
					{
						reply (o.getJMSReplyTo (), c.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
				}
				catch ( Exception e )
				{
					log.trace ("Rejected invalid transaction request ", e);
				}
			}
		});
	}

	private void addTransactionRequestListener () throws JMSException
	{
		addMessageListener ("transactionRequest", new MessageListener ()
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
					Transaction t = getTransaction (hash);
					if ( t != null )
					{
						reply (o.getJMSReplyTo (), t.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
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
		addMessageListener ("blockRequest", new MessageListener ()
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
					Block b = getBlock (hash);
					if ( b != null )
					{
						reply (o.getJMSReplyTo (), b.toProtobuf ().toByteArray ());
					}
					else
					{
						reply (o.getJMSReplyTo (), null);
					}
				}
				catch ( Exception e )
				{
					log.trace ("Rejected invalid block request ", e);
				}
			}
		});
	}

	private void addNewBlockListener () throws JMSException
	{
		addMessageListener ("newBlock", new MessageListener ()
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
					sendBlock (block);
					reply (o.getJMSReplyTo (), null);
				}
				catch ( Exception e )
				{
					BCSAPIMessage.ExceptionMessage.Builder builder = BCSAPIMessage.ExceptionMessage.newBuilder ();
					builder.setBcsapiversion (1);
					builder.addMessage (e.getMessage ());
					try
					{
						reply (o.getJMSReplyTo (), builder.build ().toByteArray ());
					}
					catch ( JMSException e1 )
					{
						log.error ("Can not send reply ", e1);
					}
				}
			}
		});
	}

	private void addNewTransactionListener () throws JMSException
	{
		addMessageListener ("newTransaction", new MessageListener ()
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
					sendTransaction (transaction);
					reply (o.getJMSReplyTo (), null);
				}
				catch ( Exception e )
				{
					BCSAPIMessage.ExceptionMessage.Builder builder = BCSAPIMessage.ExceptionMessage.newBuilder ();
					builder.setBcsapiversion (1);
					builder.addMessage (e.getMessage ());
					try
					{
						reply (o.getJMSReplyTo (), builder.build ().toByteArray ());
					}
					catch ( JMSException e1 )
					{
						log.error ("Can not send reply ", e1);
					}
				}
			}
		});
	}

	private void reply (Destination destination, byte[] msg)
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

	public Transaction getTransaction (final String hash)
	{
		try
		{
			log.trace ("get transaction " + hash);
			Tx tx = txhandler.getTransaction (hash);
			if ( tx == null )
			{
				return new TransactionTemplate (transactionManager).execute (new TransactionCallback<Transaction> ()
				{
					@Override
					public Transaction doInTransaction (TransactionStatus status)
					{
						status.setRollbackOnly ();
						Tx t;
						try
						{
							t = store.getTransaction (hash);
							if ( t != null )
							{
								return toBCSAPITransaction (t);
							}
						}
						catch ( ValidationException e )
						{
						}
						return null;
					}
				});
			}
			else
			{
				return toBCSAPITransaction (tx);
			}
		}
		finally
		{
			log.trace ("get transaction returned " + hash);
		}
	}

	public Block getBlock (final String hash)
	{
		try
		{
			log.trace ("get block " + hash);
			final WireFormat.Writer writer = new WireFormat.Writer ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
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
							b.toWire (writer);
						}
					}
					catch ( ValidationException e )
					{
					}
				}
			});
			byte[] blockdump = writer.toByteArray ();
			if ( blockdump != null && blockdump.length > 0 )
			{
				return Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			}
			return null;
		}
		finally
		{
			log.trace ("get block returned " + hash);
		}
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
	public void process (Tx tx)
	{
		try
		{
			Transaction transaction = toBCSAPITransaction (tx);
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			transactionProducer.send (m);

			synchronized ( bloomFilterProducer )
			{
				for ( BloomFilter filter : bloomFilterProducer.keySet () )
				{
					if ( tx.passesFilter (filter) )
					{
						bloomFilterProducer.get (filter).send (m);
					}
				}
			}
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	private Transaction toBCSAPITransaction (Tx tx)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		tx.toWire (writer);
		Transaction transaction = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		return transaction;
	}

	@Override
	public void trunkUpdate (final List<Blk> removed, final List<Blk> extended)
	{
		try
		{
			List<Block> r = new ArrayList<Block> ();
			List<Block> a = new ArrayList<Block> ();
			for ( Blk blk : removed )
			{
				WireFormat.Writer writer = new WireFormat.Writer ();
				blk.toWire (writer);
				r.add (Block.fromWire (new WireFormat.Reader (writer.toByteArray ())));
			}
			for ( Blk blk : extended )
			{
				WireFormat.Writer writer = new WireFormat.Writer ();
				blk.toWire (writer);
				a.add (Block.fromWire (new WireFormat.Reader (writer.toByteArray ())));
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
	}

	private Color getColor (final String hash)
	{
		try
		{
			log.trace ("get color " + hash);
			final Color color = new Color ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					StoredColor c;
					try
					{
						c = ((ColorStore) store).findColor (hash);
						if ( c != null )
						{
							color.setExpiryHeight (c.getExpiryHeight ());
							color.setSignature (c.getSignature ());
							color.setTerms (c.getTerms ());
							color.setUnit (c.getUnit ());
							color.setTransaction (c.getTxHash ());
						}
					}
					catch ( ValidationException e )
					{
						log.error ("can not get color " + hash, e);
					}

				}
			});
			if ( color.getTerms () != null )
			{
				return color;
			}
			return null;
		}
		finally
		{
			log.trace ("get block returned " + hash);
		}
	}
}
