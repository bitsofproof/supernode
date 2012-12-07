package com.bitsofproof.supernode.model;

import java.net.InetAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.StoredPeerDiscovery;

public class LvlPeerStore extends StoredPeerDiscovery
{
	private static final Logger log = LoggerFactory.getLogger (LvlPeerStore.class);

	@Override
	public List<KnownPeer> getConnectablePeers ()
	{
		return null;
	}

	@Override
	public void store (KnownPeer peer)
	{
	}

	@Override
	public KnownPeer findPeer (InetAddress address)
	{
		return null;
	}
}
