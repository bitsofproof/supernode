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

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class HeadersMessage extends BitcoinPeer.Message
{

	public HeadersMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("headers");
	}

	private List<byte[]> blockHeader = new ArrayList<byte[]> ();

	public List<byte[]> getBlockHeader ()
	{
		return blockHeader;
	}

	public void setBlockHeader (List<byte[]> blockHeader)
	{
		this.blockHeader = blockHeader;
	}

	@Override
	public void toWire (Writer writer)
	{
		writer.writeVarInt (blockHeader.size ());
		for ( byte[] b : blockHeader )
		{
			writer.writeBytes (b);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		int n = (int) Math.min (reader.readVarInt (), 2000L);
		for ( int i = 0; i < n; ++i )
		{
			blockHeader.add (reader.readBytes (81));
		}
	}
}
