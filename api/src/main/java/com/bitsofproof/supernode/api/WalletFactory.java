package com.bitsofproof.supernode.api;

public interface WalletFactory
{
	public Wallet getWallet (String fileName, String passphrase) throws BCSAPIException;
}
