package com.bitsofproof.supernode.api;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Transaction.TransactionSink;
import com.bitsofproof.supernode.api.Transaction.TransactionSource;

public class AccountManager implements WalletListener, TransactionListener, TrunkListener
{
	private static final Logger log = LoggerFactory.getLogger (ClientBusAdaptor.class);

	private class UTXO
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

		private TransactionOutput get (String tx, Long ix)
		{
			HashMap<Long, TransactionOutput> outs = utxo.get (tx);
			if ( outs != null )
			{
				return outs.get (ix);
			}
			return null;
		}

		private void remove (String tx, Long ix)
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

		private void remove (TransactionOutput out)
		{
			remove (out.getTransactionHash (), out.getSelfIx ());
		}

		private Collection<String> getTransactionHashes ()
		{
			return utxo.keySet ();
		}

		private List<TransactionOutput> getSufficientSources (long value)
		{
			List<TransactionOutput> result = new ArrayList<TransactionOutput> ();
			long sum = 0;
			for ( HashMap<Long, TransactionOutput> outs : utxo.values () )
			{
				for ( TransactionOutput o : outs.values () )
				{
					if ( ScriptFormat.isPayToAddress (o.getScript ()) && o.getVotes () == 1 && o.getAddresses ().size () == 1 )
					{
						if ( wallet.getKeyForAddress (o.getAddresses ().get (0)) != null
								&& wallet.getKeyForAddress (o.getAddresses ().get (0)).getPrivate () != null )
						{
							sum += o.getValue ();
							result.add (o);
						}
					}
					if ( sum >= value )
					{
						return result;
					}
				}
			}
			return null;
		}

		private List<TransactionOutput> getAddressSources (String address)
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

	private BCSAPI api;
	private final UTXO utxo = new UTXO ();
	private long balance = 0;
	private Wallet wallet;
	private final List<Posting> postings = new ArrayList<Posting> ();
	private final Set<String> walletAddresses = new HashSet<String> ();
	private final HashMap<String, Integer> confirmations = new HashMap<String, Integer> ();
	private final List<AccountListener> accountListener = new ArrayList<AccountListener> ();
	private final Semaphore hasUpdates = new Semaphore (0);

	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	public void track (Wallet wallet)
	{
		try
		{
			this.wallet = wallet;
			walletAddresses.addAll (wallet.getAddresses ());
			trackAddresses (walletAddresses);
			wallet.addListener (this);
			api.registerTrunkListener (this);

			Thread notifier = new Thread (new Runnable ()
			{
				@Override
				public void run ()
				{
					while ( true )
					{
						hasUpdates.acquireUninterruptibly ();
						notifyListener ();
					}
				}
			});
			notifier.setDaemon (true);
			notifier.setName ("AccountTrackerThread");
			notifier.start ();
		}
		catch ( ValidationException e )
		{
			log.error ("Error extracting wallet adresses", e);
		}
		catch ( BCSAPIException e )
		{
			log.error ("Error talking to server", e);
		}

	}

	private void trackAddresses (Collection<String> addresses) throws BCSAPIException
	{
		AccountStatement s = api.getAccountStatement (addresses, 0);
		if ( s != null )
		{
			if ( s.getOpening () != null )
			{
				for ( TransactionOutput o : s.getOpening () )
				{
					balance += o.getValue ();
					utxo.add (o);
				}
			}
			if ( s.getPosting () != null )
			{
				postings.addAll (s.getPosting ());
				for ( Posting p : s.getPosting () )
				{
					if ( p.getSpent () == null )
					{
						balance += p.getOutput ().getValue ();
						utxo.add (p.getOutput ());
					}
					else
					{
						balance -= p.getOutput ().getValue ();
						utxo.remove (p.getOutput ());
					}
				}
			}
			if ( s.getUnconfirmedReceive () != null )
			{
				for ( Transaction t : s.getUnconfirmedReceive () )
				{
					received (t);
				}
			}
			if ( s.getUnconfirmedSpend () != null )
			{
				for ( Transaction t : s.getUnconfirmedReceive () )
				{
					spent (t);
				}
			}
			api.registerReceiveListener (addresses, this);
			api.registerSpendListener (utxo.getTransactionHashes (), this);
		}
	}

