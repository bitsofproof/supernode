package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;

public interface BitcoinMessageListener
{
	public void process (BitcoinPeer.Message m, BitcoinPeer peer) throws Exception;
}
