package com.bitsofproof.supernode.core;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.util.encoders.Hex;

public class Hash
{
	private byte[] bytes;
	public static final Hash ZERO_HASH = new Hash (new byte[32]);

	public Hash (byte[] bytes)
	{
		if ( bytes.length != 32 )
		{
			throw new IllegalArgumentException ("Digest length must be 32 bytes for Hash");
		}
		this.bytes = bytes;
	}

	public Hash ()
	{
	}

	public Hash (String hex)
	{
		if ( hex.length () != 64 )
		{
			throw new IllegalArgumentException ("Digest length must be 64 hex characters for Hash");
		}

		this.bytes = reverse (Hex.decode (hex));
	}

	public static Hash digest (byte[] data)
	{
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			return new Hash (a.digest (data));
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new RuntimeException (e);
		}
	}

	public static Hash hash (byte[] data, int offset, int len)
	{
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (data, offset, len);
			return new Hash (a.digest (a.digest ()));
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new RuntimeException (e);
		}
	}

	public byte[] toByteArray ()
	{
		byte[] copy = new byte[bytes.length];
		System.arraycopy (bytes, 0, copy, 0, bytes.length);
		return copy;
	}

	public BigInteger toBigInteger ()
	{
		byte[] hashAsNumber = new byte[32];
		System.arraycopy (bytes, 0, hashAsNumber, 0, 32);
		reverse (hashAsNumber);
		return new BigInteger (hashAsNumber);
	}

	@Override
	public String toString ()
	{
		StringBuffer buf = new StringBuffer (bytes.length * 2);
		for ( int i = bytes.length - 1; i >= 0; --i )
		{
			byte b = bytes[i];
			String s = Integer.toString (0xFF & b, 16);
			if ( s.length () < 2 )
			{
				buf.append ('0');
			}
			buf.append (s);
		}
		return buf.toString ();
	}

	// in place reverse using XOR
	private static byte[] reverse (byte[] data)
	{
		for ( int i = 0, j = data.length - 1; i < data.length / 2; i++, j-- )
		{
			data[i] ^= data[j];
			data[j] ^= data[i];
			data[i] ^= data[j];
		}
		return data;
	}
}
