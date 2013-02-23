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

public class ImplementTxOutCacheDelta implements TxOutCache
{
	private final TxOutCache delegate;
	private final ImplementTxOutCache added = new ImplementTxOutCache ();
	private final Map<String, HashSet<Long>> removed = new HashMap<String, HashSet<Long>> ();
	private final Set<TxOut> used = new HashSet<TxOut> ();

	public ImplementTxOutCacheDelta (TxOutCache cache)
	{
		delegate = cache;
	}

	@Override
	public TxOut get (String hash, Long ix)
	{
		if ( removed.containsKey (hash) && removed.get (hash).contains (ix) )
		{
			return null;
		}
		TxOut a = added.get (hash, ix);
		if ( a == null )
		{
			return delegate.get (hash, ix);
		}
		return a;
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
		if ( other != delegate )
		{
			throw new RuntimeException ("not implemented");
		}
	}

	@Override
	public void add (TxOut out)
	{
		if ( removed.containsKey (out.getTxHash ()) && removed.get (out.getTxHash ()).contains (out.getIx ()) )
		{
			removed.get (out.getTxHash ()).remove (out.getIx ());
			if ( removed.get (out.getTxHash ()).size () == 0 )
			{
				removed.remove (out.getTxHash ());
			}
		}
		else
		{
			added.add (out);
		}
	}

	@Override
	public void remove (String hash, Long ix)
	{
		if ( added.get (hash, ix) != null )
		{
			added.remove (hash, ix);
		}
		else
		{
			HashSet<Long> rs = removed.get (hash);
			if ( rs == null )
			{
				rs = new HashSet<Long> ();
				removed.put (hash, rs);
			}
			rs.add (ix);
		}
	}
}
