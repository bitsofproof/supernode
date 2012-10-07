package hu.blummers.bitcoin.messages;

import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.ValidationException;

public interface BitcoinMessageListener {
	public void process (BitcoinPeer.Message m, BitcoinPeer peer) throws ValidationException;
}
