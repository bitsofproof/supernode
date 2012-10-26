package com.bitsofproof.supernode.messages;

import java.security.SecureRandom;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class PingMessage extends BitcoinPeer.Message
{
	public long nonce;

	public PingMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("ping");
		nonce = new SecureRandom ().nextLong ();
	}

	@Override
	public void toWire (Writer writer)
	{
		if ( getVersion () > 60000 )
		{
			writer.writeUint64 (nonce);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		if ( getVersion () > 60000 )
		{
			nonce = reader.readUint64 ();
		}
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
