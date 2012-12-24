package com.bitsofproof.supernode.core;

import java.util.HashMap;
import java.util.Map;

import com.bitsofproof.supernode.model.TxOut;

public class TxOutCache
{
	private final Map<String, HashMap<Long, TxOut>> map = new HashMap<String, HashMap<Long, TxOut>> ();

	public TxOut get (String hash, Long ix)
	{
		HashMap<Long, TxOut> outs = map.get (hash);
		if ( outs != null )
		{
			return outs.get (ix);
		}
		return null;
	}

	public HashMap<Long, TxOut> get (String hash)
	{
		return map.get (hash);
	}

	public void put (String hash, HashMap<Long, TxOut> outs)
	{
		map.put (hash, outs);
	}

	public void put (String hash, TxOut out)
	{
		HashMap<Long, TxOut> outs = map.get (hash);
		if ( outs == null )
		{
			outs = new HashMap<Long, TxOut> ();
			map.put (hash, outs);
		}
		outs.put (out.getIx (), out);
	}

	public void remove (String hash, Long ix)
	{
		HashMap<Long, TxOut> outs = map.get (hash);
		if ( outs != null )
		{
			outs.remove (ix);
			if ( outs.size () == 0 )
			{
				map.remove (hash);
			}
		}
	}

	public void remove (String hash)
	{
		map.remove (hash);
	}
}
