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
package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class ExtendedKeyAccountManager extends BaseAccountManager implements TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (ExtendedKeyAccountManager.class);

	private final Map<ByteVector, Integer> keyIDForAddress = new HashMap<ByteVector, Integer> ();
	private ExtendedKey master;
	private int nextSequence;
	private int lookAhead = 100;

	public ExtendedKey getMaster ()
	{
		return master;
	}

	public void setMaster (ExtendedKey master)
	{
		this.master = master;
	}

	@Override
	public int getNumberOfKeys ()
	{
		return nextSequence;
	}

	@Override
	protected void notifyListener (Transaction t)
	{
		for ( TransactionOutput o : t.getOutputs () )
		{
			if ( isOwnAddress (o.getOutputAddress ()) )
			{
				int keyId = keyIDForAddress.get (new ByteVector (o.getOutputAddress ()));
				ensureLookAhead (keyId);
			}
		}
		super.notifyListener (t);
	}

	private void ensureLookAhead (int from)
	{
		while ( keyIDForAddress.size () < (from + lookAhead) )
		{
			Key key = null;
			try
			{
				key = master.getKey (keyIDForAddress.size ());
			}
			catch ( ValidationException e )
			{
			}
			keyIDForAddress.put (new ByteVector (key.getAddress ()), keyIDForAddress.size ());
		}
	}

	public Key getKey (int i) throws ValidationException
	{
		ensureLookAhead (i);
		return master.getKey (i);
	}

	public void setNextKey (int i)
	{
		nextSequence = i;
		ensureLookAhead (nextSequence);
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		return getKey (nextSequence++);
	}

	public Integer getKeyIDForAddress (byte[] address)
	{
		return keyIDForAddress.get (new ByteVector (address));
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		Integer keyId = getKeyIDForAddress (address);
		if ( keyId == null )
		{
			return null;
		}
		try
		{
			return getKey (keyId);
		}
		catch ( ValidationException e )
		{
			return null;
		}
	}

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> addresses = new ArrayList<byte[]> ();
		for ( ByteVector v : keyIDForAddress.keySet () )
		{
			if ( keyIDForAddress.get (v).intValue () < nextSequence )
			{
				addresses.add (v.toByteArray ());
			}
		}
		return addresses;
	}

	public void sync (BCSAPI api, final int lookAhead) throws BCSAPIException, ValidationException
	{
		this.lookAhead = lookAhead;
		ensureLookAhead (0);
		log.trace ("Sync nkeys: " + getNumberOfKeys ());
		api.scanTransactions (getMaster (), lookAhead, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				for ( TransactionOutput o : t.getOutputs () )
				{
					Integer thisKey = getKeyIDForAddress (o.getOutputAddress ());
					if ( thisKey != null )
					{
						ensureLookAhead (thisKey);
						nextSequence = Math.max (nextSequence, thisKey + 1);
					}
				}
				updateWithTransaction (t);
			}
		});
		log.trace ("Sync finished with nkeys: " + getNumberOfKeys ());
	}
}
