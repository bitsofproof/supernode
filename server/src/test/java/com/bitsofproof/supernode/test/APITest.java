package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.bitsofproof.supernode.api.AccountListener;
import com.bitsofproof.supernode.api.AccountManager;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.SerializedWallet;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/memory-store.xml", "/context/EmbeddedBCSAPI.xml" })
public class APITest
{
	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	private static final long COIN = 100000000L;
	private static final long FEE = COIN / 1000L;
	private static Map<Integer, Block> blocks = new HashMap<Integer, Block> ();

	private static SerializedWallet wallet;
	private static AccountManager alice;
	private static AccountManager bob;

	private static class AccountMonitor implements AccountListener
	{
		private final Semaphore ready = new Semaphore (0);
		private final String name;

		public AccountMonitor (String name)
		{
			this.name = name;
		}

		@Override
		public synchronized void accountChanged (AccountManager account)
		{
			assertTrue (account.getName ().equals (name));
			ready.release ();
		}

		public void expectUpdates (int n)
		{
			try
			{
				assertTrue (ready.tryAcquire (n, 1, TimeUnit.SECONDS));
				assertFalse (ready.tryAcquire ());
			}
			catch ( InterruptedException e )
			{
			}
		}
	}

	private static final AccountMonitor bobMonitor = new AccountMonitor ("Bob");
	private static final AccountMonitor aliceMonitor = new AccountMonitor ("Alice");

	@BeforeClass
	public static void provider ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void init () throws BCSAPIException, ValidationException
	{
		store.resetStore (chain);
		store.cache (chain, 0);
		wallet = new SerializedWallet ();
		wallet.setApi (api);
		alice = wallet.getAccountManager ("Alice");
		bob = wallet.getAccountManager ("Bob");

		alice.addAccountListener (aliceMonitor);
		bob.addAccountListener (bobMonitor);
	}

	@Test
	public void checkGenesis () throws BCSAPIException
	{
		String genesisHash = chain.getGenesis ().getHash ();
		assertTrue (api.getBlock (genesisHash).getHash ().equals (genesisHash));
	}

	@Test
	public void send1Block () throws BCSAPIException, ValidationException
	{
		Block block = createBlock (chain.getGenesis ().getHash (), Transaction.createCoinbase (alice.getNextKey (), 50 * COIN, 1));
		mineBlock (block);
		blocks.put (1, block);

		final String hash = block.getHash ();

		final Semaphore ready = new Semaphore (0);

		TrunkListener listener = new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				Block got = added.get (0);
				got.computeHash ();
				assertTrue (got.getHash ().equals (hash));
				ready.release ();
			}
		};

		api.registerTrunkListener (listener);

		api.sendBlock (block);

		try
		{
			assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
			api.removeTrunkListener (listener);
		}
		catch ( InterruptedException e )
		{
		}
		aliceMonitor.expectUpdates (1);
	}

	@Test
	public void send10Blocks () throws ValidationException, BCSAPIException
	{
		final Semaphore ready = new Semaphore (0);

		TrunkListener listener = new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				ready.release (added.size ());
			}
		};

		api.registerTrunkListener (listener);

		String hash = blocks.get (1).getHash ();
		for ( int i = 0; i < 10; ++i )
		{
			Block block = createBlock (hash, Transaction.createCoinbase (alice.getNextKey (), 5000000000L, i + 2));
			block.setCreateTime (block.getCreateTime () + (i + 1) * 1000); // avoid clash of timestamp with median
			mineBlock (block);
			blocks.put (i + 2, block);
			hash = block.getHash ();
			api.sendBlock (block);
		}

		try
		{
			assertTrue (ready.tryAcquire (10, 2, TimeUnit.SECONDS));
			api.removeTrunkListener (listener);
		}
		catch ( InterruptedException e )
		{
		}
		aliceMonitor.expectUpdates (10);
	}

	@Test
	public void spendSome () throws BCSAPIException, ValidationException
	{
		long aliceStartingBalance = alice.getBalance ();
		Transaction spend = alice.pay (bob.getNextKey ().getAddress (), 50 * COIN, FEE);
		api.sendTransaction (spend);
		aliceMonitor.expectUpdates (1);
		bobMonitor.expectUpdates (1);
		assertTrue (bob.getBalance () == 50 * COIN);
		assertTrue (alice.getBalance () == aliceStartingBalance - bob.getBalance () - FEE);
	}

	private Block createBlock (String previous, Transaction coinbase)
	{
		Block block = new Block ();
		block.setCreateTime (System.currentTimeMillis () / 1000);
		block.setDifficultyTarget (chain.getGenesis ().getDifficultyTarget ());
		block.setPreviousHash (previous);
		block.setVersion (2);
		block.setNonce (0);
		block.setTransactions (new ArrayList<Transaction> ());
		block.getTransactions ().add (coinbase);
		return block;
	}

	private void mineBlock (Block b)
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
