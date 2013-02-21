package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

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
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

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
		JSONArray testData = readObjectArray (BITCOINJ_BLOCKTESTS);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONObject test = testData.getJSONObject (i);
			log.info ("Test " + test.getString ("name"));
			Blk blk = deserializeBlock (test.getString ("rawblock"));
			if ( i == 0 )
			{
				initdb (blk);
			}
			else
			{
				// execute some of bitcoinj tests
				// b7 is changed to reject since it is double spend
				// bitcoinj however accepts it since it is on a shorter
				// orphan path
				boolean accepted = false;
				boolean shouldAccept = test.getBoolean ("accept");
				try
				{
					store.storeBlock (blk);
					log.trace ("accepted block " + blk.getHash ());
					accepted = true;
				}
				catch ( Exception e )
				{
					if ( shouldAccept )
					{
						log.error ("rejected block " + blk.getHash ());
					}
					else
					{
						log.trace ("rejected block " + blk.getHash ());
					}
				}
				assertTrue (accepted == shouldAccept);
				if ( !test.getString ("name").equals ("b12") )
				{
					// b12 expects a trunk in bitcoinj test that
					// would only be the case if I would store
					// blocks without parent, then resolve as
					// parent arrives. This logic happens in
					// bitsofproof on a higher level, that is
					// the chain loader.
					// therefore result would be same, but not
					// in this unit test.
					String expectTrunk = test.getString ("trunk");
					String haveTrunk = store.getHeadHash ();
					if ( !expectTrunk.equals (haveTrunk) )
					{
						log.error ("Mismatch in trunk expected " + expectTrunk + " have " + haveTrunk);
					}
					assertTrue (expectTrunk.equals (haveTrunk));
				}
			}
		}
	}

	private void initdb (final Blk genesis) throws ValidationException
	{
		Chain chain = new Chain ()
		{

			@Override
			public BigInteger getMinimumTarget ()
			{
				return BigInteger.valueOf (1).shiftLeft (256).subtract (BigInteger.ONE);
			}

			@Override
			public long getRewardForHeight (int height)
			{
				return 5000000000L;
			}

			@Override
			public int getDifficultyReviewBlocks ()
			{
				return 2016;
			}

			@Override
			public int getTargetBlockTime ()
			{
				return 1209600;
			}

			@Override
			public boolean isProduction ()
			{
				return true;
			}

			@Override
			public int getAddressFlag ()
			{
				return 0;
			}

			@Override
			public int getMultisigAddressFlag ()
			{
				return 0;
			}

			@Override
			public Blk getGenesis ()
			{
				return genesis;
			}

			@Override
			public long getMagic ()
			{
				return 0;
			}

			@Override
			public int getPort ()
			{
				return 0;
			}

			@Override
			public byte[] getAlertKey ()
			{
				return null;
			}

			@Override
			public long getVersion ()
			{
				return 1;
			}

			@Override
			public boolean isUnitTest ()
			{
				return true;
			}
		};
		// this test can spend genesis
		genesis.getTransactions ().get (0).getOutputs ().get (0).setAvailable (true);
		store.resetStore (chain);
		store.cache (chain, 0);
	}

	private Blk deserializeBlock (String s)
	{
		final Blk gb = Blk.fromWireDump (s);
		gb.parseTransactions ();
		gb.computeHash ();
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
