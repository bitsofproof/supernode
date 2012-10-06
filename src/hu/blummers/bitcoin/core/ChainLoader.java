package hu.blummers.bitcoin.core;

import java.util.List;

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

	public ChainLoader(PlatformTransactionManager transactionManager, BitcoinNetwork network, ChainStore store) {
		this.network = network;
		this.store = store;
		this.transactionManager = transactionManager;
	}

	private ChainStore store;
	private BitcoinNetwork network;
	private PlatformTransactionManager transactionManager;

	private void getAnotherBatch(BitcoinPeer peer) {
		GetBlocksMessage gbm = (GetBlocksMessage) peer.createMessage("getblocks");
		for (String s : store.getLocator())
			gbm.getHashes().add(new Hash(s).toByteArray());
		peer.send(gbm);
		log.info("asking for known blocks from " + peer.getAddress());
	}

	private void getABlock(final BitcoinPeer peer) {
		List<String> requests = store.getRequests(peer);
		if (!requests.isEmpty()) {
			GetDataMessage gdm = (GetDataMessage) peer.createMessage("getdata");
			for (String pick : requests)
				gdm.getBlocks().add(new Hash(pick).toByteArray());
			log.info("asking for blocks "+gdm.getBlocks().size()+" from " + peer.getAddress());
			peer.send(gdm);
		}
	}

	private void processBlock(BlockMessage m, final BitcoinPeer peer) {
		JpaBlock block = m.getBlock();
		try {
			block.validate();
			store.store(block);
		} catch (ValidationException e) {
			log.error("invalid block", e);
		}
	}

	private void processInv(final InvMessage m, final BitcoinPeer peer) {
		if (!m.getBlockHashes().isEmpty()) {
			try {
				log.trace("received inventory of " + m.getBlockHashes().size() + " from " + peer.getAddress());
				for (byte[] h : m.getBlockHashes()) {
					store.addInventory(new Hash(h).toString(), peer);
				}
				if ( m.getBlockHashes().size() < 5 )
					getAnotherBatch (peer);
				else
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
					processBlock((BlockMessage) m, peer);
				}

			});

			network.addListener("inv", new BitcoinMessageListener() {
				@Override
				public void process(Message m, BitcoinPeer peer) {
					processInv((InvMessage) m, peer);
				}

			});

			new TransactionTemplate (transactionManager).execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus arg0) {
					store.cache();
				}});
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
