package com.bitsofproof.supernode.core;

import java.io.UnsupportedEncodingException;

import org.bouncycastle.util.encoders.Hex;

public class ByteUtils
{
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

	public boolean equals (byte[] a, byte[] b)
	{
		if ( a == null && b == null )
		{
			return true;
		}
		if ( a == null || b == null )
		{
			return false;
		}
		if ( a.length == b.length )
		{
			for ( int i = 0; i < a.length; ++i )
			{
				if ( a[i] != b[i] )
				{
					return false;
				}
			}
		}
		return true;
	}

	public byte[] dup (byte[] b)
	{
		byte[] a = new byte[b.length];
		System.arraycopy (b, 0, a, 0, b.length);
		return a;
	}

	public byte[] dup (byte[] b, int offset, int length)
	{
		byte[] a = new byte[length - offset];
		System.arraycopy (b, offset, a, 0, length);
		return a;
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
}
