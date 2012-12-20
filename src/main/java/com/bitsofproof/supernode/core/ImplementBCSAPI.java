package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
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
	public List<TransactionOutput> getBalance (final List<String> address, final long asOf)
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
					List<TxOut> utxo = store.getUnspentOutput (address, asOf);
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
	public List<TransactionOutput> getReceivedTransactions (final List<String> address, final long after)
	{
		try
		{
			log.trace ("get received");
			final List<TransactionOutput> outs = new ArrayList<TransactionOutput> ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					List<TxOut> recvd = store.getReceived (address, after);
					for ( TxOut o : recvd )
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
			log.trace ("get received returned");
		}
	}

	@Override
	public List<TransactionInput> getSpentTransactions (final List<String> address, final long after)
	{
		try
		{
			log.trace ("get spent");
			final List<TransactionInput> ins = new ArrayList<TransactionInput> ();
			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus status)
				{
					status.setRollbackOnly ();
					List<TxIn> spent = store.getSpent (address, after);
					for ( TxIn i : spent )
					{
						WireFormat.Writer writer = new WireFormat.Writer ();
						i.toWire (writer);
						TransactionInput in = TransactionInput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
						in.setTransactionHash (i.getTxHash ());
						ins.add (in);
					}
				}
			});
			return ins;
		}
		finally
		{
			log.trace ("get spent returned");
		}
	}

	@Override
	public long getHeartbeat (long mine)
	{
		log.trace ("get heartbeat");
		return mine;
	}

}
