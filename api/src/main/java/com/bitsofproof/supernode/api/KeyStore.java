package com.bitsofproof.supernode.api;

public interface KeyStore
{
	public String getKeyForAddress (String address);

	public void storeKey (ECKeyPair key);
}
