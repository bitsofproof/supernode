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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class BitcoinNetwork extends P2P
{
	public BitcoinNetwork (int connections) throws IOException
	{
		super (connections);
	}

	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);

	private Chain chain;
	private BlockStore store;
	private Discovery discovery;
	private PeerStore peerStore;

	private final long versionNonce = new SecureRandom ().nextLong ();

	private final Map<BitcoinMessageListener<? extends BitcoinPeer.Message>, ArrayList<String>> listener = Collections
			.synchronizedMap (new HashMap<BitcoinMessageListener<? extends BitcoinPeer.Message>, ArrayList<String>> ());
	private final Set<BitcoinPeer> connectedPeers = new CopyOnWriteArraySet<BitcoinPeer> ();
	private final List<BitcoinPeerListener> peerListener = Collections.synchronizedList (new ArrayList<BitcoinPeerListener> ());

	@Override
	public void start () throws IOException
	{
		int port = getPort ();
		if ( port == 0 )
		{
			port = chain.getPort ();
		}
		setPort (port);

		super.start ();
	}

	static class JobWrapper
	{

		private static final Map<Long, Runnable> jobs = Collections.synchronizedMap (new HashMap<Long, Runnable> ());

		static class WrappedRunnable implements Runnable
		{
			private final Long nr = new SecureRandom ().nextLong ();
			private final Runnable job;
			private final boolean single;

			WrappedRunnable (Runnable job, boolean single)
			{
				this.job = job;
				jobs.put (nr, this);
				this.single = single;
			}

			@Override
			public void run ()
			{
				if ( jobs.containsKey (nr) )
				{
					if ( single )
					{
						jobs.remove (nr);
					}
					try
					{
						job.run ();
					}
					catch ( Exception e )
					{
						log.debug ("Exception in scheduled job. The job will not be rescheduled.", e);
						jobs.remove (this);
					}
				}
			}
		}

		public WrappedRunnable wrap (Runnable job, boolean single)
		{
			return new WrappedRunnable (job, single);
		}

		public void cancelJob (long id)
		{
			jobs.remove (id);
		}
	}

	private static JobWrapper jobWrapper = new JobWrapper ();

	public long scheduleJob (final Runnable job, int delay, TimeUnit unit)
	{
		JobWrapper.WrappedRunnable runnable = jobWrapper.wrap (job, true);
		getScheduler ().schedule (runnable, delay, unit);
		return runnable.nr;
	}

	public long scheduleJobWithFixedDelay (Runnable job, int startDelay, int delay, TimeUnit unit)
	{
		JobWrapper.WrappedRunnable runnable = jobWrapper.wrap (job, false);
		getScheduler ().scheduleWithFixedDelay (runnable, startDelay, delay, unit);
		return runnable.nr;
	}

	public void cancelJob (long jobid)
	{
		jobWrapper.cancelJob (jobid);
	}

	public BitcoinPeer[] getConnectPeers ()
	{
		return connectedPeers.toArray (new BitcoinPeer[0]);
	}

	public void addPeer (final BitcoinPeer peer)
	{
		connectedPeers.add (peer);

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
	}

	public void notifyPeerRemoved (final BitcoinPeer peer)
	{
		connectedPeers.remove (peer);
		for ( final BitcoinPeerListener listener : peerListener )
		{
			listener.remove (peer);
		}
	}

	private void notifyPeerAdded (final BitcoinPeer peer)
	{
		for ( final BitcoinPeerListener listener : peerListener )
		{
			listener.add (peer);
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
		BitcoinPeer peer = new BitcoinPeer (this, address, outgoing);
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
		return store.getChainHeight ();
	}

	@Override
	protected boolean discover ()
	{
		List<InetAddress> al = discovery.discover ();

		for ( InetAddress a : al )
		{
			addPeer (a, chain.getPort ());
		}

		return !al.isEmpty ();
	}

	public void setDiscovery (Discovery discovery)
	{
		this.discovery = discovery;
	}

	public PeerStore getPeerStore ()
	{
		return peerStore;
	}

	public void setPeerStore (PeerStore peerStore)
	{
		this.peerStore = peerStore;
	}
}
