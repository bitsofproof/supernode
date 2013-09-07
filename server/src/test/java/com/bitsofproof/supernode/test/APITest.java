/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;
import com.bitsofproof.supernode.wallet.AccountListener;
import com.bitsofproof.supernode.wallet.AccountManager;
import com.bitsofproof.supernode.wallet.ExtendedKeySetWallet;
import com.bitsofproof.supernode.wallet.Wallet;

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

	private static class AccountMonitor implements AccountListener
	{
		private final Semaphore ready = new Semaphore (0);
		private final AccountManager account;

		public AccountMonitor (AccountManager account)
		{
			this.account = account;
		}

		@Override
		public void accountChanged (AccountManager account, Transaction t)
		{
			assertTrue (this.account == account);
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

	public static class ValidationMonitor implements TrunkListener, TransactionListener
	{
		private final Semaphore blockValidated = new Semaphore (0);
		private final Semaphore transactionValidated = new Semaphore (0);
		private String last;

		@Override
		public void trunkUpdate (List<Block> removed, List<Block> added)
		{
			if ( added != null )
			{
				for ( Block b : added )
				{
					b.computeHash ();
					last = b.getHash ();
					blockValidated.release ();
				}
			}
		}

		public String getLast ()
		{
			return last;
		}

		public void expectBlockValidations (int n)
		{
			try
			{
				assertTrue (blockValidated.tryAcquire (n, 1, TimeUnit.SECONDS));
				assertFalse (blockValidated.tryAcquire ());
			}
			catch ( InterruptedException e )
			{
			}
		}

		public void expectTransactionValidations (int n)
		{
			try
			{
				assertTrue (transactionValidated.tryAcquire (n, 1, TimeUnit.SECONDS));
				assertFalse (transactionValidated.tryAcquire ());
			}
			catch ( InterruptedException e )
			{
			}
		}

		@Override
		public void process (Transaction t)
		{
			transactionValidated.release ();
		}
	}

	@BeforeClass
	public static void provider ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void test () throws BCSAPIException, ValidationException, IOException
	{
		Map<Integer, Block> blocks = new HashMap<Integer, Block> ();

		Wallet wallet = new ExtendedKeySetWallet ();
		AccountManager alice = wallet.createAccountManager ("Alice");
		AccountManager bob = wallet.createAccountManager ("Bob");

		AccountMonitor bobMonitor = new AccountMonitor (bob);
		AccountMonitor aliceMonitor = new AccountMonitor (alice);
		ValidationMonitor validationMonitor = new ValidationMonitor ();

		store.resetStore (chain);
		store.cache (chain, 0);

		api.registerTransactionListener (alice);
		api.registerTransactionListener (bob);

		alice.addAccountListener (aliceMonitor);
		bob.addAccountListener (bobMonitor);

		api.registerTrunkListener (validationMonitor);
		api.registerTransactionListener (validationMonitor);

		// check genesis
		String genesisHash = chain.getGenesis ().getHash ();
		assertTrue (api.getBlock (genesisHash).getHash ().equals (genesisHash));

		// send 1 block
		Block block = createBlock (chain.getGenesis ().getHash (), Transaction.createCoinbase (alice.getNextKey (), 50 * COIN, 1));
		mineBlock (block);
		blocks.put (1, block);

		api.sendBlock (block);

		validationMonitor.expectBlockValidations (1);
		assertTrue (validationMonitor.getLast ().equals (block.getHash ()));
		validationMonitor.expectTransactionValidations (1);
		aliceMonitor.expectUpdates (1);

		// send 10 blocks
		String hash = blocks.get (1).getHash ();
		for ( int i = 0; i < 10; ++i )
		{
			block = createBlock (hash, Transaction.createCoinbase (alice.getNextKey (), 5000000000L, i + 2));
			block.setCreateTime (block.getCreateTime () + (i + 1) * 1000); // avoid clash of timestamp with median
			mineBlock (block);
			blocks.put (i + 2, block);
			hash = block.getHash ();
			api.sendBlock (block);
		}
		validationMonitor.expectBlockValidations (10);
		assertTrue (validationMonitor.getLast ().equals (hash));
		aliceMonitor.expectUpdates (10);
		validationMonitor.expectTransactionValidations (10);

		// spend some
		long aliceStartingBalance = alice.getBalance ();
		Transaction spend = alice.pay (bob.getNextKey ().getAddress (), 50 * COIN, FEE);
		api.sendTransaction (spend);
		aliceMonitor.expectUpdates (1);
		bobMonitor.expectUpdates (1);
		validationMonitor.expectTransactionValidations (1);
		assertTrue (bob.getBalance () == 50 * COIN);
		assertTrue (alice.getBalance () == aliceStartingBalance - bob.getBalance () - FEE);

		// split
		aliceStartingBalance = alice.getBalance ();
		spend = alice.split (new long[] { 1 * COIN, 2 * COIN }, FEE);
		api.sendTransaction (spend);
		aliceMonitor.expectUpdates (1);
		validationMonitor.expectTransactionValidations (1);
		assertTrue (alice.getBalance () == aliceStartingBalance - FEE);
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