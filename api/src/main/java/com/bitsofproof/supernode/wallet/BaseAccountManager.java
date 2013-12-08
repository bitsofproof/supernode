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

import java.io.PrintStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.Key;
import com.bitsofproof.supernode.common.ScriptFormat;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.common.WireFormat;

public abstract class BaseAccountManager implements AccountManager
{
	private static final Logger log = LoggerFactory.getLogger (BaseAccountManager.class);

	private static final long MINIMUM_FEE = 10000;
	private static final long MAXIMUM_FEE = 1000000;

	private UTXO confirmed = createConfirmedUTXO ();
	private UTXO change = createChangeUTXO ();
	private UTXO receiving = createReceivingUTXO ();
	private UTXO sending = createSendingUTXO ();

	private long created;

	@Override
	public long getCreated ()
	{
		return created;
	}

	public void setCreated (long created)
	{
		this.created = created;
	}

	private final List<AccountListener> accountListener = Collections.synchronizedList (new ArrayList<AccountListener> ());
	private final Map<String, Transaction> transactions = new HashMap<String, Transaction> ();

	protected UTXO createConfirmedUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createChangeUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createSendingUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected UTXO createReceivingUTXO ()
	{
		return new InMemoryUTXO ();
	}

	protected void reset ()
	{
		confirmed = createConfirmedUTXO ();
		change = createChangeUTXO ();
		receiving = createReceivingUTXO ();
		sending = createSendingUTXO ();
	}

	public boolean isOwnAddress (Address address)
	{
		return getKeyForAddress (address) != null;
	}

	protected static class TransactionSink
	{
		private final Address address;
		private final long value;

		public TransactionSink (Address address, long value)
		{
			super ();
			this.address = address;
			this.value = value;
		}

		public Address getAddress ()
		{
			return address;
		}

		public long getValue ()
		{
			return value;
		}
	}

