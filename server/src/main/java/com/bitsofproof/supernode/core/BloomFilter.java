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
package com.bitsofproof.supernode.core;

import java.math.BigInteger;

import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.WireFormat;

public class BloomFilter
{
	private final BigInteger filter;
	private final long hashFunctions;
	private final long tweak;
	private final long flags;

	public BloomFilter (byte[] data, long hashFunctions, long tweak, long flags)
	{
		byte[] tmp = Arrays.clone (data);
		this.filter = new BigInteger (1, ByteUtils.reverse (tmp));
		this.hashFunctions = hashFunctions;
		this.tweak = tweak;
		this.flags = flags;
	}

	public void add (byte[] data)
	{
		for ( int i = 0; i < hashFunctions; ++i )
		{
			filter.setBit (murmurhash3bit (i, data));
		}
	}

	public boolean contains (byte[] data)
	{
		for ( int i = 0; i < hashFunctions; ++i )
		{
			if ( !filter.testBit (murmurhash3bit (i, data)) )
			{
				return false;
			}
		}
		return true;
	}

	private int murmurhash3bit (int hashNum, byte[] data)
	{
		return (int) ((murmurhash3 (data, 0, data.length, (int) (hashNum * 0xFBA4C795L + tweak)) & 0xFFFFFFFFL) % (data.length * 8));
	}

	public void toWire (WireFormat.Writer writer)
	{
		byte[] data = filter.toByteArray ();
		ByteUtils.reverse (data);
		writer.writeVarBytes (data);
		writer.writeUint32 (hashFunctions);
		writer.writeUint32 (tweak);
		writer.writeUint32 (flags);
	}

	public static BloomFilter fromWire (WireFormat.Reader reader)
	{
		byte[] data = reader.readVarBytes ();
		long hashFunctions = reader.readUint32 ();
		long tweak = reader.readUint32 ();
		long flags = reader.readUint32 ();
		return new BloomFilter (data, hashFunctions, tweak, flags);
	}

	/*
	 * This code is public domain.
	 * 
	 * The MurmurHash3 algorithm was created by Austin Appleby and put into the public domain. See http://code.google.com/p/smhasher/
	 * 
	 * This java port was authored by Yonik Seeley and was placed into the public domain per
	 * https://github.com/yonik/java_util/blob/master/src/util/hash/MurmurHash3.java.
	 */
	private static int murmurhash3 (byte[] data, int offset, int len, int seed)
	{
		int c1 = 0xcc9e2d51;
		int c2 = 0x1b873593;

		int h1 = seed;
		int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

		for ( int i = offset; i < roundedEnd; i += 4 )
		{
			// little endian load order
			int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
			k1 *= c1;
			k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
			k1 *= c2;

			h1 ^= k1;
			h1 = (h1 << 13) | (h1 >>> 19); // ROTL32(h1,13);
			h1 = h1 * 5 + 0xe6546b64;
		}

		// tail
		int k1 = 0;

		switch ( len & 0x03 )
		{
			case 3:
				k1 = (data[roundedEnd + 2] & 0xff) << 16;
				// fallthrough
			case 2:
				k1 |= (data[roundedEnd + 1] & 0xff) << 8;
				// fallthrough
			case 1:
				k1 |= data[roundedEnd] & 0xff;
				k1 *= c1;
				k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
				k1 *= c2;
				h1 ^= k1;
			default:
		}

		// finalization
		h1 ^= len;

		// fmix(h1);
		h1 ^= h1 >>> 16;
		h1 *= 0x85ebca6b;
		h1 ^= h1 >>> 13;
		h1 *= 0xc2b2ae35;
		h1 ^= h1 >>> 16;

		return h1;
	}

	public BigInteger getFilter ()
	{
		return filter;
	}

	public long getHashFunctions ()
	{
		return hashFunctions;
	}

	public long getTweak ()
	{
		return tweak;
	}

	public long getFlags ()
	{
		return flags;
	}
}
