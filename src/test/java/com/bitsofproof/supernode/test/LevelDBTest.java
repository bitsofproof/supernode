package com.bitsofproof.supernode.test;

import static org.fusesource.leveldbjni.JniDBFactory.factory;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBComparator;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.ReadOptions;
import org.iq80.leveldb.WriteBatch;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDBTest
{
	private static final Logger log = LoggerFactory.getLogger (LevelDBTest.class);

	private static DB db;

	private static final String testdb = "levelDBExample";

	@BeforeClass
	public static void openDB () throws IOException
	{
		DBComparator comparator = new DBComparator ()
		{
			@Override
			public int compare (byte[] key1, byte[] key2)
			{
				return new String (key1).compareTo (new String (key2));
			}

			@Override
			public String name ()
			{
				return "simple";
			}

			@Override
			public byte[] findShortestSeparator (byte[] start, byte[] limit)
			{
				return start;
			}

			@Override
			public byte[] findShortSuccessor (byte[] key)
			{
				return key;
			}
		};
		org.iq80.leveldb.Logger logger = new org.iq80.leveldb.Logger ()
		{
			@Override
			public void log (String message)
			{
				log.trace (message);
			}
		};
		Options options = new Options ();
		options.comparator (comparator);
		options.logger (logger);
		options.cacheSize (100 * 1048576); // 100MB cache
		options.createIfMissing (true);
		db = factory.open (new File (testdb), options);
	}

	@AfterClass
	public static void closeDB () throws IOException
	{
		String stats = db.getProperty ("leveldb.stats");
		System.out.println (stats);
		db.close ();
		Options options = new Options ();
		factory.destroy (new File (testdb), options);
	}

	@Test
	public void putGetDeleteTest () throws UnsupportedEncodingException, DBException
	{
		db.put ("Tampa".getBytes (), "rocks".getBytes ());
		assertTrue (new String (db.get ("Tampa".getBytes ()), "US-ASCII").equals ("rocks"));
		db.delete ("Tampa".getBytes ());
		db.put ("Denver".getBytes (), "whatever".getBytes ());
	}

	@Test
	public void batchTest () throws UnsupportedEncodingException, DBException
	{
		WriteBatch batch = db.createWriteBatch ();

		batch.delete ("Denver".getBytes ());
		batch.put ("Tampa".getBytes (), "green".getBytes ());
		batch.put ("London".getBytes (), "red".getBytes ());

		db.write (batch);
		assertTrue (new String (db.get ("London".getBytes ()), "US-ASCII").equals ("red"));
	}

	@Test
	public void iteratorTest () throws UnsupportedEncodingException, IOException
	{
		DBIterator iterator = db.iterator ();
		doIterate (iterator);
	}

	private void doIterate (DBIterator iterator) throws UnsupportedEncodingException, IOException
	{
		try
		{
			String[] keys = { "London", "Tampa" };
			String[] values = { "red", "green" };
			int i = 0;
			for ( iterator.seekToFirst (); iterator.hasNext (); iterator.next () )
			{
				String key = new String (iterator.peekNext ().getKey (), "US-ASCII");
				String value = new String (iterator.peekNext ().getValue (), "US-ASCII");
				assertTrue (keys[i].equals (key));
				assertTrue (values[i].equals (value));
				++i;
			}
		}
		finally
		{
			// Make sure you close the iterator to avoid resource leaks.
			iterator.close ();
		}
	}

	@Test
	public void snapshotTest () throws UnsupportedEncodingException, IOException
	{
		ReadOptions ro = new ReadOptions ();
		ro.snapshot (db.getSnapshot ());
		try
		{
			doIterate (db.iterator (ro));
		}
		finally
		{
			// Make sure you close the snapshot to avoid resource leaks.
			ro.snapshot ().close ();
		}
	}
}
