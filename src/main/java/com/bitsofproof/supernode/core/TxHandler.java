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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;
import com.bitsofproof.supernode.model.TxOut;

public class TxHandler implements ChainListener
{
	private static final Logger log = LoggerFactory.getLogger (TxHandler.class);

	private final Set<String> heard = Collections.synchronizedSet (new HashSet<String> ());
	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());
	private final Map<String, HashMap<Long, TxOut>> newOutput = new HashMap<String, HashMap<Long, TxOut>> ();

	public TxHandler (final BitcoinNetwork network, final ChainLoader loader)
	{
		final BlockStore store = network.getStore ();
		loader.addChainListener (this);

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
				if ( !loader.isBehind () && get.getTransactions ().size () > 0 )
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
				if ( !unconfirmed.containsKey (txm.getTx ().getHash ()) && !loader.isBehind () )
				{
					try
					{
						store.validateTransaction (txm.getTx (), newOutput);
						cacheTransaction (txm.getTx ());
						for ( BitcoinPeer p : network.getConnectPeers () )
						{
							if ( p != peer )
							{
								TxMessage tm = (TxMessage) p.createMessage ("tx");
								tm.setTx (txm.getTx ());
								p.send (tm);
							}
						}
						log.trace ("sent validated transaction to peers " + txm.getTx ().getHash ());
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

	public void cacheTransaction (Tx tx)
	{
		log.trace ("Caching unconfirmed transaction " + tx.getHash ());
		unconfirmed.put (tx.getHash (), tx);
		HashMap<Long, TxOut> outs = new HashMap<Long, TxOut> ();
		newOutput.put (tx.getHash (), outs);
		for ( TxOut out : tx.getOutputs () )
		{
			outs.put (out.getIx (), out);
		}
	}

	@Override
	public void blockAdded (final Blk blk)
	{
		if ( unconfirmed.isEmpty () )
		{
			return;
		}
		for ( Tx tx : blk.getTransactions () )
		{
			heard.remove (tx.getHash ());
			Tx cached = unconfirmed.get (tx.getHash ());
			if ( cached != null )
			{
				unconfirmed.remove (tx.getHash ());
			}
		}
	}
}
