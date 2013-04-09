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

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultKeyGenerator implements KeyGenerator
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);

	private final ExtendedKey master;
	private int nextKey;
	private List<DefaultKeyGenerator> subs;
	private final List<Key> importedKeys = new ArrayList<Key> ();
	private final Map<String, Key> keyForAddress = new HashMap<String, Key> ();
	private final List<KeyGeneratorListener> walletListener = new ArrayList<KeyGeneratorListener> ();
	private final int addressFlag;
	private final int p2shAddressFlag;

	private static final X9ECParameters curve = SECNamedCurves.getByName ("secp256k1");

	/**
	 * Key Generator following BIP32 https://en.bitcoin.it/wiki/BIP_0032
	 */
	private static ExtendedKey generateKey (ExtendedKey parent, int sequence) throws ValidationException
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
				return new ExtendedKey (new ECKeyPair (k, true), r);
			}
			else
			{
				ECPoint q = curve.getCurve ().decodePoint (pub).multiply (m);
				pub = new ECPoint.Fp (curve.getCurve (), q.getX (), q.getY (), true).getEncoded ();

				return new ExtendedKey (new ECPublicKey (pub, true), r);
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

	public DefaultKeyGenerator (ExtendedKey master, int nextKey, int addressFlag, int p2shAddressFlag) throws ValidationException
	{
		this.master = master;
		this.nextKey = nextKey;
		this.addressFlag = addressFlag;
		this.p2shAddressFlag = p2shAddressFlag;
		for ( int i = 0; i < nextKey; ++i )
		{
			Key k = getKey (i);
			keyForAddress.put (AddressConverter.toSatoshiStyle (k.getPublic (), addressFlag), k);
		}
	}

	@Override
	public int getAddressFlag ()
	{
		return addressFlag;
	}

	@Override
	public int getP2SHAddressFlag ()
	{
		return p2shAddressFlag;
	}

	public static KeyGenerator createKeyGenerator (int addressFlag, int multiAddressFlag) throws ValidationException
	{
		SecureRandom random = new SecureRandom ();
		ECKeyPair master = ECKeyPair.createNew (true);
		byte[] chainCode = new byte[32];
		random.nextBytes (chainCode);
		ExtendedKey parent = new ExtendedKey (master, chainCode);
		log.debug ("Created new wallet");
		return new DefaultKeyGenerator (parent, 0, addressFlag, multiAddressFlag);
	}

	@Override
	public KeyGenerator createSubKeyGenerator (int sequence) throws ValidationException
	{
		if ( sequence > nextKey )
		{
			throw new ValidationException ("Subwallets must use consecutive sequences");
		}
		if ( sequence == nextKey )
		{
			generateNextKey ();
		}
		if ( subs == null )
		{
			subs = new ArrayList<DefaultKeyGenerator> ();
		}
		DefaultKeyGenerator sub = new DefaultKeyGenerator (generateKey (master, sequence), 0, addressFlag, p2shAddressFlag);
		subs.add (sub);
		for ( KeyGeneratorListener l : walletListener )
		{
			sub.addListener (l);
		}
		log.debug ("Created new sub-wallet " + sequence);
		return sub;
	}

	@Override
	public void addListener (KeyGeneratorListener listener)
	{
		walletListener.add (listener);
		if ( subs != null )
		{
			for ( KeyGenerator sub : subs )
			{
				sub.addListener (listener);
			}
		}
	}

	@Override
	public ExtendedKey getExtendedKey (int sequence) throws ValidationException
	{
		if ( sequence > nextKey )
		{
			throw new ValidationException ("Sequence requested is higher than generated before");
		}
		return generateKey (master, sequence);
	}

	@Override
	public Key getKey (int sequence) throws ValidationException
	{
		return getExtendedKey (sequence).getKey ();
	}

	@Override
	public Key generateNextKey () throws ValidationException
	{
		ExtendedKey k = generateKey (master, nextKey++);
		String address = AddressConverter.toSatoshiStyle (k.getKey ().getAddress (), addressFlag);
		log.debug ("Created new key [" + (nextKey - 1) + "] for address " + address);
		Key key = k.getKey ();
		keyForAddress.put (address, key);
		notifyNewKey (key, address, true);
		return key;
	}

	private void notifyNewKey (Key k, String address, boolean pristine)
	{
		for ( KeyGeneratorListener l : walletListener )
		{
			l.notifyNewKey (address, k, pristine);
		}
	}

	@Override
	public void importKey (Key k)
	{
		try
		{
			importedKeys.add (k.clone ());

			String address = AddressConverter.toSatoshiStyle (k.getAddress (), addressFlag);
			log.debug ("Imported key for address " + address);
			keyForAddress.put (address, k);
			notifyNewKey (k, address, false);
		}
		catch ( CloneNotSupportedException e )
		{
		}
	}

	@Override
	public Key getKeyForAddress (String address)
	{
		return keyForAddress.get (address);
	}

	@Override
	public List<String> getAddresses () throws ValidationException
	{
		List<String> addresses = new ArrayList<String> (keyForAddress.keySet ());
		if ( subs != null )
		{
			for ( KeyGenerator sub : subs )
			{
				addresses.addAll (sub.getAddresses ());
			}
		}
		return addresses;
	}

	@Override
	public ExtendedKey getMaster ()
	{
		return master;
	}

	@Override
	public int getNextKeySequence ()
	{
		return nextKey;
	}
}
