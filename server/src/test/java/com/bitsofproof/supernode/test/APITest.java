package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/storeonly.xml", "/context/EmbeddedBCSAPI.xml" })
public class APITest
{
	private static final Logger log = LoggerFactory.getLogger (APITest.class);

	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	@Test
	public void init () throws ValidationException
	{
		store.resetStore (chain);
		store.cache (chain, 0);
	}

	@Test
	public void checkGenesis ()
	{
		assertTrue (api.getBlock (chain.getGenesis ().getHash ()).getHash ().equals (chain.getGenesis ().getHash ()));
	}

}
