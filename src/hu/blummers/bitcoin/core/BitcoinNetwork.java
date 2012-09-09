package hu.blummers.bitcoin.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Collections;

import hu.blummers.bitcoin.core.WireFormat.Address;
import hu.blummers.bitcoin.messages.AdrMessage;
import hu.blummers.bitcoin.messages.BitcoinMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.MessageFactory;
import hu.blummers.bitcoin.messages.VersionMessage;
import hu.blummers.p2p.P2P;

public class BitcoinNetwork extends P2P {
	private static final Logger log = LoggerFactory.getLogger(BitcoinNetwork.class);
	private Chain chain;
	private ChainStore store;
	
	@SuppressWarnings("unchecked")
	public Map<BitcoinMessageListener, List<String>>listener = Collections.synchronizedMap(new HashMap<BitcoinMessageListener, ArrayList<String>> ());
	
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
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				AdrMessage am = (AdrMessage)m;
				for ( Address a : am.getAddresses() )
				{
					log.trace("received new address " + a.address);
					addPeer (a.address, (int)a.port);
				}
			}});
		
		addListener("version", new BitcoinMessageListener () {
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				VersionMessage v = (VersionMessage)m;
				peer.setVersion (v);
				log.info("connected to " +v.getAgent());
				peer.send (MessageFactory.createMessage(m.getChain(), "verack"));
			}});
		
		addListener("verack", new BitcoinMessageListener () {
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				log.info("Connection to " + peer + " acknowledged");
			}});
		super.start();
	}

	public void addListener (final String type, final BitcoinMessageListener l)
	{
		// listen currently connected
		forAllConnected(new PeerTask () {
			@Override
			public void run(Peer peer) {
				BitcoinPeer bp = (BitcoinPeer)peer;
				bp.addListener(type, l);
			}});
		
		// store listener that should be added to new node
		List<String> listenedTypes;
		if ( (listenedTypes = listener.get(l)) == null )
		{
			listenedTypes = new ArrayList<String> ();
			listener.put(l, listenedTypes);
		}
		if ( !listenedTypes.contains(type) )
			listenedTypes.add(type);
	}
	
	@Override
	public Peer createPeer(InetSocketAddress address) {
		BitcoinPeer peer = new BitcoinPeer (this, address);

		// add stored listener to new nodes
		for ( BitcoinMessageListener l : listener.keySet() )
		{
			for ( String type : listener.get(l) )
				peer.addListener(type, l);
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
