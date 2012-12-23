package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.AccountPosting;
import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.BCSAPIDirect;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class ImplementBCSAPI implements BCSAPIDirect, TrunkListener, TransactionListener, TemplateListener
{
	private static final Logger log = LoggerFactory.getLogger (ImplementBCSAPI.class);

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
	private MessageProducer extendProducer;
	private MessageProducer revertProducer;
	private MessageProducer templateProducer;

	public ImplementBCSAPI (BitcoinNetwork network, TxHandler txHandler, BlockTemplater blockTemplater)
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
			extendProducer = session.createProducer (session.createTopic ("extend"));
			revertProducer = session.createProducer (session.createTopic ("revert"));
			templateProducer = session.createProducer (session.createTopic ("work"));
			addMessageListener ("newTransaction", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					ObjectMessage o = (ObjectMessage) arg0;
					try
					{
						sendTransaction ((Transaction) o.getObject ());
					}
					catch ( ValidationException e )
					{
						log.trace ("Rejected invalid transaction ", e);
					}
					catch ( JMSException e )
					{
						log.trace ("Rejected invalid transaction", e);
					}
				}
			});
			addMessageListener ("newBlock", new MessageListener ()
			{
				@Override
				public void onMessage (Message arg0)
				{
					ObjectMessage o = (ObjectMessage) arg0;
					try
					{
						sendBlock ((Block) o.getObject ());
					}
					catch ( ValidationException e )
					{
						log.trace ("Rejected invalid block ", e);
					}
					catch ( JMSException e )
					{
						log.trace ("Rejected invalid block", e);
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

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	@Override
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
					b.toWire (writer);
				}
			});
			return Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		}
		finally
		{
			log.trace ("get block returned " + hash);
		}
	}

	@Override
	public Transaction getTransaction (final String hash)
	{
		try
		{
			log.trace ("get transaction " + hash);
			final WireFormat.Writer writer = new WireFormat.Writer ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					Tx t = store.getTransaction (hash);
					t.toWire (writer);
				}
			});
			return Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		}
		finally
		{
			log.trace ("get transaction returned " + hash);
		}
	}

	@Override
	public String getTrunk ()
	{
		log.trace ("get trunk ");
		return store.getHeadHash ();
	}

	@Override
	public String getPreviousBlockHash (String hash)
	{
		log.trace ("get previous block " + hash);
		return store.getPreviousBlockHash (hash);
	}

	@Override
	public List<TransactionOutput> getBalance (final List<String> address)
	{
		try
		{
			log.trace ("get balance ");
			final List<TransactionOutput> outs = new ArrayList<TransactionOutput> ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					List<TxOut> utxo = store.getUnspentOutput (address);
					for ( TxOut o : utxo )
					{
						WireFormat.Writer writer = new WireFormat.Writer ();
						o.toWire (writer);
						TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
						out.setTransactionHash (o.getTxHash ());
						outs.add (out);
					}
				}
			});
			return outs;
		}
		finally
		{
			log.trace ("get balance returned");
		}
	}

	public void sendTransaction (Transaction transaction) throws ValidationException
	{
		log.trace ("send transaction " + transaction.getHash ());
		WireFormat.Writer writer = new WireFormat.Writer ();
		transaction.toWire (writer);
		Tx t = new Tx ();
		t.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		txhandler.sendTransaction (t, null);
	}

	public void sendBlock (Block block) throws ValidationException
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
	public long getHeartbeat (long mine)
	{
		log.trace ("get heartbeat");
		return mine;
	}

	@Override
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
					statement.setOpeningBalances (balances);

					Blk trunk = store.getBlock (store.getHeadHash ());
					statement.setExtracted (trunk.getCreateTime ());
					statement.setMostRecentBlock (store.getHeadHash ());
					statement.setOpening (statement.getExtracted ());

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

					List<AccountPosting> postings = new ArrayList<AccountPosting> ();
					statement.setPostings (postings);

					log.trace ("retrieve spent");
					for ( TxIn spent : store.getSpent (addresses, from) )
					{
						AccountPosting p = new AccountPosting ();
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
						p.setReceived (out);
					}

					log.trace ("retrieve received");
					for ( TxOut o : store.getReceived (addresses, from) )
					{
						AccountPosting p = new AccountPosting ();
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
						p.setReceived (out);
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
					Collections.sort (postings, new Comparator<AccountPosting> ()
					{
						@Override
						public int compare (AccountPosting arg0, AccountPosting arg1)
						{
							if ( arg0.getTimestamp () != arg1.getTimestamp () )
							{
								return (int) (arg0.getTimestamp () - arg1.getTimestamp ());
							}
							else
							{
								if ( arg0.getReceived () != null && arg1.getSpent () == null )
								{
									return -1;
								}
								if ( arg0.getReceived () == null && arg1.getSpent () != null )
								{
									return 1;
								}
								return 0;
							}
						}
					});
				}
			});
			return statement;
		}
		finally
		{
			log.trace ("get account statement returned");
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
			ObjectMessage m = session.createObjectMessage (t);
			transactionProducer.send (m);
		}
		catch ( JMSException e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	@Override
	public void workOn (Block template)
	{
		try
		{
			ObjectMessage m = session.createObjectMessage (template);
			templateProducer.send (m);
		}
		catch ( JMSException e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	@Override
	public void trunkExtended (Blk blk)
	{
		try
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			blk.toWire (writer);
			Block b = Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			ObjectMessage m = session.createObjectMessage (b);
			extendProducer.send (m);
		}
		catch ( JMSException e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}

	@Override
	public void trunkShortened (Blk blk)
	{
		try
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			blk.toWire (writer);
			Block b = Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			ObjectMessage m = session.createObjectMessage (b);
			revertProducer.send (m);
		}
		catch ( JMSException e )
		{
			log.error ("Can not send JMS message ", e);
		}
	}
}
