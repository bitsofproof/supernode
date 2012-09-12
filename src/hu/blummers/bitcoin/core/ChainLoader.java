package hu.blummers.bitcoin.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.BitcoinPeer.Message;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetDataMessage;
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
			network.addListener("block", new BitcoinMessageListener (){
				public void process(Message m, BitcoinPeer peer) {
					log.info("got block");
				}});
			
			network.addListener ("inv", new BitcoinMessageListener (){
				@Override
				public void process(Message m, BitcoinPeer peer) {
					InvMessage im = (InvMessage)m;
					if ( !im.getBlockHashes().isEmpty() )
					{
						GetDataMessage gdm = (GetDataMessage)peer.createMessage("getdata");
						for ( byte [] h : im.getBlockHashes() )
						{
							String hash = new Hash (h).toString();
							synchronized ( knownBlocks )
							{
								 if (!knownBlocks.contains(hash))
									 gdm.getBlocks().add(h);
								knownBlocks.add(hash);
							}
						}
						peer.send(gdm);
						if ( knownBlocks.size() < peer.getHeight() )
						{
							GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");
							gbm.getHashes().add(im.getBlockHashes().get(im.getBlockHashes().size()-1));
							peer.send(gbm);
						}
						else
						{
							peer.removeListener("inv", this);
						}
					}
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
