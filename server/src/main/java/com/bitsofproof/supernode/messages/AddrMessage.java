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

import com.bitsofproof.supernode.common.WireFormat;
import com.bitsofproof.supernode.common.WireFormat.Reader;
import com.bitsofproof.supernode.common.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

public class AddrMessage extends BitcoinPeer.Message
{

	public AddrMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("addr");
	}

	private List<WireFormat.Address> addresses = new ArrayList<WireFormat.Address> ();

	@Override
	public void toWire (Writer writer)
	{
		writer.writeVarInt (addresses.size ());
		for ( WireFormat.Address a : addresses )
		{
			writer.writeAddress (a, getVersion (), false);
		}
	}

	@Override
	public void fromWire (Reader reader)
	{
		long n = reader.readVarInt ();
		for ( long i = 0; i < n; ++i )
		{
			addresses.add (reader.readAddress (getVersion (), false));
		}
	}

	public List<WireFormat.Address> getAddresses ()
	{
		return addresses;
	}

	public void setAddresses (List<WireFormat.Address> addresses)
	{
		this.addresses = addresses;
	}

}
