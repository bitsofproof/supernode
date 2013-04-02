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

import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeyGeneratorTest
{
	private final SecureRandom random = new SecureRandom ();

	@BeforeClass
	public static void init ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void testGenerator () throws ValidationException
	{
		ECKeyPair master = ECKeyPair.createNew (true);
		byte[] chainCode = new byte[32];
		random.nextBytes (chainCode);
		ExtendedKey ekprivate = new ExtendedKey (master, chainCode);
		ExtendedKey ekpublic = new ExtendedKey (new ECPublicKey (master.getPublic (), master.isCompressed ()), chainCode);

		KeyGenerator privateGenerator = new DefaultKeyGenerator (ekprivate, 0, 0x00, 0x05);
		KeyGenerator publicGenerator = new DefaultKeyGenerator (ekpublic, 0, 0x00, 0x05);

		for ( int i = 0; i < 10; ++i )
		{
			Key k1 = privateGenerator.generateNextKey ();
			Key k2 = publicGenerator.generateNextKey ();
			assertTrue (Arrays.equals (k1.getPublic (), k2.getPublic ()));
		}
	}
}
