package com.bitsofproof.supernode.model;

import static org.fusesource.leveldbjni.JniDBFactory.factory;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LevelDBTest
{
	private static DB db;

	@BeforeClass
	public static void openDB () throws IOException
	{
		Options options = new Options ();
		options.createIfMissing (true);
		db = factory.open (new File ("levelDBExample"), options);
	}

	@AfterClass
	public static void closeDB ()
	{
		db.close ();
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
}
