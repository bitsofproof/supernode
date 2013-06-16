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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Transaction.TransactionSink;
import com.bitsofproof.supernode.api.Transaction.TransactionSource;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ScriptFormat.Token;
import com.bitsofproof.supernode.common.ValidationException;

class InMemoryAccountManager implements TransactionListener, AccountManager
{
	private static final Logger log = LoggerFactory.getLogger (InMemoryAccountManager.class);

	private BCSAPI api;

	private final InMemoryUTXO confirmed = new InMemoryUTXO ();
	private final InMemoryUTXO change = new InMemoryUTXO ();
	private final InMemoryUTXO receiving = new InMemoryUTXO ();
	private final InMemoryUTXO sending = new InMemoryUTXO ();

	private final List<AccountListener> accountListener = Collections.synchronizedList (new ArrayList<AccountListener> ());
	private final Set<String> processedTransaction = new HashSet<String> ();
	private final Map<String, Transaction> relevantTransaction = new HashMap<String, Transaction> ();

	private final String name;
	private final ExtendedKey extended;
	private final Map<ByteVector, Integer> keyIDForAddress = new HashMap<ByteVector, Integer> ();
	private int nextKey;
	private final long created;
	private final Wallet wallet;

	@Override
	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	@Override
	public long getCreated ()
	{
		return created;
	}

	public InMemoryAccountManager (Wallet wallet, String name, ExtendedKey extended, int nextKey, long created)
	{
		this.name = name;
		this.extended = extended;
		this.nextKey = nextKey;
		this.created = created;
		this.wallet = wallet;
	}

	public void sync (final int lookAhead, long after) throws BCSAPIException, ValidationException
	{
		for ( int i = nextKey; i < nextKey + lookAhead; ++i )
		{
			Key key = extended.getKey (i);
			keyIDForAddress.put (new ByteVector (key.getAddress ()), i);
		}
		final AtomicInteger lastUsedKey = new AtomicInteger (-1);
		api.scanTransactions (extended, lookAhead, after, new TransactionListener ()
		{
			@Override
			public void process (Transaction t)
			{
				for ( TransactionOutput o : t.getOutputs () )
				{
					try
					{
						for ( Token token : ScriptFormat.parse (o.getScript ()) )
						{
							if ( token.data != null )
							{
								Integer thisKey = keyIDForAddress.get (new ByteVector (token.data));
								if ( thisKey != null )
								{
									lastUsedKey.set (Math.max (thisKey, lastUsedKey.get ()));
								}
								else
								{
									while ( thisKey == null && (keyIDForAddress.size () - lastUsedKey.get ()) < lookAhead )
									{
										Key key = extended.getKey (keyIDForAddress.size ());
										keyIDForAddress.put (new ByteVector (key.getAddress ()), keyIDForAddress.size ());
									}
									thisKey = keyIDForAddress.get (new ByteVector (token.data));
									if ( thisKey != null )
									{
										lastUsedKey.set (Math.max (thisKey, lastUsedKey.get ()));
									}
								}
							}
						}
					}
					catch ( ValidationException e )
					{
					}
					updateWithTransaction (t);
				}
			}
		});
		nextKey = Math.max (lastUsedKey.get (), nextKey);
		api.registerTransactionListener (this);
	}

	@Override
	public int getNextSequence ()
	{
		return nextKey;
	}

