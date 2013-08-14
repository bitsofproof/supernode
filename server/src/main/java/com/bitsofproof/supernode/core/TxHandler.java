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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;

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

	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());
	private ImplementTxOutCacheDelta availableOutput = null;
	private PlatformTransactionManager transactionManager;

	private final Set<Tx> dependencyOrderedSet = new TreeSet<Tx> (new Comparator<Tx> ()
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
	});

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
					if ( !unconfirmed.containsKey (hash) )
					{
						log.trace ("heard about transaction " + hash + " from " + peer.getAddress ());
						get.getTransactions ().add (h);
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

	private final Semaphore serializedValidation = new Semaphore (1);

	public void validateCacheAndSend (final Tx t, final BitcoinPeer peer) throws ValidationException
	{
		try
		{
			serializedValidation.acquireUninterruptibly ();

			ValidationException exception = network.getStore ().runInCacheContext (new BlockStore.CacheContextRunnable ()
			{
				@Override
				public void run (TxOutCache cache) throws ValidationException
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
									if ( network.getStore ().getTransaction (t.getHash ()) == null )
									{
										network.getStore ().validateTransaction (t, availableOutput);
										cacheTransaction (t);
										sendTransaction (t, peer);
										notifyListener (t, false);
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
			});
			if ( exception != null )
			{
				log.debug ("REJECTING transaction " + t.getHash ());
				throw exception;
			}
		}
		finally
		{
			serializedValidation.release ();
		}
	}

	public Tx getTransaction (String hash)
	{
		return unconfirmed.get (hash);
	}

	private void cacheTransaction (Tx tx)
	{
		synchronized ( unconfirmed )
		{
			unconfirmed.put (tx.getHash (), tx);
			log.trace ("Caching unconfirmed transaction " + tx.getHash () + " pool size: " + unconfirmed.size ());

			for ( TxOut out : tx.getOutputs () )
			{
				availableOutput.add (out);
			}
			for ( TxIn in : tx.getInputs () )
			{
				availableOutput.remove (in.getSourceHash (), in.getIx ());
			}

			dependencyOrderedSet.add (tx);
		}
	}

	private void sendTransaction (Tx tx, BitcoinPeer peer)
	{
		for ( BitcoinPeer p : network.getConnectPeers () )
		{
			if ( p != peer )
			{
				if ( p.isRelay () || p.getFilter () == null || tx.passesFilter (p.getFilter ()) )
				{
					InvMessage tm = (InvMessage) p.createMessage ("inv");
					tm.getTransactionHashes ().add (new Hash (tx.getHash ()).toByteArray ());
					p.send (tm);
				}
			}
		}
		log.debug ("relaying transaction " + tx.getHash ());
	}

	private void notifyListener (Tx tx, boolean doubleSpend)
	{
		for ( TxListener l : transactionListener )
		{
			// This further extends transaction and cache context
			l.process (tx, doubleSpend);
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
				List<Tx> firstSeenInBlock = new ArrayList<Tx> ();

				for ( Blk blk : removedBlocks )
				{
					boolean coinbase = true;
					for ( Tx tx : blk.getTransactions () )
					{
						if ( coinbase )
						{
							coinbase = false;
							continue;
						}
						unconfirmed.put (tx.getHash (), tx);
						dependencyOrderedSet.add (tx);
					}
				}
				for ( Blk blk : addedBlocks )
				{
					for ( Tx tx : blk.getTransactions () )
					{
						tx.setBlockHash (blk.getHash ());
						if ( unconfirmed.containsKey (tx.getHash ()) )
						{
							unconfirmed.remove (tx.getHash ());
							dependencyOrderedSet.remove (tx);
							notifyListener (tx, false);
						}
						else
						{
							firstSeenInBlock.add (tx);
						}
					}
				}

				availableOutput.reset ();

				Iterator<Tx> txi = dependencyOrderedSet.iterator ();
				while ( txi.hasNext () )
				{
					Tx tx = txi.next ();
					try
					{
						network.getStore ().resolveTransactionInputs (tx, availableOutput);

						for ( TxOut out : tx.getOutputs () )
						{
							availableOutput.add (out);
						}
						for ( TxIn in : tx.getInputs () )
						{
							availableOutput.remove (in.getSourceHash (), in.getIx ());
						}
					}
					catch ( ValidationException e )
					{
						unconfirmed.remove (tx.getHash ());
						txi.remove ();
						notifyListener (tx, true);
					}
				}
				availableOutput.resetUse ();
				for ( Tx tx : firstSeenInBlock )
				{
					notifyListener (tx, false);
				}
			}
		}
		catch ( Exception e )
		{
			log.error ("Error broadcasting trunk update", e);
		}
	}
}
