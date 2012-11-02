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
