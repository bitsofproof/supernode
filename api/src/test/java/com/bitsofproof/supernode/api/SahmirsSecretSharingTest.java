package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.wallet.ShamirsSecretSharing;

public class SahmirsSecretSharingTest
{
	@Test
	public void test () throws ValidationException
	{
		SecureRandom random = new SecureRandom ();
		for ( int s = 3; s < 10; ++s )
		{
			for ( int g = 1; g < s - 1; ++g )
			{
				for ( int n = 2; n < s - g; ++n )
				{
					BigInteger secret = new BigInteger (256, random);
					ShamirsSecretSharing.SecretShare[] all = ShamirsSecretSharing.cut (secret, s, n);

					ShamirsSecretSharing.SecretShare[] some = new ShamirsSecretSharing.SecretShare[n];
					Set<Integer> pick = new HashSet<Integer> ();
					while ( pick.size () < n )
					{
						int p = random.nextInt (n);
						if ( !pick.contains (p) )
						{
							some[pick.size ()] = all[p];
							pick.add (p);
						}
					}
					assertTrue (ShamirsSecretSharing.reconstruct (some).equals (secret));
				}
			}
		}
	}
}
