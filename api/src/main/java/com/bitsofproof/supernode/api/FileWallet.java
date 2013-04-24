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

public class FileWallet extends DefaultWallet
{
	private SerializedWallet storedWallet;
	private String passphrase;
	private String fileName;
	private long timeStamp;

	@Override
	public void read (String fileName, String passphrase) throws BCSAPIException
	{
		try
		{
			this.fileName = fileName;
			this.passphrase = passphrase;
			File f = new File (fileName);
			timeStamp = f.lastModified () / 1000;
			FileInputStream in = new FileInputStream (f);
			storedWallet = SerializedWallet.readWallet (in, passphrase);
			in.close ();
			for ( Account a : storedWallet.getAccounts () )
			{
				addAccount (a);
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
				storedWallet.writeWallet (out, passphrase);
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

	@Override
	public long getTimeStamp ()
	{
		return timeStamp;
	}

}
