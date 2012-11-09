package com.bitsofproof.supernode.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.PingMessage;
import com.bitsofproof.supernode.messages.PongMessage;

public class HeartbeatHandler implements BitcoinMessageListener<PongMessage>, Runnable
{
	private static final Logger log = LoggerFactory.getLogger (HeartbeatHandler.class);
	private final BitcoinNetwork network;
	private int delay = 60;
	private int timeout = 10;
	private final Map<BitcoinPeer, Long> sentNonces = Collections.synchronizedMap (new HashMap<BitcoinPeer, Long> ());

	public HeartbeatHandler (BitcoinNetwork network)
	{
		this.network = network;
		network.scheduleJobWithFixedDelay (this, delay, delay, TimeUnit.SECONDS);

		network.addListener ("pong", this);
	}

	@Override
	public void process (PongMessage m, BitcoinPeer peer)
	{
		log.trace ("Got pong from " + peer.getAddress ().getAddress ());
		Long n = sentNonces.get (peer);
		if ( n != null && n.longValue () == m.getNonce () )
		{
			sentNonces.remove (peer);
		}
	}

	@Override
	public void run ()
	{
		for ( final BitcoinPeer peer : network.getConnectPeers () )
		{
			if ( peer.getLastSpoken () > (System.currentTimeMillis () / 1000 - delay) )
			{
				return;
			}
			PingMessage pi = (PingMessage) peer.createMessage ("ping");
			log.trace ("Sent ping to " + peer.getAddress ().getAddress ());
			if ( peer.getVersion () > 60000 )
			{
				sentNonces.put (peer, pi.getNonce ());
				network.scheduleJob (new Runnable ()
				{
					@Override
					public void run ()
					{
						if ( sentNonces.containsKey (peer) )
						{
							log.trace ("Peer does not answer ping. Disconnecting." + peer.getAddress ().getAddress ());
							sentNonces.remove (peer);
							peer.disconnect ();
						}
					}
				}, timeout, TimeUnit.SECONDS);
			}
		}
	}

	public int getDelay ()
	{
		return delay;
	}

	public void setDelay (int delay)
	{
		this.delay = delay;
	}

	public int getTimeout ()
	{
		return timeout;
	}

	public void setTimeout (int timeout)
	{
		this.timeout = timeout;
	}

}
