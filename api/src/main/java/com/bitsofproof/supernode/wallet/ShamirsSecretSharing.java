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
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.common.ValidationException;

public class ShamirsSecretSharing
{
	public static class SecretShare
	{
		public byte x;
		public byte dl;
		public byte yl;
		public byte[] y;
	}

	private final static SecureRandom random = new SecureRandom ();

	public static List<String> issueMnemonicShares (byte[] data, int pieces, int needed, String passphrase) throws ValidationException
	{
		List<String> m = new ArrayList<> ();
		for ( SecretShare share : cut (data, pieces, needed) )
		{
			int l = share.y.length + 3;
			if ( l % 8 != 0 )
			{
				l += 8 - l % 8;
			}
			byte[] e = new byte[l];
			e[0] = share.x;
			e[1] = share.dl;
			e[2] = (byte) share.y.length;
			System.arraycopy (share.y, 0, e, 3, share.y.length);
			m.add (BIP39.encode (e, passphrase));
		}
		return m;
	}

	public static byte[] reconstructFromMnemonicShares (List<String> shareList, String passphrase) throws ValidationException
	{
		SecretShare[] shares = new SecretShare[shareList.size ()];
		int i = 0;
		for ( String m : shareList )
		{
			byte[] e = BIP39.decode (m, passphrase);
			shares[i] = new SecretShare ();
			shares[i].x = e[0];
			shares[i].dl = e[1];
			shares[i].yl = e[2];
			shares[i].y = Arrays.copyOfRange (e, 3, shares[i].yl + 3);
			++i;
		}
		return reconstruct (shares);
	}

	public static SecretShare[] cut (byte[] secret, int pieces, int needed) throws ValidationException
	{
		if ( secret.length > 127 )
		{
			throw new ValidationException ("secret too long");
		}
		BigInteger[] coeff = new BigInteger[needed - 1];
		for ( int i = 0; i < coeff.length; ++i )
		{
			byte[] r = new byte[secret.length];
			random.nextBytes (r);
			coeff[i] = new BigInteger (r);
		}

		SecretShare[] shares = new SecretShare[pieces];
		for ( int x = 1; x <= pieces; ++x )
		{
			int pow = x;
			BigInteger poly = new BigInteger (1, secret);
			for ( int i = 0; i < needed - 1; ++i )
			{
				poly = poly.add (BigInteger.valueOf (pow).multiply (coeff[i]));
				pow *= x;
			}
			shares[x - 1] = new SecretShare ();
			shares[x - 1].x = (byte) x;
			shares[x - 1].dl = (byte) secret.length;
			shares[x - 1].y = poly.toByteArray ();
			shares[x - 1].yl = (byte) poly.toByteArray ().length;
		}

		return shares;
	}

	public static byte[] reconstruct (SecretShare[] shares) throws ValidationException
	{
		for ( int i = 0; i < shares.length - 1; ++i )
		{
			for ( int j = 0; j < shares.length; ++j )
			{
				if ( i != j && shares[i].x == shares[j].x )
				{
					throw new ValidationException ("Shares are not unique");
				}
			}
		}
		BigInteger[] y = new BigInteger[shares.length];
		for ( int i = 0; i < shares.length; ++i )
		{
			y[i] = new BigInteger (shares[i].y);
		}
		int d, i;
		for ( d = 1; d < shares.length; d++ )
		{
			for ( i = 0; i < shares.length - d; i++ )
			{
				int j = i + d;
				BigInteger num =
						BigInteger.valueOf (shares[j].x).multiply (y[i]).subtract (BigInteger.valueOf (shares[i].x).multiply (y[i + 1]));
				BigInteger den = BigInteger.valueOf (shares[j].x).subtract (BigInteger.valueOf (shares[i].x));
				y[i] = num.divide (den);
			}
		}
		byte[] result = y[0].toByteArray ();
		if ( result.length > shares[0].dl )
		{
			result = Arrays.copyOfRange (result, result.length - shares[0].dl, result.length);
		}
		if ( result.length < shares[0].dl )
		{
			byte[] tmp = new byte[shares[0].dl];
			System.arraycopy (result, 0, tmp, shares[0].dl - result.length, result.length);
			result = tmp;
		}
		return result;
	}
}
