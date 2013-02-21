package com.bitsofproof.supernode.test;

import java.net.InetAddress;
import java.util.List;

import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.PeerStore;
import com.bitsofproof.supernode.core.TrunkListener;
import com.bitsofproof.supernode.core.TxOutCache;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.KnownPeer;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class TestStore implements BlockStore, PeerStore
{

	@Override
	public List<KnownPeer> getConnectablePeers ()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void store (KnownPeer peer)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public KnownPeer findPeer (InetAddress address)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void cache (Chain chain, int cacheSize) throws ValidationException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void runInCacheContext (CacheContextRunnable runnable)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void addTrunkListener (TrunkListener l)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public String getPreviousBlockHash (String hash)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getPeriodLength (String previousHash, int reviewPeriod)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<String> getLocator ()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TxIn> getSpent (List<String> addresses, long from)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TxOut> getReceived (List<String> addresses, long from)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void storeBlock (Blk b) throws ValidationException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public String getHeadHash ()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetStore (Chain chain)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public Blk getBlock (String hash)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Tx getTransaction (String hash)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isStoredBlock (String hash)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean validateTransaction (Tx tx, TxOutCache resolvedInputs) throws ValidationException
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void resolveTransactionInputs (Tx tx, TxOutCache resolvedInputs) throws ValidationException
	{
		// TODO Auto-generated method stub

	}

	@Override
	public long getChainHeight ()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isEmpty ()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<String> getInventory (List<String> locator, String last, int limit)
	{
		// TODO Auto-generated method stub
		return null;
	}

}
