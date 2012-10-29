package com.bitsofproof.supernode.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.core.BitcoinNetwork.PeerTask;
import com.bitsofproof.supernode.core.BitcoinPeer.Message;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.PingMessage;
import com.bitsofproof.supernode.messages.PongMessage;

public class HeartbeatHandler implements BitcoinMessageListener, Runnable
{
	private static final Logger log = LoggerFactory.getLogger (HeartbeatHandler.class);
	private final BitcoinNetwork network;
	private int delay = 60;
	private int timeout = 10;
	private final Map<BitcoinPeer, Long> sentNonces = Collections.synchronizedMap (new HashMap<BitcoinPeer, Long> ());

	public HeartbeatHandler (BitcoinNetwork network)
	{
		this.network = network;
		network.scheduleWithFixedDelay (this, delay, delay, TimeUnit.SECONDS);

		network.addListener ("pong", this);
	}

	@Override
	public void process (Message m, BitcoinPeer peer) throws Exception
	{
		log.trace ("Got pong from " + peer.getAddress ().getAddress ());
		Long n = sentNonces.get (peer);
		if ( n != null && n.longValue () == ((PongMessage) m).getNonce () )
		{
			sentNonces.remove (peer);
		}
	}

	@Override
	public void run ()
	{
		try
		{
			network.runForConnected (new PeerTask ()
			{

				@Override
				public void run (final BitcoinPeer peer) throws Exception
				{
					if ( peer.getLastSpoken () > (System.currentTimeMillis () / 1000 - delay) )
					{
						return;
					}
					PingMessage pi = (PingMessage) peer.createMessage ("ping");
					peer.send (pi);
					log.trace ("Sent ping to " + peer.getAddress ().getAddress ());
					if ( peer.getVersion () > 60000 )
					{
						sentNonces.put (peer, pi.getNonce ());
						network.schedule (new Runnable ()
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
			});
		}
		catch ( Exception e )
		{
			log.error ("Ignored exception in heartbeat", e);
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
