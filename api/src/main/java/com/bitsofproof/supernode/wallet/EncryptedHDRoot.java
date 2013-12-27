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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;

/**
 * BIP of serialized or encrypted HD key root discussion: https://bitcointalk.org/index.php?topic=258678.0
 */
public class EncryptedHDRoot
{
	public static enum ScryptDifficulty
	{
		LOW, MEDIUM, HIGH
	}

	private static final byte[] clear16 = { 0x0b, 0x2d, 0x7b };
	private static final byte[] clear32 = { 0x14, (byte) 0x82, 0x17 };
	private static final byte[] clear64 = { 0x01, 0x30, (byte) 0xb7 };
	private static final byte[] encrypted16 = { 0x14, (byte) 0xd6, 0x0d };
	private static final byte[] encrypted16l = encrypted16;
	private static final byte[] encrypted16m = { 0x14, (byte) 0xd6, 0x0e };
	private static final byte[] encrypted16h = { 0x14, (byte) 0xd6, 0x0f };
	private static final byte[] encrypted32 = { 0x26, 0x3a, (byte) 0xa2 };
	private static final byte[] encrypted32l = encrypted32;
	private static final byte[] encrypted32m = { 0x26, 0x3a, (byte) 0xa3 };
	private static final byte[] encrypted32h = { 0x26, 0x3a, (byte) 0xa4 };
	private static final byte[] encrypted64 = { 0x02, 0x38, 0x04 };
	private static final byte[] encrypted64l = encrypted64;
	private static final byte[] encrypted64m = { 0x02, 0x38, 0x05 };
	private static final byte[] encrypted64h = { 0x02, 0x38, 0x06 };

	public static Date decodeBirthDate (String ws) throws ValidationException
	{
		byte[] raw = ByteUtils.fromBase58WithChecksum (ws);
		int weeks = raw[3] + raw[4] << 8;
		Calendar c = new GregorianCalendar (2013, Calendar.JANUARY, 1);
		c.add (Calendar.DAY_OF_YEAR, weeks * 7);
		return c.getTime ();
	}

	public static ExtendedKey decode (String ws) throws ValidationException
	{
		byte[] raw = ByteUtils.fromBase58WithChecksum (ws);
		byte[] magic = Arrays.copyOf (raw, 3);
		byte[] seed;
		if ( Arrays.equals (magic, clear16) )
		{
			seed = Arrays.copyOfRange (raw, 9, 16 + 9);
		}
		else if ( Arrays.equals (magic, clear32) )
		{
			seed = Arrays.copyOfRange (raw, 9, 32 + 9);
		}
		else if ( Arrays.equals (magic, clear64) )
		{
			seed = Arrays.copyOfRange (raw, 9, 64 + 9);
		}
		else
		{
			throw new ValidationException ("Not an encoded HD root");
		}
		ExtendedKey key = ExtendedKey.create (seed);
		if ( !Arrays.equals (Arrays.copyOf (Hash.hash (key.getMaster ().getPrivate ()), 4), Arrays.copyOfRange (raw, 5, 9)) )
		{
			throw new ValidationException ("HD root checksum error");
		}
		return key;
	}

