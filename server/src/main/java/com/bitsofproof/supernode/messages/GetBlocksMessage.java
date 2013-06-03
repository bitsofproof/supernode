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

import com.bitsofproof.supernode.common.WireFormat.Reader;
import com.bitsofproof.supernode.common.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

public class GetBlocksMessage extends BitcoinPeer.Message
{

	public GetBlocksMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("getblocks");
	}

	private List<byte[]> hashes = new ArrayList<byte[]> ();
	private byte[] lastHash = new byte[32];

	@Override
	public void toWire (Writer writer)
	{
		writer.writeUint32 (getVersion ());
		writer.writeVarInt (hashes.size ());
		for ( byte[] h : hashes )
		{
			writer.writeBytes (h);
		}
		writer.writeBytes (new byte[32]);
	}

	@Override
	public void fromWire (Reader reader)
	{
		setVersion (reader.readUint32 ());
		long n = reader.readVarInt ();
		for ( long i = 0; i < n; ++i )
		{
			hashes.add (reader.readBytes (32));
		}
	}

	public List<byte[]> getHashes ()
	{
		return hashes;
	}

	public void setHashes (List<byte[]> hashes)
	{
		this.hashes = hashes;
	}

	public byte[] getLastHash ()
	{
		return lastHash;
	}

	public void setLastHash (byte[] lastHash)
	{
		this.lastHash = lastHash;
	}

}
