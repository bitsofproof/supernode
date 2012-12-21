package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.AccountPosting;
import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class ImplementBCSAPI implements BCSAPI
{
	private static final Logger log = LoggerFactory.getLogger (ImplementBCSAPI.class);

	private BlockStore store;
	private BitcoinNetwork network;
	private PlatformTransactionManager transactionManager;

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void setStore (BlockStore store)
	{
		this.store = store;
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
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

	@Override
	public void sendTransaction (Transaction transaction) throws ValidationException
	{
		log.trace ("send transaction " + transaction.getHash ());
		WireFormat.Writer writer = new WireFormat.Writer ();
		transaction.toWire (writer);
		Tx t = new Tx ();
		t.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		network.sendTransaction (t);
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
}
