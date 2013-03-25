package com.bitsofproof.supernode.api;

import java.util.List;

public interface AccountManager
{
	public void track (KeyGenerator generator);

	public Transaction pay (String receiver, long amount, long fee) throws ValidationException, BCSAPIException;

	public Transaction transfer (String receiver, long units, long fee, Color color) throws ValidationException, BCSAPIException;

	public Transaction createColorGenesis (long quantity, long unitSize, long fee) throws ValidationException, BCSAPIException;

	public void importKey (String serialized, String passpharse) throws ValidationException;

	public Transaction cashIn (String serialized, String passpharse, long fee) throws ValidationException, BCSAPIException;

	public long getBalance ();

	public long getBalance (Color color);

	public List<String> getColors ();

	public void addAccountListener (AccountListener listener);

	public void removeAccountListener (AccountListener listener);
}