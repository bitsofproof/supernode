package com.bitsofproof.supernode.api;

import java.util.HashMap;
import java.util.Map;

import com.bitsofproof.supernode.common.ValidationException;

public class ExtendedKeySetWallet implements Wallet
{
	private final Map<String, AccountManager> accountManager = new HashMap<> ();

	public void addKey (String name, ExtendedKey key, long created) throws ValidationException
	{
		if ( accountManager.containsKey (name) )
		{
			throw new ValidationException (name + " account manager exsists");
		}
		ExtendedKeyAccountManager manager;
		accountManager.put (name, manager = new ExtendedKeyAccountManager (name, created));
		manager.setMaster (key);
	}

	@Override
	public AccountManager getAccountManager (String name)
	{
		return accountManager.get (name);
	}

	@Override
	public AccountManager createAccountManager (String name) throws ValidationException
	{
		if ( accountManager.containsKey (name) )
		{
			throw new ValidationException (name + " account manager exsists");
		}
		ExtendedKeyAccountManager manager;
		accountManager.put (name, manager = new ExtendedKeyAccountManager (name, System.currentTimeMillis ()));
		manager.setMaster (ExtendedKey.createNew ());
		return manager;
	}

}
