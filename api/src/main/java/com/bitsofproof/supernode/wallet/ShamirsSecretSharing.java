/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.wallet;

import java.math.BigInteger;
import java.security.SecureRandom;

import com.bitsofproof.supernode.common.ValidationException;

public class ShamirsSecretSharing
{
	public static class SecretShare
	{
		public BigInteger m;
		public BigInteger x;
		public BigInteger y;
		public int n;
	}

	private final static SecureRandom random = new SecureRandom ();

	public static SecretShare[] cut (BigInteger secret, int pieces, int needed)
	{
		BigInteger mod = BigInteger.probablePrime (secret.bitLength () + 1, random);
		BigInteger[] coeff = new BigInteger[needed - 1];
		for ( int i = 0; i < coeff.length; ++i )
		{
			byte[] r = new byte[secret.bitLength () / 8];
			random.nextBytes (r);
			coeff[i] = new BigInteger (1, r);
		}

		SecretShare[] shares = new SecretShare[pieces];
		for ( int x = 1; x <= pieces; ++x )
		{
			int pow = x;
			BigInteger poly = secret;
			for ( int i = 0; i < needed - 1; ++i )
			{
				poly = poly.add (BigInteger.valueOf (pow).multiply (coeff[i]).mod (mod)).mod (mod);
				pow *= x;
			}
			shares[x - 1] = new SecretShare ();
			shares[x - 1].m = mod;
			shares[x - 1].x = BigInteger.valueOf (x);
			shares[x - 1].y = poly;
			shares[x - 1].n = needed;
		}

		return shares;
	}

	public static BigInteger reconstruct (SecretShare[] shares) throws ValidationException
	{
		for ( int i = 0; i < shares.length - 1; ++i )
		{
			if ( !shares[i].m.equals (shares[i + 1].m) ||
					shares[i].n != shares[i + 1].n )
			{
				throw new ValidationException ("Not shares of the same secret");
			}
			for ( int j = 0; j < shares.length; ++j )
			{
				if ( i != j && shares[i].x.equals (shares[j].x) )
				{
					throw new ValidationException ("Shares are not unique");
				}
			}
		}
		if ( shares.length < shares[0].n )
		{
			throw new ValidationException ("Not enough shares");
		}
		BigInteger m = shares[0].m;
		BigInteger[] y = new BigInteger[shares.length];
		for ( int i = 0; i < shares.length; ++i )
		{
			y[i] = shares[i].y;
		}
		int d, i;
		for ( d = 1; d < shares.length; d++ )
		{
			for ( i = 0; i < shares.length - d; i++ )
			{
				int j = i + d;
				BigInteger num = shares[j].x.multiply (y[i]).mod (m).subtract (shares[i].x.multiply (y[i + 1]).mod (m)).mod (m);
				BigInteger den = shares[j].x.subtract (shares[i].x).mod (m);
				y[i] = num.multiply (den.modInverse (m)).mod (m);
			}
		}
		return y[0];
	}
}
