package com.bitsofproof.supernode.api;

import java.util.Collection;

public interface Account
{
	public String getName ();

	public Collection<byte[]> getAddresses ();

	public Key getKeyForAddress (byte[] address);

	public Key getKey (int ix) throws ValidationException;

	public Key getNextKey () throws ValidationException;

}