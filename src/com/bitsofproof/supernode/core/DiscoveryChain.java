package com.bitsofproof.supernode.core;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryChain implements Discovery
{
	private final List<Discovery> discoveryChain;

	public DiscoveryChain (List<Discovery> chain)
	{
		discoveryChain = chain;
	}

	@Override
	public List<InetAddress> discover ()
	{
		for ( Discovery discovery : discoveryChain )
		{
			List<InetAddress> addresses = discovery.discover ();
			if ( !addresses.isEmpty () )
			{
				return addresses;
			}
		}
		return new ArrayList<InetAddress> ();
	}
}
