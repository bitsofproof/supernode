package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.jpa.JpaBlock;
import hu.blummers.bitcoin.messages.GetBlocksMessage;
import hu.blummers.bitcoin.messages.MessageFactory;
import hu.blummers.p2p.P2P;
import hu.blummers.p2p.P2P.Peer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import edu.emory.mathcs.backport.java.util.Collections;

public class ChainLoader {
	@SuppressWarnings("unchecked")
	List<BlockListener> listener = Collections.synchronizedList(new ArrayList<BlockListener> ());
	
	public void addListener (BlockListener l)
	{
		listener.add(l);
	}
	
	private void notify (JpaBlock block)
	{
		for ( BlockListener b : listener )
			b.received(block);
	}
	
	public void startDownloading (final ChainStore chainstore)
	{
		chainstore.getNetwork().forAllConnected(new P2P.PeerTask () {
			@Override
			public void run(Peer peer) {
				BitcoinPeer bp = (BitcoinPeer)peer;
				GetBlocksMessage gbm = (GetBlocksMessage)MessageFactory.createMessage(chainstore.getNetwork().getChain(), "getblocks");
				peer.send(gbm);
			}});
	}
}
