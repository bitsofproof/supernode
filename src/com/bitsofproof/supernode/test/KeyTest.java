package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.ValidationException;

public class KeyTest
{

	@Test
	public void signTest () throws ValidationException
	{
		ECKeyPair k = ECKeyPair.createNew ();
		byte[] hash = Hash.digest ("Hello world".getBytes ()).toByteArray ();
		byte[] signature = k.sign (hash);
		assertTrue (ECKeyPair.verify (hash, signature, k.getPublic ()));
	}

	@Test
	public void asn1Test () throws ValidationException
	{
		ECKeyPair k = ECKeyPair.createNew ();
		byte[] hash = Hash.digest ("Hello world".getBytes ()).toByteArray ();
		byte[] signature = k.sign (hash);

		byte[] storedKey = k.toByteArray ();

		ECKeyPair read = new ECKeyPair (storedKey);
		assertTrue (ECKeyPair.verify (hash, signature, read.getPublic ()));
		assertTrue (ECKeyPair.verify (hash, read.sign (hash), k.getPublic ()));
	}
}
