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

public abstract class DefaultWallet implements Wallet
{
	private static final Logger log = LoggerFactory.getLogger (DefaultWallet.class);

	private final Map<String, AccountManager> accountManager = new HashMap<String, AccountManager> ();

	private SerializedWallet storedWallet;
	private BCSAPI api;

	@Override
	public abstract void read (String fileName, String passphrase) throws BCSAPIException;

	@Override
	public abstract void persist () throws BCSAPIException;

	@Override
	public abstract long getTimeStamp ();

	@Override
	public void addAccount (Account account) throws BCSAPIException
	{
		if ( !accountManager.containsKey (account.getName ()) )
		{
			DefaultAccountManager manager = new DefaultAccountManager ();
			manager.setApi (api);
			manager.setAccount (account);
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
		return Collections.unmodifiableList (storedWallet.getTransactions ());
	}
}
