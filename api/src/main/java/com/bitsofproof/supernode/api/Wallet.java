package com.bitsofproof.supernode.api;


public interface Wallet
{

	public AccountManager getAccountManager (String name) throws BCSAPIException;

	public void addTransaction (Transaction t);

	public void persist () throws BCSAPIException;

	public void setTimeStamp (long time);
}