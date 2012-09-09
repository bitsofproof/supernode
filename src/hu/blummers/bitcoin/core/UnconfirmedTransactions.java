package hu.blummers.bitcoin.core;

import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.InvMessage;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnconfirmedTransactions implements BitcoinMessageListener {
	private static final Logger log = LoggerFactory.getLogger(UnconfirmedTransactions.class);
	
	private Set<String> seenHashes = new HashSet<String>();
	
	private BitcoinNetwork network;
	
	public UnconfirmedTransactions (BitcoinNetwork network)
	{
		this.network = network;
	}
	
	public void start ()
	{
		network.addListener("inv", this);
	}
	
	
	@Override
	public synchronized void process(BitcoinPeer.Message m, BitcoinPeer peer) {
		if ( m instanceof InvMessage )
		{
			InvMessage im = (InvMessage)m;
			for ( byte [] h : im.getTransactionHashes() )
			{
				String hash = new Hash (h).toString();
				if ( seenHashes.contains(hash) )
				{
					return;
				}
				seenHashes.add(hash);				
				log.info("new transaction " + hash);
			}
		}
	}
}
