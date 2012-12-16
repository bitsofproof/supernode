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
package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import org.junit.Test;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.ValidationException;

public class AddressTest
{
	private static final ChainParameter chain = new ChainParameter ()
	{
		@Override
		public int getDifficultyReviewBlocks ()
		{
			return 2016;
		}

		@Override
		public int getTargetBlockTime ()
		{
			return 1209600;
		}

		@Override
		public boolean isProduction ()
		{
			return true;
		}

		@Override
		public int getAddressFlag ()
		{
			return 0x00;
		}

		@Override
		public int getMultisigAddressFlag ()
		{
			return 0x05;
		}
	};

	@Test
	public void base58Test () throws ValidationException
	{
		SecureRandom rnd = new SecureRandom ();
		for ( int i = 0; i < 10000; ++i )
		{
			BigInteger n = new BigInteger (160, rnd);
			assertTrue (new BigInteger (AddressConverter.fromBase58 (AddressConverter.toBase58 (n.toByteArray ()))).equals (n));
		}
	}

	@Test
	public void satoshiAddressTest () throws ValidationException, UnsupportedEncodingException
	{
		// some real addresses
		assertTrue (AddressConverter.toSatoshiStyle (ByteUtils.fromHex ("9e969049aefe972e41aaefac385296ce18f30751"), false, chain).equals (
				"1FTY8etSpSW3xv6s2XRrYE77rrRfza8aJJ"));
		assertTrue (AddressConverter.toSatoshiStyle (ByteUtils.fromHex ("623dbe779a29c6bc2615cd7bf5a35453f495e229"), false, chain).equals (
				"19xTBrDcnZiJSMuzirE7SfcsjkG1ghp1RL"));

		// some random
		SecureRandom rnd = new SecureRandom ();
		for ( int i = 0; i < 10000; ++i )
		{
			BigInteger n = new BigInteger (160, rnd);
			byte[] keyDigest = n.toByteArray ();
			String a = AddressConverter.toSatoshiStyle (keyDigest, false, chain);
			byte[] check = AddressConverter.fromSatoshiStyle (a, chain);
			assertTrue (Arrays.equals (check, keyDigest));
		}
	}
}
