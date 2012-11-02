package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.core.AddressConverter;
import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.SatoshiChain;
import com.bitsofproof.supernode.core.ValidationException;

public class AddressTest
{
	static AddressConverter converter;

	@BeforeClass
	public static void setup ()
	{
		converter = new AddressConverter (new SatoshiChain ());
	}

	@Test
	public void base58Test () throws ValidationException
	{
		SecureRandom rnd = new SecureRandom ();
		for ( int i = 0; i < 10000; ++i )
		{
			BigInteger n = new BigInteger (160, rnd);
			assertTrue (new BigInteger (AddressConverter.fromBase58 (AddressConverter.toBase58 (n.toByteArray ()))).equals (n));
		}
	}

	@Test
	public void satoshiAddressTest () throws ValidationException, UnsupportedEncodingException
	{
		// some real addresses
		assertTrue (converter.toSatoshiStyle (ByteUtils.fromHex ("9e969049aefe972e41aaefac385296ce18f30751"), false).equals (
				"1FTY8etSpSW3xv6s2XRrYE77rrRfza8aJJ"));
		assertTrue (converter.toSatoshiStyle (ByteUtils.fromHex ("623dbe779a29c6bc2615cd7bf5a35453f495e229"), false).equals (
				"19xTBrDcnZiJSMuzirE7SfcsjkG1ghp1RL"));

		// some random
		SecureRandom rnd = new SecureRandom ();
		for ( int i = 0; i < 10000; ++i )
		{
			BigInteger n = new BigInteger (160, rnd);
			byte[] keyDigest = n.toByteArray ();
			String a = converter.toSatoshiStyle (keyDigest, false);
			byte[] check = converter.fromSatoshiStyle (a);
			for ( int j = 0; j < keyDigest.length; ++j )
			{
				assertTrue (check[j] == keyDigest[j]);
			}
		}
	}
}
