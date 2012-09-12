package hu.blummers.bitcoin.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.WireFormat.Address;
import hu.blummers.bitcoin.messages.AddrMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.p2p.P2P;

public class BitcoinNetwork extends P2P {
	private static final Logger log = LoggerFactory.getLogger(BitcoinNetwork.class);
	private Chain chain;
	private ChainStore store;
	
	private Map<BitcoinMessageListener, ArrayList<String>> listener = Collections.synchronizedMap(new HashMap<BitcoinMessageListener, ArrayList<String>> ());
	private Set<BitcoinPeer> connectedPeers = Collections.synchronizedSet(new HashSet<BitcoinPeer> ());
	private Semaphore numberOfPeers = new Semaphore (0);
	
	public BitcoinNetwork (Chain chain, ChainStore store) throws IOException
	{
		super (chain.getPort());
		this.chain = chain;
		this.store = store;
	}
	
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
		
		super.start();
	}
	
	public void addPeer (BitcoinPeer peer)
	{
		connectedPeers.add(peer);
		numberOfPeers.release();
	}
	
	public void removePeer (BitcoinPeer peer)
	{
		connectedPeers.remove(peer);
		numberOfPeers.tryAcquire();
	}
	
	public boolean isConnected (Peer peer)
	{
		return connectedPeers.contains(peer);
	}

	public void waitForConnectedPeers (int peersNeeded)
	{
		numberOfPeers.acquireUninterruptibly(peersNeeded);
		numberOfPeers.release(peersNeeded);
	}
	
	public interface PeerTask
	{
		public void run (BitcoinPeer peer);
	}
	
	public void runForConnected (PeerTask task)
	{
		synchronized ( connectedPeers )
		{
			for ( BitcoinPeer peer : connectedPeers )
				task.run(peer);
		}
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
		BitcoinPeer peer = new BitcoinPeer (this, address);
		// add stored listener to new nodes
		synchronized ( listener )
		{
			for ( BitcoinMessageListener l : listener.keySet() )
			{
				for ( String type : listener.get(l) )
					peer.addListener(type, l);
			}
		}
		return peer;
	}

	public Chain getChain() {
		return chain;
	}
	
	public ChainStore getStore ()
	{
		return store;
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
