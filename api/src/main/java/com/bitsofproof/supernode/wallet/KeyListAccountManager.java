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
package com.bitsofproof.supernode.wallet;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class KeyListAccountManager extends BaseAccountManager
{
	private static final Logger log = LoggerFactory.getLogger (KeyListAccountManager.class);

	private final Map<Address, ECKeyPair> keyByAddress = new HashMap<> ();
	private final List<ECKeyPair> keys = new ArrayList<ECKeyPair> ();
	private static final SecureRandom rnd = new SecureRandom ();

	@Override
	public Set<Address> getAddresses ()
	{
		return Collections.unmodifiableSet (keyByAddress.keySet ());
	}

	@Override
	public Key getKeyForAddress (Address address)
	{
		return keyByAddress.get (address);
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		return keys.get (rnd.nextInt (keys.size ()));
	}

	public void addKey (ECKeyPair key)
	{
		keys.add (key);
		keyByAddress.put (key.getAddress (), key);
	}

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		reset ();
		log.trace ("Sync naddr: " + keys.size ());
		api.scanTransactionsForAddresses (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
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
		api.scanUTXOForAddresses (getAddresses (), UpdateMode.all, getCreated (), new TransactionListener ()
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
