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
package com.bitsofproof.supernode.wallet;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.crypto.generators.SCrypt;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Network;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;

/**
 * Key serializer following BIP38 https://en.bitcoin.it/wiki/BIP_0038 and WIF https://en.bitcoin.it/wiki/Wallet_import_format
 */
public class KeyFormatter
{
	private final Network network;
	private final String passphrase;

	public KeyFormatter (String passphrase, Network network)
	{
		this.passphrase = passphrase;
		this.network = network;
	}

	public boolean hasPassPhrase ()
	{
		return passphrase != null;
	}

	public Network getNetwork ()
	{
		return network;
	}

	public String serializeKey (Key key) throws ValidationException
	{
		if ( passphrase == null )
		{
			return ECKeyPair.serializeWIF (key);
		}
		return serializeBIP38 (key);
	}

	public ECKeyPair parseSerializedKey (String serialized) throws ValidationException
	{
		byte[] store = ByteUtils.fromBase58 (serialized);
		return parseBytesKey (store);
	}

	private ECKeyPair parseBytesKey (byte[] store) throws ValidationException
	{
		if ( (store[0] & 0xff) == 0x80 )
		{
			return ECKeyPair.parseBytesWIF (store);
		}
		else if ( (store[0] & 0xff) == 0x01 )
		{
			if ( passphrase == null )
			{
				throw new ValidationException ("Need passphrase");
			}
			return parseBIP38 (store);
		}

		throw new ValidationException ("invalid key");
	}

	private String serializeBIP38 (Key key) throws ValidationException
	{
		return ByteUtils.toBase58 (bytesBIP38 (key));
	}

	public String createBIP38Request (int lot, int sequence) throws ValidationException
	{
		byte[] result = new byte[49];

		SecureRandom random = new SecureRandom ();
		byte[] ownersalt = null;
		byte[] ownentropy = new byte[8];
		if ( lot != 0 )
		{
			ownersalt = new byte[4];
			random.nextBytes (ownersalt);
			byte[] ls = BigInteger.valueOf (lot << 12 + sequence).toByteArray ();
			System.arraycopy (ownersalt, 0, ownentropy, 0, 4);
			System.arraycopy (ls, Math.max (0, ls.length - 4), ownentropy, 4 + Math.max (0, 4 - ls.length), Math.min (4, ls.length));
		}
		else
		{
			ownersalt = new byte[8];
			random.nextBytes (ownersalt);
			ownentropy = ownersalt;
		}
		try
		{
			byte[] prefactor = SCrypt.generate (passphrase.getBytes ("UTF-8"), ownersalt, 16384, 8, 8, 32);
			byte[] passfactor = prefactor;
			if ( lot != 0 )
			{
				byte[] tmp = new byte[32 + 8];
				System.arraycopy (prefactor, 0, tmp, 0, 32);
				System.arraycopy (ownentropy, 0, tmp, 32, 8);
				passfactor = Hash.hash (tmp);
			}
			ECKeyPair kp = new ECKeyPair (passfactor, true);
			byte[] passpoint = kp.getPublic ();
			result[0] = (byte) 0x2C;
			result[1] = (byte) 0xE9;
			result[2] = (byte) 0xB3;
			result[3] = (byte) 0xE1;
			result[4] = (byte) 0xFF;
			result[5] = (byte) 0x39;
			result[6] = (byte) 0xE2;
			if ( lot != 0 )
			{
				result[7] = (byte) 0x53;
			}
			else
			{
				result[7] = (byte) 0x51;
			}
			System.arraycopy (ownentropy, 0, result, 8, 8);
			System.arraycopy (passpoint, 0, result, 16, 33);

		}
		catch ( UnsupportedEncodingException e )
		{
		}
		return ByteUtils.toBase58WithChecksum (result);
	}

