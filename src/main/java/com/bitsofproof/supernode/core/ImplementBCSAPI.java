package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class ImplementBCSAPI implements BCSAPI
{
	private BlockStore store;
	private BitcoinNetwork network;

	public void setStore (BlockStore store)
	{
		this.store = store;
	}

	public void setNetwork (BitcoinNetwork network)
	{
		this.network = network;
	}

	@Override
	public Block getBlock (String hash)
	{
		Blk b = store.getBlock (hash);
		WireFormat.Writer writer = new WireFormat.Writer ();
		b.toWire (writer);
		return Block.fromWire (new WireFormat.Reader (writer.toByteArray ()));
	}

	@Override
	public Transaction getTransaction (String hash)
	{
		Tx t = store.getTransaction (hash);
		WireFormat.Writer writer = new WireFormat.Writer ();
		t.toWire (writer);
		return Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
	}

	@Override
	public String getTrunk ()
	{
		return store.getHeadHash ();
	}

	@Override
	public String getPreviousBlockHash (String hash)
	{
		return store.getPreviousBlockHash (hash);
	}

	@Override
	public List<TransactionOutput> getBalance (List<String> address)
	{
		List<TransactionOutput> outs = new ArrayList<TransactionOutput> ();
		List<TxOut> utxo = store.getUnspentOutput (address);
		for ( TxOut o : utxo )
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			o.toWire (writer);
			TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			out.setTransactionHash (o.getTxHash ());
			outs.add (out);
		}
		return outs;
	}

	@Override
	public void sendTransaction (Transaction transaction) throws ValidationException
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		transaction.toWire (writer);
		Tx t = new Tx ();
		t.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		network.sendTransaction (t);
	}

	@Override
	public List<TransactionOutput> getReceivedTransactions (List<String> address)
	{
		List<TransactionOutput> outs = new ArrayList<TransactionOutput> ();
		List<TxOut> recvd = store.getReceived (address);
		for ( TxOut o : recvd )
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			o.toWire (writer);
			TransactionOutput out = TransactionOutput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			out.setTransactionHash (o.getTxHash ());
			outs.add (out);
		}
		return outs;
	}

	@Override
	public List<TransactionInput> getSpentTransactions (List<String> address)
	{
		List<TransactionInput> ins = new ArrayList<TransactionInput> ();
		List<TxIn> spent = store.getSpent (address);
		for ( TxIn i : spent )
		{
			WireFormat.Writer writer = new WireFormat.Writer ();
			i.toWire (writer);
			TransactionInput in = TransactionInput.fromWire (new WireFormat.Reader (writer.toByteArray ()));
			in.setTransactionHash (i.getTxHash ());
			ins.add (in);
		}
		return ins;
	}

}
