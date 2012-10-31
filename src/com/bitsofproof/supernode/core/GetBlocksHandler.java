package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.GetBlocksMessage;

public class GetBlocksHandler implements BitcoinMessageListener<GetBlocksMessage>
{
	private static final Logger log = LoggerFactory.getLogger (GetBlocksHandler.class);

	public GetBlocksHandler (BitcoinNetwork network)
	{
		network.addListener ("getblocks", this);
	}

	@Override
	public void process (GetBlocksMessage m, BitcoinPeer peer) throws Exception
	{
		log.trace ("received gedblocks");
	}

}