	public synchronized void pay (String receiver, long amount, long fee) throws ValidationException, BCSAPIException
	{
		List<TransactionSource> sources = new ArrayList<TransactionSource> ();
		List<TransactionSink> sinks = new ArrayList<TransactionSink> ();

		List<TransactionOutput> sufficient = utxo.getSufficientSources (amount + fee);
		if ( sufficient == null )
		{
			throw new ValidationException ("Insufficient funds to pay " + (amount + fee));
		}
		long in = 0;
		for ( TransactionOutput o : sufficient )
		{
			sources.add (new TransactionSource (o, wallet.getKeyForAddress (o.getAddresses ().get (0))));
			in += o.getValue ();
		}
		TransactionSink target = new TransactionSink (AddressConverter.fromSatoshiStyle (receiver, wallet.getAddressFlag ()), amount);
		TransactionSink change = new TransactionSink (wallet.generateNextKey ().getKey ().getAddress (), in - amount - fee);
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
		log.debug ("Paying " + in + "(+" + fee + ") to " + receiver);
		Transaction transaction = Transaction.createSpend (sources, sinks, fee);
		api.sendTransaction (transaction);
	}

	public synchronized void importKey (String serialized, String passpharse) throws ValidationException
	{
		KeyFormatter formatter = new KeyFormatter (passpharse, wallet.getAddressFlag ());
		Key key = formatter.parseSerializedKey (serialized);
		wallet.importKey (key);
	}

	public synchronized void cashIn (String serialized, String passpharse, long fee) throws ValidationException, BCSAPIException
	{
		KeyFormatter formatter = new KeyFormatter (passpharse, wallet.getAddressFlag ());
		Key key = formatter.parseSerializedKey (serialized);
		wallet.importKey (key);
		String inAddress = AddressConverter.toSatoshiStyle (key.getAddress (), wallet.getAddressFlag ());
		List<TransactionOutput> available = utxo.getAddressSources (inAddress);
		if ( available.size () > 0 )
		{
			long sum = 0;
			List<TransactionSource> sources = new ArrayList<TransactionSource> ();
			for ( TransactionOutput o : available )
			{
				sources.add (new TransactionSource (o, key));
				sum += o.getValue ();
			}
			List<TransactionSink> sinks = new ArrayList<TransactionSink> ();
			sinks.add (new TransactionSink (wallet.generateNextKey ().getKey ().getAddress (), sum - fee));
			log.debug ("Caching in " + sum + "(-" + fee + ") from " + inAddress);
			Transaction transaction = Transaction.createSpend (sources, sinks, fee);
			api.sendTransaction (transaction);
		}
	}

	@Override
	public synchronized void notifyNewKey (String address, Key key)
	{
		walletAddresses.add (address);
		ArrayList<String> a = new ArrayList<String> ();
		a.add (address);
		try
		{
			trackAddresses (a);
		}
		catch ( BCSAPIException e )
		{
			log.error ("Can not track new key " + address, e);
		}
		hasUpdates.release ();
	}

	@Override
	public void validated (Transaction t)
	{
	}

	@Override
	public synchronized void spent (Transaction t)
	{
		confirmations.put (t.getHash (), 0);
		for ( TransactionInput input : t.getInputs () )
		{
			TransactionOutput output = utxo.get (input.getSourceHash (), input.getIx ());
			balance -= output.getValue ();
			Posting p = new Posting ();
			p.setOutput (output);
			p.setTimestamp (System.currentTimeMillis () / 1000);
			p.setSpent (t.getHash ());
			postings.add (p);
			utxo.remove (input.getSourceHash (), input.getIx ());
		}
		hasUpdates.release ();
	}

