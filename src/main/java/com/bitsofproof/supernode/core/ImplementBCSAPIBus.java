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

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Block;
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
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			transactionProducer = session.createProducer (session.createTopic ("transaction"));
			trunkProducer = session.createProducer (session.createTopic ("trunk"));
			templateProducer = session.createProducer (session.createTopic ("work"));
			addMessageListener ("newTransaction", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					TextMessage o = (TextMessage) arg0;
					try
					{
						sendTransaction (Transaction.fromJSON (new JSONObject (o.getText ())));
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
					TextMessage o = (TextMessage) arg0;
					try
					{
						sendBlock (Block.fromJSON (new JSONObject (o.getText ())));
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
			Transaction t = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			TextMessage m = session.createTextMessage (t.toJSON ().toString ());
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
			TextMessage m = session.createTextMessage (template.toJSON ().toString ());
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
			TextMessage om = session.createTextMessage (tu.toJSON ().toString ());
			trunkProducer.send (om);
		}
		catch ( Exception e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}
}
