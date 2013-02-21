package com.bitsofproof.supernode.core;

import java.net.InetAddress;
import java.util.List;

import com.bitsofproof.supernode.model.KnownPeer;

public interface PeerStore
{

	public List<KnownPeer> getConnectablePeers ();

	public void store (KnownPeer peer);

	public KnownPeer findPeer (InetAddress address);
}