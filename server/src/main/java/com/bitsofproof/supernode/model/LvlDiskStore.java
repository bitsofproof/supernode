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
package com.bitsofproof.supernode.model;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.model.OrderedMapStoreKey.KeyType;

public class LvlDiskStore implements OrderedMapStore
{
	private static final Logger log = LoggerFactory.getLogger (LvlDiskStore.class);

	private String database = "data";
	private long cacheSize = 100;
	private DB db;
	private WriteBatch batch = null;
	private final Map<String, byte[]> batchCache = new HashMap<String, byte[]> ();

	@Override
	public synchronized void put (byte[] key, byte[] data)
	{
		if ( batch != null )
		{
			batch.put (key, data);
			batchCache.put (ByteUtils.toHex (key), data);
		}
		else
		{
			db.put (key, data);
		}
	}

	@Override
	public void clearStore ()
	{
	}

	@Override
	public synchronized byte[] get (byte[] key)
	{
		if ( batch != null )
		{
			String ks = ByteUtils.toHex (key);
			byte[] data = batchCache.get (ks);
			if ( data != null )
			{
				return data;
			}
		}
		return db.get (key);
	}

	@Override
	public synchronized void startBatch ()
	{
		batch = db.createWriteBatch ();
		batchCache.clear ();
	}

	@Override
	public synchronized void endBatch ()
	{
		if ( batch != null )
		{
			db.write (batch);
			try
			{
				batch.close ();
			}
			catch ( IOException e )
			{
			}
			batchCache.clear ();
		}
	}

	@Override
	public synchronized void cancelBatch ()
	{
		if ( batch != null )
		{
			try
			{
				batch.close ();
			}
			catch ( IOException e )
			{
			}
			batchCache.clear ();
		}
	}

	public LvlDiskStore ()
	{
		org.iq80.leveldb.Logger logger = new org.iq80.leveldb.Logger ()
		{
			@Override
			public void log (String message)
			{
				log.trace (message);
			}
		};
		Options options = new Options ();
		options.logger (logger);
		options.cacheSize (cacheSize * 1048576);
		options.createIfMissing (true);
		try
		{
			db = factory.open (new File (database), options);
			log.debug (db.getProperty ("leveldb.stats"));
		}
		catch ( IOException e )
		{
			log.error ("Error opening LevelDB ", e);
		}
	}

	public void setDatabase (String database)
	{
		this.database = database;
	}

	public void setCacheSize (long cacheSize)
	{
		this.cacheSize = cacheSize;
	}

	@Override
	public void forAll (KeyType t, DataProcessor processor)
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (OrderedMapStoreKey.minKey (t));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				if ( !OrderedMapStoreKey.hasType (t, entry.getKey ()) )
				{
					break;
				}
				if ( !processor.process (entry.getKey (), entry.getValue ()) )
				{
					break;
				}
			}
		}
		finally
		{
			try
			{
				iterator.close ();
			}
			catch ( IOException e )
			{
			}
		}
	}

	@Override
	public void forAll (KeyType t, byte[] partialKey, DataProcessor processor)
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (OrderedMapStoreKey.createKey (t, partialKey));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				byte[] key = entry.getKey ();
				if ( !OrderedMapStoreKey.hasType (t, key) )
				{
					break;
				}
				boolean found = true;
				for ( int i = 0; i < partialKey.length; ++i )
				{
					if ( key[i + 1] != partialKey[i] )
					{
						found = false;
						break;
					}
				}
				if ( !found )
				{
					break;
				}
				if ( !processor.process (entry.getKey (), entry.getValue ()) )
				{
					break;
				}
			}
		}
		finally
		{
			try
			{
				iterator.close ();
			}
			catch ( IOException e )
			{
			}
		}
	}

	@Override
	public boolean isEmpty ()
	{
		DBIterator iterator = db.iterator ();
		try
		{
			for ( iterator.seekToFirst (); iterator.hasNext (); )
			{
				return false;
			}
			return true;
		}
		finally
		{
			try
			{
				iterator.close ();
			}
			catch ( IOException e )
			{
			}
		}
	}
}
