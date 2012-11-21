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

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

public class KeyTest
{

	@Test
	public void otherSignTest () throws ValidationException
	{
		BigInteger privkey = new BigInteger (1, ByteUtils.fromHex ("180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19"));
		ECKeyPair key = new ECKeyPair (privkey);
		byte[] message = new byte[32];
		byte[] output = key.sign (message);
		assertTrue (ECKeyPair.verify (message, output, key.getPublic ()));

		byte[] sig =
				ByteUtils
						.fromHex ("3046022100dffbc26774fc841bbe1c1362fd643609c6e42dcb274763476d87af2c0597e89e022100c59e3c13b96b316cae9fa0ab0260612c7a133a6fe2b3445b6bf80b3123bf274d");
		assertTrue (ECKeyPair.verify (message, sig, key.getPublic ()));
	}

	@Test
	public void otherasnTest () throws ValidationException
	{
		byte[] privkeyASN1 =
				ByteUtils
						.fromHex ("3082011302010104205c0b98e524ad188ddef35dc6abba13c34a351a05409e5d285403718b93336a4aa081a53081a2020101302c06072a8648ce3d0101022100fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f300604010004010704410479be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8022100fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141020101a144034200042af7a2aafe8dafd7dc7f9cfb58ce09bda7dce28653ab229b98d1d3d759660c672dd0db18c8c2d76aa470448e876fc2089ab1354c01a6e72cefc50915f4a963ee");
		ECKeyPair decodedKey = new ECKeyPair (privkeyASN1);

		ECKeyPair roundtripKey = new ECKeyPair (decodedKey.toByteArray ());

		for ( ECKeyPair key : new ECKeyPair[] { decodedKey, roundtripKey } )
		{
			byte[] message = ByteUtils.reverse (ByteUtils.fromHex ("11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"));
			byte[] output = key.sign (message);
			assertTrue (ECKeyPair.verify (message, output, key.getPublic ()));

			output =
					ByteUtils
							.fromHex ("304502206faa2ebc614bf4a0b31f0ce4ed9012eb193302ec2bcaccc7ae8bb40577f47549022100c73a1a1acc209f3f860bf9b9f5e13e9433db6f8b7bd527a088a0e0cd0a4c83e9");
			assertTrue (ECKeyPair.verify (message, output, key.getPublic ()));
		}

		byte[] message = ByteUtils.reverse (ByteUtils.fromHex ("11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"));
		assertTrue (ECKeyPair.verify (message, decodedKey.sign (message), roundtripKey.getPublic ()));
		assertTrue (ECKeyPair.verify (message, roundtripKey.sign (message), decodedKey.getPublic ()));
	}

	@Test
	public void signTest () throws ValidationException
	{
		ECKeyPair k = ECKeyPair.createNew ();
		byte[] hash = Hash.sha256 ("Hello world".getBytes ());
		byte[] signature = k.sign (hash);
		assertTrue (ECKeyPair.verify (hash, signature, k.getPublic ()));
	}

	@Test
	public void asn1Test () throws ValidationException
	{
		ECKeyPair k = ECKeyPair.createNew ();
		byte[] hash = Hash.sha256 ("Hello world".getBytes ());
		byte[] signature = k.sign (hash);

		byte[] storedKey = k.toByteArray ();

		ECKeyPair read = new ECKeyPair (storedKey);
		assertTrue (ECKeyPair.verify (hash, signature, read.getPublic ()));
		assertTrue (ECKeyPair.verify (hash, read.sign (hash), k.getPublic ()));
	}
}
