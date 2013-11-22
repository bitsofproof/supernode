package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.BeforeClass;
import org.junit.Test;

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
	private static APIServerInABox box;
	private static BCSAPI api;

	@BeforeClass
	public static void init () throws IOException, ValidationException
	{
		box = new APIServerInABox (new UnitTestChain ());
		api = box.getAPI ();
	}

	@Test
	public void run () throws BCSAPIException
	{
		final Address target = ECKeyPair.createNew (true).getAddress ();
		AddressListAccountManager addm = new AddressListAccountManager ();
		addm.addAddress (target.toByteArray ());
		api.registerTransactionListener (addm);

		ECKeyPair miner = ECKeyPair.createNew (true);
		Address a = miner.getAddress ();
		box.setNewCoinsAddress (a);
		final KeyListAccountManager am = new KeyListAccountManager ();
		am.addKey (miner);
		api.registerTransactionListener (am);

		final Semaphore nb = new Semaphore (0);
		api.registerTrunkListener (new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				try
				{
					api.sendTransaction (am.pay (target, 10 * 100000000L, 0));
				}
				catch ( ValidationException | BCSAPIException e )
				{
				}
				nb.release ();
			}
		});

		final Semaphore nb2 = new Semaphore (0);
		am.addAccountListener (new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account, Transaction t)
			{
				nb2.release ();
			}
		});
		final Semaphore nb3 = new Semaphore (0);
		addm.addAccountListener (new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account, Transaction t)
			{
				nb3.release ();
			}
		});
		box.mine (1000, 10);
		nb.acquireUninterruptibly (10);
		nb2.acquireUninterruptibly (20);
		nb3.acquireUninterruptibly (10);
		long b = am.getBalance ();
		assertTrue (am.getBalance () == 10 * 40 * 100000000L);
		assertTrue (addm.getBalance () == 10 * 10 * 100000000L);
	}
}
