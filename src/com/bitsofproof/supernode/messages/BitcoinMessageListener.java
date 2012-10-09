package com.bitsofproof.supernode.messages;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.ValidationException;

public interface BitcoinMessageListener {
	public void process (BitcoinPeer.Message m, BitcoinPeer peer) throws ValidationException;
}
