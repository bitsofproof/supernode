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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.ChainStore;

public class ChainLoader
{
	private static final Logger log = LoggerFactory.getLogger (ChainLoader.class);

	public ChainLoader (PlatformTransactionManager transactionManager, BitcoinNetwork network, ChainStore store)
	{
		this.network = network;
		this.store = store;
		this.transactionManager = transactionManager;
	}

	private final ChainStore store;
	private final BitcoinNetwork network;
	private final PlatformTransactionManager transactionManager;
	private long chainHeightSeen = 0;
	private long chainHeightStored = 0;

	private void getAnotherBatch (BitcoinPeer peer) throws Exception
	{
		if ( chainHeightSeen > chainHeightStored )
		{
			GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage ("getblocks");
			for ( String s : store.getLocator () )
			{
				gbm.getHashes ().add (new Hash (s).toByteArray ());
			}
			peer.send (gbm);
			log.trace ("asking for known blocks from " + peer.getAddress ());
		}
	}

	private void getBlocks (final BitcoinPeer peer) throws Exception
	{
		if ( store.getNumberOfRequests (peer) == 0 )
		{
			List<String> requests = store.getRequests (peer);
			if ( !requests.isEmpty () )
			{
				GetDataMessage gdm = (GetDataMessage) peer.createMessage ("getdata");
				for ( String pick : requests )
				{
					gdm.getBlocks ().add (new Hash (pick).toByteArray ());
				}
				log.trace ("asking for blocks " + gdm.getBlocks ().size () + " from " + peer.getAddress ());
				peer.send (gdm);
			}
		}
	}

	private void processBlock (BlockMessage m, final BitcoinPeer peer) throws Exception
	{
		Blk block = m.getBlock ();
		chainHeightStored = store.store (block);
		if ( store.getNumberOfRequests (peer) == 0 )
		{
			getAnotherBatch (peer);
		}
	}

	private void processInv (final InvMessage m, final BitcoinPeer peer) throws Exception
	{
		if ( !m.getBlockHashes ().isEmpty () )
		{
			log.trace ("received inventory of " + m.getBlockHashes ().size () + " from " + peer.getAddress ());
			List<String> hashes = new ArrayList<String> ();
			for ( byte[] h : m.getBlockHashes () )
			{
				hashes.add (new Hash (h).toString ());
			}
			store.addInventory (hashes, peer);
			getBlocks (peer);
		}
	}

	public void start ()
	{
		try
		{
			network.addListener ("block", new BitcoinMessageListener ()
			{
				@Override
				public void process (Message m, BitcoinPeer peer) throws Exception
				{
					processBlock ((BlockMessage) m, peer);
				}

			});

			network.addListener ("inv", new BitcoinMessageListener ()
			{
				@Override
				public void process (Message m, BitcoinPeer peer) throws Exception
				{
					processInv ((InvMessage) m, peer);
				}

			});

			network.addPeerListener (new BitcoinPeerListener ()
			{

				@Override
				public void remove (final BitcoinPeer peer)
				{
					new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
					{
						@Override
						protected void doInTransactionWithoutResult (TransactionStatus arg0)
						{
							store.removePeer (peer);
						}
					});
				}

				@Override
				public void add (BitcoinPeer peer)
				{
					if ( chainHeightSeen < peer.getHeight () )
					{
						chainHeightSeen = peer.getHeight ();
					}
				}
			});
			network.runForAll (new BitcoinNetwork.PeerTask ()
			{
				@Override
				public void run (final BitcoinPeer peer) throws Exception
				{
					getAnotherBatch (peer);
				}

			});
		}
		catch ( Exception e )
		{
			log.error ("Could not start chain loader", e);
		}
	}
}
