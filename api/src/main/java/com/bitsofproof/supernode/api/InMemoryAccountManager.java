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

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Token;
import com.bitsofproof.supernode.common.ValidationException;

class InMemoryAccountManager extends BaseAccountManager implements TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (InMemoryAccountManager.class);

	public InMemoryAccountManager (String name, long created)
	{
		super (name, created);
	}

	public void sync (BCSAPI api, final int lookAhead, long after) throws BCSAPIException, ValidationException
	{
		final AtomicInteger lastUsedKey = new AtomicInteger (getNextSequence ());
		for ( int i = 0; i < lookAhead; ++i )
		{
			getNextKey ();
		}

		api.scanTransactions (getMaster (), lookAhead, after, new TransactionListener ()
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
									while ( thisKey == null && (getNextSequence () - lastUsedKey.get ()) < lookAhead )
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
		setNextSequence (Math.min (lastUsedKey.get (), getNextSequence ()));
		api.registerTransactionListener (this);
	}

	@Override
	public void storeTransaction (Transaction t)
	{
	}

	@Override
	public void removeTransaction (Transaction t)
	{
	}
}
