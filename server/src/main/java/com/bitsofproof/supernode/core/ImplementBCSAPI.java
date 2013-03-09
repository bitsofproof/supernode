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
package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.BCSAPIMessage;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.Posting;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.api.TrunkUpdateMessage;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;
import com.google.protobuf.ByteString;

public class ImplementBCSAPI implements TrunkListener, TransactionListener
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
	private final Map<String, MessageProducer> filterProducer = new HashMap<String, MessageProducer> ();

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
			addMessageListener ("accountRequest", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					BytesMessage o = (BytesMessage) arg0;
					try
					{
						byte[] body = new byte[(int) o.getBodyLength ()];
						o.readBytes (body);
						BCSAPIMessage.AccountRequest ar = BCSAPIMessage.AccountRequest.parseFrom (body);

						AccountStatement as = getAccountStatement (ar.getAddressList (), ar.getFrom ());
						if ( as != null )
						{
							reply (o.getJMSReplyTo (), as.toProtobuf ().toByteArray ());
						}
						else
						{
							reply (o.getJMSReplyTo (), null);
						}
					}
					catch ( Exception e )
					{
						log.trace ("Rejected invalid account request ", e);
					}
				}
			});
			addMessageListener ("inventory", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					BytesMessage o = (BytesMessage) arg0;
					try
					{
						byte[] body = new byte[(int) o.getBodyLength ()];
						o.readBytes (body);
						BCSAPIMessage.Hash ar = BCSAPIMessage.Hash.parseFrom (body);
						List<String> locator = new ArrayList<String> ();
						for ( ByteString s : ar.getHashList () )
						{
							locator.add (new Hash (s.toByteArray ()).toString ());
						}
						List<String> inventory = store.getInventory (locator, Hash.ZERO_HASH_STRING, Integer.MAX_VALUE);
						BCSAPIMessage.Hash.Builder ab = BCSAPIMessage.Hash.newBuilder ();
						ab.setBcsapiversion (1);
						for ( String s : inventory )
						{
							ab.addHash (ByteString.copyFrom (new Hash (s).toByteArray ()));
						}
						reply (o.getJMSReplyTo (), ab.build ().toByteArray ());
					}
					catch ( Exception e )
					{
						log.trace ("Rejected invalid inventory request ", e);
					}
				}
			});
		}
		catch ( JMSException e )
		{
			log.error ("Error creating JMS producer", e);
		}
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
			final WireFormat.Writer writer = new WireFormat.Writer ();
			if ( tx == null )
			{
				new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
				{
					@Override
					protected void doInTransactionWithoutResult (TransactionStatus status)
					{
						status.setRollbackOnly ();
						Tx t = store.getTransaction (hash);
						if ( t != null )
						{
							t.toWire (writer);
						}
					}
				});
			}
			else
			{
				tx.toWire (writer);
			}
			byte[] read = writer.toByteArray ();
			if ( read.length > 0 )
			{
				return Transaction.fromWire (new WireFormat.Reader (read));
			}
			return null;
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
					Blk b = store.getBlock (h);
					if ( b != null )
					{
						b.toWire (writer);
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

	private void sendToHashFilter (String hash, BytesMessage m) throws JMSException
	{
		String key = hash.substring (hash.length () - 3, hash.length ());
		MessageProducer p = filterProducer.get (key);
		if ( p == null )
		{
			p = session.createProducer (session.createTopic ("filter" + key));
			filterProducer.put (key, p);
		}
		p.send (m);
	}

	private void sendToAddressFilter (String address, BytesMessage m) throws JMSException
	{
		if ( address != null )
		{
			String key = address.substring (address.length () - 2, address.length ());
			MessageProducer p = filterProducer.get (key);
			if ( p == null )
			{
				p = session.createProducer (session.createTopic ("filter" + key));
				filterProducer.put (key, p);
			}
			p.send (m);
		}
	}

	@Override
	public void onTransaction (Tx tx)
	{
		try
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			tx.toWire (writer);
			Transaction transaction = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (transaction.toProtobuf ().toByteArray ());
			transactionProducer.send (m);

			for ( TxOut o : tx.getOutputs () )
			{
				sendToAddressFilter (o.getOwner1 (), m);
				sendToAddressFilter (o.getOwner2 (), m);
				sendToAddressFilter (o.getOwner3 (), m);
			}
			for ( TxIn i : tx.getInputs () )
			{
				sendToHashFilter (i.getSourceHash (), m);
			}
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	@Override
	public void trunkUpdate (final List<Blk> removed, final List<Blk> extended)
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
		try
		{
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

	public AccountStatement getAccountStatement (final List<String> addresses, final long from)
	{
		log.trace ("get account statement ");
		try
		{
			final AccountStatement statement = new AccountStatement ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					List<TransactionOutput> balances = new ArrayList<TransactionOutput> ();
					statement.setOpening (balances);

					Blk trunk = store.getBlock (store.getHeadHash ());
					statement.setTimestamp (trunk.getCreateTime ());
					statement.setLastBlock (store.getHeadHash ());

					log.trace ("retrieve balance");
					HashMap<String, HashMap<Long, TxOut>> utxo = new HashMap<String, HashMap<Long, TxOut>> ();
					for ( TxOut o : store.getUnspentOutput (addresses) )
					{
						HashMap<Long, TxOut> outs = utxo.get (o.getTxHash ());
						if ( outs == null )
						{
							outs = new HashMap<Long, TxOut> ();
							utxo.put (o.getTxHash (), outs);
						}
						outs.put (o.getIx (), o);
					}

					List<Posting> postings = new ArrayList<Posting> ();
					statement.setPosting (postings);

					log.trace ("retrieve spent");
					for ( TxIn spent : store.getSpent (addresses, from) )
					{
						Posting p = new Posting ();
						postings.add (p);

						p.setTimestamp (spent.getBlockTime ());

						TxOut o = spent.getSource ();
						HashMap<Long, TxOut> outs = utxo.get (o.getTxHash ());
						if ( outs == null )
						{
							outs = new HashMap<Long, TxOut> ();
							utxo.put (o.getTxHash (), outs);
						}
						outs.put (o.getIx (), o);

						WireFormat.Writer writer = new WireFormat.Writer ();
						o.toWire (writer);
						TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
						out.setTransactionHash (o.getTxHash ());
						p.setOutput (out);
						p.setSpent (spent.getTransaction ().getHash ());
					}

					log.trace ("retrieve received");
					for ( TxOut o : store.getReceived (addresses, from) )
					{
						Posting p = new Posting ();
						postings.add (p);

						p.setTimestamp (o.getBlockTime ());
						HashMap<Long, TxOut> outs = utxo.get (o.getTxHash ());
						if ( outs != null )
						{
							outs.remove (o.getIx ());
							if ( outs.size () == 0 )
							{
								utxo.remove (o.getTxHash ());
							}
						}

						WireFormat.Writer writer = new WireFormat.Writer ();
						o.toWire (writer);
						TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
						out.setTransactionHash (o.getTxHash ());
						p.setOutput (out);
					}
					for ( HashMap<Long, TxOut> outs : utxo.values () )
					{
						for ( TxOut o : outs.values () )
						{
							WireFormat.Writer writer = new WireFormat.Writer ();
							o.toWire (writer);
							TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
							out.setTransactionHash (o.getTxHash ());
							balances.add (out);
						}
					}
					Collections.sort (postings, new Comparator<Posting> ()
					{
						@Override
						public int compare (Posting arg0, Posting arg1)
						{
							if ( arg0.getTimestamp () != arg1.getTimestamp () )
							{
								return (int) (arg0.getTimestamp () - arg1.getTimestamp ());
							}
							else
							{
								if ( arg0.getSpent () == null && arg1.getSpent () != null )
								{
									return -1;
								}
								if ( arg0.getSpent () != null && arg1.getSpent () == null )
								{
									return 1;
								}
								return 0;
							}
						}
					});
				}
			});
			Set<String> as = new HashSet<String> ();
			as.addAll (addresses);
			List<Tx> unconfirmed = txhandler.getUnconfirmedForAddresses (as);
			Set<String> ah = new HashSet<String> ();
			for ( TransactionOutput o : statement.getOpening () )
			{
				ah.add (o.getTransactionHash ());
			}
			statement.setUnconfirmedReceive (new ArrayList<Transaction> ());
			for ( Tx t : txhandler.getUnconfirmedForHashes (ah) )
			{
				WireFormat.Writer writer = new WireFormat.Writer ();
				t.toWire (writer);
				statement.getUnconfirmedReceive ().add (Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ())));
			}
			statement.setUnconfirmedSpend (new ArrayList<Transaction> ());
			for ( Tx t : unconfirmed )
			{
				WireFormat.Writer writer = new WireFormat.Writer ();
				t.toWire (writer);
				statement.getUnconfirmedSpend ().add (Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ())));
			}
			return statement;
		}
		finally
		{
			log.trace ("get account statement returned");
		}
	}
}
