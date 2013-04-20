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
package com.bitsofproof.supernode.api;

import java.security.SecureRandom;

import org.bouncycastle.util.Arrays;

public class BloomFilter
{
	public static enum UpdateMode
	{
		none, all, keys
	}

	private final byte[] filter;
	private final long hashFunctions;
	private final long tweak;
	private final UpdateMode update;

	private static final int MAX_FILTER_SIZE = 36000;
	private static final int MAX_HASH_FUNCS = 50;

	public static BloomFilter createOptimalFilter (int n, double falsePositiveProbability, UpdateMode update)
	{
		return createOptimalFilter (n, falsePositiveProbability, Math.abs (new SecureRandom ().nextInt ()), update);
	}

	public static BloomFilter createOptimalFilter (int n, double falsePositiveProbability, long tweak, UpdateMode update)
	{
		// http://en.wikipedia.org/wiki/Bloom_filter#Probability_of_false_positives
		double ln2 = Math.log (2.0);
		int mod = Math.max (1, (int) Math.min ((-n * Math.log (falsePositiveProbability) / (ln2 * ln2)) / 8.0, MAX_FILTER_SIZE));
		long hashFunctions = Math.max (1, Math.min ((int) (mod * 8.0 / n * ln2), MAX_HASH_FUNCS));
		return new BloomFilter (new byte[mod], hashFunctions, tweak, update);
	}

	public double getFalsePositiveProbability (int n)
	{
		return Math.pow (1 - Math.pow (Math.E, -1.0 * (hashFunctions * n) / (filter.length * 8)), hashFunctions);
	}

	public BloomFilter (byte[] data, long hashFunctions, long tweak, UpdateMode update)
	{
		filter = Arrays.clone (data);
		this.hashFunctions = Math.min (hashFunctions, MAX_HASH_FUNCS);
		this.tweak = tweak;
		this.update = update;
	}

	public void addAddress (String address, int addressFlag) throws ValidationException
	{
		add (AddressConverter.fromSatoshiStyle (address, addressFlag));
	}

	public void addOutpoint (String hash, long ix)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeHash (new Hash (hash));
		writer.writeUint32 (ix);
		add (writer.toByteArray ());
	}

	public boolean containsOutpoint (String hash, long ix)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeHash (new Hash (hash));
		writer.writeUint32 (ix);
		return contains (writer.toByteArray ());
	}

	public boolean containsAddress (String address, int addressFlag) throws ValidationException
	{
		return contains (AddressConverter.fromSatoshiStyle (address, addressFlag));
	}

	private void setBit (int n)
	{
		filter[n >>> 3] |= 1 << (7 & n);
	}

	private boolean testBit (int n)
	{
		return (filter[n >>> 3] & 1 << (7 & n)) != 0;
	}

	public void add (byte[] data)
	{
		for ( int i = 0; i < hashFunctions; ++i )
		{
			setBit (murmurhash3bit (i, data));
		}
	}

	public boolean contains (byte[] data)
	{
		for ( int i = 0; i < hashFunctions; ++i )
		{
			if ( !testBit (murmurhash3bit (i, data)) )
			{
				return false;
			}
		}
		return true;
	}

	private int murmurhash3bit (int hashNum, byte[] data)
	{
		return (int) ((murmurhash3 (data, 0, data.length, (int) (hashNum * 0xFBA4C795L + tweak)) & 0xFFFFFFFFL) % (filter.length * 8));
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeVarBytes (filter);
		writer.writeUint32 (hashFunctions);
		writer.writeUint32 (tweak);
		writer.writeByte (update.ordinal ());
	}

	public static BloomFilter fromWire (WireFormat.Reader reader)
	{
		byte[] data = reader.readVarBytes ();
		long hashFunctions = reader.readUint32 ();
		long tweak = reader.readUint32 ();
		int update = reader.readByte ();
		return new BloomFilter (data, hashFunctions, tweak, UpdateMode.values ()[update]);
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

	public byte[] getFilter ()
	{
		return filter.clone ();
	}

	public long getHashFunctions ()
	{
		return hashFunctions;
	}

	public long getTweak ()
	{
		return tweak;
	}

	public UpdateMode getUpdateMode ()
	{
		return update;
	}
}
