package com.bitsofproof.supernode.testbox;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
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
import com.bitsofproof.supernode.core.TxListener;
import com.bitsofproof.supernode.model.LvlMemoryStore;
import com.bitsofproof.supernode.model.LvlStore;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.wallet.Address;

public class APIServerInABox
{
	private static final Logger log = LoggerFactory.getLogger (APIServerInABox.class);
	private final BitcoinNetwork network;
	private final InMemoryBusConnectionFactory connectionFactory = new InMemoryBusConnectionFactory ();
	private final Chain chain;
	private final TxHandler txhandler;
	private final ImplementBCSAPI bcsapi;
	private final JMSServerConnector api;
	private Address newCoinsAddress;

	public APIServerInABox (Chain chain) throws IOException, ValidationException
	{
		this.chain = chain;
		LvlStore store = new LvlStore ();
		store.setStore (new LvlMemoryStore ());
		FixedAddressDiscovery discovery = new FixedAddressDiscovery ();
		discovery.setConnectTo ("localhost");
		network = new BitcoinNetwork (0);
		network.setChain (chain);
		network.setStore (store);
		network.setListen (false);
		network.setDiscovery (discovery);
		txhandler = new TxHandler (network);
		api = new JMSServerConnector ();
		api.setConnectionFactory (connectionFactory);
		bcsapi = new ImplementBCSAPI (network, txhandler);
		bcsapi.setConnectionFactory (connectionFactory);
		reset ();
		bcsapi.init ();
		api.init ();
	}

	public void reset () throws ValidationException
	{
		network.getStore ().resetStore (chain);
		network.getStore ().cache (chain, 0);
		txhandler.clear ();
	}

	public void setNewCoinsAddress (Address a)
	{
		newCoinsAddress = a;
	}

	public void mine (long milisBetweenBlocks, int nblocks)
	{
		String previousHash = chain.getGenesis ().getHash ();
		final List<Block> current = new ArrayList<Block> ();
		txhandler.addTransactionListener (new TxListener ()
		{
			@Override
			public void process (Tx transaction, boolean doubleSpend)
			{
				Transaction t = Transaction.fromWireDump (transaction.toWireDump ());
				synchronized ( current )
				{
					while ( current.isEmpty () )
					{
						try
						{
							current.wait ();
						}
						catch ( InterruptedException e )
						{
						}
					}
					current.get (0).getTransactions ().add (t);
				}
			}
		});
		for ( int blockHeight = 1; blockHeight <= nblocks; ++blockHeight )
		{
			Transaction coinbase = null;
			try
			{
				coinbase = Transaction.createCoinbase (newCoinsAddress, 50 * 100000000L, blockHeight);
				Block block = createBlock (previousHash, coinbase);
				synchronized ( current )
				{
					current.clear ();
					current.add (block);
					current.notify ();

					try
					{
						current.wait (milisBetweenBlocks);
					}
					catch ( InterruptedException e )
					{
						return;
					}

					mineBlock (block);
					previousHash = block.getHash ();
				}
				api.sendBlock (block);
			}
			catch ( ValidationException | BCSAPIException e )
			{
				log.error ("Server in a box ", e);
			}
		}

	}

	public BitcoinNetwork getNetwork ()
	{
		return network;
	}

	public InMemoryBusConnectionFactory getConnectionFactory ()
	{
		return connectionFactory;
	}

	public Chain getChain ()
	{
		return chain;
	}

	public BCSAPI getAPI ()
	{

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