	private ECKeyPair parseBIP38 (byte[] store) throws ValidationException
	{
		if ( store.length != 43 )
		{
			throw new ValidationException ("invalid key length for BIP38");
		}
		boolean ec = false;
		boolean compressed = false;
		boolean hasLot = false;
		if ( (store[1] & 0xff) == 0x42 )
		{
			if ( (store[2] & 0xff) == 0xc0 )
			{
				// non-EC-multiplied keys without compression (prefix 6PR)
			}
			else if ( (store[2] & 0xff) == 0xe0 )
			{
				// non-EC-multiplied keys with compression (prefix 6PY)
				compressed = true;
			}
			else
			{
				throw new ValidationException ("invalid key");
			}
		}
		else if ( (store[1] & 0xff) == 0x43 )
		{
			// EC-multiplied keys without compression (prefix 6Pf)
			// EC-multiplied keys with compression (prefix 6Pn)
			ec = true;
			compressed = (store[2] & 0x20) != 0;
			hasLot = (store[2] & 0x04) != 0;
			if ( (store[2] & 0x24) != store[2] )
			{
				throw new ValidationException ("invalid key");
			}
		}
		else
		{
			throw new ValidationException ("invalid key");
		}

		byte[] checksum = new byte[4];
		System.arraycopy (store, store.length - 4, checksum, 0, 4);
		byte[] ekey = new byte[store.length - 4];
		System.arraycopy (store, 0, ekey, 0, store.length - 4);
		byte[] hash = Hash.hash (ekey);
		for ( int i = 0; i < 4; ++i )
		{
			if ( hash[i] != checksum[i] )
			{
				throw new ValidationException ("checksum mismatch");
			}
		}

		if ( ec == false )
		{
			return parseBIP38NoEC (store, compressed);
		}
		else
		{
			return parseBIP38EC (store, compressed, hasLot);
		}
	}

	private ECKeyPair parseBIP38NoEC (byte[] store, boolean compressed) throws ValidationException
	{
		byte[] addressHash = new byte[4];
		System.arraycopy (store, 3, addressHash, 0, 4);
		try
		{
			byte[] derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), addressHash, 16384, 8, 8, 64);
			byte[] key = new byte[32];
			System.arraycopy (derived, 32, key, 0, 32);
			SecretKeySpec keyspec = new SecretKeySpec (key, "AES");
			Cipher cipher = Cipher.getInstance ("AES/ECB/NoPadding", "BC");
			cipher.init (Cipher.DECRYPT_MODE, keyspec);
			byte[] decrypted = cipher.doFinal (store, 7, 32);
			for ( int i = 0; i < 32; ++i )
			{
				decrypted[i] ^= derived[i];
			}
			ECKeyPair kp = new ECKeyPair (decrypted, compressed);

