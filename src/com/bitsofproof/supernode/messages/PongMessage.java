package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class PongMessage extends BitcoinPeer.Message
{
	public long nonce;

	public PongMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("pong");
	}

	@Override
	public void toWire (Writer writer)
	{
		writer.writeUint64 (nonce);
	}

	@Override
	public void fromWire (Reader reader)
	{
		nonce = reader.readUint64 ();
	}

	public long getNonce ()
	{
		return nonce;
	}

	public void setNonce (long nonce)
	{
		this.nonce = nonce;
	}

}
