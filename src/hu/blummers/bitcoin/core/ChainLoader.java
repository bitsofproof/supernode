package hu.blummers.bitcoin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hu.blummers.bitcoin.core.BitcoinPeer.Message;
import hu.blummers.bitcoin.jpa.JpaBlock;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.BlockMessage;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetDataMessage;
import hu.blummers.bitcoin.messages.InvMessage;


public class ChainLoader {
	private static final Logger log = LoggerFactory.getLogger(ChainLoader.class);
	
	private Map<String, ArrayList<JpaBlock>> orphanes = Collections.synchronizedMap(new HashMap<String, ArrayList<JpaBlock>>());

	ChainStore store;
	BitcoinNetwork network;

	public ChainLoader (BitcoinNetwork network, ChainStore store)
	{
		this.network = network;
		this.store = store;
	}
	
	private void storePending (String h)
	{
		synchronized ( orphanes )
		{
			List<JpaBlock> pendingBlocks = orphanes.get(h);
			if ( pendingBlocks != null )
				for ( JpaBlock b : pendingBlocks)
					try {
						pendingBlocks.remove(b);
						if ( store.store(b) )
						{
							log.info("storing (previously pending)" + b.getHash());
							storePending (b.getHash());
						}
					} catch (ChainStoreException e) {
					}
		}
	}
	
	public void start ()
	{
		try {
			network.addListener("block", new BitcoinMessageListener (){
				public void process(Message m, BitcoinPeer peer) {
					BlockMessage bm = (BlockMessage)m;
					try {
						JpaBlock block = bm.getBlock();
						block.validate();
						try {
								JpaBlock prev =  store.get(block.getPreviousHash());
								if ( prev != null )
								{
									if ( store.store(block) )
									{
										log.info("stored " + block.getHash());
										storePending (block.getHash());
									}
								}
								else
								{
									synchronized ( orphanes )
									{
										ArrayList<JpaBlock> l = orphanes.get(block.getPreviousHash());
										if ( l == null )
										{
											l = new ArrayList<JpaBlock> ();
											orphanes.put(block.getPreviousHash(), l);
										}
										log.info("pending " + block.getHash());
										l.add(block);
									}
								}
						} catch (ChainStoreException e) {
							log.error("can not store block", e);
						}
					} catch (ValidationException e) {
						log.error("invalid block", e);
					}
				}});
			
			network.addListener ("inv", new BitcoinMessageListener (){
				@Override
				public void process(Message m, BitcoinPeer peer) {
					InvMessage im = (InvMessage)m;
					if ( !im.getBlockHashes().isEmpty() )
					{
						try {
							GetDataMessage gdm = (GetDataMessage)peer.createMessage("getdata");
							for ( byte [] h : im.getBlockHashes() )
							{
								String hash = new Hash (h).toString();
									if ( store.get(hash) == null )
										gdm.getBlocks().add(h);
							}
							peer.send(gdm);
	
							GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");
							gbm.getHashes().add(im.getBlockHashes().get(im.getBlockHashes().size()-1));
							peer.send(gbm);
						} catch (ChainStoreException e) {
							log.error("can not read store", e);
						}
					}
				}});

		
			network.runForAll(new BitcoinNetwork.PeerTask() {
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
