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
package com.bitsofproof.supernode.model;

import java.io.Serializable;

import com.bitsofproof.supernode.common.ValidationException;
import com.google.protobuf.InvalidProtocolBufferException;

public class KnownPeer implements Serializable
{
	private static final long serialVersionUID = 1L;

	private Long id;

	private String address;

	private long version;

	private long services;

	private long height;

	private String name;

	private String agent;

	private long responseTime;

	private long connected;
	private long disconnected;

	private long trafficIn;
	private long trafficOut;

	private long banned;

	private String banReason;

	public byte[] toLevelDB ()
	{
		LevelDBStore.PEER.Builder builder = LevelDBStore.PEER.newBuilder ();
		builder.setStoreVersion (1);
		builder.setAddress (address);
		builder.setVersion ((int) version);
		builder.setServices (services);
		builder.setHeight ((int) height);
		builder.setName (name);
		builder.setAgent (agent);
		builder.setResponseTime (responseTime);
		builder.setConnected (connected);
		builder.setDisconnected (disconnected);
		builder.setTrafficIn (trafficIn);
		builder.setTrafficOut (trafficOut);
		builder.setBanned (banned);
		if ( banReason != null )
		{
			builder.setBanReason (banReason);
		}

		return builder.build ().toByteArray ();
	}

	public static KnownPeer fromLevelDB (byte[] data) throws ValidationException
	{
		LevelDBStore.PEER p;
		try
		{
			p = LevelDBStore.PEER.parseFrom (data);
			KnownPeer e = new KnownPeer ();
			e.address = p.getAddress ();
			e.version = p.getVersion ();
			e.services = p.getServices ();
			e.height = p.getHeight ();
			e.name = p.getName ();
			e.agent = p.getAgent ();
			e.responseTime = p.getResponseTime ();
			e.connected = p.getConnected ();
			e.disconnected = p.getDisconnected ();
			e.trafficIn = p.getTrafficIn ();
			e.trafficOut = p.getTrafficOut ();
			e.banned = p.getBanned ();
			if ( p.hasBanReason () )
			{
				e.banReason = p.getBanReason ();
			}
			return e;
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new ValidationException (e);
		}
	}

	public long getConnected ()
	{
		return connected;
	}

	public void setConnected (long connected)
	{
		this.connected = connected;
	}

	public long getDisconnected ()
	{
		return disconnected;
	}

	public void setDisconnected (long disconnected)
	{
		this.disconnected = disconnected;
	}

	public String getBanReason ()
	{
		return banReason;
	}

	public void setBanReason (String banReason)
	{
		this.banReason = banReason;
	}

	private long preference;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getAddress ()
	{
		return address;
	}

	public void setAddress (String address)
	{
		this.address = address;
	}

	public long getVersion ()
	{
		return version;
	}

	public void setVersion (long version)
	{
		this.version = version;
	}

	public long getHeight ()
	{
		return height;
	}

	public void setHeight (long height)
	{
		this.height = height;
	}

	public String getAgent ()
	{
		return agent;
	}

	public void setAgent (String agent)
	{
		this.agent = agent;
	}

	public long getResponseTime ()
	{
		return responseTime;
	}

	public void setResponseTime (long responseTime)
	{
		this.responseTime = responseTime;
	}

	public String getName ()
	{
		return name;
	}

	public void setName (String name)
	{
		this.name = name;
	}

	public long getTrafficIn ()
	{
		return trafficIn;
	}

	public void setTrafficIn (long trafficIn)
	{
		this.trafficIn = trafficIn;
	}

	public long getTrafficOut ()
	{
		return trafficOut;
	}

	public void setTrafficOut (long trafficOut)
	{
		this.trafficOut = trafficOut;
	}

	public long getBanned ()
	{
		return banned;
	}

	public void setBanned (long banned)
	{
		this.banned = banned;
	}

	public long getPreference ()
	{
		return preference;
	}

	public void setPreference (long preference)
	{
		this.preference = preference;
	}

	public long getServices ()
	{
		return services;
	}

	public void setServices (long services)
	{
		this.services = services;
	}

}
