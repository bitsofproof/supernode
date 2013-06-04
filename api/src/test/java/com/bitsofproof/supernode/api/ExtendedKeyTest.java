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

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.ECPublicKey;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

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

	private static final String BIP32 = "BIP32.json";

	private JSONArray readObjectArray (String resource) throws IOException, JSONException
	{
		InputStream input = this.getClass ().getResource ("/" + resource).openStream ();
		StringBuffer content = new StringBuffer ();
		byte[] buffer = new byte[1024];
		int len;
		while ( (len = input.read (buffer)) > 0 )
		{
			byte[] s = new byte[len];
			System.arraycopy (buffer, 0, s, 0, len);
			content.append (new String (buffer, "UTF-8"));
		}
		return new JSONArray (content.toString ());
	}

	@Test
	public void bip32Test () throws IOException, JSONException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, ValidationException
	{
		JSONArray tests = readObjectArray (BIP32);
		for ( int i = 0; i < tests.length (); ++i )
		{
			JSONObject test = tests.getJSONObject (i);
			byte[] seed = ByteUtils.fromHex (test.getString ("master"));
			Mac mac = Mac.getInstance ("HmacSHA512", "BC");
			SecretKey seedkey = new SecretKeySpec ("Bitcoin seed".getBytes (), "HmacSHA512");
			mac.init (seedkey);
			byte[] lr = mac.doFinal (seed);
			byte[] l = Arrays.copyOfRange (lr, 0, 32);
			byte[] r = Arrays.copyOfRange (lr, 32, 64);
			ECKeyPair keyPair = new ECKeyPair (l, true);
			ExtendedKey ekprivate = new ExtendedKey (keyPair, r, 0, 0, 0);
			ExtendedKey ekpublic = ekprivate.getReadOnly ();
			assertTrue (ekprivate.serialize (true).equals (test.get ("private")));
			assertTrue (ekpublic.serialize (true).equals (test.get ("public")));
			JSONArray derived = test.getJSONArray ("derived");
			for ( int j = 0; j < derived.length (); ++j )
			{
				JSONObject derivedTest = derived.getJSONObject (j);
				JSONArray locator = derivedTest.getJSONArray ("locator");
				ExtendedKey ek = ekprivate;
				ExtendedKey ep = ekpublic;
				for ( int k = 0; k < locator.length (); ++k )
				{
					JSONObject c = locator.getJSONObject (k);
					if ( !c.getBoolean ("private") )
					{
						ek = ek.getChild (c.getInt ("sequence"));
					}
					else
					{
						ek = ek.getChild (c.getInt ("sequence") | 0x80000000);
					}
					ep = ek.getReadOnly ();
				}
				assertTrue (ek.serialize (true).equals (derivedTest.getString ("private")));
				assertTrue (ep.serialize (true).equals (derivedTest.getString ("public")));
			}
		}
	}

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
