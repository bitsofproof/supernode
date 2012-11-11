package com.bitsofproof.supernode.plugins.bccapi;

import java.util.ArrayList;
import java.util.List;

class Client
{
	private long lastSeen;
	private final List<byte[]> keys = new ArrayList<byte[]> ();

	public Client (long loginTime, byte[] pubkey)
	{
		this.lastSeen = loginTime;
		keys.add (pubkey);
	}

	public long getLastSeen ()
	{
		return lastSeen;
	}

	private void touch ()
	{
		lastSeen = System.currentTimeMillis () / 1000;
	}

	public void addKey (byte[] pubkey)
	{
		keys.add (pubkey);
		touch ();
	}

}
