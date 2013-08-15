package com.bitsofproof.supernode.api;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	public int getNumberOfKeys ()
	{
		return keys.size ();
	}

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

	public void sync (BCSAPI api) throws BCSAPIException, ValidationException
	{
		log.trace ("Sync nkeys: " + keys.size ());
		api.scanUTXO (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished nkeys: " + keys.size ());
	}
}
