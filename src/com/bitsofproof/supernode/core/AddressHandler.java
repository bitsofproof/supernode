package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.core.WireFormat.Address;
import com.bitsofproof.supernode.messages.AddrMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;

public class AddressHandler implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (AddressHandler.class);

	@Override
	public void process (Message m, BitcoinPeer peer) throws Exception
	{
		AddrMessage am = (AddrMessage) m;
		for ( Address a : am.getAddresses () )
		{
			log.trace ("received address " + a + " from " + peer.getAddress ());
			peer.getNetwork ().addPeer (a.address, (int) a.port);
		}
	}
}
