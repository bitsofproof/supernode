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
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientBusAdaptor implements BCSAPIBus
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);
	private Connection connection;
	private Session session;

	private String brokerURL;
	private String user;
	private String password;
	private String clientId;

	private final List<TransactionListener> transactionListener = Collections.synchronizedList (new ArrayList<TransactionListener> ());
	private final List<TrunkListener> trunkListener = Collections.synchronizedList (new ArrayList<TrunkListener> ());
	private final List<TemplateListener> blockTemplateListener = Collections.synchronizedList (new ArrayList<TemplateListener> ());
	private final Map<String, ArrayList<TransactionListener>> addressListener = Collections
			.synchronizedMap (new HashMap<String, ArrayList<TransactionListener>> ());

	private MessageProducer transactionProducer;
	private MessageProducer blockProducer;

	public void setClientId (String clientId)
	{
		this.clientId = clientId;
	}

	public void setBrokerURL (String brokerUrl)
	{
		this.brokerURL = brokerUrl;
	}

	public void setUser (String user)
	{
		this.user = user;
	}

	public void setPassword (String password)
	{
		this.password = password;
	}

	private void addMessageListener (String topic, MessageListener listener) throws JMSException
	{
		Destination destination = session.createTopic (topic);
		MessageConsumer consumer = session.createConsumer (destination);
		consumer.setMessageListener (listener);
	}

	public void init ()
	{
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory (user, password, brokerURL);
		try
		{
			connection = connectionFactory.createConnection ();
			connection.setClientID (clientId);
			connection.start ();
			session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
			addMessageListener ("transaction", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					ObjectMessage o = (ObjectMessage) arg0;
					for ( TransactionListener l : transactionListener )
					{
						try
						{
							l.process ((Transaction) o.getObject ());
						}
						catch ( JMSException e )
						{
							log.error ("Transaction message error", e);
						}
					}
				}
			});
			addMessageListener ("trunk", new MessageListener ()
			{
				@SuppressWarnings ("unchecked")
				@Override
				public void onMessage (Message arg0)
				{
					MapMessage o = (MapMessage) arg0;
					for ( TrunkListener l : trunkListener )
					{
						try
						{
							l.trunkUpdate ((List<Block>) o.getObject ("removed"), (List<Block>) o.getObject ("added"));
						}
						catch ( JMSException e )
						{
							log.error ("Block message error", e);
						}
					}
				}
			});
			addMessageListener ("work", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					ObjectMessage o = (ObjectMessage) arg0;
					for ( TemplateListener l : blockTemplateListener )
					{
						try
						{
							l.workOn ((Block) o.getObject ());
						}
						catch ( JMSException e )
						{
							log.error ("Block message error", e);
						}
					}
				}
			});
			transactionProducer = session.createProducer (session.createTopic ("newTransaction"));
			blockProducer = session.createProducer (session.createTopic ("newBlock"));
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
	public void registerTransactionListener (TransactionListener listener)
	{
		transactionListener.add (listener);
	}

	@Override
	public void registerTrunkListener (TrunkListener listener)
	{
		trunkListener.add (listener);
	}

	@Override
	public void registerBlockTemplateListener (TemplateListener listener)
	{
		blockTemplateListener.add (listener);
	}

	@Override
	public void registerAccountListener (List<String> addresses, TransactionListener listener)
	{
		try
		{
			for ( final String address : addresses )
			{
				ArrayList<TransactionListener> al = addressListener.get (address);
				if ( al == null )
				{
					al = new ArrayList<TransactionListener> ();
					addressListener.put (address, al);

					Destination blockDestination = session.createTopic (address);
					MessageConsumer blockConsumer = session.createConsumer (blockDestination);
					blockConsumer.setMessageListener (new MessageListener ()
					{
						@Override
						public void onMessage (Message arg0)
						{
							ObjectMessage o = (ObjectMessage) arg0;
							for ( TransactionListener l : addressListener.get (address) )
							{
								try
								{
									l.process ((Transaction) o.getObject ());
								}
								catch ( JMSException e )
								{
									log.error ("Transaction message error", e);
								}
							}
						}
					});
				}
				al.add (listener);
			}
		}
		catch ( JMSException e )
		{
			log.error ("Can not create JMS session", e);
		}
	}

	@Override
	public void sendTransaction (Transaction transaction)
	{
		try
		{
			transactionProducer.send (session.createObjectMessage (transaction));
		}
		catch ( JMSException e )
		{
			log.error ("Can not send transaction", e);
		}
	}

	@Override
	public void sendBlock (Block block)
	{
		try
		{
			blockProducer.send (session.createObjectMessage (block));
		}
		catch ( JMSException e )
		{
			log.error ("Can not send transaction", e);
		}
	}
}
