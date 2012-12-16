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

import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class GetDataHandler implements BitcoinMessageListener<GetDataMessage>
{
	private static final Logger log = LoggerFactory.getLogger (GetDataHandler.class);

	private final BlockStore store;
	private TxHandler txHandler;

	public GetDataHandler (BitcoinNetwork network)
	{
		network.addListener ("getdata", this);
		store = network.getStore ();
	}

	@Override
	public void process (GetDataMessage m, final BitcoinPeer peer)
	{
		log.trace ("received getheader for " + m.getBlocks ().size () + " blocks " + m.getTransactions ().size () + " transactions from " + peer);
		for ( byte[] h : m.getTransactions () )
		{
			Tx t = txHandler.getTransaction (new Hash (h).toString ());
			if ( t != null )
			{
				TxMessage tm = (TxMessage) peer.createMessage ("tx");
				tm.setTx (t);
				peer.send (tm);
				log.trace ("sent transaction " + t.getHash () + " to " + peer.getAddress ());
			}
		}

		for ( final byte[] h : m.getBlocks () )
		{
			final Blk b = store.getBlock (new Hash (h).toString ());
			if ( b != null )
			{
				final BlockMessage bm = (BlockMessage) peer.createMessage ("block");

				bm.setBlock (b);
				peer.send (bm);
				log.trace ("sent block " + b.getHash () + " to " + peer.getAddress ());
			}
		}
	}

	public void setTxHandler (TxHandler txHandler)
	{
		this.txHandler = txHandler;
	}
}