	public static long estimateFee (Transaction t)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		t.toWire (writer);
		return Math.min (MAXIMUM_FEE, Math.max (MINIMUM_FEE, writer.toByteArray ().length / 1000 * MINIMUM_FEE));
	}

	protected Transaction createSpend (List<TransactionOutput> sources, List<TransactionSink> sinks, long fee) throws ValidationException
	{
		if ( fee < 0 || fee > MAXIMUM_FEE )
		{
			throw new ValidationException ("You unlikely want to do that");
		}
		Transaction transaction = new Transaction ();
		transaction.setInputs (new ArrayList<TransactionInput> ());
		transaction.setOutputs (new ArrayList<TransactionOutput> ());

		long sumOut = 0;
		for ( TransactionSink s : sinks )
		{
			TransactionOutput o = new TransactionOutput ();
			o.setValue (s.getValue ());
			sumOut += s.getValue ();
			o.setScript (s.getAddress ().getAddressScript ());

			transaction.getOutputs ().add (o);
		}

		long sumInput = 0;
		for ( TransactionOutput o : sources )
		{
			TransactionInput i = new TransactionInput ();
			i.setSourceHash (o.getTxHash ());
			i.setIx (o.getIx ());
			sumInput += o.getValue ();

			transaction.getInputs ().add (i);
		}
		if ( sumInput != (sumOut + fee) )
		{
			throw new ValidationException ("Sum of sinks (+fee) does not match sum of sources");
		}

		int j = 0;
		for ( TransactionOutput s : sources )
		{
			TransactionInput i = transaction.getInputs ().get (j);
			ScriptFormat.Writer sw = new ScriptFormat.Writer ();
			if ( ScriptFormat.isPayToAddress (s.getScript ()) )
			{
				Address address = s.getOutputAddress ();
				Key key = getKeyForAddress (address);
				if ( key == null )
				{
					throw new ValidationException ("Have no key to spend this output");
				}
				byte[] sig = key.sign (hashTransaction (transaction, j, ScriptFormat.SIGHASH_ALL, s.getScript ()));
				byte[] sigPlusType = new byte[sig.length + 1];
				System.arraycopy (sig, 0, sigPlusType, 0, sig.length);
				sigPlusType[sigPlusType.length - 1] = (byte) (ScriptFormat.SIGHASH_ALL & 0xff);
				sw.writeData (sigPlusType);
				sw.writeData (key.getPublic ());
			}
			else
			{
				spendNonAddressOutput (j, s, sw, transaction);
			}
			i.setScript (sw.toByteArray ());
			++j;
		}

		transaction.computeHash ();
		return transaction;
	}

	protected void spendNonAddressOutput (int ix, TransactionOutput source, ScriptFormat.Writer writer, Transaction transaction) throws ValidationException
	{
		throw new ValidationException ("Can not spend this output type");
	}

	public static byte[] hashTransaction (Transaction transaction, int inr, int hashType, byte[] script) throws ValidationException
	{
		Transaction copy = null;
		try
		{
			copy = transaction.clone ();
		}
		catch ( CloneNotSupportedException e1 )
		{
			return null;
		}

		// implicit SIGHASH_ALL
		int i = 0;
		for ( TransactionInput in : copy.getInputs () )
		{
			if ( i == inr )
			{
				in.setScript (script);
			}
			else
			{
				in.setScript (new byte[0]);
			}
			++i;
		}

		if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_NONE )
		{
			copy.getOutputs ().clear ();
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		else if ( (hashType & 0x1f) == ScriptFormat.SIGHASH_SINGLE )
		{
			int onr = inr;
			if ( onr >= copy.getOutputs ().size () )
			{
				// this is a Satoshi client bug.
				// This case should throw an error but it instead retuns 1 that is not checked and interpreted as below
				return ByteUtils.fromHex ("0100000000000000000000000000000000000000000000000000000000000000");
			}
			for ( i = copy.getOutputs ().size () - 1; i > onr; --i )
			{
				copy.getOutputs ().remove (i);
			}
			for ( i = 0; i < onr; ++i )
			{
				copy.getOutputs ().get (i).setScript (new byte[0]);
				copy.getOutputs ().get (i).setValue (-1L);
			}
			i = 0;
			for ( TransactionInput in : copy.getInputs () )
			{
				if ( i != inr )
				{
					in.setSequence (0);
				}
				++i;
			}
		}
		if ( (hashType & ScriptFormat.SIGHASH_ANYONECANPAY) != 0 )
		{
			List<TransactionInput> oneIn = new ArrayList<TransactionInput> ();
			oneIn.add (copy.getInputs ().get (inr));
			copy.setInputs (oneIn);
		}

		WireFormat.Writer writer = new WireFormat.Writer ();
		copy.toWire (writer);

		byte[] txwire = writer.toByteArray ();
		byte[] hash = null;
		try
		{
			MessageDigest a = MessageDigest.getInstance ("SHA-256");
			a.update (txwire);
			a.update (new byte[] { (byte) (hashType & 0xff), 0, 0, 0 });
			hash = a.digest (a.digest ());
		}
		catch ( NoSuchAlgorithmException e )
		{
		}
		return hash;
	}

	protected List<TransactionOutput> getSufficientSources (long amount, long fee, String color)
	{
		List<TransactionOutput> candidates = new ArrayList<TransactionOutput> ();
		candidates.addAll (confirmed.getUTXO ());
		// prefer confirmed
		Collections.sort (candidates, new Comparator<TransactionOutput> ()
		{
			// prefer aggregation of UTXO
			@Override
			public int compare (TransactionOutput o1, TransactionOutput o2)
			{
				return o1.getValue () < o2.getValue () ? -1 : o1.getValue () > o2.getValue () ? 1 : 0;
			}
		});
		List<TransactionOutput> changelist = new ArrayList<TransactionOutput> ();
		changelist.addAll (change.getUTXO ());
		// ... then change
		Collections.sort (changelist, new Comparator<TransactionOutput> ()
		{
			// prefer aggregation of UTXO
			@Override
			public int compare (TransactionOutput o1, TransactionOutput o2)
			{
				return o1.getValue () < o2.getValue () ? -1 : o1.getValue () > o2.getValue () ? 1 : 0;
			}
		});
		candidates.addAll (changelist);

		List<TransactionOutput> result = new ArrayList<TransactionOutput> ();
		long sum = 0;
		for ( TransactionOutput o : candidates )
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
		return null;
	}

	@Override
	public Address getNextAddress () throws ValidationException
	{
		return getNextKey ().getAddress ();
	}

	@Override
	public Transaction pay (Address receiver, long amount, long fee, boolean senderPaysFee) throws ValidationException
	{
		List<Address> a = new ArrayList<Address> ();
		a.add (receiver);
		List<Long> v = new ArrayList<Long> ();
		v.add (amount);
		return pay (a, v, fee, senderPaysFee);
	}

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts, long fee, boolean senderPaysFee) throws ValidationException
	{
		synchronized ( confirmed )
		{
			long amount = 0;
			for ( Long a : amounts )
			{
				amount += a;
			}
			log.trace ("pay " + amount + (senderPaysFee ? " + " + fee : ""));
			List<TransactionOutput> sources = getSufficientSources (amount, senderPaysFee ? fee : 0, null);
			if ( sources == null )
			{
				throw new ValidationException ("Insufficient funds to pay " + amount + (senderPaysFee ? " + " + fee : ""));
			}
			long in = 0;
			for ( TransactionOutput o : sources )
			{
				log.trace ("using input " + o.getTxHash () + "[" + o.getIx () + "] " + o.getValue ());
				in += o.getValue ();
			}
			List<TransactionSink> sinks = new ArrayList<TransactionSink> ();
			Iterator<Long> ai = amounts.iterator ();
			for ( Address r : receiver )
			{
				sinks.add (new TransactionSink (r, ai.next ()));
			}
			if ( !senderPaysFee )
			{
				TransactionSink last = sinks.get (sinks.size () - 1);
				sinks.set (sinks.size () - 1, new TransactionSink (last.getAddress (),
						Math.max (last.getValue () - fee, 0)));
			}
			if ( (in - amount) > (senderPaysFee ? fee : 0) )
			{
				TransactionSink change = new TransactionSink (getNextAddress (), in - amount - (senderPaysFee ? fee : 0));
				log.trace ("change to " + change.getAddress () + " " + change.getValue ());
				sinks.add (change);
			}
			Collections.shuffle (sinks);
			return createSpend (sources, sinks, fee);
		}
	}

	@Override
	public Transaction pay (Address receiver, long amount, boolean senderPaysFee) throws ValidationException
	{
		List<Address> a = new ArrayList<Address> ();
		a.add (receiver);
		List<Long> v = new ArrayList<Long> ();
		v.add (amount);
		return pay (a, v, senderPaysFee);
	}

	@Override
	public Transaction pay (List<Address> receiver, List<Long> amounts, boolean senderPaysFee) throws ValidationException
	{
		long fee = MINIMUM_FEE;
		long estimate = 0;
		Transaction t = null;

		do
		{
			fee = Math.max (fee, estimate);
			t = pay (receiver, amounts, fee, senderPaysFee);
			estimate = estimateFee (t);
			if ( fee < estimate )
			{
				log.trace ("The transaction requires more network fees. Reassembling.");
			}
		} while ( fee < estimate );

		return t;
	}

	public boolean updateWithTransaction (Transaction t)
	{
		synchronized ( confirmed )
		{
			boolean modified = false;
			if ( !t.isDoubleSpend () )
			{
				TransactionOutput spend = null;
				for ( TransactionInput i : t.getInputs () )
				{
					spend = confirmed.get (i.getSourceHash (), i.getIx ());
					if ( spend != null )
					{
						confirmed.remove (i.getSourceHash (), i.getIx ());
						log.trace ("Spend settled output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
					}
					else
					{
						spend = change.get (i.getSourceHash (), i.getIx ());
						if ( spend != null )
						{
							change.remove (i.getSourceHash (), i.getIx ());
							log.trace ("Spend change output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
						}
						else
						{
							spend = receiving.get (i.getSourceHash (), i.getIx ());
							if ( spend != null )
							{
								receiving.remove (i.getSourceHash (), i.getIx ());
								log.trace ("Spend receiving output " + i.getSourceHash () + " [" + i.getIx () + "] " + spend.getValue ());
							}
						}
					}
				}
				modified = spend != null;
				for ( TransactionOutput o : t.getOutputs () )
				{
					confirmed.remove (o.getTxHash (), o.getIx ());
					change.remove (o.getTxHash (), o.getIx ());
					receiving.remove (o.getTxHash (), o.getIx ());
					sending.remove (o.getTxHash (), o.getIx ());

					if ( isOwnAddress (o.getOutputAddress ()) )
					{
						modified = true;
						if ( t.getBlockHash () != null )
						{
							confirmed.add (o);
							log.trace ("Settled " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
						}
						else
						{
							if ( spend != null )
							{
								change.add (o);
								log.trace ("Change " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
										+ o.getValue ());
							}
							else
							{
								receiving.add (o);
								log.trace ("Receiving " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") "
										+ o.getValue ());
							}
						}
					}
					else
					{
						if ( t.getBlockHash () == null && spend != null )
						{
							modified = true;
							sending.add (o);
							log.trace ("Sending " + t.getHash () + " [" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
						}
					}
				}
				if ( modified )
				{
					transactions.put (t.getHash (), t);
				}
			}
			else
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
						log.trace ("Remove DS " + out.getTxHash () + " [" + out.getIx () + "] (" + out.getOutputAddress () + ")"
								+ out.getValue ());
					}
					modified |= out != null;
				}
				transactions.remove (t.getHash ());
			}
			return modified;
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
	public long getConfirmed ()
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
	public Collection<TransactionOutput> getConfirmedOutputs ()
	{
		return confirmed.getUTXO ();
	}

	@Override
	public Collection<TransactionOutput> getSendingOutputs ()
	{
		return sending.getUTXO ();
	}

	@Override
	public Collection<TransactionOutput> getReceivingOutputs ()
	{
		return receiving.getUTXO ();
	}

	@Override
	public Collection<TransactionOutput> getChangeOutputs ()
	{
		return change.getUTXO ();
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

	protected void notifyListener (Transaction t)
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
					log.error ("Uncaught exception in account listener", e);
				}
			}
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
	public List<Transaction> getTransactions ()
	{
		List<Transaction> tl = new ArrayList<Transaction> ();
		tl.addAll (transactions.values ());
		Collections.sort (tl, new Comparator<Transaction> ()
		{
			@Override
			public int compare (Transaction a, Transaction b)
			{
				for ( TransactionInput i : b.getInputs () )
				{
					if ( i.getSourceHash ().equals (a.getHash ()) )
					{
						return -1;
					}
				}
				for ( TransactionInput i : a.getInputs () )
				{
					if ( i.getSourceHash ().equals (b.getHash ()) )
					{
						return 1;
					}
				}
				return 0;
			}
		});

		return tl;
	}

	public void dumpOutputs (PrintStream print)
	{
		print.println ("Confirmed:");
		dumpUTXO (print, confirmed);
		print.println ("Receiving:");
		dumpUTXO (print, receiving);
		print.println ("Change:");
		dumpUTXO (print, change);
		print.println ("Sending:");
		dumpUTXO (print, sending);
	}

	public void dumpKeys (PrintStream print)
	{
		Set<Address> addresses = new HashSet<> ();
		for ( TransactionOutput o : confirmed.getUTXO () )
		{
			addresses.add (o.getOutputAddress ());
		}
		for ( TransactionOutput o : change.getUTXO () )
		{
			addresses.add (o.getOutputAddress ());
		}
		for ( TransactionOutput o : receiving.getUTXO () )
		{
			addresses.add (o.getOutputAddress ());
		}
		for ( Address a : addresses )
		{
			Key k = getKeyForAddress (a);
			if ( k != null )
			{
				print.println (ECKeyPair.serializeWIF (k) + " (" + a + ")");
			}
		}
	}

	private void dumpUTXO (PrintStream print, UTXO set)
	{
		for ( TransactionOutput o : set.getUTXO () )
		{
			print.println (o.getTxHash () + "[" + o.getIx () + "] (" + o.getOutputAddress () + ") " + o.getValue ());
		}
		print.println ();
	}
}
