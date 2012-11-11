package com.bitsofproof.supernode.plugins.bccapi;

import java.util.ArrayList;
import java.util.List;

import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Hash;

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
}