	@Override
	public synchronized void received (Transaction t)
	{
		confirmations.put (t.getHash (), 0);
		for ( TransactionOutput output : t.getOutputs () )
		{
			for ( String address : output.getAddresses () )
			{
				if ( walletAddresses.contains (address) )
				{
					Posting p = new Posting ();
					p.setOutput (output);
					p.setTimestamp (System.currentTimeMillis () / 1000);
					postings.add (p);
					utxo.add (output);
					balance += output.getValue ();
				}
			}
		}
		ArrayList<String> txs = new ArrayList<String> ();
		txs.add (t.getHash ());
		try
		{
			api.registerSpendListener (txs, this);
		}
		catch ( BCSAPIException e )
		{
		}
		hasUpdates.release ();
	}

	@Override
	public synchronized void confirmed (String hash, int n)
	{
		confirmations.put (hash, n);
		hasUpdates.release ();
	}

	public synchronized long getBalance ()
	{
		return balance;
	}

	public synchronized List<Posting> getPostings ()
	{
		return postings;
	}

	public synchronized void addAccountListener (AccountListener listener)
	{
		accountListener.add (listener);
	}

	private void notifyListener ()
	{
		for ( AccountListener l : accountListener )
		{
			try
			{
				l.accountChanged (this);
			}
			catch ( Exception e )
			{
			}
		}
	}

	@Override
	public synchronized void trunkUpdate (List<Block> removed, List<Block> added)
	{
		try
		{
			if ( removed != null )
			{
				for ( Block b : removed )
				{
					for ( Posting p : postings )
					{
						if ( p.getBlock ().equals (b.getHash ()) )
						{
							if ( p.getSpent () != null )
							{
								utxo.add (p.getOutput ());
							}
							else
							{
								utxo.remove (p.getOutput ());
							}
							p.setBlock (null);
						}
					}
				}
			}
			if ( added != null )
			{
				for ( Block b : added )
				{
					for ( Transaction t : b.getTransactions () )
					{
						for ( TransactionOutput o : t.getOutputs () )
						{
							o.parseOwners (wallet.getAddressFlag (), wallet.getP2SHAddressFlag ());
							if ( o.getVotes () == 1 && walletAddresses.contains (o.getAddresses ().get (0)) )
							{
								if ( utxo.get (t.getHash (), o.getSelfIx ()) == null )
								{
									boolean found = false;
									for ( Posting p : postings )
									{
										if ( p.getOutput ().getTransactionHash ().equals (t.getHash ()) && p.getOutput ().getSelfIx () == o.getSelfIx () )
										{
											found = true;
											p.setBlock (b.getHash ());
											p.setTimestamp (b.getCreateTime ());
											break;
										}
									}
									if ( !found )
									{
										Posting p = new Posting ();
										p.setBlock (b.getHash ());
										p.setOutput (o);
										p.setTimestamp (b.getCreateTime ());
										postings.add (p);
									}
									utxo.add (o);
									balance += o.getValue ();
								}
							}
						}
						for ( TransactionInput i : t.getInputs () )
						{
							if ( utxo.get (i.getSourceHash (), i.getIx ()) != null )
							{
								Transaction source = api.getTransaction (i.getSourceHash ());
								TransactionOutput o = source.getOutputs ().get ((int) i.getSelfIx ());
								boolean found = false;
								for ( Posting p : postings )
								{
									if ( p.getOutput ().getTransactionHash ().equals (i.getSourceHash ()) && p.getOutput ().getSelfIx () == i.getIx () )
									{
										found = true;
										p.setBlock (b.getHash ());
										p.setTimestamp (b.getCreateTime ());
										break;
									}
								}
								if ( !found )
								{
									Posting p = new Posting ();
									p.setBlock (b.getHash ());
									p.setOutput (o);
									p.setTimestamp (b.getCreateTime ());
									postings.add (p);
								}
								utxo.remove (o);
								balance -= o.getValue ();
							}
						}
					}
				}
			}
			hasUpdates.release ();
		}
		catch ( BCSAPIException e )
		{
			log.error ("Error processing trunk update ", e);
		}
	}
}
