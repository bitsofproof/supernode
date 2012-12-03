package com.bitsofproof.supernode.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.model.KnownPeer;

public abstract class StoredPeerDiscovery implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (StoredPeerDiscovery.class);

	@Override
	public abstract List<KnownPeer> getConnectablePeers ();

	@Override
	public abstract void store (KnownPeer peer);

	@Override
	public abstract KnownPeer findPeer (InetAddress address);

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering stored peers");
		List<InetAddress> peers = new ArrayList<InetAddress> ();
		for ( KnownPeer kp : getConnectablePeers () )
		{
			try
			{
				peers.add (InetAddress.getByName (kp.getAddress ()));
			}
			catch ( UnknownHostException e )
			{
			}
		}
		return peers;
	}
}
