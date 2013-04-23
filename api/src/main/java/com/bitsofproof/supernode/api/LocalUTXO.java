/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.supernode.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bitsofproof.supernode.api.Transaction.TransactionSource;

public class LocalUTXO
{
	private final Map<String, HashMap<Long, TransactionOutput>> utxo = new HashMap<String, HashMap<Long, TransactionOutput>> ();

	public void add (String source, long ix, TransactionOutput out)
	{
		HashMap<Long, TransactionOutput> outs = utxo.get (source);
		if ( outs == null )
		{
			outs = new HashMap<Long, TransactionOutput> ();
			utxo.put (source, outs);
		}
		outs.put (ix, out);
	}

	public Collection<TransactionOutput> getOutputs ()
	{
		List<TransactionOutput> outs = new ArrayList<TransactionOutput> ();
		for ( HashMap<Long, TransactionOutput> o : utxo.values () )
		{
			outs.addAll (o.values ());
		}
		return outs;
	}

	public TransactionOutput get (String tx, long ix)
	{
		HashMap<Long, TransactionOutput> outs = utxo.get (tx);
		if ( outs != null )
		{
			return outs.get (ix);
		}
		return null;
	}

	public void remove (String tx, long ix)
	{
		HashMap<Long, TransactionOutput> outs = utxo.get (tx);
		if ( outs != null )
		{
			outs.remove (ix);
			if ( outs.size () == 0 )
			{
				utxo.remove (tx);
			}
		}
	}

	public List<TransactionSource> getSufficientSources (long amount, long fee, String color)
	{
		List<TransactionSource> result = new ArrayList<TransactionSource> ();
		long sum = 0;
		for ( Map.Entry<String, HashMap<Long, TransactionOutput>> txs : utxo.entrySet () )
		{
			for ( Map.Entry<Long, TransactionOutput> o : txs.getValue ().entrySet () )
			{
				if ( color == null )
				{
					if ( o.getValue ().getColor () == null )
					{
						sum += o.getValue ().getValue ();
						result.add (new TransactionSource (txs.getKey (), o.getKey (), o.getValue ()));
						if ( sum >= (amount + fee) )
						{
							return result;
						}
					}
				}
				else
				{
					if ( o.getValue ().getColor ().equals (color) )
					{
						sum += o.getValue ().getValue ();
						result.add (new TransactionSource (txs.getKey (), o.getKey (), o.getValue ()));
						if ( sum >= amount )
						{
							if ( fee > 0 )
							{
								result.addAll (getSufficientSources (0, fee, null));
							}
							return result;
						}
					}
				}
			}
		}
		return null;
	}
}
