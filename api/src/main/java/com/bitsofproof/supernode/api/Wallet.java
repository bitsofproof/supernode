package com.bitsofproof.supernode.api;

import com.bitsofproof.supernode.common.ValidationException;

public interface Wallet
{
	public void init (String passphrase);

	public void unlock (String passphrase) throws ValidationException;

	public void lock ();

	public AccountManager getAccountManager (String name);

	public AccountManager createAccountManager (String name) throws ValidationException;
}