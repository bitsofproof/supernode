package com.bitsofproof.supernode.core;

public interface BitcoinPeerListener
{
	void remove (BitcoinPeer peer);

	void add (BitcoinPeer peer);
}
