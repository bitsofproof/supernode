package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class GetHeadersHandler implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (GetHeadersHandler.class);

	public GetHeadersHandler (BitcoinNetwork network)
	{
		network.addListener ("getheader", this);
	}

	@Override
	public void process (Message m, BitcoinPeer peer) throws Exception
	{
		log.trace ("received getheader");
	}

}
