package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/storeonly.xml", "/context/EmbeddedBCSAPI.xml" })
public class APITest
{
	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	private final Semaphore ready = new Semaphore (0);

	@Test
	public void init () throws ValidationException
	{
		store.resetStore (chain);
		store.cache (chain, 0);
	}

	@Test
	public void checkGenesis ()
	{
		String genesisHash = chain.getGenesis ().getHash ();
		assertTrue (api.getBlock (genesisHash).getHash ().equals (genesisHash));
	}

	@Test
	public void sendBlock ()
	{
		Block block =
				Block.fromWireDump ("0100000006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f9898befce8d1310a0f2c470f5b924fda4106715859ef0bfe157fd67abd3cacf1072d2a51ffff7f20000000000101000000010000000000000000000000000000000000000000000000000000000000000000ffffffff020001ffffffff0100f2052a010000002321031cd2dfbdbd6d50991a022619dba47aac054c1b3b9cf5cf0186093e5c3010ded9ac00000000");

		block.computeHash ();
		final String hash = block.getHash ();

		api.registerTrunkListener (new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				Block got = added.get (0);
				got.computeHash ();
				assertTrue (got.getHash ().equals (hash));
				ready.release ();
			}
		});

		api.sendBlock (block);

		try
		{
			assertTrue (ready.tryAcquire (1, TimeUnit.SECONDS));
		}
		catch ( InterruptedException e )
		{
		}
	}

	@AfterClass
	public static void someDelayToFinishDeamonThreads ()
	{
		try
		{
			Thread.sleep (3000);
		}
		catch ( InterruptedException e )
		{
		}
	}
}