	@Override
	public String getName ()
	{
		return name;
	}

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> addresses = new ArrayList<byte[]> ();
		for ( ByteVector v : keyIDForAddress.keySet () )
		{
			addresses.add (v.toByteArray ());
		}
		return addresses;
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		Integer id = keyIDForAddress.get (new ByteVector (address));
		Key key = null;
		try
		{
			key = wallet.getKey (this, id);
		}
		catch ( ValidationException e )
		{
		}
		return key;
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		Key key = extended.getKey (nextKey);
		keyIDForAddress.put (new ByteVector (key.getAddress ()), nextKey);
		++nextKey;
		return key;
	}

	@Override
	public boolean updateWithTransaction (Transaction t)
	{
		synchronized ( confirmed )
		{
			boolean modified = false;
			if ( !processedTransaction.contains (t.getHash ()) )
			{
				if ( t.isDoubleSpend () )
				{
					return false;
				}
				processedTransaction.add (t.getHash ());
				TransactionOutput spend = null;
				for ( TransactionInput i : t.getInputs () )
				{
					spend = confirmed.get (i.getSourceHash (), i.getIx ());
					if ( spend != null )
					{
						confirmed.remove (i.getSourceHash (), i.getIx ());
						log.trace ("Spend settled output " + i.getSourceHash () + " " + i.getIx () + " " + spend.getValue ());
					}
					else
					{
						spend = change.get (i.getSourceHash (), i.getIx ());
						if ( spend != null )
						{
							change.remove (i.getSourceHash (), i.getIx ());
							log.trace ("Spend change output " + i.getSourceHash () + " " + i.getIx () + " " + spend.getValue ());
						}
						else
						{
							spend = receiving.get (i.getSourceHash (), i.getIx ());
							if ( spend != null )
							{
								receiving.remove (i.getSourceHash (), i.getIx ());
								log.trace ("Spend receiving output " + i.getSourceHash () + " " + i.getIx () + " " + spend.getValue ());
							}
						}
					}
				}
				modified = spend != null;
				long ix = 0;
				for ( TransactionOutput o : t.getOutputs () )
				{
					if ( keyIDForAddress.containsKey (new ByteVector (o.getOutputAddress ())) )
					{
						modified = true;
						if ( t.getBlockHash () != null )
						{
							confirmed.add (t.getHash (), ix, o);
							log.trace ("Settled " + ix + " " + o.getValue ());
						}
						else
						{
							if ( spend != null )
							{
								change.add (t.getHash (), ix, o);
								log.trace ("Change " + ix + " " + o.getValue ());
							}
							else
							{
								receiving.add (t.getHash (), ix, o);
								log.trace ("Receiving " + ix + " " + o.getValue ());
							}
						}
					}
					else
					{
						if ( t.getBlockHash () == null && spend != null )
						{
							modified = true;
							sending.add (t.getHash (), ix, o);
							log.trace ("Sending " + ix + " " + o.getValue ());
						}
					}
					++ix;
				}
				if ( modified )
				{
					relevantTransaction.put (t.getHash (), t);
				}
			}
			else if ( t.isDoubleSpend () )
			{
				for ( long ix = 0; ix < t.getOutputs ().size (); ++ix )
				{
					TransactionOutput out = null;
					out = confirmed.remove (t.getHash (), ix);
					if ( out == null )
					{
						out = change.remove (t.getHash (), ix);
					}
					if ( out == null )
					{
						out = receiving.remove (t.getHash (), ix);
					}
					if ( out == null )
					{
						out = sending.remove (t.getHash (), ix);
					}
					if ( out != null )
					{
						log.trace ("Remove double spend from account " + name + " tx: " + t.getHash () + " ix " + ix + " " + out.getValue ());
					}
					modified |= out != null;
				}
				processedTransaction.remove (t.getHash ());
				relevantTransaction.remove (t.getHash ());
			}
			return modified;
		}
	}

	@Override
	public List<Transaction> getTransactions ()
	{
		ArrayList<Transaction> tl = new ArrayList<Transaction> ();
		tl.addAll (relevantTransaction.values ());
		Collections.sort (tl, new Comparator<Transaction> ()
		{
			@Override
			public int compare (Transaction a, Transaction b)
			{
				for ( TransactionInput in : b.getInputs () )
				{
					if ( in.getSourceHash ().equals (a.getHash ()) )
					{
						return -1;
					}
				}
				for ( TransactionInput in : a.getInputs () )
				{
					if ( in.getSourceHash ().equals (b.getHash ()) )
					{
						return 1;
					}
				}
				return 0;
			}
		});
		return tl;
	}

	@Override
	public void process (Transaction t)
	{
		if ( updateWithTransaction (t) )
		{
			notifyListener (t);
		}
	}

	@Override
	public Transaction pay (byte[] receiver, long amount, long fee) throws ValidationException, BCSAPIException
	{
		synchronized ( confirmed )
		{
			List<TransactionSource> sources = confirmed.getSufficientSources (amount, fee, null);
			if ( sources == null )
			{
				throw new ValidationException ("Insufficient funds to pay " + (amount + fee));
			}
			List<TransactionSink> sinks = new ArrayList<TransactionSink> ();

			long in = 0;
			for ( TransactionSource o : sources )
			{
				in += o.getOutput ().getValue ();
			}
			TransactionSink target = new TransactionSink (receiver, amount);
			if ( (in - amount - fee) > 0 )
			{
				TransactionSink change = new TransactionSink (getNextKey ().getAddress (), in - amount - fee);
				if ( new SecureRandom ().nextBoolean () )
				{
					sinks.add (target);
					sinks.add (change);
				}
				else
				{
					sinks.add (change);
					sinks.add (target);
				}
			}
			else
			{
				sinks.add (target);
			}
			return Transaction.createSpend (this, sources, sinks, fee);
		}
	}

	@Override
	public Transaction split (long[] amounts, long fee) throws ValidationException, BCSAPIException
	{
		synchronized ( confirmed )
		{
			List<TransactionSink> sinks = new ArrayList<TransactionSink> ();

			long amount = 0;
			for ( long a : amounts )
			{
				amount += a;
			}
			List<TransactionSource> sources = confirmed.getSufficientSources (amount, fee, null);
			if ( sources == null )
			{
				throw new ValidationException ("Insufficient funds to pay " + (amount + fee));
			}
			long in = 0;
			for ( TransactionSource o : sources )
			{
				in += o.getOutput ().getValue ();
			}
			for ( long a : amounts )
			{
				TransactionSink target = new TransactionSink (getNextKey ().getAddress (), a);
				sinks.add (target);
			}
			if ( (in - amount - fee) > 0 )
			{
				TransactionSink change = new TransactionSink (getNextKey ().getAddress (), in - amount - fee);
				sinks.add (change);
			}
			Collections.shuffle (sinks);
			return Transaction.createSpend (this, sources, sinks, fee);
		}
	}

	@Override
	public long getBalance ()
	{
		synchronized ( confirmed )
		{
			return confirmed.getTotal () + change.getTotal () + receiving.getTotal ();
		}
	}

	@Override
	public long getSettled ()
	{
		synchronized ( confirmed )
		{
			return confirmed.getTotal ();
		}
	}

	@Override
	public long getSending ()
	{
		synchronized ( confirmed )
		{
			return sending.getTotal ();
		}
	}

	@Override
	public long getReceiving ()
	{
		synchronized ( confirmed )
		{
			return receiving.getTotal ();
		}
	}

	@Override
	public long getChange ()
	{
		synchronized ( confirmed )
		{
			return change.getTotal ();
		}
	}

	@Override
	public void addAccountListener (AccountListener listener)
	{
		accountListener.add (listener);
	}

	@Override
	public void removeAccountListener (AccountListener listener)
	{
		accountListener.remove (listener);
	}

	private void notifyListener (Transaction t)
	{
		synchronized ( accountListener )
		{
			for ( AccountListener l : accountListener )
			{
				try
				{
					l.accountChanged (this, t);
				}
				catch ( Exception e )
				{
				}
			}
		}
	}

	@Override
	public ExtendedKey getMasterKey ()
	{
		return extended;
	}
}
