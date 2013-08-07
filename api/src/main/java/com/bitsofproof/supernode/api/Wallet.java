package com.bitsofproof.supernode.api;

import com.bitsofproof.supernode.common.ValidationException;

public interface Wallet
{
	public AccountManager getAccountManager (String name);

	public AccountManager createAccountManager (String name) throws ValidationException;
}