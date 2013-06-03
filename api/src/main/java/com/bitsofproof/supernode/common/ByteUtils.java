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
package com.bitsofproof.supernode.common;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;


public class ByteUtils
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
		if ( b.length == 0 )
		{
			return "";
		}

		int lz = 0;
		while ( lz < b.length && b[lz] == 0 )
		{
			++lz;
		}

		StringBuffer s = new StringBuffer ();
		BigInteger n = new BigInteger (1, b);
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

	public static String toBase58WithChecksum (byte[] b)
	{
		byte[] cs = Hash.hash (b);
		byte[] extended = new byte[b.length + 4];
		System.arraycopy (b, 0, extended, 0, b.length);
		System.arraycopy (cs, 0, extended, b.length, 4);
		return toBase58 (extended);
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

	public static byte[] reverse (byte[] data)
	{
		for ( int i = 0, j = data.length - 1; i < data.length / 2; i++, j-- )
		{
			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}

	public static String toHex (byte[] data)
	{
		try
		{
			return new String (Hex.encode (data), "US-ASCII");
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		return null;
	}

	public static byte[] fromHex (String hex)
	{
		return Hex.decode (hex);
	}

	public static boolean isLessThanUnsigned (long n1, long n2)
	{
		return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
	}
}
