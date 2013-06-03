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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;


public class Hash
{
	private final byte[] bytes;
	public static final Hash ZERO_HASH = new Hash (new byte[32]);
	public static final String ZERO_HASH_STRING = new Hash (new byte[32]).toString ();

	public Hash (byte[] hash)
	{
		if ( hash.length != 32 )
		{
			throw new IllegalArgumentException ("Digest length must be 32 bytes for Hash");
		}
		this.bytes = new byte[32];
		System.arraycopy (hash, 0, this.bytes, 0, 32);
	}

	public Hash (String hex)
	{
		if ( hex.length () != 64 )
		{
			throw new IllegalArgumentException ("Digest length must be 64 hex characters for Hash");
		}

		this.bytes = ByteUtils.reverse (ByteUtils.fromHex (hex));
	}

	public static byte[] sha256 (byte[] data)
	{
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			return a.digest (data);
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new RuntimeException (e);
		}
	}

	public static byte[] keyHash (byte[] key)
	{
		byte[] ph = new byte[20];
		try
		{
			byte[] sha256 = MessageDigest.getInstance ("SHA-256").digest (key);
			RIPEMD160Digest digest = new RIPEMD160Digest ();
			digest.update (sha256, 0, sha256.length);
			digest.doFinal (ph, 0);
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new RuntimeException (e);
		}
		return ph;
	}

	public static byte[] hash (byte[] data, int offset, int len)
	{
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (data, offset, len);
			return a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new RuntimeException (e);
		}
	}

	public static byte[] hash (byte[] data)
	{
		return hash (data, 0, data.length);
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
		ByteUtils.reverse (hashAsNumber);
		return new BigInteger (1, hashAsNumber);
	}

	@Override
	public String toString ()
	{
		return ByteUtils.toHex (ByteUtils.reverse (toByteArray ()));
	}
}
