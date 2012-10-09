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


public class BitcoinNetwork extends P2P {

	public BitcoinNetwork(PlatformTransactionManager transactionManager,
			Chain chain, ChainStore store) throws IOException {
		super();
		this.chain = chain;
		this.store = store;
		transactionTemplate = new TransactionTemplate (transactionManager);
	}

	private static final Logger log = LoggerFactory.getLogger(BitcoinNetwork.class);
	
	private Chain chain;
	private ChainStore store;
	private final TransactionTemplate transactionTemplate;
	
	private Map<BitcoinMessageListener, ArrayList<String>> listener = Collections.synchronizedMap(new HashMap<BitcoinMessageListener, ArrayList<String>> ());
	private Set<BitcoinPeer> connectedPeers = Collections.synchronizedSet(new HashSet<BitcoinPeer> ());
	private List<PeerTask> registeredTasks = Collections.synchronizedList(new ArrayList<PeerTask> ());
	private List<BitcoinPeerListener> peerListener = Collections.synchronizedList(new ArrayList<BitcoinPeerListener> ());
	
	@Override
	public void start() throws IOException {
		addListener("addr", new BitcoinMessageListener (){
			@Override
			public void process(BitcoinPeer.Message m, BitcoinPeer peer) {
				AddrMessage am = (AddrMessage)m;
				for ( Address a : am.getAddresses() )
				{
					log.trace("received new address " + a.address);
					addPeer (a.address, (int)a.port);
				}
			}});
		setPort (chain.getPort());
		super.start();
	}
		
	public void addPeer (final BitcoinPeer peer)
	{
		connectedPeers.add(peer);

		// add stored listener to new nodes
		synchronized ( listener )
		{
			for ( BitcoinMessageListener l : listener.keySet() )
			{
				for ( String type : listener.get(l) )
					peer.addListener(type, l);
			}
		}
		// execute registered tasks
		synchronized ( registeredTasks )
		{
			for ( final PeerTask task : registeredTasks )
			{
				transactionTemplate.execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus arg0) {
						task.run(peer);
					}
				});
			}
		}
	}
	
	public void notifyPeerRemoved (final BitcoinPeer peer)
	{
		connectedPeers.remove(peer);
		for ( final BitcoinPeerListener listener : peerListener )
			transactionTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus arg0) {
					listener.remove(peer);
				}
			});
	}
	
	public void notifyPeerAdded (final BitcoinPeer peer)
	{
		for ( final BitcoinPeerListener listener : peerListener )
			transactionTemplate.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus arg0) {
					listener.add(peer);
				}
			});
	}
	
	public void addPeerListener (BitcoinPeerListener listener)
	{
		peerListener.add(listener);
	}
	
	public void removePeerListener (BitcoinPeerListener listener)
	{
		peerListener.remove(listener);
	}
	
	public boolean isConnected (Peer peer)
	{
		return connectedPeers.contains(peer);
	}

	public interface PeerTask
	{
		public void run (BitcoinPeer peer);
	}
	
	public void runForConnected (final PeerTask task)
	{
		synchronized ( connectedPeers )
		{
			for ( final BitcoinPeer peer : connectedPeers )
				transactionTemplate.execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus arg0) {
						task.run(peer);
					}
				});
		}
	}
	
	public void runForAll (final PeerTask task)
	{
		synchronized ( connectedPeers )
		{
			for ( final BitcoinPeer peer : connectedPeers )
				transactionTemplate.execute(new TransactionCallbackWithoutResult() {
					@Override
					protected void doInTransactionWithoutResult(TransactionStatus arg0) {
						task.run(peer);
					}
				});
			registeredTasks.add(task);
		}
	}
	
	public void stopRunning (PeerTask task)
	{
		registeredTasks.remove(task);
	}
	
	public void addListener (final String type, final BitcoinMessageListener l)
	{
		synchronized ( connectedPeers )
		{
			for ( BitcoinPeer peer : connectedPeers )
			{
				peer.addListener(type, l);
			}
		}
		// store listener that should be added to new node
		ArrayList<String> listenedTypes;
		if ( (listenedTypes = listener.get(l)) == null )
		{
			listenedTypes = new ArrayList<String> ();
			listener.put(l, listenedTypes);
		}
		if ( !listenedTypes.contains(type) )
			listenedTypes.add(type);
	}
	
	public void removeListener (final String type, final BitcoinMessageListener l)
	{
		synchronized ( connectedPeers )
		{
			for ( BitcoinPeer peer : connectedPeers )
			{
				peer.removeListener(type, l);
			}
		}
		listener.get(l).remove(type);
	}
	
	@Override
	public Peer createPeer(InetSocketAddress address) {
		BitcoinPeer peer = new BitcoinPeer (this, transactionTemplate, address);
		return peer;
	}

	public Chain getChain() {
		return chain;
	}
	
	public ChainStore getStore ()
	{
		return store;
	}
	
	public long getChainHeight ()
	{
		return transactionTemplate.execute(new TransactionCallback<Long> (){
			@Override
			public Long doInTransaction(TransactionStatus arg0) {
				return store.getChainHeight();
			}
		});
	}
	
	@Override
	public void discover() {
		log.info("Discovering network");
		int n = 0;
		for (String hostName : chain.getSeedHosts()) {
			try {
				InetAddress[] hostAddresses = InetAddress.getAllByName(hostName);

				for (InetAddress inetAddress : hostAddresses) {
					addPeer(inetAddress, chain.getPort());
					++n;
				}
			} catch (Exception e) {
				log.trace("DNS lookup for " + hostName + " failed.");
			}
		}
		log.info("Found " + n  + " addresses of seed hosts");
	}
}
