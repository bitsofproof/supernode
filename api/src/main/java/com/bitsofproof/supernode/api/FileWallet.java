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

	private static class NCExtendedKeyAccountManager extends ExtendedKeyAccountManager
	{
		private final String name;
		private final long created;

		public NCExtendedKeyAccountManager (String name, long created)
		{
			super ();
			this.name = name;
			this.created = created;
		}

		public String getName ()
		{
			return name;
		}

		public long getCreated ()
		{
			return created;
		}
	}

	private final Map<String, NCExtendedKeyAccountManager> accounts = new HashMap<String, NCExtendedKeyAccountManager> ();

	public FileWallet (String fileName)
	{
		this.fileName = fileName;
	}

	public boolean exists ()
	{
		return new File (fileName).exists ();
	}

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

	public void unlock (String passphrase) throws ValidationException
	{
		master = ExtendedKey.createFromPassphrase (passphrase, encryptedSeed);
		if ( !master.getMaster ().verify (encryptedSeed, signature) )
		{
			throw new ValidationException ("incorrect passphrase");
		}
		int i = 0;
		for ( ExtendedKeyAccountManager account : accounts.values () )
		{
			account.setMaster (master.getChild (i++ | 0x80000000));
		}
	}

	public void lock ()
	{
		master = null;
		for ( ExtendedKeyAccountManager account : accounts.values () )
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
				NCExtendedKeyAccountManager am = new NCExtendedKeyAccountManager (account.getName (), account.getCreated () * 1000);
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
		for ( NCExtendedKeyAccountManager account : accounts.values () )
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
			NCExtendedKeyAccountManager account = new NCExtendedKeyAccountManager (name, System.currentTimeMillis ());
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
			for ( NCExtendedKeyAccountManager am : accounts.values () )
			{
				BCSAPIMessage.Wallet.Account.Builder ab = BCSAPIMessage.Wallet.Account.newBuilder ();
				ab.setName (am.getName ());
				ab.setCreated (am.getCreated () / 1000);
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
