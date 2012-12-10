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
package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "peer")
public class KnownPeer implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 40, nullable = false, unique = true)
	private String address;

	private long version;

	private long services;

	private long height;

	@Column (length = 256, nullable = true)
	private String name;

	@Column (length = 64, nullable = true)
	private String agent;

	private long responseTime;

	private long connected;
	private long disconnected;

	private long trafficIn;
	private long trafficOut;

	private long banned;

	@Column (length = 256, nullable = true)
	private String banReason;

	public byte[] toLevelDB ()
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeString (address);
		writer.writeUint64 (version);
		writer.writeUint64 (services);
		writer.writeUint64 (height);
		writer.writeString (name);
		writer.writeString (agent);
		writer.writeUint64 (responseTime);
		writer.writeUint64 (connected);
		writer.writeUint64 (disconnected);
		writer.writeUint64 (trafficIn);
		writer.writeUint64 (trafficOut);
		writer.writeUint64 (banned);
		writer.writeString (banReason);
		return writer.toByteArray ();
	}

	public static KnownPeer fromLevelDB (byte[] data)
	{
		WireFormat.Reader reader = new WireFormat.Reader (data);
		KnownPeer p = new KnownPeer ();
		p.address = reader.readString ();
		p.version = reader.readUint64 ();
		p.services = reader.readUint64 ();
		p.height = reader.readUint64 ();
		p.name = reader.readString ();
		p.agent = reader.readString ();
		p.responseTime = reader.readUint64 ();
		p.connected = reader.readUint64 ();
		p.disconnected = reader.readUint64 ();
		p.trafficIn = reader.readUint64 ();
		p.trafficOut = reader.readUint64 ();
		p.banned = reader.readUint64 ();
		p.banReason = reader.readString ();
		return p;
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
