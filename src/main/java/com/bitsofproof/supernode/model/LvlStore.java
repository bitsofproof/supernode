package com.bitsofproof.supernode.model;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.ArrayList;
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

	private static enum KeyType
	{
		TX, BLOCK, HEAD, PEER;
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
		public abstract boolean process (byte[] data);
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
				if ( !processor.process (entry.getValue ()) )
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
				if ( !processor.process (entry.getValue ()) )
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
		db.put (Key.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
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
		db.put (Key.createKey (KeyType.HEAD, writer.toByteArray ()), h.toLevelDB ());
	}

	private Blk readBlk (String hash, boolean full)
	{
		byte[] data = db.get (Key.createKey (KeyType.BLOCK, new Hash (hash).toByteArray ()));
		if ( data != null )
		{
			Blk b = Blk.fromLevelDB (data);
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
		db.put (Key.createKey (KeyType.BLOCK, new Hash (b.getHash ()).toByteArray ()), b.toLevelDB ());
		for ( Tx t : b.getTransactions () )
		{
			writeTx (t);
		}
	}

	@Override
	protected void cacheChain ()
	{
		forAll (KeyType.BLOCK, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] data)
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
				b.setHead (readHead (b.getHeadId ()));
				CachedHead h = cachedHeads.get (b.getHead ().getId ());
				h.getBlocks ().add (cb);
				h.setLast (cb);
				return true;
			}
		});
	}

	@Override
	protected void cacheHeads ()
	{
		forAll (KeyType.HEAD, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] data)
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
			public boolean process (byte[] data)
			{
				if ( n.incrementAndGet () > after )
				{
					return false;
				}

				Blk b = Blk.fromLevelDB (data);
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

	@Override
	protected List<Object[]> getReceivedList (final List<String> addresses)
	{
		final List<Object[]> result = new ArrayList<Object[]> ();
		forAll (KeyType.TX, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] data)
			{
				Tx t = Tx.fromLevelDB (data);
				for ( TxOut o : t.getOutputs () )
				{
					if ( addresses.contains (o.getOwner1 ()) || addresses.contains (o.getOwner2 ()) || addresses.contains (o.getOwner3 ()) )
					{
						result.add (new Object[] { t.getBlockHash (), o });
					}
				}
				return true;
			}
		});
		return result;
	}

	@Override
	protected TxOut getSourceReference (TxOut source)
	{
		return readTx (source.getTxHash ()).getOutputs ().get (source.getIx ().intValue ());
	}

	@Override
	protected void insertBlock (Blk b)
	{
		WriteBatch batch = db.createWriteBatch ();

		writeBlk (b);

		db.write (batch);
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
