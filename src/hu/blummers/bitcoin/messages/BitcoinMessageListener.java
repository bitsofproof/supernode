package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;

public interface BitcoinMessageListener {
	public void process (BitcoinPeer.Message m, BitcoinPeer peer);
}
