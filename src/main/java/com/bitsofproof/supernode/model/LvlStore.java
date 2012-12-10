/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.CachedBlockStore;
import com.bitsofproof.supernode.core.Discovery;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.PeerStore;
import com.bitsofproof.supernode.core.WireFormat;

public class LvlStore extends CachedBlockStore implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (LvlStore.class);

	private String database = "data";
	private long cacheSize = 100;
	private static DB db;
	private final SecureRandom rnd = new SecureRandom ();
	private WriteBatch batch = null;

	private static enum KeyType
	{
		TX, BLOCK, HEAD, PEER, ATX;
	}

	public static class Key
	{
		public static byte[] createKey (KeyType kt, byte[] key)
		{
			byte[] k = new byte[key.length + 1];
			k[0] = (byte) kt.ordinal ();
			System.arraycopy (key, 0, k, 1, key.length);
			return k;
		}

		private static byte[] minKey (KeyType kt)
		{
			byte[] k = new byte[1];
			k[0] = (byte) kt.ordinal ();
			return k;
		}

		private static byte[] afterLAstKey (KeyType kt)
		{
			byte[] k = new byte[1];
			k[0] = (byte) (kt.ordinal () + 1);
			return k;
		}

		private static boolean hasType (KeyType kt, byte[] key)
		{
			return key[0] == (byte) kt.ordinal ();
		}
	}

	public LvlStore ()
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
			log.trace (db.getProperty ("leveldb.stats"));
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

	private abstract static class DataProcessor
	{
		public abstract boolean process (byte[] key, byte[] data);
	}

	private void forAll (KeyType t, DataProcessor processor)
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (Key.minKey (t));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				if ( !Key.hasType (t, entry.getKey ()) )
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
			iterator.close ();
		}
	}

	private void forAll (KeyType t, byte[] partialKey, DataProcessor processor)
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (Key.createKey (t, partialKey));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				byte[] key = entry.getKey ();
				if ( !Key.hasType (t, key) )
				{
					break;
				}
				for ( int i = 0; i < partialKey.length; ++i )
				{
					if ( key[i + 1] != partialKey[i] )
					{
						break;
					}
				}
				if ( !processor.process (entry.getKey (), entry.getValue ()) )
				{
					break;
				}
			}
		}
		finally
		{
			iterator.close ();
		}
	}

	private void forAllBackward (KeyType t, DataProcessor processor)
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (Key.afterLAstKey (t));
			while ( iterator.hasPrev () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.prev ();
				if ( !Key.hasType (t, entry.getKey ()) )
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
			iterator.close ();
		}
	}

	private void writeAtx (String address, String hash)
	{
		byte[] a = address.getBytes ();
		byte[] h = address.getBytes ();
		byte[] k = new byte[a.length + h.length];
		System.arraycopy (a, 0, k, 0, a.length);
		System.arraycopy (h, 0, k, a.length, h.length);
		if ( batch != null )
		{
			batch.put (Key.createKey (KeyType.ATX, k), new byte[1]);
		}
		else
		{
			db.put (Key.createKey (KeyType.ATX, k), new byte[1]);
		}
	}

	private Tx readTx (String hash)
	{
		byte[] data = db.get (Key.createKey (KeyType.TX, new Hash (hash).toByteArray ()));
		if ( data != null )
		{
			return Tx.fromLevelDB (data);
		}
		return null;
	}

	private void writeTx (Tx t)
	{
		if ( batch != null )
		{
			batch.put (Key.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
		}
		else
		{
			db.put (Key.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
		}
		for ( TxOut o : t.getOutputs () )
		{
			if ( o.getOwner1 () != null )
			{
				writeAtx (o.getOwner1 (), t.getHash ());
			}
			if ( o.getOwner2 () != null )
			{
				writeAtx (o.getOwner2 (), t.getHash ());
			}
			if ( o.getOwner3 () != null )
			{
				writeAtx (o.getOwner3 (), t.getHash ());
			}
		}
	}

	private Head readHead (Long id)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeUint64 (id);
		return Head.fromLevelDB (db.get (Key.createKey (KeyType.HEAD, writer.toByteArray ())));
	}

	private void writeHead (Head h)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeUint64 (h.getId ());
		if ( batch != null )
		{
			batch.put (Key.createKey (KeyType.HEAD, writer.toByteArray ()), h.toLevelDB ());
		}
		else
		{
			db.put (Key.createKey (KeyType.HEAD, writer.toByteArray ()), h.toLevelDB ());
		}
	}

	private Blk readBlk (String hash, boolean full)
	{
		byte[] data = db.get (Key.createKey (KeyType.BLOCK, new Hash (hash).toByteArray ()));
		if ( data != null )
		{
			Blk b = Blk.fromLevelDB (data, true);
			b.setHead (readHead (b.getHeadId ()));
			if ( full )
			{
				b.setTransactions (new ArrayList<Tx> ());
				for ( String txhash : b.getTxHashes () )
				{
					b.getTransactions ().add (readTx (txhash));
				}
			}
			return b;
		}
		return null;
	}

	private void writeBlk (Blk b)
	{
		if ( batch != null )
		{
			batch.put (Key.createKey (KeyType.BLOCK, new Hash (b.getHash ()).toByteArray ()), b.toLevelDB ());
		}
		else
		{
			db.put (Key.createKey (KeyType.BLOCK, new Hash (b.getHash ()).toByteArray ()), b.toLevelDB ());
		}
		for ( Tx t : b.getTransactions () )
		{
			writeTx (t);
		}
	}

	private void writePeer (KnownPeer p)
	{
		if ( batch != null )
		{
			batch.put (Key.createKey (KeyType.PEER, p.getAddress ().getBytes ()), p.toLevelDB ());
		}
		else
		{
			db.put (Key.createKey (KeyType.PEER, p.getAddress ().getBytes ()), p.toLevelDB ());
		}
	}

	private KnownPeer readPeer (String address)
	{
		byte[] data = db.get (Key.createKey (KeyType.PEER, address.getBytes ()));
		if ( data != null )
		{
			return KnownPeer.fromLevelDB (data);
		}
		return null;
	}

	@Override
	protected void cacheChain ()
	{
		final List<Blk> blocks = new ArrayList<Blk> ();
		forAll (KeyType.BLOCK, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				Blk b = Blk.fromLevelDB (data, false);
				blocks.add (b);
				return true;
			}
		});
		Collections.sort (blocks, new Comparator<Blk> ()
		{
			@Override
			public int compare (Blk arg0, Blk arg1)
			{
				return arg0.getHeight () - arg1.getHeight ();
			}
		});
		for ( Blk b : blocks )
		{
			CachedBlock cb = null;
			if ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				cb = new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime ());
			}
			else
			{
				cb = new CachedBlock (b.getHash (), b.getId (), null, b.getCreateTime ());
			}
			cachedBlocks.put (b.getHash (), cb);
			b.setHead (readHead (b.getHeadId ()));
			CachedHead h = cachedHeads.get (b.getHead ().getId ());
			h.getBlocks ().add (cb);
			h.setLast (cb);
		}
	}

	@Override
	protected void cacheHeads ()
	{
		forAll (KeyType.HEAD, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				Head h = Head.fromLevelDB (data);

				CachedHead sh = new CachedHead ();
				sh.setId (h.getId ());
				sh.setChainWork (h.getChainWork ());
				sh.setHeight (h.getHeight ());
				if ( h.getPrevious () != null )
				{
					sh.setPrevious (cachedHeads.get (h.getId ()));
				}
				cachedHeads.put (h.getId (), sh);
				if ( currentHead == null || currentHead.getChainWork () < sh.getChainWork () )
				{
					currentHead = sh;
				}
				return true;
			}
		});
	}

	@Override
	protected void cacheUTXO (final int after)
	{
		final AtomicInteger n = new AtomicInteger (0);
		forAllBackward (KeyType.BLOCK, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				if ( n.incrementAndGet () > after )
				{
					return false;
				}

				Blk b = Blk.fromLevelDB (data, true);
				for ( String txHash : b.getTxHashes () )
				{
					Tx t = readTx (txHash);
					for ( TxOut o : t.getOutputs () )
					{
						if ( o.isAvailable () )
						{
							HashMap<Long, TxOut> outs = cachedUTXO.get (o.getTxHash ());
							if ( outs == null )
							{
								outs = new HashMap<Long, TxOut> ();
								cachedUTXO.put (o.getTxHash (), outs);
							}
							outs.put (o.getIx (), o.flatCopy (null));
						}
					}
				}
				return true;
			}
		});
	}

	@Override
	protected void backwardCache (Blk b)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				out.setAvailable (false);
				removeUTXO (t.getHash (), out.getIx ());
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					Tx sourceTx = readTx (in.getSourceHash ());
					TxOut source = sourceTx.getOutputs ().get (in.getIx ().intValue ());
					source.setAvailable (true);
					writeTx (sourceTx);

					addUTXO (in.getSourceHash (), source.flatCopy (null));
				}
			}
			writeTx (t);
		}
	}

	@Override
	protected void forwardCache (Blk b)
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				out.setAvailable (true);
				addUTXO (t.getHash (), out.flatCopy (null));
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					Tx sourceTx = readTx (in.getSourceHash ());
					TxOut source = sourceTx.getOutputs ().get (in.getIx ().intValue ());
					source.setAvailable (false);
					writeTx (sourceTx);

					removeUTXO (in.getSourceHash (), in.getIx ());
				}
			}
			writeTx (t);
		}
	}

	@Override
	protected List<TxOut> findTxOuts (Map<String, HashSet<Long>> need)
	{
		ArrayList<TxOut> outs = new ArrayList<TxOut> ();

		for ( String hash : need.keySet () )
		{
			Tx t = readTx (hash);
			if ( t != null )
			{
				for ( Long ix : need.get (hash) )
				{
					TxOut out = t.getOutputs ().get (ix.intValue ());
					if ( out.isAvailable () )
					{
						outs.add (out);
					}
				}
			}
		}

		return outs;
	}

	private List<Tx> readRelatedTx (List<String> addresses)
	{
		final List<Tx> result = new ArrayList<Tx> ();
		for ( String address : addresses )
		{
			final byte[] pk = address.getBytes ();
			forAll (KeyType.ATX, pk, new DataProcessor ()
			{
				@Override
				public boolean process (byte[] key, byte[] data)
				{
					byte[] h = new byte[key.length - pk.length - 1];
					System.arraycopy (key, pk.length + 1, h, 0, key.length - pk.length - 1);
					result.add (readTx (new Hash (h).toString ()));
					return true;
				}
			});
		}
		return result;
	}

	@Override
	protected List<Object[]> getReceivedList (final List<String> addresses)
	{
		List<Object[]> result = new ArrayList<Object[]> ();
		List<Tx> related = readRelatedTx (addresses);
		for ( Tx t : related )
		{
			for ( TxOut o : t.getOutputs () )
			{
				result.add (new Object[] { t.getBlockHash (), o });
			}
		}
		return result;
	}

	@Override
	protected TxOut getSourceReference (TxOut source)
	{
		return readTx (source.getTxHash ()).getOutputs ().get (source.getIx ().intValue ());
	}

	@Override
	protected void startBatch ()
	{
		batch = db.createWriteBatch ();
	}

	@Override
	protected void endBatch ()
	{
		if ( batch != null )
		{
			db.write (batch);
			batch.close ();
			batch = null;
		}
	}

	@Override
	protected void cancelBatch ()
	{
		if ( batch != null )
		{
			batch.close ();
			batch = null;
		}
	}

	@Override
	protected void insertBlock (Blk b)
	{
		writeHead (b.getHead ());
		writeBlk (b);
	}

	@Override
	protected void insertHead (Head head)
	{
		head.setId (rnd.nextLong ());
		writeHead (head);
	}

	@Override
	protected Head updateHead (Head head)
	{
		writeHead (head);
		return head;
	}

	@Override
	protected Blk retrieveBlock (CachedBlock cached)
	{
		return readBlk (cached.getHash (), true);
	}

	@Override
	protected Blk retrieveBlockHeader (CachedBlock cached)
	{
		return readBlk (cached.getHash (), false);
	}

	@Override
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		List<TxOut> result = new ArrayList<TxOut> ();
		List<Tx> related = readRelatedTx (addresses);
		for ( Tx t : related )
		{
			for ( TxOut o : t.getOutputs () )
			{
				if ( o.isAvailable () )
				{
					result.add (o);
				}
			}
		}
		return result;
	}

	@Override
	protected List<Object[]> getSpendList (List<String> addresses)
	{
		// TODO Auto-generated method stub
		return null;
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
			iterator.close ();
		}
	}

	@Override
	public List<KnownPeer> getConnectablePeers ()
	{
		final List<KnownPeer> peers = new ArrayList<KnownPeer> ();
		forAll (KeyType.TX, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				KnownPeer p = KnownPeer.fromLevelDB (data);
				if ( p.getBanned () < System.currentTimeMillis () / 1000 )
				{
					peers.add (p);
				}
				return true;
			}
		});
		Collections.sort (peers, new Comparator<KnownPeer> ()
		{

			@Override
			public int compare (KnownPeer o1, KnownPeer o2)
			{
				if ( o1.getPreference () != o2.getPreference () )
				{
					return (int) (o1.getPreference () - o2.getPreference ());
				}
				if ( o1.getResponseTime () != o2.getResponseTime () )
				{
					return (int) (o1.getResponseTime () - o2.getResponseTime ());
				}
				return 0;
			}
		});

		return peers;
	}

	@Override
	public void store (KnownPeer peer)
	{
		writePeer (peer);
	}

	@Override
	public KnownPeer findPeer (InetAddress address)
	{
		return readPeer (address.getHostName ());
	}

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering stored peers");
		List<InetAddress> peers = new ArrayList<InetAddress> ();
		for ( KnownPeer kp : getConnectablePeers () )
		{
			try
			{
				peers.add (InetAddress.getByName (kp.getAddress ()));
			}
			catch ( UnknownHostException e )
			{
			}
		}
		return peers;
	}

}
