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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		ExtendedKey ekpublic = new ExtendedKey (new ECPublicKey (master.getPublic (), true), chainCode);

		KeyGenerator privateGenerator = new DefaultKeyGenerator (ekprivate, 20, 0x00);
		KeyGenerator publicGenerator = new DefaultKeyGenerator (ekpublic, 20, 0x00);

		for ( int i = 0; i < 20; ++i )
		{
			Key fullControl = privateGenerator.getKey (i);
			Key readOnly = publicGenerator.getKey (i);

			assertTrue (Arrays.equals (fullControl.getPublic (), readOnly.getPublic ()));
			assertTrue (Arrays.equals (fullControl.getAddress (), readOnly.getAddress ()));

			byte[] toSign = new byte[100];
			random.nextBytes (toSign);
			byte[] signature = fullControl.sign (toSign);

			assertTrue (readOnly.verify (toSign, signature));
		}
	}

	@Test
	public void testReadOnlyWallet () throws ValidationException
	{
		KeyGenerator fullControlWallet = DefaultKeyGenerator.createKeyGenerator (2, 0x0);

		ECPublicKey pub = new ECPublicKey (fullControlWallet.getMaster ().getKey ().getPublic (), true);
		byte[] chainCode = fullControlWallet.getMaster ().getChainCode ();

		KeyGenerator readOnlyWallet = new DefaultKeyGenerator (new ExtendedKey (pub, chainCode), 2, 0x0);

		KeyGenerator account1 = new DefaultKeyGenerator (readOnlyWallet.getExtendedKey (0), 1, 0x0);
		KeyGenerator fullAccount1 = new DefaultKeyGenerator (fullControlWallet.getExtendedKey (0), 1, 0x0);
		Key address10 = account1.getRandomKey ();
		Key fullAddress10 = fullAccount1.getRandomKey ();

		assertTrue (Arrays.equals (address10.getPublic (), fullAddress10.getPublic ()));

		KeyGenerator account2 = new DefaultKeyGenerator (readOnlyWallet.getExtendedKey (1), 3, 0x0);
		KeyGenerator fullAccount2 = new DefaultKeyGenerator (fullControlWallet.getExtendedKey (1), 3, 0x0);

		Iterator<String> readonlyAddresses = account2.getAddresses ().iterator ();
		Iterator<String> fullControlAddresses = fullAccount2.getAddresses ().iterator ();
		while ( readonlyAddresses.hasNext () )
		{
			String a1 = readonlyAddresses.next ();
			String a2 = fullControlAddresses.next ();
			assertTrue (a1.equals (a2));
		}

		KeyGenerator subWallet = new DefaultKeyGenerator (account2.getExtendedKey (1), 1, 0x0);
		KeyGenerator subFullWallet = new DefaultKeyGenerator (fullAccount2.getExtendedKey (1), 1, 0x0);
		Key a11 = subWallet.getRandomKey ();
		Key fa11 = subFullWallet.getRandomKey ();

		assertTrue (Arrays.equals (a11.getPublic (), fa11.getPublic ()));

	}

	private static final ThreadMXBean mxb = ManagementFactory.getThreadMXBean ();
	private static final Logger log = LoggerFactory.getLogger (KeyGeneratorTest.class);

	@Test
	public void testECDSASpeed () throws ValidationException
	{
		ECKeyPair key = ECKeyPair.createNew (true);
		byte[] data = new byte[32];
		random.nextBytes (data);
		byte[] signature = key.sign (data);
		long cpu = -mxb.getCurrentThreadUserTime ();
		for ( int i = 0; i < 100; ++i )
		{
			assertTrue (key.verify (data, signature));
		}
		cpu += mxb.getCurrentThreadUserTime ();
		double speed = 100.0 / (cpu / 10.0e9);
		log.info ("ECDSA validation speed : " + speed + " signatures/second");
		assertTrue (speed > 100.0);
	}
}
