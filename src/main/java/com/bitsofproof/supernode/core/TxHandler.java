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

	private PlatformTransactionManager transactionManager;

	private final Set<String> heard = Collections.synchronizedSet (new HashSet<String> ());
	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());
	private final TxOutCache availableOutput = new ImplementTxOutCache ();
	private final List<TransactionListener> transactionListener = new ArrayList<TransactionListener> ();

	public void addTransactionListener (TransactionListener listener)
	{
		transactionListener.add (listener);
	}

	public TxHandler (final BitcoinNetwork network)
	{
		this.network = network;
		final BlockStore store = network.getStore ();
		store.addTrunkListener (this);

		network.addListener ("inv", new BitcoinMessageListener<InvMessage> ()
		{
			@Override
			public void process (InvMessage im, BitcoinPeer peer)
			{
				GetDataMessage get = (GetDataMessage) peer.createMessage ("getdata");
				for ( byte[] h : im.getTransactionHashes () )
				{
					String hash = new Hash (h).toString ();
					if ( !heard.contains (hash) )
					{
						heard.add (hash);
						log.trace ("heard about new transaction " + hash + " from " + peer.getAddress ());
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
				if ( !unconfirmed.containsKey (txm.getTx ().getHash ()) )
				{
					try
					{
						boolean relay = store.validateTransaction (txm.getTx (), availableOutput);
						if ( cacheTransaction (txm.getTx ()) )
						{
							if ( relay )
							{
								sendTransaction (txm.getTx (), peer);
							}
							else
							{
								log.trace ("Not relaying transaction " + txm.getTx ().getHash ());
							}
						}
					}
					catch ( ValidationException e )
					{
						log.trace ("Rejeting transaction " + txm.getTx ().getHash () + " from " + peer.getAddress ());
					}
				}
			}
		});
	}

	public Tx getTransaction (String hash)
	{
		return unconfirmed.get (hash);
	}

	public boolean cacheTransaction (Tx tx)
	{
		log.trace ("Caching unconfirmed transaction " + tx.getHash ());
		synchronized ( unconfirmed )
		{
			if ( unconfirmed.containsKey (tx.getHash ()) )
			{
				return false;
			}

			unconfirmed.put (tx.getHash (), tx);

			for ( TxOut out : tx.getOutputs () )
			{
				availableOutput.add (out);
			}

			for ( TxIn in : tx.getInputs () )
			{
				availableOutput.remove (in.getSourceHash (), in.getIx ());
			}
			return true;
		}
	}

	public void sendTransaction (Tx tx, BitcoinPeer peer)
	{
		for ( TransactionListener l : transactionListener )
		{
			l.onTransaction (tx);
		}
		for ( BitcoinPeer p : network.getConnectPeers () )
		{
			if ( p != peer )
			{
				TxMessage tm = (TxMessage) p.createMessage ("tx");
				tm.setTx (tx);
				p.send (tm);
			}
		}
		log.info ("sent validated transaction to peers " + tx.getHash ());
	}

	@Override
	public void trunkExtended (final String blockHash)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();

				Blk blk = network.getStore ().getBlock (blockHash);
				synchronized ( unconfirmed )
				{
					for ( Tx tx : blk.getTransactions () )
					{
						unconfirmed.remove (tx.getHash ());
						for ( TxOut o : tx.getOutputs () )
						{
							availableOutput.remove (o.getTxHash (), o.getIx ());
						}
					}
				}
			}
		});
	}

	@Override
	public void trunkShortened (final String blockHash)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();

				Blk blk = network.getStore ().getBlock (blockHash);
				synchronized ( unconfirmed )
				{
					for ( Tx tx : blk.getTransactions () )
					{
						cacheTransaction (tx);
					}
				}
			}
		});
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}
}
