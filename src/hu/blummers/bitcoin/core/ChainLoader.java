package hu.blummers.bitcoin.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	
	private Map<String, JpaBlock> orphanes = Collections.synchronizedMap(new HashMap<String, JpaBlock>());
	private Set<String> askedFor = Collections.synchronizedSet(new HashSet<String> ());

	ChainStore store;
	BitcoinNetwork network;

	public ChainLoader (BitcoinNetwork network, ChainStore store)
	{
		this.network = network;
		this.store = store;
	}
	
	private List<JpaBlock> storeable (String h) throws ChainStoreException
	{
		List<JpaBlock> toStore = new ArrayList<JpaBlock> ();
		for ( JpaBlock b : orphanes.values() )
		{
			if ( b.getPreviousHash().equals(h) )
			{
				toStore.add(b);
				toStore.addAll(storeable (b.getHash()));
			}
		}
		return toStore;
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
						askedFor.remove(block.getHash());
						try {
								if ( store.get(block.getPreviousHash()) != null )
								{
									if ( store.store(block) )
									{
										log.info("stored " + block.getHash());
										synchronized ( orphanes )
										{
											for ( JpaBlock b : storeable (block.getHash()) )
											{
												log.info("flushing " + b.getHash ());
												store.store (b);
												orphanes.remove(b.getHash());
											}
										}
									}
								}
								else
								{
									orphanes.put(block.getHash(), block);
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
								if ( !askedFor.contains(hash) && !orphanes.containsKey(hash) && store.get(hash) == null )
								{
									gdm.getBlocks().add(h);
									askedFor.add(hash);
								}
							}
							peer.send(gdm);
							if ( im.getBlockHashes().size() > 100 )
							{
								GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");
								gbm.getHashes().add(im.getBlockHashes().get(im.getBlockHashes().size()-1));
								peer.send(gbm);
							}
						} catch (Exception e) {
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
