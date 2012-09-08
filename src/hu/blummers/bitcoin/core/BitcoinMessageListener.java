package hu.blummers.bitcoin.core;

public interface BitcoinMessageListener {
	public void process (BitcoinMessage m, BitcoinPeer peer);
}
