package com.bitsofproof.supernode.core;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.InvMessage;

public class UnconfirmedTransactions implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (UnconfirmedTransactions.class);

	private final Set<String> seenHashes = new HashSet<String> ();

	private final BitcoinNetwork network;

	public UnconfirmedTransactions (BitcoinNetwork network)
	{
		this.network = network;
	}

	public void start ()
	{
		network.addListener ("inv", this);
	}

	@Override
	public synchronized void process (BitcoinPeer.Message m, BitcoinPeer peer)
	{
		if ( m instanceof InvMessage )
		{
			InvMessage im = (InvMessage) m;
			for ( byte[] h : im.getTransactionHashes () )
			{
				String hash = new Hash (h).toString ();
				if ( seenHashes.contains (hash) )
				{
					return;
				}
				seenHashes.add (hash);
				log.info ("new transaction " + hash);
			}
		}
	}
}
