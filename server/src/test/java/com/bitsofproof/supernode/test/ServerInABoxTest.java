package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.core.UnitTestChain;
import com.bitsofproof.supernode.testbox.APIServerInABox;
import com.bitsofproof.supernode.wallet.AccountListener;
import com.bitsofproof.supernode.wallet.AccountManager;
import com.bitsofproof.supernode.wallet.Address;
import com.bitsofproof.supernode.wallet.AddressListAccountManager;
import com.bitsofproof.supernode.wallet.KeyListAccountManager;

public class ServerInABoxTest
{
	private static final Logger log = LoggerFactory.getLogger (ServerInABoxTest.class);
	private static APIServerInABox box;
	private static BCSAPI api;

	@BeforeClass
	public static void init () throws IOException, ValidationException
	{
		box = new APIServerInABox (new UnitTestChain ());
		api = box.getAPI ();
	}

	@Test
	public void run () throws BCSAPIException, InterruptedException
	{
		final Address target = ECKeyPair.createNew (true).getAddress ();
		AddressListAccountManager targetAccount = new AddressListAccountManager ();
		targetAccount.addAddress (target.toByteArray ());
		api.registerTransactionListener (targetAccount);

		ECKeyPair miner = ECKeyPair.createNew (true);
		Address a = miner.getAddress ();
		box.setNewCoinsAddress (a);
		final KeyListAccountManager minerAccount = new KeyListAccountManager ();
		minerAccount.addKey (miner);
		api.registerTransactionListener (minerAccount);

		final Semaphore blockMined = new Semaphore (0);
		api.registerTrunkListener (new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				blockMined.release ();
			}
		});

		final Semaphore minerAccountChanged = new Semaphore (0);
		minerAccount.addAccountListener (new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account, Transaction t)
			{
				minerAccountChanged.release ();
			}
		});
		final Semaphore targetAccountChanged = new Semaphore (0);
		targetAccount.addAccountListener (new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account, Transaction t)
			{
				targetAccountChanged.release ();
			}
		});
		box.mine (10, 2, 1000);
		for ( int i = 0; i < 9; ++i )
		{
			try
			{
				blockMined.acquireUninterruptibly ();
				minerAccountChanged.acquireUninterruptibly ();
				if ( i > 0 )
				{
					minerAccountChanged.acquireUninterruptibly ();
					targetAccountChanged.acquireUninterruptibly ();
				}

				Transaction t = minerAccount.pay (target, 10 * 100000000L, 0);
				api.sendTransaction (t);
				targetAccountChanged.acquireUninterruptibly ();
				minerAccountChanged.acquireUninterruptibly ();
			}
			catch ( ValidationException | BCSAPIException e )
			{
			}
		}
		Transaction t;
		try
		{
			t = minerAccount.pay (target, 10 * 100000000L, 0);
			api.sendTransaction (t);
			targetAccountChanged.acquireUninterruptibly ();
			minerAccountChanged.acquireUninterruptibly ();
		}
		catch ( ValidationException e )
		{
		}

		assertTrue (minerAccount.getBalance () == 10 * 40 * 100000000L);
		assertTrue (targetAccount.getBalance () == 10 * 10 * 100000000L);
	}
}
