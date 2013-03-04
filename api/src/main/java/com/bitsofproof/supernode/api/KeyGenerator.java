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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;

/**
 * Key Generator following BIP32 https://en.bitcoin.it/wiki/BIP_0032
 */
public class KeyGenerator
{
	private static final X9ECParameters curve = SECNamedCurves.getByName ("secp256k1");

	public static ExtendedKey generateKey (ExtendedKey parent, int sequence) throws ValidationException
	{
		try
		{
			Mac mac = Mac.getInstance ("HmacSHA512", "BC");
			SecretKey key = new SecretKeySpec (parent.getChainCode (), "HmacSHA512");
			mac.init (key);

			byte[] pub = parent.getKey ().getPublic ();
			byte[] extended = new byte[pub.length + 4];
			System.arraycopy (pub, 0, extended, 0, pub.length);
			extended[pub.length] = (byte) ((sequence >> 24) & 0xff);
			extended[pub.length + 1] = (byte) ((sequence >> 16) & 0xff);
			extended[pub.length + 2] = (byte) ((sequence >> 8) & 0xff);
			extended[pub.length + 3] = (byte) (sequence & 0xff);
			byte[] lr = mac.doFinal (extended);
			byte[] l = Arrays.copyOfRange (lr, 0, 32);
			byte[] r = Arrays.copyOfRange (lr, 32, 64);

			BigInteger m = new BigInteger (1, l);
			if ( parent.getKey ().getPrivate () != null )
			{
				BigInteger k = m.multiply (new BigInteger (1, parent.getKey ().getPrivate ())).mod (curve.getN ());
				return new ExtendedKey (new ECKeyPair (k, true, parent.getKey ().getAddressFlag ()), r);
			}
			else
			{
				ECPoint q = curve.getCurve ().decodePoint (pub).multiply (m);
				pub = new ECPoint.Fp (curve.getCurve (), q.getX (), q.getY (), true).getEncoded ();

				return new ExtendedKey (new ECPublicKey (pub, parent.getKey ().getAddressFlag ()), r);
			}
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchProviderException e )
		{
			throw new ValidationException (e);
		}
		catch ( InvalidKeyException e )
		{
			throw new ValidationException (e);
		}
	}
}
