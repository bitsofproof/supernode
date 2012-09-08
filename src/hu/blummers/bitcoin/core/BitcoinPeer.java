package hu.blummers.bitcoin.core;

import hu.blummers.p2p.P2P;
import hu.blummers.p2p.P2P.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Collections;

public class 
BitcoinPeer extends P2P.Peer {
	private static final Logger log = LoggerFactory.getLogger(BitcoinPeer.class);

	private BitcoinNetwork network;
	
	@SuppressWarnings("unchecked")
	private List<BitcoinMessageListener> listener = Collections.synchronizedList(new ArrayList<BitcoinMessageListener> ());

	public BitcoinPeer(P2P p2p, InetSocketAddress address) {
		p2p.super(address);
		network = (BitcoinNetwork)p2p;
	}

	public BitcoinNetwork getNetwork() {
		return network;
	}

	public void setNetwork(BitcoinNetwork network) {
		this.network = network;
	}

	@Override
	public Message receive(InputStream readIn) throws IOException {
		try {
			return BitcoinMessage.fromStream(readIn, network.getChain());
		} catch (Exception e) {
			log.error("exception in receive", e);
			disconnect("malformed package " + e.getMessage());
		}
		return null;
	}

	@Override
	public void handshake() {
		VersionMessage m = MessageFactory.createVersionMessage(network.getChain());
		m.setPeer(getAddress().getAddress());
		m.setRemotePort(getAddress().getPort());
		send(m);
	}

	@Override
	public void processMessage(P2P.Message m) {
		BitcoinMessage bm = (BitcoinMessage) m;
		for ( BitcoinMessageListener l : listener )
			l.process(bm, this);
		log.info("received " + bm.getCommand());
	}
	
	public void addListener (BitcoinMessageListener l)
	{
		listener.add(l);
	}
}
