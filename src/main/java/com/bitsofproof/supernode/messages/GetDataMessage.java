/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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
