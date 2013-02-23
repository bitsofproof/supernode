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
package com.bitsofproof.supernode.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.bitsofproof.supernode.model.TxOut;

public class ImplementTxOutCache implements TxOutCache
{
	private final Map<String, HashMap<Long, TxOut>> map = new HashMap<String, HashMap<Long, TxOut>> ();
	private final Set<TxOut> used = new HashSet<TxOut> ();

	@Override
	public TxOut get (String hash, Long ix)
	{
		HashMap<Long, TxOut> outs = map.get (hash);
		if ( outs != null )
		{
			return outs.get (ix);
		}
		return null;
	}

	@Override
	public TxOut use (String hash, Long ix)
	{
		TxOut out = get (hash, ix);
		if ( out != null )
		{
			if ( used.contains (out) )
			{
				return null;
			}
			used.add (out);
		}
		return out;
	}

	@Override
	public void copy (TxOutCache other, String hash)
	{
		if ( other instanceof ImplementTxOutCache )
		{
			ImplementTxOutCache o = (ImplementTxOutCache) other;
			HashMap<Long, TxOut> outs = o.map.get (hash);
			if ( outs != null )
			{
				map.put (hash, outs);
			}
		}
		else
		{
			throw new RuntimeException ("not implemented");
		}
	}

	@Override
	public void add (TxOut out)
	{
		HashMap<Long, TxOut> outs = map.get (out.getTxHash ());
		if ( outs == null )
		{
			outs = new HashMap<Long, TxOut> ();
			map.put (out.getTxHash (), outs);
		}
		outs.put (out.getIx (), out);
	}

	@Override
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
			for ( TxOut o : outs.values () )
			{
				used.remove (o);
			}
		}
	}
}
