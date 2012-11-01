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
import com.bitsofproof.supernode.model.BlockStore;

public class BitcoinNetwork extends P2P
{
	public BitcoinNetwork (int connections) throws IOException
	{
		super (connections);
	}

	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);

	private Chain chain;
	private BlockStore store;
	private PlatformTransactionManager transactionManager;
	private Discovery discovery;

	private TransactionTemplate transactionTemplate;
	private final long versionNonce = new SecureRandom ().nextLong ();

	private final Map<BitcoinMessageListener<? extends BitcoinPeer.Message>, ArrayList<String>> listener = Collections
			.synchronizedMap (new HashMap<BitcoinMessageListener<? extends BitcoinPeer.Message>, ArrayList<String>> ());
	private final Set<BitcoinPeer> connectedPeers = Collections.synchronizedSet (new HashSet<BitcoinPeer> ());
	private final List<PeerTask> registeredTasks = Collections.synchronizedList (new ArrayList<PeerTask> ());
	private final List<BitcoinPeerListener> peerListener = Collections.synchronizedList (new ArrayList<BitcoinPeerListener> ());

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);
	private static final Map<Long, Runnable> jobs = new HashMap<Long, Runnable> ();

	@Override
	public void start () throws IOException
	{
		setPort (chain.getPort ());

		transactionTemplate = new TransactionTemplate (transactionManager);

		super.start ();
	}

	private static class WrappedRunnable implements Runnable
	{
		Long nr = new SecureRandom ().nextLong ();
		Runnable job;

		WrappedRunnable (Runnable job)
		{
			this.job = job;
		}

		@Override
		public void run ()
		{
			if ( jobs.containsKey (nr) )
			{
				job.run ();
			}
		}

	}

	public long scheduleJob (final Runnable job, int delay, TimeUnit unit)
	{
		WrappedRunnable runnable = new WrappedRunnable (job);
		scheduler.schedule (runnable, delay, unit);
		return runnable.nr;
	}

	public long scheduleJobWithFixedDelay (Runnable job, int startDelay, int delay, TimeUnit unit)
	{
		WrappedRunnable runnable = new WrappedRunnable (job);
		scheduler.scheduleWithFixedDelay (runnable, startDelay, delay, unit);
		return runnable.nr;
	}

	public void cancelJob (long jobid)
	{
		jobs.remove (jobid);
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
			for ( BitcoinMessageListener<? extends BitcoinPeer.Message> l : listener.keySet () )
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
					try
					{
						listener.remove (peer);
					}
					catch ( Exception e )
					{
						log.error ("exception while removing peer ", e);
						arg0.setRollbackOnly ();
					}
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
					try
					{
						listener.add (peer);
					}
					catch ( Exception e )
					{
						log.error ("exception while adding peer ", e);
						arg0.setRollbackOnly ();
					}
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

	public void addListener (final String type, final BitcoinMessageListener<? extends BitcoinPeer.Message> l)
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

	public void removeListener (final String type, final BitcoinMessageListener<? extends BitcoinPeer.Message> l)
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

	public BlockStore getStore ()
	{
		return store;
	}

	public PlatformTransactionManager getTransactionManager ()
	{
		return transactionManager;
	}

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void setChain (Chain chain)
	{
		this.chain = chain;
	}

	public void setStore (BlockStore store)
	{
		this.store = store;
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

	@Override
	public boolean discover ()
	{
		List<InetAddress> al = discovery.discover ();
		Collections.shuffle (al);
		for ( InetAddress a : al )
		{
			addPeer (a, chain.getPort ());
		}

		return !al.isEmpty ();
	}

	public Discovery getDiscovery ()
	{
		return discovery;
	}

	public void setDiscovery (Discovery discovery)
	{
		this.discovery = discovery;
	}

}
