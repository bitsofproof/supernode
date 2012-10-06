package hu.blummers.bitcoin.core;

public interface BitcoinPeerListener {
	void remove (BitcoinPeer peer);
	void add (BitcoinPeer peer);
}
