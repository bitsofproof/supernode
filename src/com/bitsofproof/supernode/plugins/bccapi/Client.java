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
package com.bitsofproof.supernode.plugins.bccapi;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Hash;

import edu.emory.mathcs.backport.java.util.Arrays;

class Client
{
	private long lastSeen;
	private final List<byte[]> keys = new ArrayList<byte[]> ();
	private final List<String> addresses = new ArrayList<String> ();

	public Client (long loginTime, byte[] pubkey, Chain chain)
	{
		this.lastSeen = loginTime;
		keys.add (pubkey);
		addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (pubkey), false, chain));
	}

	public long getLastSeen ()
	{
		return lastSeen;
	}

	private void touch ()
	{
		lastSeen = System.currentTimeMillis () / 1000;
	}

	public List<String> getAddresses ()
	{
		return addresses;
	}

	public byte[] getKey (int n)
	{
		return keys.get (n);
	}

	public int getKeyIndex (byte[] key)
	{
		int i = 0;
		for ( byte[] k : keys )
		{
			if ( Arrays.equals (k, key) )
			{
				return i;
			}
			++i;
		}
		return -1;
	}

	public int getKeyIndexForKeyHash (byte[] kh)
	{
		int i = 0;
		for ( byte[] k : keys )
		{
			if ( Arrays.equals (Hash.keyHash (k), kh) )
			{
				return i;
			}
			++i;
		}
		return -1;
	}

	public void addKey (byte[] pubkey, Chain chain)
	{
		keys.add (pubkey);
		addresses.add (AddressConverter.toSatoshiStyle (Hash.keyHash (pubkey), false, chain));
		touch ();
	}

	public void addAddress (String address)
	{
		addresses.add (address);
	}

	public boolean hasAddress (String address)
	{
		return addresses.contains (address);
	}
}
