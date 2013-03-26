/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.messages;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.api.WireFormat.Reader;
import com.bitsofproof.supernode.api.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

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
		writer.writeVarInt (n);
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
