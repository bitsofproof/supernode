package com.bitsofproof.supernode.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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

import com.bitsofproof.supernode.core.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;
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
	}

	private static final Logger log = LoggerFactory.getLogger (BitcoinNetwork.class);

	private final Chain chain;
	private final ChainStore store;
	private final TransactionTemplate transactionTemplate;

	private final Map<BitcoinMessageListener, ArrayList<String>> listener = Collections
			.synchronizedMap (new HashMap<BitcoinMessageListener, ArrayList<String>> ());
	private final Set<BitcoinPeer> connectedPeers = Collections.synchronizedSet (new HashSet<BitcoinPeer> ());
	private final List<PeerTask> registeredTasks = Collections.synchronizedList (new ArrayList<PeerTask> ());
	private final List<BitcoinPeerListener> peerListener = Collections.synchronizedList (new ArrayList<BitcoinPeerListener> ());

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);

	@Override
	public void start () throws IOException
	{
		// receive adresses
		addListener ("addr", new BitcoinMessageListener ()
		{
			@Override
			public void process (BitcoinPeer.Message m, BitcoinPeer peer)
			{
				AddrMessage am = (AddrMessage) m;
				for ( Address a : am.getAddresses () )
				{
					log.trace ("received new address " + a.address);
					addPeer (a.address, (int) a.port);
				}
			}
		});
		setPort (chain.getPort ());
		super.start ();

		// send a pick of own adresses
		scheduler.scheduleWithFixedDelay (new Runnable ()
		{

			@Override
			public void run ()
			{
				try
				{
					synchronized ( connectedPeers )
					{
						BitcoinPeer[] peers = connectedPeers.toArray (new BitcoinPeer[0]);
						BitcoinPeer pick = peers[(int) (Math.floor (Math.random () * peers.length))];
						List<Address> al = new ArrayList<Address> ();
						Address address = new Address ();
						address.address = pick.getAddress ().getAddress ();
						address.port = getPort ();
						address.services = pick.getServices ();
						address.time = System.currentTimeMillis () / 1000;
						al.add (address);
						for ( BitcoinPeer peer : peers )
						{
							if ( peer != pick )
							{
								AddrMessage m = new AddrMessage (peer);
								m.setAddresses (al);
								try
								{
									peer.send (m);
								}
								catch ( Exception e )
								{
								}
							}
						}
						log.trace ("Sent an address to peers " + pick.getAddress ().getAddress ());
					}
				}
				catch ( Exception e )
				{
					log.error ("Exception while broadcasting addresses", e);
				}
			}
		}, 60, 60, TimeUnit.SECONDS);
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

	public void notifyPeerAdded (final BitcoinPeer peer)
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
	public Peer createPeer (InetSocketAddress address)
	{
		BitcoinPeer peer = new BitcoinPeer (this, transactionTemplate, address);
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
