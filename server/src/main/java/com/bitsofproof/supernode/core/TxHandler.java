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
package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ByteVector;
import com.bitsofproof.supernode.common.Hash;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.core.BlockStore.TransactionProcessor;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.MempoolMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class TxHandler implements TrunkListener
{
	private static final Logger log = LoggerFactory.getLogger (TxHandler.class);

	private final BitcoinNetwork network;

	private final Set<String> heard = new HashSet<String> ();
	private final Map<String, Tx> unconfirmed = new HashMap<String, Tx> ();
	private final Set<String> own = new HashSet<String> ();
	private TxOutCache availableOutput = null;
	private PlatformTransactionManager transactionManager;

	private final List<TxListener> transactionListener = new ArrayList<TxListener> ();

	public void addTransactionListener (TxListener listener)
	{
		transactionListener.add (listener);
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public TxHandler (final BitcoinNetwork network)
	{
		this.network = network;
		final BlockStore store = network.getStore ();

		network.scheduleJobWithFixedDelay (new Runnable ()
		{
			@Override
			public void run ()
			{
				// give retransmits of previously failed tx a chance
				synchronized ( heard )
				{
					heard.clear ();
				}
				// re-transmit own until not in a block
				synchronized ( own )
				{
					if ( !own.isEmpty () )
					{
						for ( BitcoinPeer peer : network.getConnectPeers () )
						{
							InvMessage tm = (InvMessage) peer.createMessage ("inv");
							for ( String t : own )
							{
								log.debug ("Re-broadcast " + t);
								tm.getTransactionHashes ().add (new Hash (t).toByteArray ());
							}
							peer.send (tm);
						}
					}
				}
			}
		}, 10, 10, TimeUnit.MINUTES);

		store.addTrunkListener (this);
		network.getStore ().runInCacheContext (new BlockStore.CacheContextRunnable ()
		{
			@Override
			public void run (TxOutCache cache)
			{
				availableOutput = new ImplementTxOutCacheDelta (cache);
			}
		});

		network.addListener ("inv", new BitcoinMessageListener<InvMessage> ()
		{
			@Override
			public void process (InvMessage im, BitcoinPeer peer)
			{
				GetDataMessage get = (GetDataMessage) peer.createMessage ("getdata");
				for ( byte[] h : im.getTransactionHashes () )
				{
					String hash = new Hash (h).toString ();
					synchronized ( unconfirmed )
					{
						synchronized ( heard )
						{
							if ( !heard.contains (hash) )
							{
								heard.add (hash);
								if ( !unconfirmed.containsKey (hash) )
								{
									log.trace ("heard about new transaction " + hash + " from " + peer.getAddress ());
									get.getTransactions ().add (h);
								}
							}
						}
					}
				}
				if ( get.getTransactions ().size () > 0 )
				{
					log.trace ("asking for transaction details from " + peer.getAddress ());
					peer.send (get);
				}
			}
		});
		network.addListener ("tx", new BitcoinMessageListener<TxMessage> ()
		{
			@Override
			public void process (final TxMessage txm, final BitcoinPeer peer)
			{
				log.trace ("received transaction details for " + txm.getTx ().getHash () + " from " + peer.getAddress ());
				try
				{
					validateCacheAndSend (txm.getTx (), peer);
				}
				catch ( ValidationException e )
				{
				}
			}
		});
		network.addListener ("mempool", new BitcoinMessageListener<MempoolMessage> ()
		{
			@Override
			public void process (final MempoolMessage m, final BitcoinPeer peer)
			{
				log.trace ("received mempool request from " + peer.getAddress ());
				InvMessage tm = (InvMessage) peer.createMessage ("inv");
				synchronized ( unconfirmed )
				{
					for ( Tx tx : unconfirmed.values () )
					{
						tm.getTransactionHashes ().add (new Hash (tx.getHash ()).toByteArray ());
					}
				}
				peer.send (tm);
				log.debug ("sent mempool to " + peer.getAddress ());
			}
		});

	}

	public void validateCacheAndSend (final Tx t, final BitcoinPeer peer) throws ValidationException
	{
		ValidationException exception = network.getStore ().runInCacheContext (new BlockStore.CacheContextRunnable ()
		{
			@Override
			public void run (TxOutCache cache) throws ValidationException
			{
				synchronized ( unconfirmed )
				{
					if ( !unconfirmed.containsKey (t.getHash ()) )
					{
						ValidationException exception = new TransactionTemplate (transactionManager).execute (new TransactionCallback<ValidationException> ()
						{
							@Override
							public ValidationException doInTransaction (TransactionStatus status)
							{
								status.setRollbackOnly ();

								try
								{
									network.getStore ().validateTransaction (t, availableOutput);
									sendTransaction (t, peer);
									cacheTransaction (t);
									notifyListener (t);
									if ( peer == null )
									{
										synchronized ( own )
										{
											own.add (t.getHash ());
										}
									}
									return null;
								}
								catch ( ValidationException e )
								{
									return e;
								}
							}
						});
						if ( exception != null )
						{
							throw exception;
						}
					}
				}
			}
		});
		if ( exception != null )
		{
			log.debug ("REJECTING transaction " + t.getHash ());
			throw exception;
		}
	}

	public Tx getTransaction (String hash)
	{
		synchronized ( unconfirmed )
		{
			return unconfirmed.get (hash);
		}
	}

	private void cacheTransaction (Tx tx)
	{
		log.trace ("Caching unconfirmed transaction " + tx.getHash ());
		synchronized ( unconfirmed )
		{
			unconfirmed.put (tx.getHash (), tx);
		}

		for ( TxOut out : tx.getOutputs () )
		{
			availableOutput.add (out);
		}

		for ( TxIn in : tx.getInputs () )
		{
			availableOutput.remove (in.getSourceHash (), in.getIx ());
		}
	}

	private void sendTransaction (Tx tx, BitcoinPeer peer)
	{
		for ( BitcoinPeer p : network.getConnectPeers () )
		{
			if ( p != peer )
			{
				if ( peer == null || peer.isRelay () || peer.getFilter () == null || tx.passesFilter (peer.getFilter ()) )
				{
					InvMessage tm = (InvMessage) p.createMessage ("inv");
					tm.getTransactionHashes ().add (new Hash (tx.getHash ()).toByteArray ());
					p.send (tm);
				}
			}
		}
		log.debug ("relaying transaction " + tx.getHash ());
	}

	private void notifyListener (Tx tx)
	{
		for ( TxListener l : transactionListener )
		{
			// This further extends transaction and cache context
			l.process (tx);
		}
	}

	public void scanUnconfirmedPool (Set<ByteVector> matchSet, UpdateMode update, TransactionProcessor processor)
	{
		synchronized ( unconfirmed )
		{
			for ( Tx t : unconfirmed.values () )
			{
				if ( t.matches (matchSet, update) )
				{
					processor.process (t);
				}
			}
		}
	}

	@Override
	public void trunkUpdate (final List<Blk> removedBlocks, final List<Blk> addedBlocks)
	{
		try
		{
			// this is already running in cache and transaction context
			synchronized ( unconfirmed )
			{
				for ( Blk blk : addedBlocks )
				{
					for ( Tx tx : blk.getTransactions () )
					{
						if ( unconfirmed.containsKey (tx.getHash ()) )
						{
							unconfirmed.remove (tx.getHash ());
							synchronized ( own )
							{
								own.remove (tx.getHash ());
							}
							for ( TxOut o : tx.getOutputs () )
							{
								availableOutput.remove (o.getTxHash (), o.getIx ());
							}
						}
					}
				}
				Iterator<Tx> txi = unconfirmed.values ().iterator ();
				while ( txi.hasNext () )
				{
					Tx tx = txi.next ();
					try
					{
						network.getStore ().resolveTransactionInputs (tx, availableOutput);
					}
					catch ( ValidationException e )
					{
						for ( TxOut o : tx.getOutputs () )
						{
							availableOutput.remove (o.getTxHash (), o.getIx ());
						}
						synchronized ( own )
						{
							own.remove (tx.getHash ());
						}
						txi.remove ();
					}
				}
			}
		}
		catch ( Exception e )
		{
			log.error ("Error broadcasting trunk update");
		}
	}
}
