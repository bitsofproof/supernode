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
		Wallet wallet = Wallet.createWallet (0x0);
		ExtendedKey master = wallet.getMaster ();
		Wallet account1 = wallet.createSubWallet (0);
		Wallet account2 = wallet.createSubWallet (1);
		ExtendedKey address10 = account1.generateNextKey ();
		ExtendedKey address11 = account1.generateNextKey ();
		ExtendedKey address12 = account1.generateNextKey ();
		assertTrue (account1.getNextKeySequence () == 3);
		ExtendedKey address20 = account2.generateNextKey ();
		ExtendedKey address21 = account2.generateNextKey ();
		ExtendedKey address22 = account2.generateNextKey ();
		assertTrue (account2.getNextKeySequence () == 3);
		Wallet account21 = account2.createSubWallet (1);
		ExtendedKey address210 = account21.generateNextKey ();
		ExtendedKey address211 = account21.generateNextKey ();
		ExtendedKey address212 = account21.generateNextKey ();
		assertTrue (account21.getNextKeySequence () == 3);

		assertTrue (account21.getAddresses (0x0).size () == 3);
		assertTrue (account2.getAddresses (0x0).size () == 6);
		assertTrue (wallet.getAddresses (0x0).size () == 11);

		assertTrue (Arrays.equals (account21.getMaster ().getMaster ().getPublic (), account2.getKey (1).getMaster ().getPublic ()));
	}

	@SuppressWarnings ("unused")
	@Test
	public void testReadOnlyWallet () throws ValidationException
	{
		Wallet fullControlWallet = Wallet.createWallet (0x0);

		ECPublicKey pub =
				new ECPublicKey (fullControlWallet.getMaster ().getMaster ().getPublic (), fullControlWallet.getMaster ().getMaster ().getAddressFlag ());
		byte[] chainCode = fullControlWallet.getMaster ().getChainCode ();

		Wallet readOnlyWallet = new Wallet (new ExtendedKey (pub, chainCode), 0);

		Wallet account1 = readOnlyWallet.createSubWallet (0);
		Wallet fullAccount1 = fullControlWallet.createSubWallet (0);
		ExtendedKey address10 = account1.generateNextKey ();
		ExtendedKey fullAddress10 = fullAccount1.generateNextKey ();

		assertTrue (Arrays.equals (address10.getMaster ().getPublic (), fullAddress10.getMaster ().getPublic ()));

		Wallet account2 = readOnlyWallet.createSubWallet (1);
		Wallet fullAccount2 = fullControlWallet.createSubWallet (1);
		account2.generateNextKey ();
		account2.generateNextKey ();
		account2.generateNextKey ();

		fullAccount2.generateNextKey ();
		fullAccount2.generateNextKey ();
		fullAccount2.generateNextKey ();

		Wallet subWallet = account2.createSubWallet (1);
		Wallet subFullWallet = fullAccount2.createSubWallet (1);
		ExtendedKey a1 = subWallet.generateNextKey ();
		ExtendedKey fa1 = subFullWallet.generateNextKey ();

		assertTrue (Arrays.equals (a1.getMaster ().getPublic (), fa1.getMaster ().getPublic ()));

	}
}
