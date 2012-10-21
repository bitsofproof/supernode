package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;
import com.bitsofproof.supernode.model.Tx;

public class TxMessage extends BitcoinPeer.Message
{
	Tx tx = new Tx ();

	public TxMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("tx");
	}

	@Override
	public void validate () throws ValidationException
	{
		// TODO Auto-generated method stub
		super.validate ();
	}

	@Override
	public void toWire (Writer writer)
	{
		tx.toWire (writer);
	}

	@Override
	public void fromWire (Reader reader)
	{
		tx.fromWire (reader);
	}

}