	public static ExtendedKey decrypt (String ws, String passphrase) throws ValidationException
	{
		byte[] raw = ByteUtils.fromBase58WithChecksum (ws);
		byte[] magic = Arrays.copyOf (raw, 3);
		byte[] encryptedSeed;
		int N = 1 << 14;
		int r = 16;
		int p = 16;
		if ( Arrays.equals (magic, encrypted16l) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 16 + 9);
			r = p = 8;
		}
		else if ( Arrays.equals (magic, encrypted16m) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 16 + 9);
			N = 1 << 16;
		}
		else if ( Arrays.equals (magic, encrypted16h) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 16 + 9);
			N = 1 << 18;
		}
		else if ( Arrays.equals (magic, encrypted32l) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 32 + 9);
			r = p = 8;
		}
		else if ( Arrays.equals (magic, encrypted32m) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 32 + 9);
			N = 1 << 16;
		}
		else if ( Arrays.equals (magic, encrypted32h) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 32 + 9);
			N = 1 << 18;
		}
		else if ( Arrays.equals (magic, encrypted64l) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 64 + 9);
			r = p = 8;
		}
		else if ( Arrays.equals (magic, encrypted64m) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 64 + 9);
			N = 1 << 16;
		}
		else if ( Arrays.equals (magic, encrypted64h) )
		{
			encryptedSeed = Arrays.copyOfRange (raw, 9, 64 + 9);
			N = 1 << 18;
		}
		else
		{
			throw new ValidationException ("Not an encoded HD root");
		}
		byte salt[] = Arrays.copyOf (raw, 9);

		Mac mac;
		try
		{
			mac = Mac.getInstance ("HmacSHA512", "BC");
			SecretKey seedkey = new SecretKeySpec (salt, "HmacSHA512");
			mac.init (seedkey);
			byte[] preH = mac.doFinal (passphrase.getBytes ("UTF-8"));
			byte[] strongH = SCrypt.generate (preH, preH, N, r, p, 64);
			seedkey = new SecretKeySpec (passphrase.getBytes ("UTF-8"), "HmacSHA512");
			mac.init (seedkey);
			byte[] postH = mac.doFinal (salt);
			byte[] H = SCrypt.generate (postH, strongH, 1 << 10, 1, 1, encryptedSeed.length + 32);
			byte[] X = Arrays.copyOf (H, encryptedSeed.length);
			SecretKeySpec keyspec = new SecretKeySpec (Arrays.copyOfRange (H, encryptedSeed.length, encryptedSeed.length + 32), "AES");
			Cipher cipher = Cipher.getInstance ("AES/ECB/NoPadding", "BC");
			cipher.init (Cipher.DECRYPT_MODE, keyspec);
			byte[] seed = cipher.doFinal (encryptedSeed);
			for ( int i = 0; i < encryptedSeed.length; ++i )
			{
				seed[i] ^= X[i];
			}
			ExtendedKey key = ExtendedKey.create (seed);
			if ( !Arrays.equals (Arrays.copyOf (Hash.hash (key.getMaster ().getPrivate ()), 4), Arrays.copyOfRange (raw, 5, 9)) )
			{
				throw new ValidationException ("HD root checksum error");
			}
			return key;
		}
		catch ( NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | UnsupportedEncodingException | NoSuchPaddingException
				| IllegalBlockSizeException | BadPaddingException e )
		{
			throw new ValidationException (e);
		}
	}

	public static String encode (byte[] seed, Date birth) throws ValidationException
	{
		if ( seed == null || (seed.length != 16 && seed.length != 32 && seed.length != 64) )
		{
			throw new ValidationException ("Seed must be 16, 32 or 64 bytes");
		}
		int weeks =
				(int) ((birth.getTime () - new GregorianCalendar (2013, Calendar.JANUARY, 1).getTime ().getTime ()) / (7 * 24 * 60 * 60 * 1000L));

		ExtendedKey key = ExtendedKey.create (seed);
		byte raw[];
		if ( seed.length == 16 )
		{
			raw = new byte[25];
			System.arraycopy (clear16, 0, raw, 0, 3);
		}
		else if ( seed.length == 32 )
		{
			raw = new byte[41];
			System.arraycopy (clear32, 0, raw, 0, 3);
		}
		else
		{
			raw = new byte[73];
			System.arraycopy (clear64, 0, raw, 0, 3);
		}
		raw[3] = (byte) (weeks & 0xff);
		raw[4] = (byte) ((weeks >>> 8) & 0xff);
		System.arraycopy (Hash.hash (key.getMaster ().getPrivate ()), 0, raw, 5, 4);
		System.arraycopy (seed, 0, raw, 9, seed.length);
		return ByteUtils.toBase58WithChecksum (raw);
	}

	public static String encrypt (byte[] seed, Date birth, String passphrase, ScryptDifficulty scryptDifficulty) throws ValidationException
	{
		if ( seed == null || (seed.length != 16 && seed.length != 32 && seed.length != 64) )
		{
			throw new ValidationException ("Seed must be 16, 32 or 64 bytes");
		}
		int weeks =
				(int) ((birth.getTime () - new GregorianCalendar (2013, Calendar.JANUARY, 1).getTime ().getTime ()) / (7 * 24 * 60 * 60 * 1000L));

		ExtendedKey key = ExtendedKey.create (seed);
		byte raw[];
		byte[] salt = new byte[9];
		if ( seed.length == 16 )
		{
			raw = new byte[25];
			System.arraycopy (encrypted16, 0, salt, 0, 3);
		}
		else if ( seed.length == 32 )
		{
			raw = new byte[41];
			System.arraycopy (encrypted32, 0, salt, 0, 3);
		}
		else
		{
			raw = new byte[73];
			System.arraycopy (encrypted64, 0, salt, 0, 3);
		}
		salt[2] += scryptDifficulty.ordinal ();
		salt[3] = (byte) (weeks & 0xff);
		salt[4] = (byte) ((weeks >>> 8) & 0xff);
		System.arraycopy (Hash.hash (key.getMaster ().getPrivate ()), 0, salt, 5, 4);
		System.arraycopy (salt, 0, raw, 0, 9);
		int N = (1 << 14) << (scryptDifficulty.ordinal () * 2);
		int r = scryptDifficulty == ScryptDifficulty.LOW ? 8 : 16;
		int p = scryptDifficulty == ScryptDifficulty.LOW ? 8 : 16;
		Mac mac;
		try
		{
			mac = Mac.getInstance ("HmacSHA512", "BC");
			SecretKey seedkey = new SecretKeySpec (salt, "HmacSHA512");
			mac.init (seedkey);
			byte[] preH = mac.doFinal (passphrase.getBytes ("UTF-8"));
			byte[] strongH = SCrypt.generate (preH, preH, N, r, p, 64);
			seedkey = new SecretKeySpec (passphrase.getBytes ("UTF-8"), "HmacSHA512");
			mac.init (seedkey);
			byte[] postH = mac.doFinal (salt);
			byte[] H = SCrypt.generate (postH, strongH, 1 << 10, 1, 1, seed.length + 32);
			byte[] X = Arrays.copyOf (H, seed.length);
			for ( int i = 0; i < seed.length; ++i )
			{
				X[i] ^= seed[i];
			}
			SecretKeySpec keyspec = new SecretKeySpec (Arrays.copyOfRange (H, seed.length, seed.length + 32), "AES");
			Cipher cipher = Cipher.getInstance ("AES/ECB/NoPadding", "BC");
			cipher.init (Cipher.ENCRYPT_MODE, keyspec);
			System.arraycopy (cipher.doFinal (X), 0, raw, 9, seed.length);
		}
		catch ( NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | UnsupportedEncodingException | NoSuchPaddingException
				| IllegalBlockSizeException | BadPaddingException e )
		{
			throw new ValidationException (e);
		}
		return ByteUtils.toBase58WithChecksum (raw);
	}
}
