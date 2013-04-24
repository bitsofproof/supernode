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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.SCrypt;

import com.bitsofproof.supernode.api.BloomFilter.UpdateMode;

public class SerializedWallet implements Wallet
{
	private String passphrase;
	private String fileName;
	private long timeStamp;

	private BCSAPI api;

	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	public long getTimeStamp ()
	{
		return timeStamp;
	}

	@Override
	public AccountManager getAccountManager (String name) throws BCSAPIException
	{
		try
		{
			int nextSequence = 0;
			ExtendedKey extended = null;
			for ( WalletKey key : getKeys () )
			{
				if ( key.name.equals (name) )
				{
					extended = ExtendedKey.parse (key.key);
					nextSequence = key.nextSequence;
					break;
				}
			}
			if ( extended == null )
			{
				extended = ExtendedKey.createNew ();
			}
			final DefaultAccountManager am = new DefaultAccountManager (name, extended, nextSequence);
			am.setApi (api);
			am.registerFilter ();
			for ( Transaction t : getTransactions () )
			{
				am.updateWithTransaction (t);
			}
			api.scanTransactions (am.getAddresses (), UpdateMode.all, getTimeStamp (), new TransactionListener ()
			{
				@Override
				public void process (Transaction t)
				{
					if ( am.updateWithTransaction (t) )
					{
						addTransaction (t);
					}
				}
			});
			return am;
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	public static SerializedWallet read (String fileName, String passphrase) throws BCSAPIException
	{
		try
		{
			File f = new File (fileName);
			if ( f.exists () )
			{
				long timeStamp = f.lastModified () / 1000;
				FileInputStream in = null;
				try
				{
					in = new FileInputStream (f);
					SerializedWallet wallet = readWallet (in, passphrase);
					wallet.fileName = fileName;
					wallet.passphrase = passphrase;
					wallet.timeStamp = timeStamp;
					return wallet;
				}
				finally
				{
					in.close ();
				}
			}
			else
			{
				SerializedWallet wallet = new SerializedWallet ();
				wallet.timeStamp = System.currentTimeMillis () / 1000;
				wallet.fileName = fileName;
				wallet.passphrase = passphrase;
				return wallet;
			}
		}
		catch ( FileNotFoundException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( IOException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	@Override
	public void persist () throws BCSAPIException
	{
		try
		{
			if ( fileName != null )
			{
				File f = new File (fileName);
				File tmp = File.createTempFile ("tmp", ".wallet", f.getParentFile ());
				tmp.setReadable (true, true);
				tmp.setWritable (true, true);
				FileOutputStream out = new FileOutputStream (tmp);
				writeWallet (out, passphrase);
				out.close ();
				f.delete ();
				tmp.renameTo (f);
				timeStamp = f.lastModified ();
			}
		}
		catch ( IOException e )
		{
			throw new BCSAPIException (e);
		}
		catch ( ValidationException e )
		{
			throw new BCSAPIException (e);
		}
	}

	public static class WalletKey
	{
		String key;
		String name;
		long created;
		int nextSequence;
	}

	private List<WalletKey> keys = new ArrayList<WalletKey> ();
	private final Set<String> hasTransaction = new HashSet<String> ();
	private List<Transaction> transactions = new ArrayList<Transaction> ();
	private Map<String, String> addresses = new HashMap<String, String> ();

	private static final String MAGIC = "bopwallet";

	private static SecureRandom rnd = new SecureRandom ();

	public void addKey (WalletKey key)
	{
		keys.add (key);
	}

	public void addTransaction (Transaction t)
	{
		if ( !hasTransaction.contains (t.getHash ()) )
		{
			transactions.add (t);
			hasTransaction.add (t.getHash ());
		}
	}

	public void addAddress (String name, String address)
	{
		addresses.put (name, address);
	}

	public List<WalletKey> getKeys ()
	{
		return keys;
	}

	public List<Transaction> getTransactions ()
	{
		return Collections.unmodifiableList (transactions);
	}

	public Map<String, String> getAddresses ()
	{
		return addresses;
	}

	public void writeWallet (OutputStream output, String passphrase) throws ValidationException
	{
		try
		{
			output.write (MAGIC.getBytes ("US-ASCII"));
			byte[] iv = new byte[16];
			rnd.nextBytes (iv);
			output.write (iv);
			byte[] derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), iv, 16384, 8, 8, 32);
			SecretKeySpec keyspec = new SecretKeySpec (derived, "AES");
			Cipher cipher = Cipher.getInstance ("AES/CBC/PKCS5Padding", "BC");
			cipher.init (Cipher.ENCRYPT_MODE, keyspec, new IvParameterSpec (iv));
			CipherOutputStream cip = new CipherOutputStream (output, cipher);
			GZIPOutputStream zip = new GZIPOutputStream (cip);
			BCSAPIMessage.Wallet.Builder builder = BCSAPIMessage.Wallet.newBuilder ();
			builder.setBcsapiversion (1);
			for ( WalletKey key : keys )
			{
				BCSAPIMessage.Wallet.Key.Builder kb = BCSAPIMessage.Wallet.Key.newBuilder ();
				kb.setKey (key.key);
				kb.setNextSequence (key.nextSequence);
				if ( key.name != null )
				{
					kb.setName (key.name);
				}
				if ( key.created != 0 )
				{
					kb.setCreated (key.created);
				}
				builder.addKeys (kb.build ());
			}
			for ( Transaction t : transactions )
			{
				builder.addTransactions (t.toProtobuf ());
			}
			for ( Map.Entry<String, String> e : addresses.entrySet () )
			{
				BCSAPIMessage.Wallet.PublicAddress.Builder pa = BCSAPIMessage.Wallet.PublicAddress.newBuilder ();
				pa.setName (e.getKey ());
				pa.setAddress (e.getValue ());
				builder.addPublicAddresses (pa.build ());
			}
			builder.build ().writeTo (zip);
			zip.finish ();
			zip.flush ();
			cip.close ();
		}
		catch ( UnsupportedEncodingException e )
		{
		}
		catch ( IOException e )
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
		catch ( InvalidAlgorithmParameterException e )
		{
			throw new ValidationException (e);
		}
	}

	public static SerializedWallet readWallet (InputStream input, String passphrase) throws ValidationException
	{
		SerializedWallet wallet = new SerializedWallet ();

		try
		{
			byte[] derived;
			byte[] magic = new byte[MAGIC.getBytes ("US-ASCII").length];
			input.read (magic);
			if ( !Arrays.equals (magic, MAGIC.getBytes ("US-ASCII")) )
			{
				throw new ValidationException ("Not a wallet");
			}
			byte[] iv = new byte[16];
			input.read (iv);
			derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), iv, 16384, 8, 8, 32);
			SecretKeySpec keyspec = new SecretKeySpec (derived, "AES");
			Cipher cipher = Cipher.getInstance ("AES/CBC/PKCS5Padding", "BC");
			cipher.init (Cipher.DECRYPT_MODE, keyspec, new IvParameterSpec (iv));

			CipherInputStream cis = new CipherInputStream (input, cipher);
			GZIPInputStream unzip = new GZIPInputStream (cis);

			BCSAPIMessage.Wallet walletMessage = BCSAPIMessage.Wallet.parseFrom (unzip);

			wallet.keys = new ArrayList<WalletKey> ();
			for ( BCSAPIMessage.Wallet.Key k : walletMessage.getKeysList () )
			{
				WalletKey key = new WalletKey ();
				key.key = k.getKey ();
				key.nextSequence = k.getNextSequence ();
				if ( k.hasName () )
				{
					key.name = k.getName ();
				}
				if ( k.hasCreated () )
				{
					key.created = k.getCreated ();
				}
				wallet.keys.add (key);
			}
			wallet.transactions = new ArrayList<Transaction> ();
			for ( BCSAPIMessage.Transaction t : walletMessage.getTransactionsList () )
			{
				Transaction transaction = Transaction.fromProtobuf (t);
				transaction.computeHash ();
				wallet.addTransaction (transaction);
			}
			wallet.addresses = new HashMap<String, String> ();
			for ( BCSAPIMessage.Wallet.PublicAddress p : walletMessage.getPublicAddressesList () )
			{
				wallet.addresses.put (p.getName (), p.getAddress ());
			}
		}
		catch ( UnsupportedEncodingException e )
		{
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
		catch ( IOException e )
		{
			throw new ValidationException (e);
		}
		catch ( InvalidAlgorithmParameterException e )
		{
			throw new ValidationException (e);
		}
		return wallet;
	}
}
