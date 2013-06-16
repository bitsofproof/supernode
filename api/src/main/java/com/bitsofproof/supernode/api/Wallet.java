package com.bitsofproof.supernode.api;

import java.io.IOException;

import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

public interface Wallet
{
	public void setApi (BCSAPI api);

	public void init (String passphrase);

	public void unlock (String passphrase) throws ValidationException;

	public void lock ();

	public AccountManager getAccountManager (String name);

	public AccountManager createAccountManager (String name) throws ValidationException, IOException;

	public void read (String name, int lookAhead) throws IOException, ValidationException, BCSAPIException;

	public void persist () throws IOException;

	public Key getKey (AccountManager am, int sequence) throws ValidationException;
}