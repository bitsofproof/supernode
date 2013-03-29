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

import com.bitsofproof.supernode.api.AccountStatement;
import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.KeyGenerator;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ScriptFormat.Token;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.Transaction.TransactionSink;
import com.bitsofproof.supernode.api.Transaction.TransactionSource;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/store1.xml", "/context/EmbeddedBCSAPI.xml" })
public class APICheck
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
	public void init () throws ValidationException, BCSAPIException
	{
		store.resetStore (chain);
		store.cache (chain, 0);
		byte[] chainCode = new byte[32];
		new SecureRandom ().nextBytes (chainCode);
		wallet = api.createKeyGenerator (0x0, 0x05);
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
		Block block = createBlock (chain.getGenesis ().getHash (), Transaction.createCoinbase (wallet.generateNextKey (), 50 * COIN, 1));
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
	}

	@Test
	public void testInventory () throws BCSAPIException
	{
		List<String> blocks = api.getBlocks ();
		assertTrue (blocks.size () == 1);
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
			Block block = createBlock (hash, Transaction.createCoinbase (wallet.generateNextKey (), 5000000000L, i + 2));
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
	}

	@Test
	public void spendSome () throws ValidationException, BCSAPIException
	{
		final Semaphore ready = new Semaphore (0);
		final Semaphore ready2 = new Semaphore (0);
		final Semaphore ready3 = new Semaphore (0);

		List<String> sourceAddresses = new ArrayList<String> ();
		List<Transaction.TransactionSource> sources = new ArrayList<Transaction.TransactionSource> ();
		for ( int i = 0; i < 10; ++i )
		{
			TransactionOutput o = blocks.get (i + 1).getTransactions ().get (0).getOutputs ().get (0);
			List<Token> tokens = ScriptFormat.parse (o.getScript ());
			sources.add (new TransactionSource (o, wallet.getKeyForAddress (AddressConverter.toSatoshiStyle (tokens.get (2).data, wallet.getAddressFlag ()))));
			o.parseOwners (wallet.getAddressFlag (), wallet.getP2SHAddressFlag ());
			sourceAddresses.addAll (o.getAddresses ());
		}

		AccountStatement as = api.getAccountStatement (sourceAddresses, blocks.get (9).getCreateTime ());
		assertTrue (as.getOpening ().size () == 9);
		assertTrue (as.getPosting ().size () == 1);

		as = api.getAccountStatement (sourceAddresses, blocks.get (10).getCreateTime ());
		assertTrue (as.getOpening ().size () == 10);
		assertTrue (as.getPosting () == null);

		List<Transaction.TransactionSink> sinks = new ArrayList<Transaction.TransactionSink> ();
		Transaction.TransactionSink sink = new TransactionSink (wallet.generateNextKey ().getAddress (), 10 * 50 * COIN);
		sinks.add (sink);
		List<String> sinkAddresses = new ArrayList<String> ();
		sinkAddresses.add (AddressConverter.toSatoshiStyle (sink.getAddress (), 0x0));

		Transaction transaction = Transaction.createSpend (sources, sinks, 0);
		final String hash = transaction.getHash ();
		final List<String> spendingTxs = new ArrayList<String> ();

		TransactionListener validationListener = new TransactionListener ()
		{

			@Override
			public void process (Transaction t)
			{
				t.computeHash ();
				assertTrue (t.getHash ().equals (hash));
				ready.release ();
			}

		};
		TransactionListener outputListener = new TransactionListener ()
		{

			@Override
			public void process (Transaction t)
			{
				t.computeHash ();
				boolean spent = false;
				for ( TransactionInput i : t.getInputs () )
				{
					if ( spendingTxs.contains (i.getSourceHash ()) )
					{
						spent = true;
					}
				}
				assertTrue (spent);
				ready2.release ();
			}

		};

		api.registerTransactionListener (validationListener);

		TransactionListener addressListener = new TransactionListener ()
		{

			@Override
			public void process (Transaction t)
			{
				t.computeHash ();
				assertTrue (t.getHash ().equals (hash));
				ready3.release ();
			}

		};

		List<String> receiverAddresses = new ArrayList<String> ();
		receiverAddresses.add (AddressConverter.toSatoshiStyle (sink.getAddress (), wallet.getAddressFlag ()));
		api.registerAddressListener (receiverAddresses, addressListener);

		for ( Transaction.TransactionSource s : sources )
		{
			spendingTxs.add (s.getOutput ().getTransactionHash ());
		}
		api.registerOutputListener (spendingTxs, outputListener);

		api.sendTransaction (transaction);
		try
		{
			assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
			assertTrue (ready3.tryAcquire (2, TimeUnit.SECONDS));
			assertTrue (ready2.tryAcquire (2, TimeUnit.SECONDS));
		}
		catch ( InterruptedException e )
		{
		}

		as = api.getAccountStatement (sourceAddresses, 0);
		assertTrue (as.getOpening ().size () == 10);
		assertTrue (as.getPosting () == null);
		assertTrue (as.getUnconfirmedSpend ().size () == 1);

		as = api.getAccountStatement (sinkAddresses, 0);
		assertTrue (as.getOpening () == null);
		assertTrue (as.getPosting () == null);
		assertTrue (as.getUnconfirmedReceive ().size () == 1);

		List<String> hashes = new ArrayList<String> ();
		hashes.add (transaction.getHash ());
		TrunkListener tl = new TrunkListener ()
		{

			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				if ( added != null && added.get (0).getHash ().equals (blocks.get (12).getHash ()) )
				{
					ready.release ();
				}
			}
		};
		api.registerTrunkListener (tl);

		Block block = createBlock (blocks.get (11).getHash (), Transaction.createCoinbase (wallet.generateNextKey (), 5000000000L, 12));
		block.setCreateTime (block.getCreateTime () + 12 * 1000);
		block.getTransactions ().add (transaction);
		mineBlock (block);
		blocks.put (12, block);
		api.sendBlock (block);
		try
		{
			assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
		}
		catch ( InterruptedException e )
		{
		}
		api.removeTrunkListener (tl);

		as = api.getAccountStatement (sourceAddresses, blocks.get (11).getCreateTime ());
		assertTrue (as.getOpening ().size () == 10);
		assertTrue (as.getPosting ().size () == 10);
		assertTrue (as.getUnconfirmedSpend () == null);

		as = api.getAccountStatement (sourceAddresses, block.getCreateTime ());
		assertTrue (as.getOpening () == null);
		assertTrue (as.getPosting () == null);
		assertTrue (as.getUnconfirmedSpend () == null);

		as = api.getAccountStatement (sinkAddresses, block.getCreateTime ());
		assertTrue (as.getOpening ().size () == 1);
		assertTrue (as.getPosting () == null);
		assertTrue (as.getUnconfirmedSpend () == null);
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
