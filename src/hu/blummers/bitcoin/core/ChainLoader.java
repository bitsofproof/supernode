package hu.blummers.bitcoin.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.BitcoinPeer.Message;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetHeadersMessage;
import hu.blummers.p2p.P2P;
import hu.blummers.p2p.P2P.Peer;
import hu.blummers.p2p.P2P.PeerTask;


public class ChainLoader {
	private static final Logger log = LoggerFactory.getLogger(ChainLoader.class);

	ChainStore store;
	BitcoinNetwork network;

	public ChainLoader (BitcoinNetwork network, ChainStore store)
	{
		this.network = network;
		this.store = store;
	}
	
	public void start ()
	{
		try {
			network.addListener ("headers", new BitcoinMessageListener (){
				@Override
				public void process(Message m, BitcoinPeer peer) {
					log.info("got headers");
				}});
		} catch (Exception e) {
			log.error("Could not start chain loader", e);
		}
	}
	
	public void startDownloading (final BitcoinNetwork network, final ChainStore chainstore)
	{
		network.forAllConnected(new PeerTask (){
			@Override
			public void run(Peer peer) {
				BitcoinPeer bp = (BitcoinPeer)peer;
				GetHeadersMessage ghm = (GetHeadersMessage) bp.createMessage("getheaders");
				try {
					ghm.getLocators().add(new Hash (network.getStore().getHeadHash()).toByteArray());
					peer.send(ghm);
				} catch (ChainStoreException e) {
					log.error("can not start header download", e);
				}
			}});
		
	}
}
