package com.bitsofproof.supernode.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;
import com.google.protobuf.ByteString;

public class FileWallet implements Wallet
{
	private BCSAPI api;

	private static final SecureRandom random = new SecureRandom ();
	private transient ExtendedKey master;
	private byte[] encryptedSeed;
	private byte[] signature;
	private String fileName;

	private final Map<String, AccountManager> accounts = new HashMap<String, AccountManager> ();

	public FileWallet (String fileName)
	{
		this.fileName = fileName;
	}

	public boolean exists ()
	{
		return new File (fileName).exists ();
	}

	@Override
	public void setApi (BCSAPI api)
	{
		this.api = api;
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
	}

	@Override
	public void lock ()
	{
		master = null;
	}

	public void setFileName (String fileName)
	{
		this.fileName = fileName;
	}

	@Override
	public void read (String fileName, int lookAhead) throws IOException, ValidationException, BCSAPIException
	{
		this.fileName = fileName;
		master = null;
		File f = new File (fileName);
		InputStream in = new FileInputStream (f);
		try
		{
			BCSAPIMessage.Wallet walletMessage = BCSAPIMessage.Wallet.parseFrom (in);
			encryptedSeed = walletMessage.getEncryptedSeed ().toByteArray ();
			signature = walletMessage.getSignature ().toByteArray ();
			for ( BCSAPIMessage.Wallet.Account account : walletMessage.getAccountsList () )
			{
				InMemoryAccountManager am =
						new InMemoryAccountManager (this, account.getName (), ExtendedKey.parse (account.getPublicKey ()), account.getNextSequence (),
								account.getCreated ());
				am.setApi (api);
				accounts.put (account.getName (), am);
				for ( BCSAPIMessage.Transaction t : account.getTransactionsList () )
				{
					am.updateWithTransaction (Transaction.fromProtobuf (t));
				}
				am.sync (lookAhead, account.getCreated ());
			}
		}
		finally
		{
			in.close ();
		}
	}

	@Override
	public synchronized AccountManager getAccountManager (String name)
	{
		return accounts.get (name);
	}

	@Override
	public synchronized AccountManager createAccountManager (String name) throws ValidationException, IOException
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
			InMemoryAccountManager account =
					new InMemoryAccountManager (this, name, master.getChild (accounts.size () | 0x80000000), 0, System.currentTimeMillis () / 1000);
			account.setApi (api);
			accounts.put (name, account);
			persist ();
			return account;
		}
	}

	@Override
	public Key getKey (AccountManager am, int sequence) throws ValidationException
	{
		if ( master == null )
		{
			return am.getMasterKey ().getKey (sequence);
		}
		else
		{
			return master.getChild (am.getMasterKey ().getSequence ()).getKey (sequence);
		}
	}

	@Override
	public void persist () throws IOException
	{
		FileOutputStream out = new FileOutputStream (fileName);
		try
		{
			BCSAPIMessage.Wallet.Builder builder = BCSAPIMessage.Wallet.newBuilder ();
			builder.setBcsapiversion (1);
			builder.setEncryptedSeed (ByteString.copyFrom (encryptedSeed));
			builder.setSignature (ByteString.copyFrom (signature));
			for ( AccountManager am : accounts.values () )
			{
				BCSAPIMessage.Wallet.Account.Builder ab = BCSAPIMessage.Wallet.Account.newBuilder ();
				ab.setName (am.getName ());
				ab.setCreated (am.getCreated ());
				ab.setNextSequence (am.getNextSequence ());
				ab.setPublicKey (am.getMasterKey ().getReadOnly ().serialize (true));
				for ( Transaction t : am.getTransactions () )
				{
					ab.addTransactions (t.toProtobuf ());
				}
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
