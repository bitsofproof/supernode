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

import com.bitsofproof.supernode.api.AccountManager;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Color;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.KeyGenerator;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/memory-store.xml", "/context/EmbeddedBCSAPI.xml" })
public class ColorTest
{
	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	private static final long COIN = 100000000L;

	private static Map<Integer, Block> blocks = new HashMap<Integer, Block> ();

	private static KeyGenerator keyGenerator;
	private static AccountManager accountManager;

	@BeforeClass
	public static void provider ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void init () throws ValidationException, BCSAPIException
	{
		store.resetStore (chain);
		store.cache (chain, 0);

		keyGenerator = api.createKeyGenerator (0x00, 0x05);
		accountManager = api.createAccountManager (keyGenerator);
	}

	@Test
	public void issueColor () throws ValidationException, BCSAPIException, InterruptedException
	{
		KeyGenerator keyGenerator = api.createKeyGenerator (0x00, 0x05);
		AccountManager accountManager = api.createAccountManager (keyGenerator);

		String hash = chain.getGenesis ().getHash ();
		for ( int i = 0; i < 10; ++i )
		{
			Block block = createBlock (hash, Transaction.createCoinbase (keyGenerator.generateNextKey (), 5000000000L, i + 1));
			block.setCreateTime (block.getCreateTime () + (i + 1) * 1000); // avoid clash of timestamp with median
			mineBlock (block);
			blocks.put (i + 1, block);
			hash = block.getHash ();
			api.sendBlock (block);
		}

		Color color = new Color ();
		color.setTerms ("This is blue");
		color.setExpiryHeight (100);
		color.setUnit (COIN / 1000);

		final Transaction colorGenesis = accountManager.createColorGenesis (1000, color.getUnit (), COIN / 1000);

		final Block block = createBlock (hash, Transaction.createCoinbase (keyGenerator.generateNextKey (), 5000000000L, 11));
		block.setCreateTime (block.getCreateTime () + 11 * 1000); // avoid clash of timestamp with median
		block.getTransactions ().add (colorGenesis);
		mineBlock (block);
		blocks.put (11, block);

		final Semaphore ready = new Semaphore (0);
		TrunkListener trunkListener = new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				assertTrue (added.get (0).getHash ().equals (block.getHash ()));
				ready.release ();
			}
		};
		api.registerTrunkListener (trunkListener);
		api.sendBlock (block);
		assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
		api.removeTrunkListener (trunkListener);

		color.setTransaction (colorGenesis.getHash ());
		colorGenesis.getOutputs ().get (0).parseOwners (0x00, 0x05);
		String genesisAddress = colorGenesis.getOutputs ().get (0).getAddresses ().get (0);
		color.setPubkey (keyGenerator.getKeyForAddress (genesisAddress).getPublic ());
		color.sign (keyGenerator.getKeyForAddress (genesisAddress));

		api.issueColor (color);
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
