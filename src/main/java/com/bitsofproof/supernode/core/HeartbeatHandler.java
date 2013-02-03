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
import com.bitsofproof.supernode.model.KnownPeer;

public class HeartbeatHandler implements BitcoinMessageListener<PongMessage>, Runnable, BitcoinPeerListener
{
	private static final Logger log = LoggerFactory.getLogger (HeartbeatHandler.class);
	private final BitcoinNetwork network;
	private int delay = 60;
	private int timeout = 10;
	private final Map<BitcoinPeer, PingPong> sentNonces = Collections.synchronizedMap (new HashMap<BitcoinPeer, PingPong> ());

	private static class PingPong
	{
		long nonce;
		long sent;
	}

	public HeartbeatHandler (BitcoinNetwork network)
	{
		this.network = network;
		network.scheduleJobWithFixedDelay (this, delay, delay, TimeUnit.SECONDS);

		network.addListener ("pong", this);
		network.addPeerListener (this);
	}

	@Override
	public void process (PongMessage m, BitcoinPeer peer)
	{
		log.trace ("Got pong from " + peer.getAddress ().getAddress ());
		PingPong n = sentNonces.get (peer);
		if ( n != null && n.nonce == m.getNonce () )
		{
			sentNonces.remove (peer);
			if ( network.getPeerStore () != null )
			{
				KnownPeer p = network.getPeerStore ().findPeer (peer.getAddress ().getAddress ());
				if ( p != null )
				{
					p.setResponseTime (System.currentTimeMillis () - n.sent);
					network.getPeerStore ().store (p);
				}
			}
		}
	}

	@Override
	public void run ()
	{
		try
		{
			for ( final BitcoinPeer peer : network.getConnectPeers () )
			{
				if ( peer.getLastSpoken () > System.currentTimeMillis () / 1000 - (3 * delay) )
				{
					log.trace ("Disconnected silent peer " + peer.getAddress ());
					peer.disconnect ();
				}
				if ( peer.getLastSpoken () < (System.currentTimeMillis () / 1000 - delay) )
				{
					ping (peer);
				}
			}
		}
		catch ( Exception e )
		{
			log.trace ("Exception in heatbeat thread ", e);
		}
	}

	private void ping (final BitcoinPeer peer)
	{
		PingMessage pi = (PingMessage) peer.createMessage ("ping");
		peer.send (pi);
		log.trace ("Sent ping to " + peer.getAddress ().getAddress ());
		if ( peer.getVersion () > 60000 )
		{
			PingPong pp = new PingPong ();
			pp.nonce = pi.getNonce ();
			pp.sent = System.currentTimeMillis ();
			sentNonces.put (peer, pp);
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

	@Override
	public void remove (BitcoinPeer peer)
	{
		sentNonces.remove (peer);
	}

	@Override
	public void add (BitcoinPeer peer)
	{
		ping (peer);
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
