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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.ChainParameter;
import com.bitsofproof.supernode.api.Difficulty;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionFactory;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class BlockTemplater implements TrunkListener, TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (BlockTemplater.class);

	private final List<TemplateListener> templateListener = new ArrayList<TemplateListener> ();

	private final Map<String, Tx> mineable = Collections.synchronizedMap (new HashMap<String, Tx> ());

	private Block template = null;

	private static final int MAX_BLOCK_SIZE = 1000000;
	private static final int MAX_BLOCK_SIGOPS = 20000;

	private static final long LOCKTIME_THRESHOLD = 500000000;

	private String coinbaseAddress;

	private final ChainParameter chain;
	private final BitcoinNetwork network;

	private String previousHash;
	private long nextDifficulty;
	private int nextHeight;

	public void setCoinbaseAddress (String coinbaseAddress)
	{
		this.coinbaseAddress = coinbaseAddress;
	}

	public BlockTemplater (BitcoinNetwork network, TxHandler txhandler)
	{
		this.network = network;
		BlockStore store = network.getStore ();
		chain = network.getChain ();
		store.addTrunkListener (this);
		txhandler.addTransactionListener (this);
		new ScheduledThreadPoolExecutor (1).scheduleAtFixedRate (new Runnable ()
		{
			@Override
			public void run ()
			{
				feedWorker ();
			}
		}, 10L, 1L, TimeUnit.SECONDS);
	}

	public void feedWorker ()
	{
		if ( template != null )
		{
			updateTemplate ();

			for ( TemplateListener listener : templateListener )
			{
				listener.workOn (template);
			}
			log.trace ("Sent new work...");
		}
	}

	private void updateTemplate ()
	{
		network.getStore ().runInCacheContext (new BlockStore.CacheContextRunnable ()
		{
			@Override
			public void run (TxOutCache cache)
			{
				try
				{
					log.trace ("Compiling new work...");

					TxOutCache availableOutput = new ImplementTxOutCacheDelta (cache);

					template.setVersion (2);
					template.setCreateTime (System.currentTimeMillis () / 1000);
					template.setDifficultyTarget (nextDifficulty);
					template.setNonce (0);
					template.setPreviousHash (previousHash);
					template.setTransactions (new ArrayList<Transaction> ());
					try
					{
						template.getTransactions ().add (TransactionFactory.createCoinbase (coinbaseAddress, nextHeight, chain));
					}
					catch ( ValidationException e )
					{
						log.error ("Can not create coinbase ", e);
					}

					List<Tx> dependencyOrder = new ArrayList<Tx> ();
					final Comparator<Tx> dependencyComparator = new Comparator<Tx> ()
					{
						@Override
						public int compare (Tx a, Tx b)
						{
							for ( TxIn in : b.getInputs () )
							{
								if ( in.getSourceHash ().equals (a.getHash ()) )
								{
									return -1;
								}
							}
							for ( TxIn in : a.getInputs () )
							{
								if ( in.getSourceHash ().equals (b.getHash ()) )
								{
									return 1;
								}
							}
							return 0;
						}
					};

					List<Tx> candidates = new ArrayList<Tx> ();

					synchronized ( mineable )
					{
						dependencyOrder.addAll (mineable.values ());
					}

					Collections.sort (dependencyOrder, dependencyComparator);

					final Map<String, Long> feesOffered = new HashMap<String, Long> ();

					for ( Tx tx : dependencyOrder )
					{
						try
						{
							network.getStore ().resolveTransactionInputs (tx, availableOutput);

							long fee = 0;
							for ( TxOut out : tx.getOutputs () )
							{
								availableOutput.add (out);

								fee -= out.getValue ();
							}
							for ( TxIn in : tx.getInputs () )
							{
								TxOut source = availableOutput.get (in.getSourceHash (), in.getIx ());

								availableOutput.remove (in.getSourceHash (), in.getIx ());

								fee += source.getValue ();
							}
							candidates.add (tx);
							feesOffered.put (tx.getHash (), fee);
						}
						catch ( ValidationException e )
						{
							mineable.remove (tx.getHash ());
						}
					}

					Collections.sort (candidates, new Comparator<Tx> ()
					{
						@Override
						public int compare (Tx a, Tx b)
						{
							int cmp = dependencyComparator.compare (a, b);
							if ( cmp == 0 )
							{
								return (int) (feesOffered.get (a.getHash ()).longValue () - feesOffered.get (b.getHash ()).longValue ());
							}
							return cmp;
						}
					});

					template.computeHash ();

					WireFormat.Writer writer = new WireFormat.Writer ();
					template.toWire (writer);
					int blockSize = writer.toByteArray ().length;
					int sigOpCount = 1;
					List<Transaction> finalists = new ArrayList<Transaction> ();

					long fee = 0;
					for ( Tx tx : candidates )
					{
						writer = new WireFormat.Writer ();
						tx.toWire (writer);
						blockSize += writer.toByteArray ().length;
						if ( blockSize > MAX_BLOCK_SIZE )
						{
							break;
						}
						for ( TxOut out : tx.getOutputs () )
						{
							sigOpCount += ScriptFormat.sigOpCount (out.getScript (), false);
						}
						if ( sigOpCount > MAX_BLOCK_SIGOPS )
						{
							break;
						}
						Transaction t = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
						t.computeHash ();
						finalists.add (t);
						fee += feesOffered.get (t.getHash ()).longValue ();
					}
					template.getTransactions ().get (0).getOutputs ().get (0)
							.setValue (template.getTransactions ().get (0).getOutputs ().get (0).getValue () + fee);
					template.getTransactions ().addAll (finalists);
					template.computeHash ();
				}
				catch ( Exception e )
				{
					log.error ("Could not create work", e);
				}
			}
		});

	}

	public void addTemplateListener (TemplateListener listener)
	{
		templateListener.add (listener);
	}

	@Override
	public void onTransaction (Tx tx)
	{
		mineable.put (tx.getHash (), tx);
	}

	@Override
	public void trunkUpdate (final List<Blk> shortened, final List<Blk> extended)
	{
		for ( Blk blk : shortened )
		{
			boolean coinbase = true;
			for ( Tx t : blk.getTransactions () )
			{
				if ( coinbase )
				{
					coinbase = false;
				}
				else
				{
					mineable.put (t.getHash (), t);
				}
			}
		}
		for ( Blk blk : extended )
		{
			previousHash = blk.getHash ();
			nextHeight = blk.getHeight () + 1;
			if ( nextHeight % chain.getDifficultyReviewBlocks () == 0 )
			{
				nextDifficulty =
						Difficulty.getNextTarget (network.getStore ().getPeriodLength (previousHash, chain.getDifficultyReviewBlocks ()),
								blk.getDifficultyTarget (), chain);
			}
			else
			{
				nextDifficulty = blk.getDifficultyTarget ();
			}
			boolean coinbase = true;
			for ( Tx t : blk.getTransactions () )
			{
				if ( coinbase )
				{
					coinbase = false;
				}
				else
				{
					mineable.remove (t.getHash ());
				}
			}
			template = new Block ();
		}
	}
}
