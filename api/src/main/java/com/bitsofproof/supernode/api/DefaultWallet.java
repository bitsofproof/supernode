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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.api.SerializedWallet.WalletKey;

public abstract class DefaultWallet implements Wallet
{
	private static final Logger log = LoggerFactory.getLogger (DefaultWallet.class);

	private final Map<String, AccountManager> accountManager = new HashMap<String, AccountManager> ();

	protected SerializedWallet serializedWallet = new SerializedWallet ();
	protected long timeStamp;

	private BCSAPI api;

	private boolean production;

	public boolean isProduction ()
	{
		return production;
	}

	public void setProduction (boolean production)
	{
		this.production = production;
	}

	@Override
	public abstract void read (String fileName, String passphrase) throws BCSAPIException;

	@Override
	public abstract void persist () throws BCSAPIException;

	@Override
	public long getTimeStamp ()
	{
		return timeStamp;
	}

	@Override
	public void setTimeStamp (long timeStamp)
	{
		this.timeStamp = timeStamp;
	}

	@Override
	public AccountManager createAcountManager (String name, ExtendedKey master, int nextSequence, long created) throws BCSAPIException
	{
		if ( accountManager.containsKey (name) )
		{
			return accountManager.get (name);
		}
		try
		{
			WalletKey key = new WalletKey ();
			key.key = master.serialize (production);
			Account account = new DefaultAccount (name, master, nextSequence);
			key.created = created;
			key.name = name;
			key.nextSequence = nextSequence;
			serializedWallet.addKey (key);
			setTimeStamp (created);
			addAccount (account);
			return accountManager.get (name);
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	protected void addAccount (Account account) throws BCSAPIException
	{
		if ( !accountManager.containsKey (account.getName ()) )
		{
			final DefaultAccountManager manager = new DefaultAccountManager ();
			accountManager.put (account.getName (), manager);
			manager.setApi (api);
			manager.setAccount (account);
			for ( Transaction t : getTransactions () )
			{
				manager.updateWithTransaction (t);
			}
			api.scanTransactions (account.getAddresses (), UpdateMode.all, getTimeStamp (), new TransactionListener ()
			{
				@Override
				public void process (Transaction t)
				{
					if ( manager.updateWithTransaction (t) )
					{
						serializedWallet.addTransaction (t);
					}
				}
			});
		}
	}

	@Override
	public AccountManager getAccountManager (String accountName)
	{
		return accountManager.get (accountName);
	}

	@Override
	public List<Transaction> getTransactions ()
	{
		return Collections.unmodifiableList (serializedWallet.getTransactions ());
	}

	@Override
	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	@Override
	public void addTransaction (Transaction t)
	{
		serializedWallet.addTransaction (t);
	}

}
