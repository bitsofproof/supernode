package com.bitsofproof.supernode.testbox;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.JMSServerConnector;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.InMemoryBusConnectionFactory;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.core.FixedAddressDiscovery;
import com.bitsofproof.supernode.core.ImplementBCSAPI;
import com.bitsofproof.supernode.core.TxHandler;
import com.bitsofproof.supernode.model.LvlMemoryStore;
import com.bitsofproof.supernode.model.LvlStore;

public class APIServerInABox
{
	private final BitcoinNetwork network;
	private final InMemoryBusConnectionFactory connectionFactory = new InMemoryBusConnectionFactory ();
	private final Chain chain;

	public APIServerInABox (Chain chain) throws IOException, ValidationException
	{
		this.chain = chain;
		LvlStore store = new LvlStore ();
		store.setStore (new LvlMemoryStore ());
		store.resetStore (chain);
		store.cache (chain, 0);
		FixedAddressDiscovery discovery = new FixedAddressDiscovery ();
		discovery.setConnectTo ("localhost");
		network = new BitcoinNetwork (0);
		network.setChain (chain);
		network.setStore (store);
		network.setListen (false);
		network.setDiscovery (discovery);
		TxHandler txhandler = new TxHandler (network);
		ImplementBCSAPI bcsapi = new ImplementBCSAPI (network, txhandler);
		bcsapi.setConnectionFactory (connectionFactory);
		bcsapi.init ();
	}

	public BCSAPI getAPI ()
	{
		JMSServerConnector api = new JMSServerConnector ();
		api.setConnectionFactory (connectionFactory);
		api.init ();
		return api;
	}

	public Block createBlock (String previous, Transaction coinbase)
	{
		Block block = new Block ();
		try
		{
			Thread.sleep (1001);
		}
		catch ( InterruptedException e )
		{
		}
		block.setCreateTime (System.currentTimeMillis () / 1000);
		block.setDifficultyTarget (chain.getGenesis ().getDifficultyTarget ());
		block.setPreviousHash (previous);
		block.setVersion (2);
		block.setNonce (0);
		block.setTransactions (new ArrayList<Transaction> ());
		block.getTransactions ().add (coinbase);
		return block;
	}

	public void mineBlock (Block b)
	{
		for ( int nonce = Integer.MIN_VALUE; nonce <= Integer.MAX_VALUE; ++nonce )
		{
			b.setNonce (nonce);
			b.computeHash ();
			BigInteger hashAsInteger = new Hash (b.getHash ()).toBigInteger ();
			if ( hashAsInteger.compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) <= 0 )
			{
				break;
			}
		}
	}

}
