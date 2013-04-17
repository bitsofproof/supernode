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

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

import com.bitsofproof.supernode.model.OrderedMapStoreKey.KeyType;

public class LvlMemoryStore implements OrderedMapStore
{
	private class KeyComparator implements Comparator<byte[]>
	{
		@Override
		public int compare (byte[] arg0, byte[] arg1)
		{
			int n = Math.min (arg0.length, arg1.length);
			for ( int i = 0; i < n; ++i )
			{
				if ( arg0[i] != arg1[i] )
				{
					return (arg0[i] & 0xff) - (arg1[i] & 0xff);
				}
			}
			return arg0.length - arg1.length;
		}
	}

	@Override
	public void clearStore ()
	{
		db.clear ();
		batch = null;
	}

	private final TreeMap<byte[], byte[]> db = new TreeMap<byte[], byte[]> (new KeyComparator ());
	private TreeMap<byte[], byte[]> batch = null;

	@Override
	public synchronized void put (byte[] key, byte[] data)
	{
		if ( batch != null )
		{
			batch.put (key, data);
		}
		else
		{
			db.put (key, data);
		}
	}

	@Override
	public void remove (byte[] key)
	{
		if ( batch != null )
		{
			batch.remove (key);
		}
		else
		{
			db.remove (key);
		}
	}

	@Override
	public synchronized byte[] get (byte[] key)
	{
		if ( batch != null )
		{
			byte[] data = batch.get (key);
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
		batch = new TreeMap<byte[], byte[]> (new KeyComparator ());
	}

	@Override
	public synchronized void endBatch ()
	{
		if ( batch != null )
		{
			for ( Map.Entry<byte[], byte[]> e : batch.entrySet () )
			{
				db.put (e.getKey (), e.getValue ());
			}
			batch = null;
		}
	}

	@Override
	public synchronized void cancelBatch ()
	{
		batch = null;
	}

	@Override
	public void forAll (KeyType t, DataProcessor processor)
	{
		for ( Map.Entry<byte[], byte[]> entry : db.tailMap (OrderedMapStoreKey.minKey (t)).entrySet () )
		{
			byte[] key = entry.getKey ();
			if ( !OrderedMapStoreKey.hasType (t, key) )
			{
				break;
			}
			if ( !processor.process (key, entry.getValue ()) )
			{
				break;
			}
		}
	}

	@Override
	public void forAll (KeyType t, byte[] partialKey, DataProcessor processor)
	{
		for ( Map.Entry<byte[], byte[]> entry : db.tailMap (OrderedMapStoreKey.createKey (t, partialKey)).entrySet () )
		{
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
			if ( !processor.process (key, entry.getValue ()) )
			{
				break;
			}
		}
	}

	@Override
	public boolean isEmpty ()
	{
		return db.isEmpty ();
	}
}
