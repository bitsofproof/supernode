/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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
package com.bitsofproof.supernode.core;

import java.math.BigInteger;

import edu.emory.mathcs.backport.java.util.Arrays;

public class AddressConverter
{
	private static final char[] b58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray ();
	private static final int[] r58 = new int[256];
	static
	{
		for ( int i = 0; i < 256; ++i )
		{
			r58[i] = -1;
		}
		for ( int i = 0; i < b58.length; ++i )
		{
			r58[b58[i]] = i;
		}
	}

	public static String toBase58 (byte[] b)
	{
		int lz = 0;
		while ( b[lz] == 0 && lz < b.length )
		{
			++lz;
		}

		StringBuffer s = new StringBuffer ();
		BigInteger n = new BigInteger (b);
		while ( n.compareTo (BigInteger.ZERO) > 0 )
		{
			BigInteger[] r = n.divideAndRemainder (BigInteger.valueOf (58));
			n = r[0];
			char digit = b58[r[1].intValue ()];
			s.append (digit);
		}
		while ( lz > 0 )
		{
			--lz;
			s.append ("1");
		}
		return s.reverse ().toString ();
	}

	public static byte[] fromBase58WithChecksum (String s) throws ValidationException
	{
		byte[] b = fromBase58 (s);
		if ( b.length < 4 )
		{
			throw new ValidationException ("Too short for checksum " + s);
		}
		byte[] cs = new byte[4];
		System.arraycopy (b, b.length - 4, cs, 0, 4);
		byte[] data = new byte[b.length - 4];
		System.arraycopy (b, 0, data, 0, b.length - 4);
		byte[] h = new byte[4];
		System.arraycopy (Hash.hash (data), 0, h, 0, 4);
		if ( Arrays.equals (cs, h) )
		{
			return data;
		}
		throw new ValidationException ("Checksum mismatch " + s);
	}

	public static byte[] fromBase58 (String s) throws ValidationException
	{
		try
		{
			boolean leading = true;
			int lz = 0;
			BigInteger b = BigInteger.ZERO;
			for ( char c : s.toCharArray () )
			{
				if ( leading && c == '1' )
				{
					++lz;
				}
				else
				{
					leading = false;
					b = b.multiply (BigInteger.valueOf (58));
					b = b.add (BigInteger.valueOf (r58[c]));
				}
			}
			byte[] encoded = b.toByteArray ();
			if ( encoded[0] == 0 )
			{
				if ( lz > 0 )
				{
					--lz;
				}
				else
				{
					byte[] e = new byte[encoded.length - 1];
					System.arraycopy (encoded, 1, e, 0, e.length);
					encoded = e;
				}
			}
			byte[] result = new byte[encoded.length + lz];
			System.arraycopy (encoded, 0, result, lz, encoded.length);

			return result;
		}
		catch ( ArrayIndexOutOfBoundsException e )
		{
			throw new ValidationException ("Invalid character in address");
		}
		catch ( Exception e )
		{
			throw new ValidationException (e);
		}
	}

	public static byte[] fromSatoshiStyle (String s, Chain chain) throws ValidationException
	{
		try
		{
			byte[] raw = fromBase58 (s);
			if ( chain.isProduction () )
			{
				if ( raw[0] != 0 && raw[0] != 5 )
				{ // 5 is multisig
					throw new ValidationException ("invalid address for this chain");
				}
			}
			byte[] check = Hash.hash (raw, 0, raw.length - 4);
			for ( int i = 0; i < 4; ++i )
			{
				if ( check[i] != raw[raw.length - 4 + i] )
				{
					throw new ValidationException ("Address checksum mismatch");
				}
			}
			byte[] keyDigest = new byte[raw.length - 5];
			System.arraycopy (raw, 1, keyDigest, 0, raw.length - 5);
			return keyDigest;
		}
		catch ( Exception e )
		{
			throw new ValidationException (e);
		}
	}

	public static String toSatoshiStyle (byte[] keyDigest, boolean multisig, Chain chain)
	{
		byte[] addressBytes = new byte[1 + keyDigest.length + 4];
		addressBytes[0] = (byte) (multisig ? chain.getMultisigAddressFlag () : chain.getAddressFlag ());
		System.arraycopy (keyDigest, 0, addressBytes, 1, keyDigest.length);
		byte[] check = Hash.hash (addressBytes, 0, keyDigest.length + 1);
		System.arraycopy (check, 0, addressBytes, keyDigest.length + 1, 4);
		return toBase58 (addressBytes);
	}
}
