package com.bitsofproof.supernode.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.PingMessage;
import com.bitsofproof.supernode.messages.PongMessage;

public class PingPongHandler implements BitcoinMessageListener
{
	private static final Logger log = LoggerFactory.getLogger (PingPongHandler.class);

	@Override
	public void process (Message m, BitcoinPeer peer) throws Exception
	{
		PingMessage pi = (PingMessage) m;
		if ( peer.getVersion () > 60000 )
		{
			log.trace ("received ping from " + peer.getAddress ());
			PongMessage po = (PongMessage) peer.createMessage ("pong");
			po.setNonce (pi.getNonce ());
			try
			{
				peer.send (po);
				log.trace ("sent pong to " + peer.getAddress ());
			}
			catch ( Exception e )
			{
				peer.disconnect ();
			}
		}
	}
}
