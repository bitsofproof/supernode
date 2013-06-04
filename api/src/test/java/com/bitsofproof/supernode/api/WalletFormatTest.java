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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Security;
import java.util.HashMap;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.ExtendedKey;
import com.bitsofproof.supernode.api.SerializedWallet;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.ScriptFormat.Token;

public class WalletFormatTest
{

	@BeforeClass
	public static void init ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void testInOut () throws ValidationException
	{
		SerializedWallet sw = new SerializedWallet ();
		for ( int i = 0; i < 5; ++i )
		{
			SerializedWallet.WalletKey wk = new SerializedWallet.WalletKey ();
			ExtendedKey k = ExtendedKey.createNew ();
			wk.key = k.serialize (true);
			wk.created = System.currentTimeMillis () / 1000;
			wk.nextSequence = 0;
			sw.addKey (wk);
			sw.addTransaction (Transaction.createCoinbase (k.getKey (i), 5000000, i));
			sw.addAddress ("coinbase" + i, AddressConverter.toSatoshiStyle (k.getKey (i).getAddress (), 0x0));
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream ();
		sw.writeWallet (out, "passphrase");
		SerializedWallet read = SerializedWallet.readWallet (new ByteArrayInputStream (out.toByteArray ()), "passphrase", true);
		HashMap<ByteVector, Key> keyForAddress = new HashMap<ByteVector, Key> ();
		for ( int i = 0; i < 5; ++i )
		{
			assertTrue (read.getKeys ().get (i).key.equals (sw.getKeys ().get (i).key));
			ExtendedKey k = ExtendedKey.parse (read.getKeys ().get (i).key);
			keyForAddress.put (new ByteVector (k.getKey (i).getAddress ()), k.getKey (i));
		}
		for ( Transaction t : read.getTransactions () )
		{
			for ( Token token : ScriptFormat.parse (t.getOutputs ().get (0).getScript ()) )
			{
				if ( token.data != null )
				{
					assertTrue (keyForAddress.get (new ByteVector (token.data)) != null);
				}
			}
		}
	}
}
