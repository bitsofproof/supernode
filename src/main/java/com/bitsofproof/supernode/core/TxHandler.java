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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxIn;
import com.bitsofproof.supernode.model.TxOut;

public class TxHandler implements TrunkListener
{
	private static final Logger log = LoggerFactory.getLogger (TxHandler.class);

	private final BitcoinNetwork network;

	private final Set<String> heard = Collections.synchronizedSet (new HashSet<String> ());
	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());
	private TxOutCache availableOutput = null;
	private PlatformTransactionManager transactionManager;

	private final List<TransactionListener> transactionListener = new ArrayList<TransactionListener> ();

	public void addTransactionListener (TransactionListener listener)
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
					synchronized ( heard )
					{
						if ( !heard.contains (hash) )
						{
							heard.add (hash);
							log.trace ("heard about new transaction " + hash + " from " + peer.getAddress ());
							get.getTransactions ().add (h);
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
				validateCacheAndSend (txm.getTx (), peer);
			}
		});
	}

	public void validateCacheAndSend (final Tx t, final BitcoinPeer peer)
	{
		network.getStore ().runInCacheContext (new BlockStore.CacheContextRunnable ()
		{
			@Override
			public void run (TxOutCache cache)
			{
				synchronized ( unconfirmed )
				{
					if ( !unconfirmed.containsKey (t.getHash ()) )
					{
						new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
						{
							@Override
							protected void doInTransactionWithoutResult (TransactionStatus status)
							{
								status.setRollbackOnly ();

								try
								{
									boolean relay = network.getStore ().validateTransaction (t, availableOutput);
									cacheTransaction (t);
									if ( relay )
									{
										sendTransaction (t, peer);
									}
									else
									{
										log.trace ("Not relaying transaction " + t.getHash ());
									}
									notifyListener (t);
								}
								catch ( ValidationException e )
								{
									log.trace ("Rejeting transaction " + t.getHash () + " from " + peer.getAddress ());
								}
							}
						});
					}
				}
			}
		});
	}

	public Tx getTransaction (String hash)
	{
		return unconfirmed.get (hash);
	}

	private void cacheTransaction (Tx tx)
	{
		log.trace ("Caching unconfirmed transaction " + tx.getHash ());
		unconfirmed.put (tx.getHash (), tx);

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
				InvMessage tm = (InvMessage) p.createMessage ("inv");
				tm.getTransactionHashes ().add (new Hash (tx.getHash ()).toByteArray ());
				p.send (tm);
			}
		}
		log.info ("sent validated transaction to peers " + tx.getHash ());
	}

	private void notifyListener (Tx tx)
	{
		for ( TransactionListener l : transactionListener )
		{
			// This further extends transaction and cache context
			l.onTransaction (tx);
		}
	}

	@Override
	public void trunkUpdate (final List<Blk> removedBlocks, final List<Blk> addedBlocks)
	{
		// this is already running in cache and transaction context
		List<String> dropped = new ArrayList<String> ();

		synchronized ( unconfirmed )
		{
			for ( Blk blk : removedBlocks )
			{
				for ( Tx tx : blk.getTransactions () )
				{
					if ( !unconfirmed.containsKey (tx.getHash ()) )
					{
						cacheTransaction (tx.flatCopy ());
						dropped.add (tx.getHash ());
					}
				}
			}
			for ( Blk blk : addedBlocks )
			{
				for ( Tx tx : blk.getTransactions () )
				{
					if ( unconfirmed.containsKey (tx.getHash ()) )
					{
						unconfirmed.remove (tx.getHash ());
						for ( TxOut o : tx.getOutputs () )
						{
							availableOutput.remove (o.getTxHash (), o.getIx ());
						}
					}
				}
			}
			for ( String o : dropped )
			{
				Tx tx = unconfirmed.get (o);
				if ( tx != null )
				{
					sendTransaction (tx, null);
					notifyListener (tx);
				}
			}
		}
	}
}
