/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.BlocktesterChain;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

/*
 * This test will not run automaticaly with maven since
 * it requires significant resources.
 * Run it with at least 1GB of heap otherwise memomry database overflows.
 * Run it on a strong machine, it does multithreaded validation
 * for thousands of signatures.
 */
@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/storeonly.xml" })
public class BitcoinJBlocktester
{
	private static final Logger log = LoggerFactory.getLogger (BitcoinJBlocktester.class);

	private static final String BITCOINJ_BLOCKTESTS = "bitcoinj_blocktester.json";

	@Autowired
	BlockStore store;

	private JSONArray readObjectArray (String resource) throws IOException, JSONException
	{
		InputStream input = this.getClass ().getResource ("/" + resource).openStream ();
		StringBuffer content = new StringBuffer ();
		byte[] buffer = new byte[1024];
		int len;
		while ( (len = input.read (buffer)) > 0 )
		{
			byte[] s = new byte[len];
			System.arraycopy (buffer, 0, s, 0, len);
			content.append (new String (buffer, "UTF-8"));
		}
		return new JSONArray (content.toString ());
	}

	@Test
	public void bitcoindValidTxTest () throws IOException, JSONException, ValidationException
	{
		BlocktesterChain chain = new BlocktesterChain ();
		store.resetStore (chain);
		store.cache (chain, 0);

		Map<String, Blk> pending = new HashMap<String, Blk> ();

		JSONArray testData = readObjectArray (BITCOINJ_BLOCKTESTS);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONObject test = testData.getJSONObject (i);
			String rawblock = test.getString ("rawblock");
			String expectTrunk = test.getString ("trunk");
			String name = test.getString ("name");
			log.trace ("evaluate " + name);
			if ( name.equals ("b48") )
			{
				log.trace ("b48 test skipped as it only works in real-time (checking block time vs. system time)");
				continue;
			}
			if ( rawblock.length () <= 2000000 || name.equals ("b64") )
			// this is checked at higher level, and I think b64 test case is wrong at least in its serialized form it is 1000008 long
			// TODO: follow up on b64 with BlueMatt and TD
			{
				Blk blk = deserializeBlock (rawblock);
				try
				{
					if ( store.isStoredBlock (blk.getPreviousHash ()) )
					{
						store.storeBlock (blk);
						String hash = blk.getHash ();
						while ( pending.containsKey (hash) )
						{
							Blk b = pending.get (hash);
							store.storeBlock (b); // out of order blocks checked at higher level
							hash = b.getHash ();
						}

						log.trace ("accepted " + name);
					}
					else
					{
						pending.put (blk.getPreviousHash (), blk);
						log.trace ("pending " + name);
					}
				}
				catch ( Exception e )
				{
					log.trace ("rejected " + name + " " + e.getMessage ());
				}
			}
			else
			{
				log.trace (name + " block size limit exceeded: " + rawblock.length () / 2);
			}
			assertTrue (expectTrunk.equals (store.getHeadHash ()));
		}
	}

	private Blk deserializeBlock (String s)
	{
		final Blk gb = Blk.fromWireDump (s);
		gb.parseTransactions ();
		for ( Tx t : gb.getTransactions () )
		{
			t.setBlock (gb);
			for ( TxOut out : t.getOutputs () )
			{
				out.setTransaction (t);
			}
			for ( TxIn in : t.getInputs () )
			{
				in.setTransaction (t);
			}
		}
		return gb;
	}
}
