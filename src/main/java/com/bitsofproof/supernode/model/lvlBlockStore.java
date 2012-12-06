package com.bitsofproof.supernode.model;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bitsofproof.supernode.core.CachedBlockStore;

public class lvlBlockStore extends CachedBlockStore
{
	private static final Logger log = LoggerFactory.getLogger (lvlBlockStore.class);

	private String database = "data";
	private long cacheSize = 100;
	private static DB db;

	private static enum KeyType
	{
		UTXO, TX, BLOCK, HEAD, PEER;
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

	public lvlBlockStore ()
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
		}
		catch ( IOException e )
		{
			log.error ("Error opening LevelDB ", e);
		}
		log.trace (db.getProperty ("leveldb.stats"));
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
	protected void cacheChain ()
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (Key.minKey (KeyType.BLOCK));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				if ( !Key.hasType (KeyType.BLOCK, entry.getKey ()) )
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

	@Override
	protected void cacheHeads ()
	{
		DBIterator iterator = db.iterator ();
		try
		{
			iterator.seek (Key.minKey (KeyType.HEAD));
			while ( iterator.hasNext () )
			{
				Map.Entry<byte[], byte[]> entry = iterator.next ();
				if ( !Key.hasType (KeyType.HEAD, entry.getKey ()) )
				{
					break;
				}

				Head h = Head.fromLevelDB (entry.getValue ());

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
		}
		finally
		{
			iterator.close ();
		}
	}

	@Override
	protected void cacheUTXO ()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void backwardCache (Blk b)
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected void forwardCache (Blk b)
	{
		// TODO Auto-generated method stub
	}

	@Override
	protected List<TxOut> findTxOuts (Map<Long, HashSet<String>> need)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected List<Object[]> getReceivedList (List<String> addresses)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected TxOut getSourceReference (TxOut source)
	{
		// TODO Auto-generated method stub
		return null;
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
	@Transactional (propagation = Propagation.MANDATORY)
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

}
