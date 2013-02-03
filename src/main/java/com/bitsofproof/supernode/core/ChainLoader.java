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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.model.Blk;

public class ChainLoader
{
	private static final Logger log = LoggerFactory.getLogger (ChainLoader.class);

	private int timeout = 60;

	private final Map<BitcoinPeer, TreeSet<KnownBlock>> knownInventory = new HashMap<BitcoinPeer, TreeSet<KnownBlock>> ();
	private final Map<BitcoinPeer, HashSet<String>> requests = new HashMap<BitcoinPeer, HashSet<String>> ();
	private final Map<BitcoinPeer, Long> invTimer = new HashMap<BitcoinPeer, Long> ();
	private final Map<String, ArrayList<Blk>> pendingOn = Collections.synchronizedMap (new HashMap<String, ArrayList<Blk>> ());
	private final Map<String, Blk> havePending = Collections.synchronizedMap (new HashMap<String, Blk> ());

	private final BlockStore store;
	private final BitcoinNetwork network;

	private final static int ORPHANLIMIT = 1000;

	private static class KnownBlock
	{
		private int nr;
		private String hash;
	}

	public ChainLoader (final BitcoinNetwork network)
	{
		this.network = network;
		store = network.getStore ();

		network.addListener ("block", new BitcoinMessageListener<BlockMessage> ()
		{
			@Override
			public void process (BlockMessage m, final BitcoinPeer peer)
			{
				boolean validatedBlock = false;

				Blk block = m.getBlock ();
				log.trace ("received block " + block.getHash () + " from " + peer.getAddress ());
				if ( store.isStoredBlock (block.getPreviousHash ()) )
				{
					try
					{
						havePending.remove (block.getHash ());
						store.storeBlock (block);
						validatedBlock = true;

						if ( pendingOn.containsKey (block.getHash ()) )
						{
							storePending (pendingOn.get (block.getHash ()));
							pendingOn.remove (block.getHash ());
						}
					}
					catch ( ValidationException e )
					{
						log.debug ("Rejecting block " + block.getHash () + " from " + peer.getAddress ());
						log.trace ("Rejecting block " + block.getHash () + " from " + peer.getAddress (), e);
						peer.ban ("Sent invalid block");
					}
				}
				else
				{
					GetDataMessage getparent = (GetDataMessage) peer.createMessage ("getdata");
					getparent.getBlocks ().add (new Hash (block.getPreviousHash ()).toByteArray ());
					peer.send (getparent);

					if ( !havePending.containsKey (block.getHash ()) )
					{
						ArrayList<Blk> pendingList = pendingOn.get (block.getPreviousHash ());
						if ( pendingList == null )
						{
							pendingList = new ArrayList<Blk> ();
							pendingOn.put (block.getPreviousHash (), pendingList);
						}
						pendingList.add (block);

						havePending.put (block.getHash (), block);
						if ( havePending.size () > ORPHANLIMIT )
						{
							havePending.clear ();
							pendingOn.clear ();
							log.warn ("Orphan block limit exceeded. Resetting cache.");
						}
					}
				}

				HashSet<String> peerRequests;
				synchronized ( knownInventory )
				{
					peerRequests = requests.get (peer);
					if ( peerRequests == null )
					{
						return;
					}
					if ( peerRequests.size () == 1 )
					{
						// TODO: this will send last block of every bulk request, not just single blocks received.
						if ( validatedBlock )
						{
							sendBlock (block, peer);
						}
					}
					peerRequests.remove (block.getHash ());
				}
				if ( peerRequests.isEmpty () )
				{
					if ( peer.getHeight () > store.getChainHeight () )
					{
						getBlockInventory (network, store, peer);
					}
				}
			}

			private void storePending (List<Blk> blocks) throws ValidationException
			{
				Iterator<Blk> i = blocks.iterator ();
				while ( i.hasNext () )
				{
					Blk b = i.next ();
					havePending.remove (b.getHash ());
					store.storeBlock (b);
					List<Blk> next = pendingOn.get (b.getHash ());
					if ( next != null )
					{
						if ( next.size () == 1 )
						{
							// avoid deep recursion for simple chain
							i = next.iterator ();
						}
						else
						{
							storePending (next);
						}
						pendingOn.remove (b.getHash ());
					}
				}
			}
		});

		network.addListener ("inv", new BitcoinMessageListener<InvMessage> ()
		{
			@Override
			public void process (InvMessage m, BitcoinPeer peer)
			{
				if ( !m.getBlockHashes ().isEmpty () )
				{
					log.trace ("received inventory of " + m.getBlockHashes ().size () + " blocks from " + peer.getAddress ());
					synchronized ( knownInventory )
					{
						Set<KnownBlock> k = knownInventory.get (peer);
						if ( k == null )
						{
							return; // disconnected peer
						}
						if ( m.getBlockHashes ().size () > 1 )
						{
							Long job = invTimer.get (peer);
							if ( job != null )
							{
								network.cancelJob (job);
								invTimer.remove (peer);
							}
						}
						int n = k.size ();
						for ( byte[] h : m.getBlockHashes () )
						{
							String hash = new Hash (h).toString ();
							if ( !havePending.containsKey (hash) && !store.isStoredBlock (hash) )
							{
								KnownBlock kn = new KnownBlock ();
								kn.nr = n++;
								kn.hash = hash;
								k.add (kn);
							}
						}
					}
					getBlocks (network, peer);
				}
			}
		});

		network.addPeerListener (new BitcoinPeerListener ()
		{

			@Override
			public void remove (final BitcoinPeer peer)
			{
				boolean nowork = false;
				synchronized ( knownInventory )
				{
					knownInventory.remove (peer);
					requests.remove (peer);
					invTimer.remove (peer);
					nowork = invTimer.isEmpty ();
				}
				if ( nowork )
				{
					for ( BitcoinPeer p : network.getConnectPeers () )
					{
						if ( store.getChainHeight () < p.getHeight () )
						{
							getBlockInventory (network, store, p);
						}
					}
				}
			}

			@Override
			public void add (BitcoinPeer peer)
			{
				synchronized ( knownInventory )
				{
					requests.put (peer, new HashSet<String> ());
					knownInventory.put (peer, new TreeSet<KnownBlock> (incomingOrder));
				}
				if ( peer.getHeight () > store.getChainHeight () )
				{
					getBlockInventory (network, store, peer);
				}
			}
		});
	}

