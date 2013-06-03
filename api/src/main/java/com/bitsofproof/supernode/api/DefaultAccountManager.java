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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Transaction.TransactionSink;
import com.bitsofproof.supernode.api.Transaction.TransactionSource;
import com.bitsofproof.supernode.common.BloomFilter;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;

class DefaultAccountManager implements TransactionListener, TrunkListener, AccountManager
{
	private static final Logger log = LoggerFactory.getLogger (DefaultAccountManager.class);

	private BCSAPI api;

	private final Wallet wallet;

	private final LocalUTXO confirmed = new LocalUTXO ();
	private final LocalUTXO change = new LocalUTXO ();
	private final LocalUTXO receiving = new LocalUTXO ();
	private final LocalUTXO sending = new LocalUTXO ();

	private long balance;

	private final List<AccountListener> accountListener = Collections.synchronizedList (new ArrayList<AccountListener> ());
	private final Map<String, Transaction> processedTransaction = new HashMap<String, Transaction> ();

	private final String name;
	private final ExtendedKey extended;
	private final Map<ByteVector, Key> keyForAddress = new HashMap<ByteVector, Key> ();
	private final BloomFilter filter = BloomFilter.createOptimalFilter (100, 1.0 / 1000000.0, UpdateMode.all);
	private final Set<ByteVector> addressSet = new HashSet<ByteVector> ();

	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	public DefaultAccountManager (Wallet wallet, String name, ExtendedKey extended, int nextSequence) throws ValidationException
	{
		this.wallet = wallet;
		this.name = name;
		this.extended = extended;
		for ( int i = 0; i < nextSequence; ++i )
		{
			Key key = extended.getKey (i);
			keyForAddress.put (new ByteVector (key.getAddress ()), key);
			filter.add (key.getAddress ());
			addressSet.add (new ByteVector (key.getAddress ()));
		}
	}

	public void registerFilter () throws BCSAPIException
	{
		api.registerTrunkListener (this);
		api.registerFilteredListener (filter, this);
	}

	@Override
	public int getNextSequence ()
	{
		return keyForAddress.size ();
	}

	@Override
	public String getName ()
	{
		return name;
	}

	@Override
	public BloomFilter getFilter ()
	{
		return filter;
	}

	@Override
	public Collection<byte[]> getAddresses ()
	{
		List<byte[]> addresses = new ArrayList<byte[]> ();
		for ( ByteVector v : keyForAddress.keySet () )
		{
			addresses.add (v.toByteArray ());
		}
		return addresses;
	}

	@Override
	public Key getKeyForAddress (byte[] address)
	{
		return keyForAddress.get (new ByteVector (address));
	}

	@Override
	public Key getNextKey () throws ValidationException
	{
		Key key = extended.getKey (keyForAddress.size ());
		keyForAddress.put (new ByteVector (key.getAddress ()), key);
		filter.add (key.getAddress ());
		addressSet.add (new ByteVector (key.getAddress ()));
		try
		{
			api.registerFilteredListener (filter, this);
		}
		catch ( BCSAPIException e )
		{
		}
		return key;
	}

	@Override
	public Key getKey (int ix) throws ValidationException
	{
		if ( ix >= keyForAddress.size () )
		{
			throw new ValidationException ("Use consequtive keys");
		}
		return extended.getKey (ix);
	}

	@Override
	public Collection<Transaction> getTransactions ()
	{
		return Collections.unmodifiableCollection (processedTransaction.values ());
	}

	@Override
	public Transaction getTransaction (String hash)
	{
		return processedTransaction.get (hash);
	}

	public boolean updateWithTransaction (Transaction t)
	{
		synchronized ( confirmed )
		{
			boolean modified = false;
			if ( !processedTransaction.containsKey (t.getHash ()) )
			{
				processedTransaction.put (t.getHash (), t);
				TransactionOutput spend = null;
				for ( TransactionInput i : t.getInputs () )
				{
					spend = confirmed.get (i.getSourceHash (), i.getIx ());
					if ( spend != null )
					{
						confirmed.remove (i.getSourceHash (), i.getIx ());
					}
					else
					{
						spend = change.get (i.getSourceHash (), i.getIx ());
						if ( spend != null )
						{
							change.remove (i.getSourceHash (), i.getIx ());
						}
						else
						{
							spend = receiving.get (i.getSourceHash (), i.getIx ());
							if ( spend != null )
							{
								receiving.remove (i.getSourceHash (), i.getIx ());
							}
						}
					}
				}
				modified = spend != null;
				long ix = 0;
				for ( TransactionOutput o : t.getOutputs () )
				{
					if ( addressSet.contains (new ByteVector (o.getOutputAddress ())) )
					{
						modified = true;
						if ( t.getBlockHash () != null )
						{
							confirmed.add (t.getHash (), ix, o);
						}
						else
						{
							if ( spend != null )
							{
								change.add (t.getHash (), ix, o);
							}
							else
							{
								receiving.add (t.getHash (), ix, o);
							}
						}
					}
					else
					{
						if ( t.getBlockHash () == null && spend != null )
						{
							modified = true;
							sending.add (t.getHash (), ix, o);
						}
					}
					++ix;
				}
			}
			if ( modified )
			{
				wallet.addTransaction (t);
				log.trace ("Updated account " + name + " with " + t.getHash () + " balance " + balance);
			}

			return modified;
		}
	}

	@Override
	public void trunkUpdate (List<Block> removed, List<Block> added)
	{
		List<Transaction> newTransactions = new ArrayList<Transaction> ();
		synchronized ( confirmed )
		{
			if ( added != null )
			{
				for ( Block b : added )
				{
					for ( Transaction t : b.getTransactions () )
					{
						if ( updateWithTransaction (t) )
						{
							t.setBlockHash (b.getHash ());
							newTransactions.add (t);
						}
					}
				}
			}
		}
		for ( Transaction t : newTransactions )
		{
			notifyListener (t);
		}
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
}
