package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BCSAPIClientSide implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (BCSAPIClientSide.class);

	private BCSAPIRemoteCalls remote;
	private Connection connection;
	private Session session;

	private String brokerURL;
	private String user;
	private String password;
	private String clientId;

	private final List<TransactionListener> transactionListener = Collections.synchronizedList (new ArrayList<TransactionListener> ());
	private final List<BlockListener> blockListener = Collections.synchronizedList (new ArrayList<BlockListener> ());
	private final Map<String, ArrayList<TransactionListener>> addressListener = Collections
			.synchronizedMap (new HashMap<String, ArrayList<TransactionListener>> ());

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

	public void setRemote (BCSAPIRemoteCalls proxy)
	{
		this.remote = proxy;
	}

	@Override
	public long getHeartbeat (long mine)
	{
		return remote.getHeartbeat (mine);
	}

	@Override
	public Block getBlock (String hash)
	{
		return remote.getBlock (hash);
	}

	@Override
	public Transaction getTransaction (String hash)
	{
		return remote.getTransaction (hash);
	}

	@Override
	public String getTrunk ()
	{
		return remote.getTrunk ();
	}

	@Override
	public String getPreviousBlockHash (String hash)
	{
		return remote.getPreviousBlockHash (hash);
	}

	@Override
	public List<TransactionOutput> getBalance (List<String> address)
	{
		return remote.getBalance (address);
	}

	@Override
	public void sendTransaction (Transaction transaction) throws ValidationException
	{
		remote.sendTransaction (transaction);
	}

	@Override
	public void sendBlock (Block block) throws ValidationException
	{
		remote.sendBlock (block);
	}

	@Override
	public AccountStatement getAccountStatement (List<String> addresses, long from)
	{
		return remote.getAccountStatement (addresses, from);
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
			Destination transactionDestination = session.createTopic ("transaction");
			MessageConsumer transactionConsumer = session.createConsumer (transactionDestination);
			transactionConsumer.setMessageListener (new MessageListener ()
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
			Destination blockDestination = session.createTopic ("block");
			MessageConsumer blockConsumer = session.createConsumer (blockDestination);
			blockConsumer.setMessageListener (new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					ObjectMessage o = (ObjectMessage) arg0;
					for ( BlockListener l : blockListener )
					{
						try
						{
							l.process ((Block) o.getObject ());
						}
						catch ( JMSException e )
						{
							log.error ("Block message error", e);
						}
					}
				}
			});
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
	public void registerBlockListener (BlockListener listener)
	{
		blockListener.add (listener);
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
}