			byte[] acs = Hash.hash (new Address (network, kp.getAddress ()).toString ().getBytes ("US-ASCII"));
			byte[] check = new byte[4];
			System.arraycopy (acs, 0, check, 0, 4);
			if ( !Arrays.equals (check, addressHash) )
			{
				throw new ValidationException ("failed to decrpyt");
			}
			return kp;
		}
		catch ( UnsupportedEncodingException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchPaddingException e )
		{
			throw new ValidationException (e);
		}
		catch ( InvalidKeyException e )
		{
			throw new ValidationException (e);
		}
		catch ( IllegalBlockSizeException e )
		{
			throw new ValidationException (e);
		}
		catch ( BadPaddingException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchProviderException e )
		{
			throw new ValidationException (e);
		}
	}

	private ECKeyPair parseBIP38EC (byte[] store, boolean compressed, boolean hasLot) throws ValidationException
	{
		byte[] addressHash = new byte[4];
		System.arraycopy (store, 3, addressHash, 0, 4);

		byte[] ownentropy = new byte[8];
		System.arraycopy (store, 7, ownentropy, 0, 8);

		byte[] ownersalt = ownentropy;
		if ( hasLot )
		{
			ownersalt = new byte[4];
			System.arraycopy (ownentropy, 0, ownersalt, 0, 4);
		}
		try
		{
			byte[] passfactor = SCrypt.generate (passphrase.getBytes ("UTF-8"), ownersalt, 16384, 8, 8, 32);
			if ( hasLot )
			{
				byte[] tmp = new byte[40];
				System.arraycopy (passfactor, 0, tmp, 0, 32);
				System.arraycopy (ownentropy, 0, tmp, 32, 8);
				passfactor = Hash.hash (tmp);
			}
			ECKeyPair kp = new ECKeyPair (passfactor, true);

			byte[] salt = new byte[12];
			System.arraycopy (store, 3, salt, 0, 12);
			byte[] derived = SCrypt.generate (kp.getPublic (), salt, 1024, 1, 1, 64);
			byte[] aeskey = new byte[32];
			System.arraycopy (derived, 32, aeskey, 0, 32);

			SecretKeySpec keyspec = new SecretKeySpec (aeskey, "AES");
			Cipher cipher = Cipher.getInstance ("AES/ECB/NoPadding", "BC");
			cipher.init (Cipher.DECRYPT_MODE, keyspec);

			byte[] encrypted = new byte[16];
			System.arraycopy (store, 23, encrypted, 0, 16);
			byte[] decrypted2 = cipher.doFinal (encrypted);
			for ( int i = 0; i < 16; ++i )
			{
				decrypted2[i] ^= derived[i + 16];
			}

			System.arraycopy (store, 15, encrypted, 0, 8);
			System.arraycopy (decrypted2, 0, encrypted, 8, 8);
			byte[] decrypted1 = cipher.doFinal (encrypted);
			for ( int i = 0; i < 16; ++i )
			{
				decrypted1[i] ^= derived[i];
			}

			byte[] seed = new byte[24];
			System.arraycopy (decrypted1, 0, seed, 0, 16);
			System.arraycopy (decrypted2, 8, seed, 16, 8);
			BigInteger priv =
					new BigInteger (1, passfactor).multiply (new BigInteger (1, Hash.hash (seed))).remainder (SECNamedCurves.getByName ("secp256k1").getN ());

			kp = new ECKeyPair (priv, compressed);
			byte[] acs = Hash.hash (new Address (network, kp.getAddress ()).toString ().getBytes ("US-ASCII"));
			byte[] check = new byte[4];
			System.arraycopy (acs, 0, check, 0, 4);
			if ( !Arrays.equals (check, addressHash) )
			{
				throw new ValidationException ("failed to decrpyt");
			}
			return kp;
		}
		catch ( UnsupportedEncodingException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchAlgorithmException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchProviderException e )
		{
			throw new ValidationException (e);
		}
		catch ( NoSuchPaddingException e )
		{
			throw new ValidationException (e);
		}
		catch ( InvalidKeyException e )
		{
			throw new ValidationException (e);
		}
		catch ( IllegalBlockSizeException e )
		{
			throw new ValidationException (e);
		}
		catch ( BadPaddingException e )
		{
			throw new ValidationException (e);
		}
	}

	private byte[] bytesBIP38 (Key key) throws ValidationException
	{
		if ( passphrase == null )
		{
			throw new ValidationException ("Must have passphrase to encrypt keys");
		}
		byte[] store = new byte[43];
		store[0] = 0x01;
		store[1] = 0x42;
		store[2] = key.isCompressed () ? (byte) 0xe0 : (byte) 0xc0;
		byte[] addressHash = new byte[4];
		byte[] aesKey = new byte[32];
		byte[] xor = new byte[32];
		try
		{
			byte[] ac = Hash.hash (new Address (network, key.getAddress ()).toString ().getBytes ("US-ASCII"));
			System.arraycopy (ac, 0, addressHash, 0, 4);
			System.arraycopy (ac, 0, store, 3, 4);
			byte[] derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), addressHash, 16384, 8, 8, 64);
			System.arraycopy (derived, 32, aesKey, 0, 32);
			System.arraycopy (derived, 0, xor, 0, 32);
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		SecretKeySpec keyspec = new SecretKeySpec (aesKey, "AES");
		try
		{
			byte[] priv = key.getPrivate ();
			for ( int i = 0; i < 32; ++i )
			{
				priv[i] ^= xor[i];
			}
			Cipher cipher = Cipher.getInstance ("AES/ECB/NoPadding", "BC");
			cipher.init (Cipher.ENCRYPT_MODE, keyspec);
			byte[] encrypted = cipher.doFinal (priv);
			System.arraycopy (encrypted, 0, store, 7, encrypted.length);
			byte[] cs = Hash.hash (store, 0, 39);
			System.arraycopy (cs, 0, store, 39, 4);
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
		catch ( NoSuchProviderException e )
		{
		}
		catch ( NoSuchPaddingException e )
		{
		}
		catch ( InvalidKeyException e )
		{
		}
		catch ( IllegalBlockSizeException e )
		{
		}
		catch ( BadPaddingException e )
		{
		}
		return store;
	}
}
