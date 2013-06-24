package com.bitsofproof.supernode.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.bitsofproof.supernode.common.ValidationException;
import com.google.protobuf.ByteString;

public class FileWallet implements Wallet
{
	private static final SecureRandom random = new SecureRandom ();
	private transient ExtendedKey master;
	private byte[] encryptedSeed;
	private byte[] signature;
	private String fileName;

	private final Map<String, InMemoryAccountManager> accounts = new HashMap<String, InMemoryAccountManager> ();

	public FileWallet (String fileName)
	{
		this.fileName = fileName;
	}

	public boolean exists ()
	{
		return new File (fileName).exists ();
	}

	@Override
	public void init (String passphrase)
	{
		master = null;
		encryptedSeed = new byte[32];
		random.nextBytes (encryptedSeed);
		try
		{
			signature = ExtendedKey.createFromPassphrase (passphrase, encryptedSeed).getMaster ().sign (encryptedSeed);
		}
		catch ( ValidationException e )
		{
		}
	}

	@Override
	public void unlock (String passphrase) throws ValidationException
	{
		master = ExtendedKey.createFromPassphrase (passphrase, encryptedSeed);
		if ( !master.getMaster ().verify (encryptedSeed, signature) )
		{
			throw new ValidationException ("incorrect passphrase");
		}
		int i = 0;
		for ( InMemoryAccountManager account : accounts.values () )
		{
			account.setMaster (master.getChild (i++ | 0x80000000));
		}
	}

	@Override
	public void lock ()
	{
		master = null;
		for ( InMemoryAccountManager account : accounts.values () )
		{
			account.setMaster (account.getMaster ().getReadOnly ());
		}
	}

	public void setFileName (String fileName)
	{
		this.fileName = fileName;
	}

	public static FileWallet read (String fileName) throws IOException, ValidationException
	{
		FileWallet wallet = new FileWallet (fileName);
		File f = new File (fileName);
		InputStream in = new FileInputStream (f);
		try
		{
			BCSAPIMessage.Wallet walletMessage = BCSAPIMessage.Wallet.parseFrom (in);
			wallet.encryptedSeed = walletMessage.getEncryptedSeed ().toByteArray ();
			wallet.signature = walletMessage.getSignature ().toByteArray ();
			for ( BCSAPIMessage.Wallet.Account account : walletMessage.getAccountsList () )
			{
				InMemoryAccountManager am = new InMemoryAccountManager (account.getName (), account.getCreated ());
				wallet.accounts.put (account.getName (), am);
				am.setMaster (ExtendedKey.parse (account.getPublicKey ()));
			}
		}
		finally
		{
			in.close ();
		}
		return wallet;
	}

	public void sync (BCSAPI api, int lookAhead) throws BCSAPIException, ValidationException
	{
		for ( InMemoryAccountManager account : accounts.values () )
		{
			account.sync (api, lookAhead, account.getCreated ());
		}
	}

	@Override
	public synchronized AccountManager getAccountManager (String name)
	{
		return accounts.get (name);
	}

	@Override
	public synchronized AccountManager createAccountManager (String name) throws ValidationException
	{
		if ( accounts.containsKey (name) )
		{
			return accounts.get (name);
		}
		else
		{
			if ( master == null )
			{
				throw new ValidationException ("The wallet is locked");
			}
			InMemoryAccountManager account = new InMemoryAccountManager (name, System.currentTimeMillis () / 1000);
			account.setMaster (master.getChild (accounts.size () | 0x80000000));
			accounts.put (name, account);
			return account;
		}
	}

	public void persist () throws IOException
	{
		FileOutputStream out = new FileOutputStream (fileName);
		try
		{
			BCSAPIMessage.Wallet.Builder builder = BCSAPIMessage.Wallet.newBuilder ();
			builder.setBcsapiversion (1);
			builder.setEncryptedSeed (ByteString.copyFrom (encryptedSeed));
			builder.setSignature (ByteString.copyFrom (signature));
			for ( InMemoryAccountManager am : accounts.values () )
			{
				BCSAPIMessage.Wallet.Account.Builder ab = BCSAPIMessage.Wallet.Account.newBuilder ();
				ab.setName (am.getName ());
				ab.setCreated (am.getCreated ());
				ab.setPublicKey (am.getMaster ().getReadOnly ().serialize (true));
				builder.addAccounts (ab.build ());
			}
			builder.build ().writeTo (out);
		}
		finally
		{
			out.close ();
		}
	}
}
