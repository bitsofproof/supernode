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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.BlockStore;

public class ChainLoader
{
	private static final Logger log = LoggerFactory.getLogger (ChainLoader.class);

	private int timeout = 30;

	private final Map<BitcoinPeer, TreeSet<KnownBlock>> known = new HashMap<BitcoinPeer, TreeSet<KnownBlock>> ();
	private final Map<BitcoinPeer, HashSet<String>> requests = new HashMap<BitcoinPeer, HashSet<String>> ();
	private final Map<BitcoinPeer, Long> waitingForInventory = new HashMap<BitcoinPeer, Long> ();
	private final Map<BitcoinPeer, Long> waitingForBlocks = new HashMap<BitcoinPeer, Long> ();
	private final Map<String, Blk> pending = Collections.synchronizedMap (new HashMap<String, Blk> ());

	private class KnownBlock
	{
		private int nr;
		private String hash;
	}

	public boolean isBehind ()
	{
		synchronized ( known )
		{
			return !waitingForInventory.isEmpty ();
		}
	}

	public ChainLoader (final BitcoinNetwork network)
	{
		final BlockStore store = network.getStore ();

		Thread loader = new Thread (new Runnable ()
		{
			@Override
			public void run ()
			{
				try
				{
					while ( true )
					{
						synchronized ( known )
						{
							for ( final BitcoinPeer peer : known.keySet () )
							{
								if ( requests.get (peer).size () == 0 && peer.getHeight () > store.getChainHeight () && !waitingForInventory.containsKey (peer) )
								{
									GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage ("getblocks");
									for ( String h : store.getLocator () )
									{
										gbm.getHashes ().add (new Hash (h).toByteArray ());
									}
									gbm.setLastHash (Hash.ZERO_HASH.toByteArray ());
									try
									{
										if ( !gbm.getHashes ().isEmpty () )
										{
											log.trace ("Sending inventory request to " + peer.getAddress ());
											peer.send (gbm);
											waitingForInventory.put (peer, network.scheduleJob (new Runnable ()
											{
												@Override
												public void run ()
												{
													log.trace ("Peer did not answer to inventory request " + peer.getAddress ());
													peer.disconnect ();
													synchronized ( known )
													{
														known.notify ();
													}
												}
											}, timeout, TimeUnit.SECONDS));
										}
									}
									catch ( Exception e )
									{
										log.trace ("could not send to peer", e);
										peer.disconnect ();
										continue;
									}
								}
								GetDataMessage gdm = (GetDataMessage) peer.createMessage ("getdata");
								for ( KnownBlock b : known.get (peer) )
								{
									boolean have = false;
									for ( Set<String> ps : requests.values () )
									{
										if ( ps.contains (b.hash) )
										{
											have = true;
											break;
										}
										if ( pending.containsKey (b.hash) )
										{
											have = true;
											break;
										}
									}
									if ( !have )
									{
										gdm.getBlocks ().add (new Hash (b.hash).toByteArray ());
										requests.get (peer).add (b.hash);
									}
								}
								known.get (peer).clear ();
								if ( gdm.getBlocks ().size () > 0 )
								{
									peer.send (gdm);

									waitingForBlocks.put (peer, network.scheduleJob (new Runnable ()
									{
										@Override
										public void run ()
										{
											log.trace ("Peer did not answer to getblock requests " + peer.getAddress ());
											peer.disconnect ();
											synchronized ( known )
											{
												known.notify ();
											}
										}
									}, timeout, TimeUnit.SECONDS));
								}
							}
							try
							{
								known.wait ();
							}
							catch ( InterruptedException e )
							{
							}
						}
					}
				}
				catch ( Exception e )
				{
					log.error ("Chainloader thread unexpectedly exists", e);
				}

			}
		});
		loader.setName ("Chain loader");
		loader.setDaemon (true);
		loader.start ();

		network.addListener ("block", new BitcoinMessageListener<BlockMessage> ()
		{
			@Override
			public void process (BlockMessage m, BitcoinPeer peer)
			{
				Blk block = m.getBlock ();
				log.trace ("received block " + block.getHash () + " from " + peer.getAddress ());
				synchronized ( known )
				{
					HashSet<String> peerRequests = requests.get (peer);
					if ( peerRequests == null )
					{
						return;
					}
					peerRequests.remove (block.getHash ());
					if ( peerRequests.isEmpty () )
					{
						Long job = waitingForBlocks.get (peer);
						network.cancelJob (job);
						waitingForBlocks.remove (peer);
						peer.disconnect ();
						known.notify ();
					}
				}
				if ( store.isStoredBlock (block.getPreviousHash ()) )
				{
					try
					{
						store.storeBlock (block);
						while ( pending.containsKey (block.getHash ()) )
						{
							block = pending.get (block.getHash ());
							store.storeBlock (block);
						}
					}
					catch ( ValidationException e )
					{
						log.trace ("Rejecting block " + block.getHash () + " from " + peer.getAddress (), e);
					}
				}
				else
				{
					pending.put (block.getPreviousHash (), block);
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
					synchronized ( known )
					{
						known.notify ();
						if ( m.getBlockHashes ().size () > 1 )
						{
							Long jobid = waitingForInventory.get (peer);
							if ( jobid != null )
							{
								network.cancelJob (jobid.longValue ());
								waitingForInventory.remove (peer);
							}
						}
						Set<KnownBlock> k = known.get (peer);
						if ( k == null )
						{
							return;
						}

						int n = k.size ();
						for ( byte[] h : m.getBlockHashes () )
						{
							String hash = new Hash (h).toString ();
							if ( !store.isStoredBlock (hash) )
							{
								KnownBlock kn = new KnownBlock ();
								kn.nr = n++;
								kn.hash = hash;
								k.add (kn);
							}
						}
					}
				}
			}
		});

		network.addPeerListener (new BitcoinPeerListener ()
		{

			@Override
			public void remove (final BitcoinPeer peer)
			{
				synchronized ( known )
				{
					known.remove (peer);
					requests.remove (peer);
					waitingForInventory.remove (peer);
					waitingForBlocks.remove (peer);
					known.notify ();
				}
			}

			@Override
			public void add (BitcoinPeer peer)
			{
				synchronized ( known )
				{
					known.notify ();
					requests.put (peer, new HashSet<String> ());
					known.put (peer, new TreeSet<KnownBlock> (new Comparator<KnownBlock> ()
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
					}));
				}
			}
		});
	}

	public int getTimeout ()
	{
		return timeout;
	}

	public void setTimeout (int inventoryTimeout)
	{
		this.timeout = inventoryTimeout;
	}

}
