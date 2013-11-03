package com.bitsofproof.supernode.wallet;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class KeyListAccountManager extends BaseAccountManager
{
	private static final Logger log = LoggerFactory.getLogger (KeyListAccountManager.class);

	private final Map<ByteVector, ECKeyPair> keyByAddress = new HashMap<ByteVector, ECKeyPair> ();
	private final List<ECKeyPair> keys = new ArrayList<ECKeyPair> ();
	private static final SecureRandom rnd = new SecureRandom ();

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> a = new ArrayList<byte[]> ();
		for ( ByteVector b : keyByAddress.keySet () )
		{
			a.add (b.toByteArray ());
		}
		return a;
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		return keyByAddress.get (new ByteVector (address));
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		return keys.get (rnd.nextInt (keys.size ()));
	}

	public void addKey (ECKeyPair key)
	{
		keys.add (key);
		keyByAddress.put (new ByteVector (key.getAddress ()), key);
	}

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + keys.size ());
		api.scanTransactions (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + keys.size ());
	}

	@Override
	public void sync (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + keys.size ());
		api.scanUTXO (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + keys.size ());
	}
}
