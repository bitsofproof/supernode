package hu.blummers.bitcoin.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.BitcoinPeer.Message;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.InvMessage;


public class ChainLoader {
	private static final Logger log = LoggerFactory.getLogger(ChainLoader.class);
	
	private Set<String> knownBlocks = Collections.synchronizedSet(new HashSet<String> ());

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
			network.addListener ("inv", new BitcoinMessageListener (){
				@Override
				public void process(Message m, BitcoinPeer peer) {
					InvMessage im = (InvMessage)m;
					if ( !im.getBlockHashes().isEmpty() )
						log.info("got block adresses " + im.getBlockHashes().size());
				}});

			network.waitForConnectedPeers(20);
			
			network.runForConnected(new BitcoinNetwork.PeerTask() {
				public void run(BitcoinPeer peer) {
					GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");					
					try {
						gbm.getHashes().add(new Hash (network.getStore().getHeadHash()).toByteArray());
						peer.send(gbm);
					} catch (ChainStoreException e) {
						log.error("can not start header download", e);
					}
				}
			});
		} catch (Exception e) {
			log.error("Could not start chain loader", e);
		}
	}
}
