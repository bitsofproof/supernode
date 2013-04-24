package com.bitsofproof.supernode.api;

public interface Wallet
{

	public AccountManager getAccountManager (String name) throws BCSAPIException;

	public void persist () throws BCSAPIException;
}