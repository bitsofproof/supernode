package com.bitsofproof.supernode.messages;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class InvMessage extends BitcoinPeer.Message
{

	public InvMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("inv");
	}

	List<byte[]> transactionHashes = new ArrayList<byte[]> ();
	List<byte[]> blockHashes = new ArrayList<byte[]> ();
	boolean error;

	@Override
	public void toWire (Writer writer)
	{
		if ( error )
		{
			writer.writeUint32 (1);
			writer.writeUint32 (0);
			writer.writeBytes (new byte[32]);
			return;
		}
		long n = transactionHashes.size () + blockHashes.size ();
		writer.writeUint32 (n);
		for ( byte[] b : transactionHashes )
		{
			writer.writeUint32 (1);
			writer.writeBytes (b);
		}
		for ( byte[] b : blockHashes )
		{
			writer.writeUint32 (2);
			writer.writeBytes (b);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		long numberOfEntries = reader.readVarInt ();
		for ( long i = 0; i < numberOfEntries; ++i )
		{
			long t = reader.readUint32 ();
			byte[] hash = reader.readBytes (32);
			if ( t == 1 )
			{
				transactionHashes.add (hash);
			}
			else if ( t == 2 )
			{
				blockHashes.add (hash);
			}
			else
			{
				error = true;
			}
		}
	}

	public List<byte[]> getTransactionHashes ()
	{
		return transactionHashes;
	}

	public void setTransactionHashes (List<byte[]> transactionHashes)
	{
		this.transactionHashes = transactionHashes;
	}

	public List<byte[]> getBlockHashes ()
	{
		return blockHashes;
	}

	public void setBlockHashes (List<byte[]> blockHashes)
	{
		this.blockHashes = blockHashes;
	}

	public boolean isError ()
	{
		return error;
	}

	public void setError (boolean error)
	{
		this.error = error;
	}

}
