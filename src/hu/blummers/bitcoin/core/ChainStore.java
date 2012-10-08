package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.model.JpaBlock;
import hu.blummers.bitcoin.model.JpaTransaction;

import java.util.List;
import java.util.Set;

public interface ChainStore {
	public void cache ();
	public long getChainHeight ();
	public void resetStore (Chain chain);
	public String getHeadHash ();	
	public long store (JpaBlock block) throws ValidationException;	
	public List<String> getLocator ();
	public List<String> getRequests (BitcoinPeer peer);
	public int getNumberOfRequests (BitcoinPeer peer);
	public void addInventory (List<String> hash, BitcoinPeer peer);
	public JpaBlock get (String hash);
	public void removePeer (BitcoinPeer peer);
}
