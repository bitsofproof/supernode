package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;

public interface BitcoinMessageListener {
	public void process (BitcoinMessage m, BitcoinPeer peer);
}
