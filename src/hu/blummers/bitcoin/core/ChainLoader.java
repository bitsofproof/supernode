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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

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

	private Set<String> currentBatch = Collections.synchronizedSet(new HashSet<String>());
	private Map<String, HashSet<JpaBlock>> pending = Collections.synchronizedMap(new HashMap<String, HashSet<JpaBlock>>());

	private List<JpaBlock> storePending(String hash) throws ChainStoreException {
		List<JpaBlock> stored = new ArrayList<JpaBlock>();
		Set<JpaBlock> bs = pending.get(hash);
		if (bs != null)
			for (JpaBlock b : bs) {
				store.store(b);
				log.info("stored pending " + b.getHash());
				stored.add(b);
				stored.addAll(storePending(b.getHash()));
			}
		return stored;
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
	}

	private void getABlock(final BitcoinPeer peer) {
		synchronized (currentBatch) {
			if (!currentBatch.isEmpty()) {
				GetDataMessage gdm = (GetDataMessage) peer.createMessage("getdata");
				String pick = currentBatch.iterator().next();
				currentBatch.remove(pick);

				gdm.getBlocks().add(new Hash(pick).toByteArray());
				peer.send(gdm);
			} else {
				getAnotherBatch(peer);
			}
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
							List<JpaBlock> stored = storePending(block.getHash());
							for (JpaBlock b : stored)
								pending.remove(b);
						}
					} else {
						HashSet<JpaBlock> bs = pending.get(block.getPreviousHash());
						if (bs == null) {
							bs = new HashSet<JpaBlock>();
							pending.put(block.getPreviousHash(), bs);
						}
						bs.add(block);
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
				synchronized (currentBatch) {
					for (byte[] h : m.getBlockHashes()) {
						String hash = new Hash(h).toString();
						if (!currentBatch.contains(hash) && store.get(hash) == null)
							currentBatch.add(hash);
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
