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
package com.bitsofproof.supernode.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.util.Arrays;

import com.bitsofproof.supernode.api.Address;

public class ECKeyPair implements Key
{
	private static final SecureRandom secureRandom = new SecureRandom ();
	private static final X9ECParameters curve = SECNamedCurves.getByName ("secp256k1");
	private static final ECDomainParameters domain = new ECDomainParameters (curve.getCurve (), curve.getG (), curve.getN (), curve.getH ());

	private BigInteger priv;
	private byte[] pub;
	private boolean compressed;

	private ECKeyPair ()
	{
	}

	@Override
	public boolean isCompressed ()
	{
		return compressed;
	}

	@Override
	public ECKeyPair clone () throws CloneNotSupportedException
	{
		ECKeyPair c = (ECKeyPair) super.clone ();
		c.priv = new BigInteger (c.priv.toByteArray ());
		c.pub = Arrays.clone (pub);
		c.compressed = compressed;
		return c;
	}

	public static ECKeyPair createNew (boolean compressed)
	{
		ECKeyPairGenerator generator = new ECKeyPairGenerator ();
		ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters (domain, secureRandom);
		generator.init (keygenParams);
		AsymmetricCipherKeyPair keypair = generator.generateKeyPair ();
		ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate ();
		ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic ();
		ECKeyPair k = new ECKeyPair ();
		k.priv = privParams.getD ();
		k.compressed = compressed;
		k.pub = pubParams.getQ ().getEncoded (compressed);
		return k;
	}

	public void setPublic (byte[] pub) throws ValidationException
	{
		throw new ValidationException ("Can not set public key if private is present");
	}

	@Override
	public byte[] getPrivate ()
	{
		byte[] p = priv.toByteArray ();

		if ( p.length != 32 )
		{
			byte[] tmp = new byte[32];
			System.arraycopy (p, Math.max (0, p.length - 32), tmp, Math.max (0, 32 - p.length), Math.min (32, p.length));
			p = tmp;
		}

		return p;
	}

	@Override
	public byte[] getPublic ()
	{
		return Arrays.clone (pub);
	}

	@Override
	public Key getReadOnly ()
	{
		return new ECPublicKey (pub, compressed);
	}

	@Override
	public Address getAddress ()
	{
		try
		{
			return new Address (Address.Type.COMMON, Hash.keyHash (pub));
		}
		catch ( ValidationException e )
		{
			return null;
		}
	}

	public ECKeyPair (byte[] p, boolean compressed) throws ValidationException
	{
		if ( p.length != 32 )
		{
			throw new ValidationException ("Invalid private key");
		}
		this.priv = new BigInteger (1, p).mod (curve.getN ());
		this.compressed = compressed;
		pub = curve.getG ().multiply (priv).getEncoded (compressed);
	}

	public ECKeyPair (BigInteger priv, boolean compressed)
	{
		this.priv = priv;
		this.compressed = compressed;
		pub = curve.getG ().multiply (priv).getEncoded (compressed);
	}

	@Override
	public byte[] sign (byte[] hash) throws ValidationException
	{
		if ( priv == null )
		{
			throw new ValidationException ("Need private key to sign");
		}
		ECDSASigner signer = new ECDSASigner (new HMacDSAKCalculator (new SHA256Digest ()));
		signer.init (true, new ECPrivateKeyParameters (priv, domain));
		BigInteger[] signature = signer.generateSignature (hash);
		ByteArrayOutputStream s = new ByteArrayOutputStream ();
		try
		{
			DERSequenceGenerator seq = new DERSequenceGenerator (s);
			seq.addObject (new ASN1Integer (signature[0]));
			seq.addObject (new ASN1Integer (signature[1]));
			seq.close ();
			return s.toByteArray ();
		}
		catch ( IOException e )
		{
		}
		return null;
	}

	@Override
	public boolean verify (byte[] hash, byte[] signature)
	{
		return verify (hash, signature, pub);
	}

	public static boolean verify (byte[] hash, byte[] signature, byte[] pub)
	{
		ASN1InputStream asn1 = new ASN1InputStream (signature);
		try
		{
			ECDSASigner signer = new ECDSASigner ();
			signer.init (false, new ECPublicKeyParameters (curve.getCurve ().decodePoint (pub), domain));

			DLSequence seq = (DLSequence) asn1.readObject ();
			BigInteger r = ((ASN1Integer) seq.getObjectAt (0)).getPositiveValue ();
			BigInteger s = ((ASN1Integer) seq.getObjectAt (1)).getPositiveValue ();
			return signer.verifySignature (hash, r, s);
		}
		catch ( Exception e )
		{
			// threat format errors as invalid signatures
			return false;
		}
		finally
		{
			try
			{
				asn1.close ();
			}
			catch ( IOException e )
			{
			}
		}
	}

	@Override
	public String toString ()
	{
		return serializeWIF (this);
	}

	public static String serializeWIF (Key key)
	{
		return ByteUtils.toBase58 (bytesWIF (key));
	}

	private static byte[] bytesWIF (Key key)
	{
		byte[] k = key.getPrivate ();
		if ( key.isCompressed () )
		{
			byte[] encoded = new byte[k.length + 6];
			byte[] ek = new byte[k.length + 2];
			ek[0] = (byte) 0x80;
			System.arraycopy (k, 0, ek, 1, k.length);
			ek[k.length + 1] = 0x01;
			byte[] hash = Hash.hash (ek);
			System.arraycopy (ek, 0, encoded, 0, ek.length);
			System.arraycopy (hash, 0, encoded, ek.length, 4);
			return encoded;
		}
		else
		{
			byte[] encoded = new byte[k.length + 5];
			byte[] ek = new byte[k.length + 1];
			ek[0] = (byte) 0x80;
			System.arraycopy (k, 0, ek, 1, k.length);
			byte[] hash = Hash.hash (ek);
			System.arraycopy (ek, 0, encoded, 0, ek.length);
			System.arraycopy (hash, 0, encoded, ek.length, 4);
			return encoded;
		}
	}

	public static ECKeyPair parseWIF (String serialized) throws ValidationException
	{
		byte[] store = ByteUtils.fromBase58 (serialized);
		return parseBytesWIF (store);
	}

	public static ECKeyPair parseBytesWIF (byte[] store) throws ValidationException
	{
		if ( store.length == 37 )
		{
			checkChecksum (store);
			byte[] key = new byte[store.length - 5];
			System.arraycopy (store, 1, key, 0, store.length - 5);
			return new ECKeyPair (key, false);
		}
		else if ( store.length == 38 )
		{
			checkChecksum (store);
			byte[] key = new byte[store.length - 6];
			System.arraycopy (store, 1, key, 0, store.length - 6);
			return new ECKeyPair (key, true);
		}
		throw new ValidationException ("Invalid key length");
	}

	private static void checkChecksum (byte[] store) throws ValidationException
	{
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
	}
}
