package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class GetBlocksHandler implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (GetBlocksHandler.class);

	public GetBlocksHandler (BitcoinNetwork network)
	{
		network.addListener ("getblocks", this);
	}

	@Override
	public void process (Message m, BitcoinPeer peer) throws Exception
	{

	}

}
