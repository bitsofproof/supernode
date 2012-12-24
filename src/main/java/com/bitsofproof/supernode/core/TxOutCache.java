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

	public void copy (TxOutCache other, String hash)
	{
		HashMap<Long, TxOut> outs = other.map.get (hash);
		if ( outs != null )
		{
			map.put (hash, outs);
		}
	}

	public void put (TxOut out)
	{
		HashMap<Long, TxOut> outs = map.get (out.getTxHash ());
		if ( outs == null )
		{
			outs = new HashMap<Long, TxOut> ();
			map.put (out.getTxHash (), outs);
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
