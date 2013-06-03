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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;

public class SerializedWallet implements Wallet
{
	private static final Logger log = LoggerFactory.getLogger (SerializedWallet.class);

	private String passphrase;
	private String fileName;
	private long timeStamp;
	private boolean production;
	private SecretKeySpec keyspec = null;
	private final byte[] iv = new byte[16];

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
	public void setTimeStamp (long time)
	{
		timeStamp = time;
	}

	@Override
	public AccountManager getAccountManager (String name) throws BCSAPIException
	{
		try
		{
			int nextSequence = 0;
			ExtendedKey extended = null;

			DefaultAccountManager am = null;
			for ( WalletKey key : getKeys () )
			{
				if ( key.name.equals (name) )
				{
					if ( key.am != null )
					{
						return key.am;
					}
					extended = ExtendedKey.parse (key.key);
					nextSequence = key.nextSequence;
					key.am = am = new DefaultAccountManager (this, name, extended, nextSequence);
					break;
				}
			}
			if ( extended == null )
			{
				WalletKey wk = new WalletKey ();
				extended = ExtendedKey.createNew ();

				wk.created = System.currentTimeMillis () / 1000;
				wk.name = name;
				wk.key = extended.serialize (production);
				nextSequence = wk.nextSequence = 0;
				addKey (wk);
				wk.am = am = new DefaultAccountManager (this, name, extended, nextSequence);
			}
			am.setApi (api);
			am.registerFilter ();
			if ( getTimeStamp () > 0 )
			{
				for ( Transaction t : getTransactions () )
				{
					am.updateWithTransaction (t);
				}
			}
			else
			{
				transactions.clear ();
			}
			final DefaultAccountManager fam = am;
			long ts = getTimeStamp ();
			if ( ts > 0 )
			{
				// go back a day to ensure possible re-orgs are included.
				ts -= 60 * 60 * 24;
			}
			api.scanTransactions (am.getAddresses (), UpdateMode.all, ts, new TransactionListener ()
			{
				@Override
				public void process (Transaction t)
				{
					if ( fam.updateWithTransaction (t) )
					{
						addTransaction (t);
					}
				}
			});
			persist ();
			am.addAccountListener (new AccountListener ()
			{
				@Override
				public void accountChanged (AccountManager account, Transaction t)
				{
					try
					{
						persist ();
					}
					catch ( BCSAPIException e )
					{
						log.error ("Failed to persist wallet", e);
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

	public static SerializedWallet read (String fileName, String passphrase, boolean production) throws BCSAPIException
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
					SerializedWallet wallet = readWallet (in, passphrase, production);
					wallet.fileName = fileName;
					wallet.passphrase = passphrase;
					wallet.timeStamp = timeStamp;
					wallet.production = production;
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
				wallet.production = production;
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
				timeStamp = f.lastModified () / 1000;
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
		AccountManager am;
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

	@Override
	public void addTransaction (Transaction t)
	{
		if ( !hasTransaction.contains (t.getHash ()) )
		{
			transactions.add (t);
			hasTransaction.add (t.getHash ());
		}
		else
		{
			for ( Transaction s : transactions )
			{
				if ( s.getHash ().equals (t.getHash ()) )
				{
					s.setBlockHash (t.getBlockHash ());
				}
			}
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
			if ( keyspec == null || !passphrase.equals (this.passphrase) )
			{
				this.passphrase = passphrase;
				rnd.nextBytes (iv);
				byte[] derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), iv, 16384, 8, 8, 32);
				keyspec = new SecretKeySpec (derived, "AES");
			}
			output.write (iv);
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
				kb.setNextSequence (key.am != null ? key.am.getNextSequence () : 0);
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

	public static SerializedWallet readWallet (InputStream input, String passphrase, boolean production) throws ValidationException
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
			input.read (wallet.iv);
			derived = SCrypt.generate (passphrase.getBytes ("UTF-8"), wallet.iv, 16384, 8, 8, 32);
			wallet.keyspec = new SecretKeySpec (derived, "AES");
			Cipher cipher = Cipher.getInstance ("AES/CBC/PKCS5Padding", "BC");
			cipher.init (Cipher.DECRYPT_MODE, wallet.keyspec, new IvParameterSpec (wallet.iv));

			CipherInputStream cis = new CipherInputStream (input, cipher);
			GZIPInputStream unzip = new GZIPInputStream (cis);

			BCSAPIMessage.Wallet walletMessage = BCSAPIMessage.Wallet.parseFrom (unzip);

			wallet.keys = new ArrayList<WalletKey> ();
			for ( BCSAPIMessage.Wallet.Key k : walletMessage.getKeysList () )
			{
				WalletKey key = new WalletKey ();
				key.key = k.getKey ();
				if ( production && !key.key.startsWith ("x") )
				{
					throw new ValidationException ("Expected production keys");
				}
				if ( !production && !key.key.startsWith ("t") )
				{
					throw new ValidationException ("Expected test keys");
				}
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
