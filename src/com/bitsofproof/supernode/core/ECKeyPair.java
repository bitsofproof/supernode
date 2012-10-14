package com.bitsofproof.supernode.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSequenceGenerator;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;

public class ECKeyPair {
	private static final SecureRandom secureRandom = new SecureRandom();
	private static final X9ECParameters curve = SECNamedCurves.getByName("secp256k1");
	private static final ECDomainParameters domain = 
			new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH());
	
	private BigInteger priv;
	private byte[] pub;
	
	protected ECKeyPair (){}

	public static ECKeyPair createNew() {
		ECKeyPairGenerator generator = new ECKeyPairGenerator();
		ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(domain, secureRandom);
		generator.init(keygenParams);
		AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
		ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
		ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();
		ECKeyPair k = new ECKeyPair ();
		k.priv = privParams.getD();
		k.pub = pubParams.getQ().getEncoded();
		return k;
	}

	public byte [] getPublic ()
	{
		return pub;
	}
	
	public String getAddress() {
		byte[] ph = new byte[20];
		try {
			byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(pub);
			RIPEMD160Digest digest = new RIPEMD160Digest();
			digest.update(sha256, 0, sha256.length);
			digest.doFinal(ph, 0);
		} catch (NoSuchAlgorithmException e) {
		}
		byte[] addressBytes = new byte[1 + ph.length + 4];
		addressBytes[0] = (byte) 0; // 0 for production
		System.arraycopy(ph, 0, addressBytes, 1, ph.length);
		byte[] check = Hash.hash(addressBytes, 0, ph.length + 1).toByteArray();
		System.arraycopy(check, 0, addressBytes, ph.length + 1, 4);
		return Base58.encode(addressBytes);
	}

	public ECKeyPair (byte[] store) throws ValidationException {
		ASN1InputStream s = new ASN1InputStream(store);
		try {
			DLSequence der = (DLSequence) s.readObject();
			if (!(((DERInteger) der.getObjectAt(0)).getValue().equals(BigInteger.ONE)))
				throw new ValidationException("wrong key version");
			priv = new BigInteger(1, ((DEROctetString) der.getObjectAt(1).toASN1Primitive()).getOctets());
			s.close();
			pub = curve.getG().multiply(priv).getEncoded();
		} catch (IOException e) {
		} finally {
			try {
				s.close();
			} catch (IOException e) {
			}
		}
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		try {
			DERSequenceGenerator der = new DERSequenceGenerator(s);
			der.addObject(new ASN1Integer(1));
			der.addObject(new DEROctetString(priv.toByteArray()));
			der.addObject(new DERTaggedObject(0, curve.toASN1Primitive()));
			der.addObject(new DERTaggedObject(1, new DERBitString(pub)));
			der.close();
		} catch (IOException e) {
		}
		return s.toByteArray();
	}

	public byte[] sign(byte[] hash) throws ValidationException {
		if (priv == null)
			throw new ValidationException("Need private key to sign");
		ECDSASigner signer = new ECDSASigner();
		signer.init(true, new ECPrivateKeyParameters(priv, domain));
		BigInteger[] signature = signer.generateSignature(hash);
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		try {
			DERSequenceGenerator seq = new DERSequenceGenerator(s);
			seq.addObject(new DERInteger(signature[0]));
			seq.addObject(new DERInteger(signature[1]));
			seq.close();
			return s.toByteArray();
		} catch (IOException e) {
		}
		return s.toByteArray();
	}

	public static boolean verify(byte[] hash, byte[] signature, byte [] pub) {
		ECDSASigner signer = new ECDSASigner();
		signer.init(false, new ECPublicKeyParameters(curve.getCurve().decodePoint(pub), domain));
		ASN1InputStream asn1 = new ASN1InputStream(signature);
		try {
			DLSequence seq = (DLSequence) asn1.readObject();
			DERInteger r = (DERInteger) seq.getObjectAt(0);
			DERInteger s = (DERInteger) seq.getObjectAt(1);
			asn1.close();
			return signer.verifySignature(hash, r.getValue(), s.getValue());
		} catch (IOException e) {
		} finally {
			try {
				asn1.close();
			} catch (IOException e) {
			}
		}
		return false;
	}

}
