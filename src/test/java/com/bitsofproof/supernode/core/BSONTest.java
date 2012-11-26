package com.bitsofproof.supernode.core;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bson.BSON;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.junit.Test;

public class BSONTest
{
	@Test
	public void bsonTest ()
	{
		Map<String, ArrayList<Long>> map = new HashMap<String, ArrayList<Long>> ();
		ArrayList<Long> list = new ArrayList<Long> ();
		list.add (1L);
		list.add (2L);
		list.add (3L);
		map.put ("a", list);
		BSONObject bo = new BasicBSONObject (map);
		assertTrue (ByteUtils.toHex (BSON.encode (bo)).equals ("2e000000046100260000001230000100000000000000123100020000000000000012320003000000000000000000"));
		BSONObject o = BSON.decode (ByteUtils.fromHex ("2e000000046100260000001230000100000000000000123100020000000000000012320003000000000000000000"));
		Map<String, ArrayList> m = o.toMap ();
		assertTrue ((Long) (m.get ("a").get (1)) == 2L);
	}
}
