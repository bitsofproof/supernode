package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.wallet.ShamirsSecretSharing;

public class SahmirsSecretSharingTest
{
	@BeforeClass
	public static void init ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void testBinary () throws ValidationException
	{
		SecureRandom random = new SecureRandom ();
		for ( int i = 0; i < 100; ++i )
		{
			for ( int s = 3; s < 10; ++s )
			{
				for ( int g = 0; g < s; ++g )
				{
					for ( int n = 2; n < (s - g); ++n )
					{
						byte[] secret = new byte[32];
						random.nextBytes (secret);
						ShamirsSecretSharing.SecretShare[] all = ShamirsSecretSharing.cut (secret, s, n);

						ShamirsSecretSharing.SecretShare[] some = new ShamirsSecretSharing.SecretShare[n];
						Set<Integer> pick = new HashSet<Integer> ();
						while ( pick.size () < n )
						{
							int p = random.nextInt (s);
							if ( !pick.contains (p) )
							{
								some[pick.size ()] = all[p];
								pick.add (p);
							}
						}
						byte[] recreated = ShamirsSecretSharing.reconstruct (some);
						assertTrue (Arrays.equals (recreated, secret));
					}
				}
			}
		}
	}

	public void testMnemonic () throws ValidationException
	{
		SecureRandom random = new SecureRandom ();
		for ( int i = 0; i < 1000; ++i )
		{
			for ( int s = 3; s < 10; ++s )
			{
				for ( int g = 0; g < s; ++g )
				{
					for ( int n = 2; n < (s - g); ++n )
					{
						byte[] secret = new byte[32];
						random.nextBytes (secret);
						List<String> all = ShamirsSecretSharing.issueMnemonicShares (secret, s, n, "BOP");

						List<String> some = new ArrayList<> ();
						Set<Integer> pick = new HashSet<Integer> ();
						while ( pick.size () < n )
						{
							int p = random.nextInt (s);
							if ( !pick.contains (p) )
							{
								some.add (all.get (p));
								pick.add (p);
							}
						}
						byte[] recreated = ShamirsSecretSharing.reconstructFromMnemonicShares (some, "BOP");
						assertTrue (Arrays.equals (recreated, secret));
					}
				}
			}
		}
	}
}
