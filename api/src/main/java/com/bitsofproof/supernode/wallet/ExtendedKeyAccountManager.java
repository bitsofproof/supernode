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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public class ExtendedKeyAccountManager extends BaseAccountManager implements TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (ExtendedKeyAccountManager.class);

	private final Set<Integer> usedKeys = new HashSet<Integer> ();
	private final Map<ByteVector, Integer> keyIDForAddress = new HashMap<ByteVector, Integer> ();
	private ExtendedKey master;
	private int nextSequence;
	private int lookAhead = 100;
	private int firstIndex;

	public ExtendedKey getMaster ()
	{
		return master;
	}

	public void setMaster (ExtendedKey master)
	{
		this.master = master;
	}

	public int getFirstIndex ()
	{
		return firstIndex;
	}

	public void setLookAhead (int lookAhead)
	{
		this.lookAhead = lookAhead;
	}

	public void setFirstIndex (int firstIndex)
	{
		this.firstIndex = firstIndex;
	}

	public int getLookAhead ()
	{
		return lookAhead;
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
		while ( keyIDForAddress.size () < (from + lookAhead - firstIndex) )
		{
			Key key = null;
			try
			{
				key = master.getKey (keyIDForAddress.size () + firstIndex);
			}
			catch ( ValidationException e )
			{
			}
			keyIDForAddress.put (new ByteVector (key.getAddress ().toByteArray ()), keyIDForAddress.size () + firstIndex);
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

	public Set<Integer> getUsedKeys ()
	{
		return usedKeys;
	}

	public Integer getKeyIDForAddress (byte[] address)
	{
		Integer id = keyIDForAddress.get (new ByteVector (address));
		if ( id != null )
		{
			usedKeys.add (id);
		}
		return id;
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

	private final TransactionListener processor = new TransactionListener ()
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
	};

	@Override
	public void syncHistory (BCSAPI api) throws BCSAPIException
	{
		reset ();
		ensureLookAhead (0);
		log.trace ("Sync nkeys: " + (nextSequence - firstIndex));
		api.scanTransactions (getMaster (), firstIndex, lookAhead, getCreated (), processor);
		firstIndex = nextSequence;
		for ( Integer id : usedKeys )
		{
			firstIndex = Math.min (id, firstIndex);
		}
		log.trace ("Sync finished with nkeys: " + (nextSequence - firstIndex));
	}

	@Override
	public void sync (BCSAPI api) throws BCSAPIException
	{
		reset ();
		ensureLookAhead (0);
		log.trace ("Sync nkeys: " + (nextSequence - firstIndex));
		api.scanUTXO (getMaster (), firstIndex, lookAhead, getCreated (), processor);
		firstIndex = nextSequence;
		for ( Integer id : usedKeys )
		{
			firstIndex = Math.min (id, firstIndex);
		}
		log.trace ("Sync finished with nkeys: " + (nextSequence - firstIndex));
	}
}
