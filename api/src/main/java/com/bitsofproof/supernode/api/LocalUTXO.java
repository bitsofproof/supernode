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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalUTXO
{
	private final Map<String, HashMap<Long, TransactionOutput>> utxo = new HashMap<String, HashMap<Long, TransactionOutput>> ();

	public void add (TransactionOutput out)
	{
		HashMap<Long, TransactionOutput> outs = utxo.get (out.getTransactionHash ());
		if ( outs == null )
		{
			outs = new HashMap<Long, TransactionOutput> ();
			utxo.put (out.getTransactionHash (), outs);
		}
		outs.put (out.getSelfIx (), out);
	}

	public TransactionOutput get (String tx, Long ix)
	{
		HashMap<Long, TransactionOutput> outs = utxo.get (tx);
		if ( outs != null )
		{
			return outs.get (ix);
		}
		return null;
	}

	public void remove (String tx, Long ix)
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

	public void remove (TransactionOutput out)
	{
		remove (out.getTransactionHash (), out.getSelfIx ());
	}

	public void addOutpoints (BloomFilter filter)
	{
		for ( HashMap<Long, TransactionOutput> outpoint : utxo.values () )
		{
			for ( TransactionOutput out : outpoint.values () )
			{
				filter.addOutpoint (out.getTransactionHash (), out.getSelfIx ());
			}
		}
	}

	public List<TransactionOutput> getSufficientSources (long amount, long fee, String color)
	{
		List<TransactionOutput> result = new ArrayList<TransactionOutput> ();
		long sum = 0;
		for ( HashMap<Long, TransactionOutput> outs : utxo.values () )
		{
			for ( TransactionOutput o : outs.values () )
			{
				if ( color == null )
				{
					if ( o.getColor () == null )
					{
						sum += o.getValue ();
						result.add (o);
						if ( sum >= (amount + fee) )
						{
							return result;
						}
					}
				}
				else
				{
					if ( o.getColor ().equals (color) )
					{
						sum += o.getValue ();
						result.add (o);
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

	public List<TransactionOutput> getAddressSources (String address)
	{
		List<TransactionOutput> result = new ArrayList<TransactionOutput> ();
		for ( HashMap<Long, TransactionOutput> outs : utxo.values () )
		{
			for ( TransactionOutput o : outs.values () )
			{
				if ( o.getVotes () == 1 && o.getAddresses ().size () == 1 && o.getAddresses ().get (0).equals (address) )
				{
					result.add (o);
				}
			}
		}
		return result;
	}

}
