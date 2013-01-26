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
import java.util.List;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.BCSAPIMessage;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkUpdateMessage;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class ImplementBCSAPIBus implements TrunkListener, TransactionListener, TemplateListener
{
	private static final Logger log = LoggerFactory.getLogger (ImplementBCSAPIBus.class);

	private final BitcoinNetwork network;
	private final BlockStore store;
	private final TxHandler txhandler;

	private PlatformTransactionManager transactionManager;

	private Connection connection;
	private Session session;

	private String brokerURL;
	private String user;
	private String password;

	private MessageProducer transactionProducer;
	private MessageProducer trunkProducer;
	private MessageProducer templateProducer;

	public ImplementBCSAPIBus (BitcoinNetwork network, TxHandler txHandler, BlockTemplater blockTemplater)
	{
		this.network = network;
		this.txhandler = txHandler;
		this.store = network.getStore ();

		store.addTrunkListener (this);
		txHandler.addTransactionListener (this);
		blockTemplater.addTemplateListener (this);
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
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
			ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory (user, password, brokerURL);
			connection = connectionFactory.createConnection ();
			connection.setClientID ("bitsofproof supernode");
			connection.start ();
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			transactionProducer = session.createProducer (session.createTopic ("transaction"));
			trunkProducer = session.createProducer (session.createTopic ("trunk"));
			templateProducer = session.createProducer (session.createTopic ("work"));
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
						sendTransaction (Transaction.fromProtobuf (BCSAPIMessage.Transaction.parseFrom (body)));
					}
					catch ( Exception e )
					{
						log.trace ("Rejected invalid transaction ", e);
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
						sendBlock (Block.fromProtobuf (BCSAPIMessage.Block.parseFrom (body)));
					}
					catch ( Exception e )
					{
						log.trace ("Rejected invalid block ", e);
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
						log.trace ("Rejected invalid block ", e);
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

	public void setBrokerURL (String brokerURL)
	{
		this.brokerURL = brokerURL;
	}

	public void setUser (String user)
	{
		this.user = user;
	}

	public void setPassword (String password)
	{
		this.password = password;
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
					Blk b = store.getBlock (hash);
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
		try
		{
			store.storeBlock (b);
			for ( BitcoinPeer p : network.getConnectPeers () )
			{
				BlockMessage bm = (BlockMessage) p.createMessage ("block");
				bm.setBlock (b);
				p.send (bm);
			}
		}
		catch ( ValidationException e )
		{
			log.info ("Attempt to send invalid block " + b.getHash ());
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
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	@Override
	public void workOn (Block template)
	{
		try
		{
			BytesMessage m = session.createBytesMessage ();
			m.writeBytes (template.toProtobuf ().toByteArray ());
			templateProducer.send (m);
		}
		catch ( JMSException e )
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
}
