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
package com.bitsofproof.supernode.wallet;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.bitsofproof.supernode.api.TransactionOutput;

public class InMemoryUTXO implements AccountManager.UTXO
{
	private static class TxOutKey
	{
		String hash;
		long ix;

		public TxOutKey (String hash, long ix)
		{
			this.hash = hash;
			this.ix = ix;
		}

		@Override
		public boolean equals (Object obj)
		{
			TxOutKey o = (TxOutKey) obj;
			return o.hash.equals (hash) && o.ix == ix;
		}

		@Override
		public int hashCode ()
		{
			return (int) (hash.hashCode () + ix);
		}

	}

	private final Map<TxOutKey, TransactionOutput> utxo = new HashMap<TxOutKey, TransactionOutput> ();

	@Override
	public void add (TransactionOutput out)
	{
		utxo.put (new TxOutKey (out.getTxHash (), out.getIx ()), out);
	}

	@Override
	public Collection<TransactionOutput> getUTXO ()
	{
		return Collections.unmodifiableCollection (utxo.values ());
	}

	@Override
	public TransactionOutput get (String tx, long ix)
	{
		return utxo.get (new TxOutKey (tx, ix));
	}

	@Override
	public TransactionOutput remove (String tx, long ix)
	{
		return utxo.remove (new TxOutKey (tx, ix));
	}

	@Override
	public long getTotal ()
	{
		long s = 0;
		for ( TransactionOutput o : utxo.values () )
		{
			s += o.getValue ();
		}
		return s;
	}
}
