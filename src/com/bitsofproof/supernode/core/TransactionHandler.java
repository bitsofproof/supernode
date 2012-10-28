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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.ChainStore;

public class TransactionHandler implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (TransactionHandler.class);

	private ChainStore store;

	public TransactionHandler (BitcoinNetwork network)
	{
		network.addListener ("inv", this);
		network.addListener ("tx", this);
	}

	@Override
	public synchronized void process (BitcoinPeer.Message m, BitcoinPeer peer) throws Exception
	{
		if ( peer.getNetwork ().isBehind () )
		{
			return;
		}

		if ( m instanceof InvMessage )
		{
			InvMessage im = (InvMessage) m;
			GetDataMessage get = (GetDataMessage) peer.createMessage ("getdata");
			for ( byte[] h : im.getTransactionHashes () )
			{
				String hash = new Hash (h).toString ();
				if ( store.getTransaction (hash) == null )
				{
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
		if ( m instanceof TxMessage )
		{
			log.trace ("received transaction details for " + ((TxMessage) m).getTx ().getHash () + " from " + peer.getAddress ());
			store.storeTransaction (((TxMessage) m).getTx (), (int) peer.getNetwork ().getChainHeight ());
		}
	}

	public ChainStore getStore ()
	{
		return store;
	}

	public void setStore (ChainStore store)
	{
		this.store = store;
	}
}
