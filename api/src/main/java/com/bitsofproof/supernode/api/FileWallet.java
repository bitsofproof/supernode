package com.bitsofproof.supernode.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileWallet implements Wallet
{
	private final Map<String, Account> accounts = new HashMap<String, Account> ();

	private SerializedWallet storedWallet;
	private String passphrase;
	private String file;
	private long timeStamp;
	private boolean production = true;

	public static FileWallet read (String fileName, String passphrase) throws ValidationException
	{
		try
		{
			FileWallet dw = new FileWallet ();
			File f = new File (fileName);
			dw.timeStamp = f.lastModified () / 1000;
			FileInputStream in = new FileInputStream (f);
			dw.storedWallet = SerializedWallet.readWallet (in, passphrase);
			in.close ();
			dw.passphrase = passphrase;
			for ( Account a : dw.storedWallet.getAccounts () )
			{
				dw.addAccount (a);
			}
			return dw;
		}
		catch ( FileNotFoundException e )
		{
			throw new ValidationException (e);
		}
		catch ( IOException e )
		{
			throw new ValidationException (e);
		}
	}

	public void setProduction (boolean production)
	{
		this.production = production;
	}

	@Override
	public long getTimeStamp ()
	{
		return timeStamp;
	}

	public String getNamedAddress (String name)
	{
		return storedWallet.getAddresses ().get (name);
	}

	public void setNamedAddress (String name, String account, int ix) throws ValidationException
	{
		storedWallet.addAddress (name, AddressConverter.toSatoshiStyle (accounts.get (account).getKey (ix).getAddress (), production ? 0x0 : 0x05));
	}

	public void addTransaction (Transaction t)
	{
		storedWallet.getTransactions ().add (t);
	}

	public List<Transaction> getTransactions ()
	{
		return storedWallet.getTransactions ();
	}

	public void setPassphrase (String passphrase)
	{
		this.passphrase = passphrase;
	}

	public String getFile ()
	{
		return file;
	}

	public void setFile (String file)
	{
		this.file = file;
	}

	public void addAccount (Account account)
	{
		accounts.put (account.getName (), account);
	}

	@Override
	public void persist () throws ValidationException
	{
		try
		{
			if ( file != null )
			{
				File f = new File (file);
				File tmp = File.createTempFile ("tmp", ".wallet", f.getParentFile ());
				tmp.setReadable (true, true);
				tmp.setWritable (true, true);
				FileOutputStream out = new FileOutputStream (tmp);
				storedWallet.writeWallet (out, passphrase);
				out.close ();
				f.delete ();
				tmp.renameTo (f);
				timeStamp = f.lastModified ();
			}
		}
		catch ( IOException e )
		{
			throw new ValidationException (e);
		}
	}

	@Override
	public Collection<Account> getAccounts ()
	{
		return accounts.values ();
	}
}
