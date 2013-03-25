/* 
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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

import java.math.BigInteger;
import java.security.SecureRandom;
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
import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.KeyGenerator;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/store2.xml", "/context/EmbeddedBCSAPI.xml" })
public class AccountManagerTest
{
	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	private static final long COIN = 100000000L;

	private static KeyGenerator wallet;

	private static Map<Integer, Block> blocks = new HashMap<Integer, Block> ();

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
		byte[] chainCode = new byte[32];
		new SecureRandom ().nextBytes (chainCode);
		wallet = api.createKeyGenerator (0x0, 0x05);
	}

	@Test
	public void send21Blocks () throws ValidationException, BCSAPIException, InterruptedException
	{
		final Semaphore hasBlock = new Semaphore (0);
		TrunkListener listener = new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				hasBlock.release ();
			}
		};
		api.registerTrunkListener (listener);
		Block block = createBlock (chain.getGenesis ().getHash (), Transaction.createCoinbase (wallet.generateNextKey (), 50 * COIN, 1));
		mineBlock (block);
		blocks.put (1, block);
		api.sendBlock (block);

		String hash = blocks.get (1).getHash ();
		for ( int i = 0; i < 20; ++i )
		{
			block = createBlock (hash, Transaction.createCoinbase (wallet.generateNextKey (), 5000000000L, i + 2));
			block.setCreateTime (block.getCreateTime () + (i + 1) * 1000); // avoid clash of timestamp with median
			mineBlock (block);
			blocks.put (i + 2, block);
			hash = block.getHash ();
			api.sendBlock (block);
		}
		assertTrue (hasBlock.tryAcquire (21, 2, TimeUnit.SECONDS));
		assertFalse (hasBlock.tryAcquire ());

		api.removeTrunkListener (listener);
	}

	@Test
	public void testAccountManager1 () throws ValidationException, BCSAPIException, InterruptedException
	{
		AccountManager am = api.createAccountManager (wallet);

		assertTrue (am.getBalance () == 50 * COIN * 21);

		Block block = createBlock (blocks.get (21).getHash (), Transaction.createCoinbase (wallet.generateNextKey (), 50 * COIN, 23));
		block.setCreateTime (block.getCreateTime () + 22 * 1000);
		mineBlock (block);
		blocks.put (22, block);

		final Semaphore ready = new Semaphore (0);
		AccountListener listener = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account)
			{
				assertTrue (account.getBalance () == 50 * COIN * 22);
				ready.release ();
			}
		};

		am.addAccountListener (listener);

		api.sendBlock (block);

		assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
		assertFalse (ready.tryAcquire ());

		am.removeAccountListener (listener);
	}

	@Test
	public void testAccountManager2 () throws ValidationException, BCSAPIException, InterruptedException
	{
		AccountManager alice = api.createAccountManager (wallet);
		final long balance = alice.getBalance ();
		assertTrue (balance == 50 * COIN * 22);

		final Semaphore ready1 = new Semaphore (0);
		final Semaphore ready2 = new Semaphore (0);
		AccountListener listener1 = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account)
			{
				long newBalance = account.getBalance ();
				assertTrue (newBalance == balance - 10 * COIN - COIN / 1000);
				ready1.release ();
			}
		};
		alice.addAccountListener (listener1);

		KeyGenerator bobWallet = api.createKeyGenerator (0x00, 0x05);
		AccountManager bob = api.createAccountManager (bobWallet);
		AccountListener listener2 = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account)
			{
				long newBalance = account.getBalance ();
				assertTrue (newBalance == 10 * COIN);
				ready2.release ();
			}
		};
		bob.addAccountListener (listener2);

		Transaction t1 =
				alice.pay (AddressConverter.toSatoshiStyle (bobWallet.generateNextKey ().getAddress (), wallet.getAddressFlag ()), 10 * COIN, COIN / 1000);
		api.sendTransaction (t1);

		assertTrue (ready1.tryAcquire (2, TimeUnit.SECONDS));
		assertTrue (ready2.tryAcquire (2, TimeUnit.SECONDS));
		assertFalse (ready1.tryAcquire ());
		assertFalse (ready2.tryAcquire ());

		alice.removeAccountListener (listener1);
		bob.removeAccountListener (listener2);

		final long balance1 = alice.getBalance ();
		AccountListener listener3 = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account)
			{
				long newBalance = account.getBalance ();
				assertTrue (newBalance == balance1 + 5 * COIN);
				ready1.release ();
			}
		};
		alice.addAccountListener (listener3);
		AccountListener listener4 = new AccountListener ()
		{
			@Override
			public void accountChanged (AccountManager account)
			{
				long newBalance = account.getBalance ();
				assertTrue (newBalance == 5 * COIN - COIN / 1000);
				ready2.release ();
			}
		};
		bob.addAccountListener (listener4);

		Transaction t2 = bob.pay (AddressConverter.toSatoshiStyle (wallet.generateNextKey ().getAddress (), wallet.getAddressFlag ()), 5 * COIN, COIN / 1000);
		api.sendTransaction (t2);

		assertTrue (ready1.tryAcquire (2, TimeUnit.SECONDS));
		assertTrue (ready2.tryAcquire (2, TimeUnit.SECONDS));
		assertFalse (ready1.tryAcquire ());
		assertFalse (ready2.tryAcquire ());
		alice.removeAccountListener (listener3);
		bob.removeAccountListener (listener4);
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
