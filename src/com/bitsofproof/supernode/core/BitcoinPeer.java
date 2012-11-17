/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.messages.AddrMessage;
import com.bitsofproof.supernode.messages.AlertMessage;
import com.bitsofproof.supernode.messages.BitcoinMessageListener;
import com.bitsofproof.supernode.messages.BlockMessage;
import com.bitsofproof.supernode.messages.GetBlocksMessage;
import com.bitsofproof.supernode.messages.GetDataMessage;
import com.bitsofproof.supernode.messages.GetHeadersMessage;
import com.bitsofproof.supernode.messages.HeadersMessage;
import com.bitsofproof.supernode.messages.InvMessage;
import com.bitsofproof.supernode.messages.PingMessage;
import com.bitsofproof.supernode.messages.PongMessage;
import com.bitsofproof.supernode.messages.TxMessage;
import com.bitsofproof.supernode.messages.VersionMessage;
import com.bitsofproof.supernode.model.KnownPeer;

public class BitcoinPeer extends P2P.Peer
{
	private static final Logger log = LoggerFactory.getLogger (BitcoinPeer.class);

	private static final long BANTIME = 24 * 60 * 60;

	private final BitcoinNetwork network;

	private String agent;
	private long height;
	private long peerVersion;
	private long peerServices;
	private final boolean outgoing;
	private long lastSpoken;
	private long trafficIn;
	private long trafficOut;

	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);
	private static final long CONNECTIONTIMEOUT = 30;

	public class Message implements P2P.Message
	{
		private final String command;
		private long version;

		public Message (String command)
		{
			this.command = command;
			version = peerVersion;
		}

		public long getVersion ()
		{
			return version;
		}

		public void setVersion (long version)
		{
			this.version = version;
		}

		public String getCommand ()
		{
			return command;
		}

		@Override
		public byte[] toByteArray ()
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream ();
			WireFormat.Writer writer = new WireFormat.Writer (out);
			writer.writeUint32 (network.getChain ().getMagic ());
			writer.writeZeroDelimitedString (getCommand (), 12);
			WireFormat.Writer payload = new WireFormat.Writer ();
			toWire (payload);
			byte[] data = payload.toByteArray ();
			writer.writeUint32 (data.length);

			byte[] checksum = new byte[4];
			System.arraycopy (Hash.hash (data), 0, checksum, 0, 4);
			writer.writeBytes (checksum);

			writer.writeBytes (data);
			byte[] a = writer.toByteArray ();
			trafficOut += a.length;
			return a;
		}

		public void toWire (WireFormat.Writer writer)
		{
		};

		public void fromWire (WireFormat.Reader reader)
		{
		};
	}

	public Message createMessage (String command)
	{
		if ( command.equals ("version") )
		{
			return new VersionMessage (this);
		}
		else if ( command.equals ("inv") )
		{
			return new InvMessage (this);
		}
		else if ( command.equals ("addr") )
		{
			return new AddrMessage (this);
		}
		else if ( command.equals ("getdata") )
		{
			return new GetDataMessage (this);
		}
		else if ( command.equals ("getblocks") )
		{
			return new GetBlocksMessage (this);
		}
		else if ( command.equals ("getheaders") )
		{
			return new GetHeadersMessage (this);
		}
		else if ( command.equals ("block") )
		{
			return new BlockMessage (this);
		}
		else if ( command.equals ("headers") )
		{
			return new HeadersMessage (this);
		}
		else if ( command.equals ("alert") )
		{
			return new AlertMessage (this);
		}
		else if ( command.equals ("ping") )
		{
			return new PingMessage (this);
		}
		else if ( command.equals ("pong") )
		{
			return new PongMessage (this);
		}
		else if ( command.equals ("tx") )
		{
			return new TxMessage (this);
		}
		else if ( command.equals ("verack") )
		{
			return new Message (command);
		}
		log.trace ("Peer sent unknown message. Banned. " + getAddress ());
		ban ("Sent unknown message");
		return new Message (command);
	}

	public long getLastSpoken ()
	{
		return lastSpoken;
	}

	private final Map<String, ArrayList<BitcoinMessageListener<? extends BitcoinPeer.Message>>> listener = Collections
			.synchronizedMap (new HashMap<String, ArrayList<BitcoinMessageListener<? extends BitcoinPeer.Message>>> ());

	protected BitcoinPeer (P2P p2p, final InetSocketAddress address, boolean out)
	{
		p2p.super (address);
		network = (BitcoinNetwork) p2p;
		this.outgoing = out;

		// this will be overwritten by the first version message we get
		peerVersion = network.getChain ().getVersion ();

		addListener ("version", new BitcoinMessageListener<VersionMessage> ()
		{
			@Override
			public void process (VersionMessage v, BitcoinPeer peer)
			{
				if ( v.getNonce () == network.getVersionNonce () )
				{
					disconnect (0, Integer.MAX_VALUE, "myself"); // connect to self
				}
				else
				{
					agent = v.getAgent ();
					height = v.getHeight ();
					peerVersion = Math.min (peerVersion, v.getVersion ());
					peerServices = v.getServices ();
					if ( network.getPeerStore () != null )
					{
						KnownPeer p = network.getPeerStore ().findPeer (getAddress ().getAddress ());
						if ( p == null )
						{
							p = new KnownPeer ();
						}
						trafficIn = p.getTrafficIn ();
						trafficOut = p.getTrafficOut ();
						if ( p.getBanned () > System.currentTimeMillis () / 1000 )
						{
							log.trace ("Disconnecting banned peer " + address);
							peer.disconnect ();
						}
						else
						{
							p.setAddress (address.getAddress ().getHostAddress ());
							p.setName (address.toString ());
							p.setVersion (peerVersion);
							p.setHeight (height);
							p.setAgent (agent);
							p.setServices (peerServices);
							p.setResponseTime (Integer.MAX_VALUE);
							p.setConnected (System.currentTimeMillis () / 1000);
							network.getPeerStore ().store (p);
						}
					}
					if ( !outgoing )
					{
						onConnect ();
					}
					peer.send (peer.createMessage ("verack"));
				}
			}
		});

		addListener ("verack", new BitcoinMessageListener<BitcoinPeer.Message> ()
		{
			@Override
			public void process (BitcoinPeer.Message m, BitcoinPeer peer)
			{
				log.trace ("got verack from " + getAddress ());
				log.info ("Connection to '" + getAgent () + "' [" + peerVersion + "] at " + getAddress () + " Open connections: "
						+ getNetwork ().getNumberOfConnections ());
				network.addPeer (peer);
			}
		});
	}

	public BitcoinNetwork getNetwork ()
	{
		return network;
	}

	public long getVersion ()
	{
		return peerVersion;
	}

	public long getServices ()
	{
		return peerServices;
	}

	public long getHeight ()
	{
		return height;
	}

	public String getAgent ()
	{
		return agent;
	}

	public void ban (String reason)
	{
		disconnect (0, BANTIME, reason);
	}

	@Override
	protected void onDisconnect (long timeout, long bannedForSeconds, String reason)
	{
		network.notifyPeerRemoved (this);
		log.info ("Disconnected '" + getAgent () + "' at " + getAddress () + ". Open connections: " + getNetwork ().getNumberOfConnections ());
		if ( network.getPeerStore () != null )
		{
			KnownPeer p = network.getPeerStore ().findPeer (getAddress ().getAddress ());
			p.setTrafficIn (trafficIn);
			p.setTrafficOut (trafficOut);
			p.setDisconnected (System.currentTimeMillis () / 1000);
			if ( timeout > 0 )
			{
				p.setResponseTime (timeout * 1000 + 1);
			}
			if ( bannedForSeconds > 0 )
			{
				p.setBanned (System.currentTimeMillis () / 1000 + bannedForSeconds);
				p.setBanReason (reason);
			}
			network.getPeerStore ().store (p);
		}
	}

	private static final int MAX_BLOCK_SIZE = 1000000;

	@Override
	protected Message parse (InputStream readIn) throws IOException
	{
		try
		{
			byte[] head = new byte[24];
			if ( readIn.read (head) != head.length )
			{
				throw new ValidationException ("Read timeout for " + getAddress ());
			}
			WireFormat.Reader reader = new WireFormat.Reader (head);
			long mag = reader.readUint32 ();
			if ( mag != network.getChain ().getMagic () )
			{
				log.trace ("Peer talks to thw wrong chain. Banned.");
				ban ("Talked to the wrong chain");
				throw new ValidationException ("Wrong magic for this chain " + getAddress ());
			}

			String command = reader.readZeroDelimitedString (12);
			Message m = createMessage (command);
			long length = reader.readUint32 ();
			byte[] checksum = reader.readBytes (4);
			if ( length < 0 || length >= MAX_BLOCK_SIZE )
			{
				log.trace ("Peer sends oversize message. Banned.");
				ban ("Sent oversize message");
				throw new ValidationException ("Block size limit exceeded " + getAddress ());
			}
			else
			{
				byte[] buf = new byte[(int) length];
				if ( readIn.read (buf) != buf.length )
				{
					throw new ValidationException ("Package length mismatch " + getAddress ());
				}
				byte[] cs = new byte[4];
				System.arraycopy (Hash.hash (buf), 0, cs, 0, 4);
				if ( !Arrays.equals (cs, checksum) )
				{
					ban ("Message checksum mismatch");
					throw new ValidationException ("Checksum mismatch " + getAddress ());
				}

				m.fromWire (new WireFormat.Reader (buf));
			}
			lastSpoken = System.currentTimeMillis () / 1000;
			trafficIn += length;
			return m;
		}
		catch ( ValidationException e )
		{
			throw new IOException (e);
		}
	}

	@Override
	protected void onConnect ()
	{
		VersionMessage m = (VersionMessage) createMessage ("version");
		m.setHeight (network.getChainHeight ());
		m.setPeer (getAddress ().getAddress ());
		m.setRemotePort (getAddress ().getPort ());
		send (m);
		log.trace ("Sent version to " + getAddress ());
		final BitcoinPeer peer = this;
		scheduler.schedule (new Runnable ()
		{
			@Override
			public void run ()
			{
				if ( !network.isConnected (peer) )
				{
					peer.disconnect ();
				}
			}
		}, CONNECTIONTIMEOUT, TimeUnit.SECONDS);
	}

	@Override
	protected boolean isHandshakeSuccessful ()
	{
		return network.isConnected (this);
	}

	@Override
	@SuppressWarnings ({ "rawtypes", "unchecked" })
	protected void receive (P2P.Message m)
	{
		final BitcoinPeer self = this;
		final BitcoinPeer.Message bm = (Message) m;
		List<BitcoinMessageListener<? extends BitcoinPeer.Message>> classListener = listener.get (bm.getCommand ());
		if ( classListener != null )
		{
			for ( BitcoinMessageListener l : classListener )
			{
				l.process (bm, self);
			}
		}
	}

	protected void addListener (String type, BitcoinMessageListener<? extends BitcoinPeer.Message> l)
	{
		ArrayList<BitcoinMessageListener<? extends BitcoinPeer.Message>> ll = listener.get (type);
		if ( ll == null )
		{
			ll = new ArrayList<BitcoinMessageListener<? extends BitcoinPeer.Message>> ();
			listener.put (type, ll);
		}
		if ( !ll.contains (l) )
		{
			ll.add (l);
		}
	}

	protected void removeListener (String type, BitcoinMessageListener<? extends BitcoinPeer.Message> l)
	{
		ArrayList<BitcoinMessageListener<? extends BitcoinPeer.Message>> ll = listener.get (type);
		if ( ll != null )
		{
			ll.remove (l);
		}
	}
}
