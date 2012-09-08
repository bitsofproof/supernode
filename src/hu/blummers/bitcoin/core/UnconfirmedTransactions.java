package hu.blummers.bitcoin.core;

import java.util.HashMap;
import java.util.Map;

import org.purser.server.JpaTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnconfirmedTransactions implements BitcoinMessageListener {
	private static final Logger log = LoggerFactory.getLogger(UnconfirmedTransactions.class);
	
	private Map<String, JpaTransaction> transactions = new HashMap<String, JpaTransaction> ();
	
	@Override
	public synchronized void process(BitcoinMessage m, BitcoinPeer peer) {
		if ( m instanceof InvMessage )
		{
			InvMessage im = (InvMessage)m;
			for ( byte [] h : im.getTransactionHashes() )
			{
				String hash = new Hash (h).toString();
				if ( transactions.containsKey(hash) )
				{
					return;
				}
			}
		}
	}
}
