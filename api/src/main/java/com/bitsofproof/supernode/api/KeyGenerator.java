package com.bitsofproof.supernode.api;

import java.util.List;

public interface KeyGenerator
{

	public int getAddressFlag ();

	public int getP2SHAddressFlag ();

	public KeyGenerator createSubKeyGenerator (int sequence) throws ValidationException;

	public void addListener (KeyGeneratorListener listener);

	public ExtendedKey getExtendedKey (int sequence) throws ValidationException;

	public Key getKey (int sequence) throws ValidationException;

	public Key generateNextKey () throws ValidationException;

	public void importKey (Key k);

	public Key getKeyForAddress (String address);

	public List<String> getAddresses () throws ValidationException;

	public ExtendedKey getMaster ();

	public int getNextKeySequence ();

}