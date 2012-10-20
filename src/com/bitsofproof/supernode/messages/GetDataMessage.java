package com.bitsofproof.supernode.messages;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class GetDataMessage extends BitcoinPeer.Message
{

	List<byte[]> blocks = new ArrayList<byte[]> ();
	List<byte[]> transactions = new ArrayList<byte[]> ();

	public GetDataMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("getdata");
	}

	@Override
	public void toWire (Writer writer)
	{
		writer.writeVarInt (blocks.size () + transactions.size ());
		for ( byte[] h : transactions )
		{
			writer.writeUint32 (1);
			writer.writeBytes (h);
		}
		for ( byte[] h : blocks )
		{
			writer.writeUint32 (2);
			writer.writeBytes (h);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		long n = reader.readVarInt ();
		for ( int i = 0; i < n; ++i )
		{
			long type = reader.readUint32 ();
			if ( type == 1 )
			{
				transactions.add (reader.readBytes (32));
			}
			else
			{
				blocks.add (reader.readBytes (32));
			}
		}
	}

	public List<byte[]> getBlocks ()
	{
		return blocks;
	}

	public void setBlocks (List<byte[]> blocks)
	{
		this.blocks = blocks;
	}

	public List<byte[]> getTransactions ()
	{
		return transactions;
	}

	public void setTransactions (List<byte[]> transactions)
	{
		this.transactions = transactions;
	}
}
