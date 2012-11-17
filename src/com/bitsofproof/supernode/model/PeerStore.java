package com.bitsofproof.supernode.model;

import java.net.InetAddress;
import java.util.List;

public interface PeerStore
{

	public List<KnownPeer> getConnectablePeers ();

	public void store (KnownPeer peer);

	public KnownPeer findPeer (InetAddress address);
}