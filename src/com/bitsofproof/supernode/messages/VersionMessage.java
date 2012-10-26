package com.bitsofproof.supernode.messages;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

import com.bitsofproof.supernode.core.BitcoinPeer;
import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.core.WireFormat.Reader;
import com.bitsofproof.supernode.core.WireFormat.Writer;

public class VersionMessage extends BitcoinPeer.Message
{
	public VersionMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("version");
		myport = bitcoinPeer.getNetwork ().getChain ().getPort ();
	}

	private long services = 1;
	private long timestamp = System.currentTimeMillis () / 1000;
	private InetAddress peer;
	private long remotePort;
	private InetAddress me;
	private long myport;
	private long nonce = new BigInteger (64, new Random ()).longValue ();
	private String agent = "/bitsofproof:0.2/";
	private long height;

	@Override
	public void toWire (Writer writer)
	{
		writer.writeUint32 (getVersion ());
		writer.writeUint64 (services);
		writer.writeUint64 (timestamp);
		WireFormat.Address a = new WireFormat.Address ();
		a.services = services;
		a.time = System.currentTimeMillis () / 1000;
		try
		{
			a.address = InetAddress.getLocalHost ();
			a.port = myport;
			writer.writeAddress (a, getVersion (), true);
		}
		catch ( UnknownHostException e )
		{
		}
		a.address = peer;
		a.port = remotePort;
		writer.writeAddress (a, getVersion (), true);

		writer.writeUint64 (nonce);
		writer.writeString (agent);
		writer.writeUint32 (height);
	}

	@Override
	public void fromWire (Reader reader)
	{
		setVersion (reader.readUint32 ());
		services = reader.readUint64 ();
		timestamp = reader.readUint64 ();
		WireFormat.Address address = reader.readAddress (getVersion (), true);
		me = address.address;
		myport = address.port;
		address = reader.readAddress (getVersion (), true);
		peer = address.address;
		remotePort = address.port;
		nonce = reader.readUint64 ();
		agent = reader.readString ();
		height = reader.readUint32 ();
	}

	public long getServices ()
	{
		return services;
	}

	public void setServices (long services)
	{
		this.services = services;
	}

	public InetAddress getPeer ()
	{
		return peer;
	}

	public void setPeer (InetAddress peer)
	{
		this.peer = peer;
	}

	public InetAddress getMe ()
	{
		return me;
	}

	public void setMe (InetAddress me)
	{
		this.me = me;
	}

	public long getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp (long timestamp)
	{
		this.timestamp = timestamp;
	}

	public long getNonce ()
	{
		return nonce;
	}

	public void setNonce (long nounce)
	{
		this.nonce = nounce;
	}

	public String getAgent ()
	{
		return agent;
	}

	public void setAgent (String agent)
	{
		this.agent = agent;
	}

	public long getHeight ()
	{
		return height;
	}

	public void setHeight (long height)
	{
		this.height = height;
	}

	public long getRemotePort ()
	{
		return remotePort;
	}

	public void setRemotePort (long remotePort)
	{
		this.remotePort = remotePort;
	}

}
