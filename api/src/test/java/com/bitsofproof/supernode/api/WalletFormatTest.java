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

import com.bitsofproof.supernode.api.ScriptFormat.Token;

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

	@Test
	public void testStored () throws ValidationException
	{
		ByteArrayInputStream in =
				new ByteArrayInputStream (
						ByteUtils
								.fromHex ("626f7077616c6c657482bcc0ab558a070bbb33f16c6fae818131bcf8d47722919c77977eead6cef23b3390d30553edce384bd0e17f6707d46d0a884852aa4f2629fac59c8ee3d0e64e4850606aa70902dc31a0120ad7e3fbd2391e989efcf1381b38d11b8866c6d5e06f33dc06d61030b64a1ecd94e9a2890a2c6ebaafa229853fbc57646d7541d1a36fc17fc9cc62fc82bf9f47c69b60320b87d66065379e80f4367c636e9ebccd3b31bf1e60ceb6d99465f12168dce92aa3d33090f0285e859d3af21da1a5c1569c1c45b8ab1e294a664cbc6bc62323e816c205da3e4c40c843e9ee85475354415d6ca8b3008d6494b54ed474cc7985ef3170539e0ffb36bda7629b3930af98293caa33316b6ec1fb6dda18e715ad5fbc138024a2cdb11b03c44dc7ed1edf0fd0297ffed0dd1d98042ffd1ce287e3252c9d2cfdd16479688a39c55dd0b587da1f3a6bd2dd8e1d6e8674c4b308bef1f0818c15f7d1ff207eded52999efac246d2b886cafa468f372c193b1a704ad8d0952d635f1c5fa863dea562fff508c97a5d51469201495c80389be25b176c3789f39f36074df47ae54bbfaa8b746b4d1379cf9c29b62ce630970c0da60f2e5e8f621bc2aa8cb48c4f21e093db5f035c77843e97f0562f7a6ffc558bcce0c8936c8a90354f73c0900eafb090c3f1ef7630de4388a17b2fa10c3e09008bb5320124a7e220d10e18782aa71332bc5e12828f228ca20814925bb2ccbbe6556859630af55b2ac98118c10b6f9c004b9af6c8d4be0ef5b07ff1d13afad96168eb0caf53b489bb2e6ff53a4ac8e38fcee056e46ebe172a28f930aabdad4810f0e130256c260ef3fd714766dd92dc82987d5b839d5f84a00f6d35f1d5add77088b925e4381884f3bf0ccbc7c2d3ebd1e65c11e7d25324047beb7a9642394907ece5eba11f4583c3bdeac1e321abb6847c1d2ce798cf8bd108ada0aaa89fe1a1bf0777ed30e8f2d0cfdb80adcacb3788b68d6b9cdaf26b67236b4e41836b972a389dfd21c3d2ab8ce28b0aa6b3ff89736f17cb8fd232721619fccf505394ff2694c4a71f2daea635ee9ecd588b6efaadb343b4a418207ea4ea5e5312ae8e4095f7f1e4d85ef632f2a2883b246d942ad344e56b0c81c6a1e2d4dd03d2e8688e20d4c7ed7562dffab0ee7ef5431e979944e68abd216d3a1b93a4e0e00fc8c141ee15a974ad719e0f26d0f1a10330ac7bbab78d2a7d6f774cf"));

		SerializedWallet read = SerializedWallet.readWallet (in, "passphrase", true);
		HashMap<ByteVector, Key> keyForAddress = new HashMap<ByteVector, Key> ();
		for ( int i = 0; i < 5; ++i )
		{
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
