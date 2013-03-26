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

public class GetHeadersMessage extends BitcoinPeer.Message
{

	List<byte[]> locators = new ArrayList<byte[]> ();
	byte[] stop = new byte[32];

	public GetHeadersMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("getheaders");
	}

	@Override
	public void toWire (Writer writer)
	{
		writer.writeUint32 (getVersion ());
		writer.writeVarInt (locators.size ());
		for ( byte[] l : locators )
		{
			writer.writeBytes (l);
		}
		writer.writeBytes (stop);
	}

	@Override
	public void fromWire (Reader reader)
	{
		setVersion (reader.readUint32 ());
		long n = reader.readVarInt ();
		for ( long i = 0; i < n; ++i )
		{
			locators.add (reader.readBytes (32));
		}
		stop = reader.readBytes (32);
	}

	public List<byte[]> getLocators ()
	{
		return locators;
	}

	public void setLocators (List<byte[]> locators)
	{
		this.locators = locators;
	}

	public byte[] getStop ()
	{
		return stop;
	}

	public void setStop (byte[] stop)
	{
		this.stop = stop;
	}
}
