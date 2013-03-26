/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.supernode.messages;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.api.WireFormat.Reader;
import com.bitsofproof.supernode.api.WireFormat.Writer;
import com.bitsofproof.supernode.core.BitcoinPeer;

public class VersionMessage extends BitcoinPeer.Message
{
	public VersionMessage (BitcoinPeer bitcoinPeer)
	{
		bitcoinPeer.super ("version");
		myport = bitcoinPeer.getNetwork ().getChain ().getPort ();
		nonce = bitcoinPeer.getNetwork ().getVersionNonce ();
	}

	private long services = 1;
	private long timestamp = System.currentTimeMillis () / 1000;
	private InetAddress peer;
	private long remotePort;
	private InetAddress me;
	private long myport;
	private long nonce;
	private String agent = "/bitsofproof:0.8/";
	private long height;

	@Override
	public void toWire (Writer writer)
	{
		writer.writeUint32 (getVersion ());
		writer.writeUint64 (services);
		writer.writeUint64 (timestamp);
		WireFormat.Address a = new WireFormat.Address ();
		a.address = peer;
		a.port = remotePort;
		writer.writeAddress (a, getVersion (), true);
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
