package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetHeadersMessage;

public class GetHeadersHandler implements BitcoinMessageListener<GetHeadersMessage>
{
	private static final Logger log = LoggerFactory.getLogger (GetHeadersHandler.class);

	public GetHeadersHandler (BitcoinNetwork network)
	{
		network.addListener ("getheader", this);
	}

	@Override
	public void process (GetHeadersMessage m, BitcoinPeer peer) throws Exception
	{
		log.trace ("received getheader");
	}

}
