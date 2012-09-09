package hu.blummers.bitcoin.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.WireFormat.Address;
import hu.blummers.bitcoin.messages.AdrMessage;
import hu.blummers.bitcoin.messages.BitcoinMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.MessageFactory;
import hu.blummers.bitcoin.messages.VersionMessage;
import hu.blummers.p2p.P2P;

public class BitcoinNetwork extends P2P {
	private static final Logger log = LoggerFactory.getLogger(BitcoinNetwork.class);
	private Chain chain;
	private UnconfirmedTransactions unconfirmedTransactions = new UnconfirmedTransactions();
	
	public BitcoinNetwork (Chain chain) throws IOException
	{
		super (chain.getPort());
		this.chain = chain;
	}

	@Override
	public Peer createPeer(InetSocketAddress address) {
		BitcoinPeer peer = new BitcoinPeer (this, address);
		
		peer.addListener(new BitcoinMessageListener (){
			@Override
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				AdrMessage am = (AdrMessage)m;
				for ( Address a : am.getAddresses() )
				{
					log.trace("received new address " + a.address);
					addPeer (a.address, (int)a.port);
				}
			}}, new String [] {"addr"});
		
		peer.addListener(new BitcoinMessageListener () {
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				VersionMessage v = (VersionMessage)m;
				peer.setVersion (v);
				log.info("connected to " +v.getAgent());
				peer.send (MessageFactory.createMessage(m.getChain(), "verack"));
			}}, new String []{"version"});
		
		peer.addListener(new BitcoinMessageListener () {
			public void process(BitcoinMessage m, BitcoinPeer peer) {
				log.info("Connection to " + peer + " acknowledged");
			}}, new String []{"verack"});
		
		peer.addListener(unconfirmedTransactions, new String [] {"inv"});
		
		return peer;
	}

	public Chain getChain() {
		return chain;
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
	
	public void downloadBlockChain (String lastKnown, final BitcoinMessageListener blockListener)
	{
		final GetBlocksMessage gb = new GetBlocksMessage (chain);
		gb.getHashes().add(lastKnown);
		forAllConnected (new P2P.PeerTask() {
			@Override
			public void run(Peer peer) {
				BitcoinPeer p = (BitcoinPeer)peer;
				p.addListener (blockListener, new String []{"block"});
				peer.send(gb);
			}
		});
	}
}
