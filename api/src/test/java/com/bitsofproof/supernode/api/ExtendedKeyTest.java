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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedKeyTest
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
		ExtendedKey ekprivate = ExtendedKey.createNew ();
		ExtendedKey ekpublic = new ExtendedKey (new ECPublicKey (ekprivate.getMaster ().getPublic (), true), ekprivate.getChainCode (), 0, 0, 0);

		for ( int i = 0; i < 20; ++i )
		{
			Key fullControl = ekprivate.getKey (i);
			Key readOnly = ekpublic.getKey (i);

			assertTrue (Arrays.equals (fullControl.getPublic (), readOnly.getPublic ()));
			assertTrue (Arrays.equals (fullControl.getAddress (), readOnly.getAddress ()));

			byte[] toSign = new byte[100];
			random.nextBytes (toSign);
			byte[] signature = fullControl.sign (toSign);

			assertTrue (readOnly.verify (toSign, signature));
		}
	}

	private static final ThreadMXBean mxb = ManagementFactory.getThreadMXBean ();
	private static final Logger log = LoggerFactory.getLogger (ExtendedKeyTest.class);

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
