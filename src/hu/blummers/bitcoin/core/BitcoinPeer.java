package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.messages.BitcoinMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.MessageFactory;
import hu.blummers.bitcoin.messages.VersionMessage;
import hu.blummers.p2p.P2P;
import hu.blummers.p2p.P2P.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.Collections;

public class 
BitcoinPeer extends P2P.Peer {
	private static final Logger log = LoggerFactory.getLogger(BitcoinPeer.class);

	private BitcoinNetwork network;
	private String agent;
	private long height;
	private long version;
	
	@SuppressWarnings("unchecked")
	public Map<String, List<BitcoinMessageListener>>listener = Collections.synchronizedMap(new HashMap<String, ArrayList<BitcoinMessageListener>> ());

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

	public void setVersion (VersionMessage m)
	{
		agent = m.getAgent();
		height = m.getHeight();
		version = m.getVersion();
	}
	
	@Override
	public void onDisconnect() {
	}

	@Override
	public Message parse(InputStream readIn) throws IOException {
		try {
			return BitcoinMessage.fromStream(readIn, network.getChain(), version);
		} catch (Exception e) {
			log.error("Exception reading message", e);
			disconnect();
		}
		return null;
	}

	@Override
	public void onConnect() {
		VersionMessage m = (VersionMessage)MessageFactory.createMessage(network.getChain(), "version");
		m.setPeer(getAddress().getAddress());
		m.setRemotePort(getAddress().getPort());
		send(m);
	}

	@Override
	public void receive(P2P.Message m) {
		BitcoinMessage bm = (BitcoinMessage) m;
		try {
			bm.validate ();
			
			List<BitcoinMessageListener> classListener = listener.get(bm.getCommand());
			if ( classListener != null )
				for ( BitcoinMessageListener l : classListener )
					l.process(bm, this);
			
		} catch (ValidationException e) {
			log.error("Invalid message ", e);
			disconnect ();
		}
	}
	
	public void addListener (String type, BitcoinMessageListener l)
	{
		List<BitcoinMessageListener> ll = listener.get(type);
		if ( ll == null )
		{
			ll = new ArrayList<BitcoinMessageListener> ();
			listener.put(type, ll);
		}
		if ( !ll.contains(l) )
			ll.add(l);
	}

}
