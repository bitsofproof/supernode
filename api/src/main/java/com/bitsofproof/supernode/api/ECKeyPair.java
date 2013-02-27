/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.math.ec.ECPoint;

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

	public boolean isCompressed ()
	{
		return compressed;
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
		if ( compressed )
		{
			ECPoint q = pubParams.getQ ();
			k.pub = new ECPoint.Fp (domain.getCurve (), q.getX (), q.getY (), true).getEncoded ();
		}
		else
		{
			k.pub = pubParams.getQ ().getEncoded ();
		}
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
		if ( pub != null )
		{
			byte[] p = new byte[pub.length];
			System.arraycopy (pub, 0, p, 0, pub.length);
			return p;
		}
		return null;
	}

	public byte[] getAddress ()
	{
		return Hash.keyHash (pub);
	}

	public ECKeyPair (byte[] p, boolean compressed) throws ValidationException
	{
		if ( p.length != 32 )
		{
			throw new ValidationException ("Invalid private key");
		}
		this.priv = new BigInteger (1, p);
		this.compressed = compressed;
		if ( compressed )
		{
			ECPoint q = curve.getG ().multiply (priv);
			pub = new ECPoint.Fp (domain.getCurve (), q.getX (), q.getY (), true).getEncoded ();
		}
		else
		{
			pub = curve.getG ().multiply (priv).getEncoded ();
		}
	}

	public ECKeyPair (BigInteger priv, boolean compressed) throws ValidationException
	{
		this.priv = priv;
		this.compressed = compressed;
		if ( compressed )
		{
			ECPoint q = curve.getG ().multiply (priv);
			pub = new ECPoint.Fp (domain.getCurve (), q.getX (), q.getY (), true).getEncoded ();
		}
		else
		{
			pub = curve.getG ().multiply (priv).getEncoded ();
		}
	}

	public byte[] sign (byte[] hash) throws ValidationException
	{
		if ( priv == null )
		{
			throw new ValidationException ("Need private key to sign");
		}
		ECDSASigner signer = new ECDSASigner ();
		signer.init (true, new ECPrivateKeyParameters (priv, domain));
		BigInteger[] signature = signer.generateSignature (hash);
		ByteArrayOutputStream s = new ByteArrayOutputStream ();
		try
		{
			DERSequenceGenerator seq = new DERSequenceGenerator (s);
			seq.addObject (new DERInteger (signature[0]));
			seq.addObject (new DERInteger (signature[1]));
			seq.close ();
			return s.toByteArray ();
		}
		catch ( IOException e )
		{
		}
		return null;
	}

	public static boolean verify (byte[] hash, byte[] signature, byte[] pub)
	{
		ASN1InputStream asn1 = new ASN1InputStream (signature);
		try
		{
			ECDSASigner signer = new ECDSASigner ();
			signer.init (false, new ECPublicKeyParameters (curve.getCurve ().decodePoint (pub), domain));

			DLSequence seq = (DLSequence) asn1.readObject ();
			BigInteger r = ((DERInteger) seq.getObjectAt (0)).getPositiveValue ();
			BigInteger s = ((DERInteger) seq.getObjectAt (1)).getPositiveValue ();
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
}
