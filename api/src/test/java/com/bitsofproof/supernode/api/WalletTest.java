/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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

import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

public class WalletTest
{
	@BeforeClass
	public static void init ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@SuppressWarnings ("unused")
	@Test
	public void testPrivateWallet () throws ValidationException
	{
		KeyGenerator wallet = DefaultKeyGenerator.createKeyGenerator (0x0, 0x05);
		ExtendedKey master = wallet.getMaster ();
		KeyGenerator account1 = wallet.createSubKeyGenerator (0);
		KeyGenerator account2 = wallet.createSubKeyGenerator (1);
		Key address10 = account1.generateNextKey ();
		Key address11 = account1.generateNextKey ();
		Key address12 = account1.generateNextKey ();
		assertTrue (account1.getNextKeySequence () == 3);
		Key address20 = account2.generateNextKey ();
		Key address21 = account2.generateNextKey ();
		Key address22 = account2.generateNextKey ();
		assertTrue (account2.getNextKeySequence () == 3);
		KeyGenerator account21 = account2.createSubKeyGenerator (1);
		Key address210 = account21.generateNextKey ();
		Key address211 = account21.generateNextKey ();
		Key address212 = account21.generateNextKey ();
		assertTrue (account21.getNextKeySequence () == 3);

		assertTrue (account21.getAddresses ().size () == 3);
		assertTrue (account2.getAddresses ().size () == 6);
		assertTrue (wallet.getAddresses ().size () == 11);

		assertTrue (Arrays.equals (account21.getMaster ().getKey ().getPublic (), account2.getKey (1).getPublic ()));
	}

	@Test
	public void testReadOnlyWallet () throws ValidationException
	{
		KeyGenerator fullControlWallet = DefaultKeyGenerator.createKeyGenerator (0x0, 0x05);

		ECPublicKey pub = new ECPublicKey (fullControlWallet.getMaster ().getKey ().getPublic (), fullControlWallet.getMaster ().getKey ().isCompressed ());
		byte[] chainCode = fullControlWallet.getMaster ().getChainCode ();

		KeyGenerator readOnlyWallet = new DefaultKeyGenerator (new ExtendedKey (pub, chainCode), 0, 0x0, 0x05);

		KeyGenerator account1 = readOnlyWallet.createSubKeyGenerator (0);
		KeyGenerator fullAccount1 = fullControlWallet.createSubKeyGenerator (0);
		Key address10 = account1.generateNextKey ();
		Key fullAddress10 = fullAccount1.generateNextKey ();

		assertTrue (Arrays.equals (address10.getPublic (), fullAddress10.getPublic ()));

		KeyGenerator account2 = readOnlyWallet.createSubKeyGenerator (1);
		KeyGenerator fullAccount2 = fullControlWallet.createSubKeyGenerator (1);
		account2.generateNextKey ();
		account2.generateNextKey ();
		account2.generateNextKey ();

		fullAccount2.generateNextKey ();
		fullAccount2.generateNextKey ();
		fullAccount2.generateNextKey ();

		KeyGenerator subWallet = account2.createSubKeyGenerator (1);
		KeyGenerator subFullWallet = fullAccount2.createSubKeyGenerator (1);
		Key a1 = subWallet.generateNextKey ();
		Key fa1 = subFullWallet.generateNextKey ();

		assertTrue (Arrays.equals (a1.getPublic (), fa1.getPublic ()));

	}
}
