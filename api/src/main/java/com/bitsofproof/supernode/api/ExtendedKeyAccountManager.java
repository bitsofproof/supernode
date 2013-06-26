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
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Token;
import com.bitsofproof.supernode.common.ValidationException;

public class ExtendedKeyAccountManager extends BaseAccountManager implements TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (ExtendedKeyAccountManager.class);

	private final Map<ByteVector, Integer> keyIDForAddress = new HashMap<ByteVector, Integer> ();
	private ExtendedKey master;
	private int nextSequence;

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

	public void setNextSequence (int nextSequence) throws ValidationException
	{
		while ( this.nextSequence < nextSequence )
		{
			getNextKey ();
		}
		this.nextSequence = nextSequence;
	}

	public Key getKey (int i) throws ValidationException
	{
		return master.getKey (i);
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		Key key = master.getKey (nextSequence);
		keyIDForAddress.put (new ByteVector (key.getAddress ()), nextSequence);
		++nextSequence;
		return key;
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
			addresses.add (v.toByteArray ());
		}
		return addresses;
	}

	public ExtendedKeyAccountManager (String name, long created)
	{
		super (name, created);
	}

	public void sync (BCSAPI api, final int lookAhead) throws BCSAPIException, ValidationException
	{
		final AtomicInteger lastUsedKey = new AtomicInteger (-1);
		for ( int i = 0; i < lookAhead; ++i )
		{
			getNextKey ();
		}
		log.trace ("Sync " + getName () + " nkeys: " + getNumberOfKeys ());
		api.scanTransactions (getMaster (), lookAhead, getCreated (), new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				for ( TransactionOutput o : t.getOutputs () )
				{
					try
					{
						for ( Token token : ScriptFormat.parse (o.getScript ()) )
						{
							if ( token.data != null )
							{
								Integer thisKey = getKeyIDForAddress (token.data);
								if ( thisKey != null )
								{
									lastUsedKey.set (Math.max (thisKey, lastUsedKey.get ()));
								}
								else
								{
									while ( thisKey == null && (getNumberOfKeys () - lastUsedKey.get ()) < lookAhead )
									{
										getNextKey ();
									}
									thisKey = getKeyIDForAddress (token.data);
									if ( thisKey != null )
									{
										lastUsedKey.set (Math.max (thisKey, lastUsedKey.get ()));
									}
								}
							}
						}
					}
					catch ( ValidationException e )
					{
					}
				}
				updateWithTransaction (t);
			}
		});
		setNextSequence (Math.min (lastUsedKey.get () + 1, getNumberOfKeys ()));
		log.trace ("Sync " + getName () + " finished with nkeys: " + getNumberOfKeys ());
	}
}
