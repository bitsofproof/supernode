package com.bitsofproof.supernode.model;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.CachedBlockStore;
import com.bitsofproof.supernode.core.Discovery;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.PeerStore;

public class LvlStore extends CachedBlockStore implements Discovery, PeerStore
{
	private static final Logger log = LoggerFactory.getLogger (LvlStore.class);

	private String database = "data";
	private long cacheSize = 100;
	private static DB db;

	private static enum KeyType
	{
		TX, BLOCK, HEAD, PEER;
	}

	public static class Key
	{
		public static byte[] createKey (KeyType kt, byte[] key)
		{
			byte[] k = new byte[key.length];
			k[0] = (byte) kt.ordinal ();
			System.arraycopy (key, 1, k, 0, key.length);
			return k;
		}

		private static byte[] minKey (KeyType kt)
		{
			byte[] k = new byte[1];
			k[0] = (byte) kt.ordinal ();
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
		public abstract void process (byte[] data);
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
				processor.process (entry.getValue ());
			}
		}
		finally
		{
			iterator.close ();
		}
	}

	private Tx readTx (byte[] hash)
	{
		byte[] data = db.get (Key.createKey (KeyType.TX, hash));
		if ( data != null )
		{
			return Tx.fromLevelDB (data);
		}
		return null;
	}

	private void writeTx (Tx t)
	{
		db.put (Key.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
	}

	@Override
	protected void cacheChain ()
	{
		forAll (KeyType.BLOCK, new DataProcessor ()
		{
			@Override
			public void process (byte[] data)
			{
				Blk b = Blk.fromLevelDB (data);
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
				CachedHead h = cachedHeads.get (b.getHead ().getId ());
				h.getBlocks ().add (cb);
				h.setLast (cb);

			}
		});
	}

	@Override
	protected void cacheHeads ()
	{
		forAll (KeyType.HEAD, new DataProcessor ()
		{
			@Override
			public void process (byte[] data)
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
			}
		});
	}

	@Override
	protected void cacheUTXO ()
	{
		forAll (KeyType.TX, new DataProcessor ()
		{
			@Override
			public void process (byte[] data)
			{
				Tx t = Tx.fromLevelDB (data);
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
					Tx sourceTx = readTx (new Hash (in.getSourceHash ()).toByteArray ());
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
					Tx sourceTx = readTx (new Hash (in.getSourceHash ()).toByteArray ());
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
			Tx t = readTx (new Hash (hash).toByteArray ());
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

	@Override
	protected List<Object[]> getReceivedList (final List<String> addresses)
	{
		final List<Object[]> result = new ArrayList<Object[]> ();
		forAll (KeyType.TX, new DataProcessor ()
		{
			@Override
			public void process (byte[] data)
			{
				Tx t = Tx.fromLevelDB (data);
				for ( TxOut o : t.getOutputs () )
				{
					if ( addresses.contains (o.getOwner1 ()) || addresses.contains (o.getOwner2 ()) || addresses.contains (o.getOwner3 ()) )
					{
						result.add (new Object[] { t.getBlockHash (), o });
					}
				}
			}
		});
		return result;
	}

	@Override
	protected TxOut getSourceReference (TxOut source)
	{
		return readTx (new Hash (source.getTxHash ()).toByteArray ()).getOutputs ().get (source.getIx ().intValue ());
	}

	@Override
	protected void insertBlock (Blk b)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void insertHead (Head head)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected Head updateHead (Head head)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Blk retrieveBlock (CachedBlock cached)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Blk retrieveBlockHeader (CachedBlock cached)
	{
		return null;
	}

	@Override
	public List<TxOut> getUnspentOutput (List<String> addresses)
	{
		// TODO Auto-generated method stub
		return null;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void store (KnownPeer peer)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public KnownPeer findPeer (InetAddress address)
	{
		// TODO Auto-generated method stub
		return null;
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
