package hu.blummers.bitcoin.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import hu.blummers.bitcoin.core.BitcoinPeer.Message;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.BlockMessage;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.GetDataMessage;
import hu.blummers.bitcoin.messages.InvMessage;
import hu.blummers.bitcoin.model.JpaBlock;

public class ChainLoader {
	private static final Logger log = LoggerFactory.getLogger(ChainLoader.class);
	
	public ChainLoader (PlatformTransactionManager transactionManager, BitcoinNetwork network, ChainStore store)
	{
		this.network = network;
		this.store = store;
	}

	ChainStore store;
	BitcoinNetwork network;

	private List<String> currentBatch = Collections.synchronizedList(new LinkedList<String>());
	private Map<String, HashMap<String,JpaBlock>> pending = Collections.synchronizedMap(new HashMap<String, HashMap<String,JpaBlock>>());
	private Set<String> inPending = Collections.synchronizedSet(new HashSet<String>());

	// this is already synchronized on pending, factored out as recursive
	private void storePending(String hash) throws ChainStoreException {
		HashMap<String,JpaBlock> bs = pending.get(hash);
		if (bs != null)
		{
			for (JpaBlock b : bs.values()) {
				store.store(b);
				inPending.remove(b.getHash());
				log.info("stored pending " + b.getHash());
				storePending (b.getHash());
			}
			pending.remove(hash);
		}
	}

	private void getAnotherBatch(BitcoinPeer peer) {
		GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");
		String headHash = network.getStore().getHeadHash();
		gbm.getHashes().add(new Hash(headHash).toByteArray());
		
		JpaBlock curr = store.get(headHash);
		JpaBlock prev = curr.getPrevious();
		
		for (int i = 0; prev!=null && i < 9; ++i) {
			gbm.getHashes().add(new Hash(headHash).toByteArray());
			curr = prev;
			prev = curr.getPrevious();
		}
		for (int step = 2; prev != null; step *= 2) {
			for ( int i = 0; prev != null && i < step; ++i ) {
				curr = prev;
				prev = curr.getPrevious();
			}
			if ( prev != null )
				gbm.getHashes().add(new Hash (prev.getHash()).toByteArray());
		}
		if ( !curr.getHash().equals(network.getChain().getGenesis().getHash()) )
		{
			gbm.getHashes().add(new Hash (network.getChain().getGenesis().getHash()).toByteArray());
		}
		peer.send(gbm);
		log.info("asking for a batch of " + gbm.getHashes().size() + " from " + peer.getAddress());
	}

	private void getABlock(final BitcoinPeer peer) {
			if (!currentBatch.isEmpty()) {
				GetDataMessage gdm = null;
				synchronized (currentBatch) {
					if ( !currentBatch.isEmpty() )
					{
						gdm = (GetDataMessage) peer.createMessage("getdata");
						String pick = currentBatch.iterator().next();
						currentBatch.remove(pick);
						gdm.getBlocks().add(new Hash(pick).toByteArray());
					}
				}
				if ( gdm != null )
				{
					peer.send(gdm);
					log.info("asking for block " + new Hash(gdm.getBlocks().get(0)) + " from " + peer.getAddress());
				}
			} else {
				getAnotherBatch(peer);
			}
	}

	private void processBlock(BlockMessage m, final BitcoinPeer peer) {
		JpaBlock block = m.getBlock();
		try {
			block.validate();
			try {
				synchronized (pending) {
					if (store.get(block.getPreviousHash()) != null) {
						if (store.get(block.getHash()) == null) {
							store.store(block);
							log.info("stored " + block.getHash());
							storePending(block.getHash());
						}
					} else {
						if ( pending.values().size() < 100 )
						{
							HashMap<String,JpaBlock> bs = pending.get(block.getPreviousHash());
							if (bs == null) {
								bs = new HashMap<String,JpaBlock>();
								pending.put(block.getPreviousHash(), bs);
							}
							bs.put(block.getHash(), block);
							inPending.add(block.getHash());
							log.info("queueing block " + block.getHash() + " from " + peer.getAddress());
						}
						else
						{
							log.info("too many pending give up");
							pending.clear();
						}
					}
				}
				getABlock(peer);
			} catch (ChainStoreException e) {
				log.error("can not store block", e);
			}
		} catch (ValidationException e) {
			log.error("invalid block", e);
		}
	}

	private void processInv(final InvMessage m, final BitcoinPeer peer) {
		if (!m.getBlockHashes().isEmpty()) {
			try {
				log.info("received inventory of " + m.getBlockHashes().size() + " from " + peer.getAddress());
				synchronized (pending) // avoid deadlock: using same order locks as in processBlock
				{
					synchronized (currentBatch) {
						for (byte[] h : m.getBlockHashes()) {
							String hash = new Hash(h).toString();
							if (!currentBatch.contains(hash) && !inPending.contains(hash) && store.get(hash) == null)
								currentBatch.add(hash);
						}
						log.info("makes current batch to " + currentBatch.size());
					}
				}
				getABlock(peer);
			} catch (Exception e) {
				log.error("can not read store", e);
			}
		}
	}

	public void start() {
		try {
			network.addListener("block", new BitcoinMessageListener() {
				public void process(Message m, BitcoinPeer peer) {
					processBlock((BlockMessage)m, peer);
				}

			});

			network.addListener("inv", new BitcoinMessageListener() {
				@Override
				public void process(Message m, BitcoinPeer peer) {
					processInv((InvMessage)m, peer);
				}

			});

			network.runForAll(new BitcoinNetwork.PeerTask() {
				public void run(final BitcoinPeer peer) {
					getAnotherBatch(peer);
				}

			});
		} catch (Exception e) {
			log.error("Could not start chain loader", e);
		}
	}
}
