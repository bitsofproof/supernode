package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class AddressListAccountManager extends BaseAccountManager
{
	private static final Logger log = LoggerFactory.getLogger (AddressListAccountManager.class);

	private final Set<ByteVector> addresses = new HashSet<ByteVector> ();

	@Override
	public int getNumberOfKeys ()
	{
		return addresses.size ();
	}

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> al = new ArrayList<byte[]> ();
		for ( ByteVector b : addresses )
		{
			al.add (b.toByteArray ());
		}
		return al;
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		return null;
	}

	@Override
	public boolean isOwnAddress (byte[] address)
	{
		return addresses.contains (new ByteVector (address));
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		return null;
	}

	public void addAddress (byte[] address)
	{
		addresses.add (new ByteVector (address));
	}

	public void sync (BCSAPI api) throws BCSAPIException, ValidationException
	{
		log.trace ("Sync naddr: " + addresses.size ());
		api.scanTransactions (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + addresses.size ());
	}
}
