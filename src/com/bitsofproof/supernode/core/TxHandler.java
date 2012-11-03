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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.BlockStore;
import com.bitsofproof.supernode.model.Tx;

public class TxHandler
{
	private static final Logger log = LoggerFactory.getLogger (TxHandler.class);

	private BlockStore store;
	private ChainLoader loader;

	private final Map<String, Tx> unconfirmed = Collections.synchronizedMap (new HashMap<String, Tx> ());

	public TxHandler (final BitcoinNetwork network)
	{
		network.addListener ("inv", new BitcoinMessageListener<InvMessage> ()
		{
			@Override
			public void process (InvMessage im, BitcoinPeer peer) throws Exception
			{
				GetDataMessage get = (GetDataMessage) peer.createMessage ("getdata");
				for ( byte[] h : im.getTransactionHashes () )
				{
					String hash = new Hash (h).toString ();
					if ( unconfirmed.get (hash) == null )
					{
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
			public void process (final TxMessage txm, final BitcoinPeer peer) throws Exception
			{
				log.trace ("received transaction details for " + txm.getTx ().getHash () + " from " + peer.getAddress ());
				if ( !unconfirmed.containsKey (txm.getTx ().getHash ()) )
				{
					store.validateTransaction (txm.getTx ());
					log.trace ("Caching unconfirmed transaction " + txm.getTx ().getHash ());
					unconfirmed.put (txm.getTx ().getHash (), txm.getTx ());
					for ( BitcoinPeer p : network.getConnectPeers () )
					{
						if ( p != peer )
						{
							TxMessage tm = (TxMessage) p.createMessage ("tx");
							tm.setTx (txm.getTx ());
							try
							{
								p.send (tm);
								log.trace ("Sent transaction " + txm.getTx ().getHash () + " to " + p.getAddress ());
							}
							catch ( IOException e )
							{
								log.trace ("Can not send to transaction, likely disconnected ", p.getAddress ());
							}
						}

					}
				}
			}
		});
	}

	public Tx getTransaction (String hash)
	{
		return unconfirmed.get (hash);
	}

	public BlockStore getStore ()
	{
		return store;
	}

	public void setStore (BlockStore store)
	{
		this.store = store;
	}

	public ChainLoader getLoader ()
	{
		return loader;
	}

	public void setLoader (ChainLoader loader)
	{
		this.loader = loader;
	}

}
