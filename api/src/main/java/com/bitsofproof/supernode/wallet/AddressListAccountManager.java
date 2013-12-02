package com.bitsofproof.supernode.wallet;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class AddressListAccountManager extends BaseAccountManager
{
	private static final Logger log = LoggerFactory.getLogger (AddressListAccountManager.class);

	private final Set<Address> addresses = new HashSet<> ();

	@Override
	public Set<Address> getAddresses ()
	{
		return Collections.unmodifiableSet (addresses);
	}

	@Override
	public Key getKeyForAddress (Address address)
	{
		return null;
	}

	@Override
	public boolean isOwnAddress (Address address)
	{
		return addresses.contains (address);
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		return null;
	}

	public void addAddress (Address address)
	{
		addresses.add (address);
	}

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + addresses.size ());
		api.scanTransactionsForAddresses (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished naddr: " + addresses.size ());
	}

	@Override
	public void sync (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + addresses.size ());
		api.scanTransactionsForAddresses (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
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
