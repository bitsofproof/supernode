package com.bitsofproof.supernode.model;

import java.util.List;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.ValidationException;

public interface ChainStore
{

	public void cache ();

	public void addInventory (List<String> hashes, BitcoinPeer peer);

	public List<String> getRequests (BitcoinPeer peer);

	public void removePeer (BitcoinPeer peer);

	public List<String> getLocator ();

	public long store (Blk b) throws ValidationException;

	public String getHeadHash ();

	public void resetStore (Chain chain);

	public Blk get (String hash);

	public long getChainHeight ();

	public int getNumberOfRequests (BitcoinPeer peer);

}