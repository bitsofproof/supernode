package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.model.JpaBlock;
import hu.blummers.bitcoin.model.JpaTransaction;

import java.util.List;

public interface ChainStore {
	public void cache ();
	public int getChainHeight ();
	public void resetStore (Chain chain);
	public String getHeadHash ();	
	public void store (JpaBlock block) throws ValidationException;	
	public List<String> getLocator ();
	public List<String> getRequests (BitcoinPeer peer);
	public int getNumberOfPeersWorking ();
	public void addInventory (String hash, BitcoinPeer peer);
	public JpaBlock get (String hash);
	public List<JpaTransaction> getTransactions (String hash);
	public void removePeer (BitcoinPeer peer);
}