	private void sendBlock (Blk b, BitcoinPeer peer)
	{
		for ( BitcoinPeer p : network.getConnectPeers () )
		{
			if ( p != peer )
			{
				InvMessage bm = (InvMessage) p.createMessage ("inv");
				bm.getBlockHashes ().add (new Hash (b.getHash ()).toByteArray ());
				p.send (bm);
			}
		}
		log.trace ("sent block " + b.getHash ());
	}

	public int getTimeout ()
	{
		return timeout;
	}

	public void setTimeout (int inventoryTimeout)
	{
		this.timeout = inventoryTimeout;
	}

	private void getBlocks (final BitcoinNetwork network, final BitcoinPeer peer)
	{
		GetDataMessage gdm = (GetDataMessage) peer.createMessage ("getdata");
		synchronized ( knownInventory )
		{
			if ( !knownInventory.containsKey (peer) )
			{
				return;
			}
			for ( KnownBlock b : knownInventory.get (peer) )
			{
				boolean askedElswhere = false;
				for ( Set<String> ps : requests.values () )
				{
					if ( ps.contains (b.hash) )
					{
						askedElswhere = true;
						break;
					}
				}
				if ( !askedElswhere )
				{
					gdm.getBlocks ().add (new Hash (b.hash).toByteArray ());
					requests.get (peer).add (b.hash);
				}
			}
			knownInventory.get (peer).clear ();
		}
		if ( gdm.getBlocks ().size () > 0 )
		{
			peer.send (gdm);
			log.trace ("asking for " + gdm.getBlocks ().size () + " blocks from " + peer.getAddress ());
		}
	}

	private void getBlockInventory (final BitcoinNetwork network, final BlockStore store, final BitcoinPeer peer)
	{
		synchronized ( knownInventory )
		{
			if ( invTimer.containsKey (peer) )
			{
				return;
			}
			HashSet<String> peerRequests = requests.get (peer);
			if ( peerRequests != null && !peerRequests.isEmpty () )
			{
				return;
			}
		}
		GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage ("getblocks");
		for ( String h : store.getLocator () )
		{
			gbm.getHashes ().add (new Hash (h).toByteArray ());
		}
		gbm.setLastHash (Hash.ZERO_HASH.toByteArray ());
		if ( !gbm.getHashes ().isEmpty () )
		{
			log.trace ("Sending inventory request to " + peer.getAddress ());
			peer.send (gbm);
			invTimer.put (peer, network.scheduleJob (new Runnable ()
			{
				@Override
				public void run ()
				{
					log.trace ("Peer did not answer to inventory requests " + peer.getAddress ());
					peer.disconnect ();
				}
			}, timeout, TimeUnit.SECONDS));
		}
	}

	private static final Comparator<KnownBlock> incomingOrder = new Comparator<KnownBlock> ()
	{
		@Override
		public int compare (KnownBlock arg0, KnownBlock arg1)
		{
			int diff = arg0.nr - arg1.nr;
			if ( diff != 0 )
			{
				return diff;
			}
			else
			{
				return arg0.equals (arg1) ? 0 : arg0.hashCode () - arg1.hashCode ();
			}
		}
	};
}
