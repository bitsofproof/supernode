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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.model.ChainStore;

public class BitcoinNetwork extends P2P
{
	public BitcoinNetwork (PlatformTransactionManager transactionManager, Chain chain, ChainStore store, int connections) throws IOException
	{
		super (connections);
		this.chain = chain;
		this.store = store;
		transactionTemplate = new TransactionTemplate (transactionManager);
		loader = new ChainLoader (transactionManager, this, store);
	}

	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);

	private final Chain chain;
	private final ChainStore store;
	private final TransactionTemplate transactionTemplate;
	private final long versionNonce = new SecureRandom ().nextLong ();
	private final ChainLoader loader;

	private final Map<BitcoinMessageListener, ArrayList<String>> listener = Collections
			.synchronizedMap (new HashMap<BitcoinMessageListener, ArrayList<String>> ());
	private final Set<BitcoinPeer> connectedPeers = Collections.synchronizedSet (new HashSet<BitcoinPeer> ());
	private final List<PeerTask> registeredTasks = Collections.synchronizedList (new ArrayList<PeerTask> ());
	private final List<BitcoinPeerListener> peerListener = Collections.synchronizedList (new ArrayList<BitcoinPeerListener> ());

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);

	@Override
	public void start () throws IOException
	{
		setPort (chain.getPort ());
		super.start ();
		loader.start ();

		addListener ("ping", new PingPongHandler ());
		addListener ("addr", new AddressHandler ());
		scheduler.scheduleWithFixedDelay (new AddressSeeder (this), 60, 60, TimeUnit.SECONDS);
		addListener ("inv", new TransactionHandler ());
	}

	public BitcoinPeer[] getConnectPeers ()
	{
		return connectedPeers.toArray (new BitcoinPeer[0]);
	}

	public void addPeer (final BitcoinPeer peer)
	{
		connectedPeers.add (peer);

		// add stored listener to new nodes
		synchronized ( listener )
		{
			for ( BitcoinMessageListener l : listener.keySet () )
			{
				for ( String type : listener.get (l) )
				{
					peer.addListener (type, l);
				}
			}
		}

		notifyPeerAdded (peer);

		// execute registered tasks
		synchronized ( registeredTasks )
		{
			for ( final PeerTask task : registeredTasks )
			{
				transactionTemplate.execute (new TransactionCallbackWithoutResult ()
				{
					@Override
					protected void doInTransactionWithoutResult (TransactionStatus arg0)
					{
						try
						{
							task.run (peer);
						}
						catch ( Exception e )
						{
							arg0.setRollbackOnly ();
							log.error ("Peer task failed for " + peer.getAddress (), e);
							peer.disconnect ();
						}

					}
				});
			}
		}
	}

	public void notifyPeerRemoved (final BitcoinPeer peer)
	{
		connectedPeers.remove (peer);
		for ( final BitcoinPeerListener listener : peerListener )
		{
			transactionTemplate.execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus arg0)
				{
					listener.remove (peer);
				}
			});
		}
	}

	private void notifyPeerAdded (final BitcoinPeer peer)
	{
		for ( final BitcoinPeerListener listener : peerListener )
		{
			transactionTemplate.execute (new TransactionCallbackWithoutResult ()
			{
				@Override
				protected void doInTransactionWithoutResult (TransactionStatus arg0)
				{
					listener.add (peer);
				}
			});
		}
	}

	public void addPeerListener (BitcoinPeerListener listener)
	{
		peerListener.add (listener);
	}

	public void removePeerListener (BitcoinPeerListener listener)
	{
		peerListener.remove (listener);
	}

	public boolean isConnected (Peer peer)
	{
		return connectedPeers.contains (peer);
	}

	public interface PeerTask
	{
		public void run (BitcoinPeer peer) throws Exception;
	}

	public void runForConnected (final PeerTask task)
	{
		synchronized ( connectedPeers )
		{
			for ( final BitcoinPeer peer : connectedPeers )
			{
				transactionTemplate.execute (new TransactionCallbackWithoutResult ()
				{
					@Override
					protected void doInTransactionWithoutResult (TransactionStatus arg0)
					{
						try
						{
							task.run (peer);
						}
						catch ( Exception e )
						{
							arg0.setRollbackOnly ();
							log.error ("Peer task failed for " + peer.getAddress (), e);
							peer.disconnect ();
						}
					}
				});
			}
		}
	}

	public void runForAll (final PeerTask task)
	{
		synchronized ( connectedPeers )
		{
			for ( final BitcoinPeer peer : connectedPeers )
			{
				transactionTemplate.execute (new TransactionCallbackWithoutResult ()
				{
					@Override
					protected void doInTransactionWithoutResult (TransactionStatus arg0)
					{
						try
						{
							task.run (peer);
						}
						catch ( Exception e )
						{
							arg0.setRollbackOnly ();
							log.error ("Peer task failed for " + peer.getAddress (), e);
							peer.disconnect ();
						}
					}
				});
			}
			registeredTasks.add (task);
		}
	}

	public void stopRunning (PeerTask task)
	{
		registeredTasks.remove (task);
	}

	public void addListener (final String type, final BitcoinMessageListener l)
	{
		synchronized ( connectedPeers )
		{
			for ( BitcoinPeer peer : connectedPeers )
			{
				peer.addListener (type, l);
			}
		}
		// store listener that should be added to new node
		ArrayList<String> listenedTypes;
		if ( (listenedTypes = listener.get (l)) == null )
		{
			listenedTypes = new ArrayList<String> ();
			listener.put (l, listenedTypes);
		}
		if ( !listenedTypes.contains (type) )
		{
			listenedTypes.add (type);
		}
	}

	public void removeListener (final String type, final BitcoinMessageListener l)
	{
		synchronized ( connectedPeers )
		{
			for ( BitcoinPeer peer : connectedPeers )
			{
				peer.removeListener (type, l);
			}
		}
		listener.get (l).remove (type);
	}

	@Override
	public Peer createPeer (InetSocketAddress address, boolean outgoing)
	{
		BitcoinPeer peer = new BitcoinPeer (this, transactionTemplate, address, outgoing);
		return peer;
	}

	public Chain getChain ()
	{
		return chain;
	}

	public ChainStore getStore ()
	{
		return store;
	}

	public long getVersionNonce ()
	{
		return versionNonce;
	}

	public long getChainHeight ()
	{
		return transactionTemplate.execute (new TransactionCallback<Long> ()
		{
			@Override
			public Long doInTransaction (TransactionStatus arg0)
			{
				return store.getChainHeight ();
			}
		});
	}

	private boolean testPeer = false;

	@Override
	public boolean discover ()
	{
		if ( testPeer )
		{
			return false;
		}

		log.info ("Discovering network");
		int n = 0;
		List<InetAddress> al = new ArrayList<InetAddress> ();
		for ( String hostName : chain.getSeedHosts () )
		{
			try
			{
				InetAddress[] hostAddresses = InetAddress.getAllByName (hostName);

				for ( InetAddress inetAddress : hostAddresses )
				{
					al.add (inetAddress);
					++n;
				}
			}
			catch ( Exception e )
			{
				log.trace ("DNS lookup for " + hostName + " failed.");
			}
		}

		Collections.shuffle (al);
		for ( InetAddress a : al )
		{
			addPeer (a, chain.getPort ());
		}

		log.info ("Found " + n + " addresses of seed hosts");
		return true;
	}

	// for tests
	public void start (InetAddress address, int port) throws IOException
	{
		testPeer = true;
		setPort (port);
		super.start ();
	}

}
