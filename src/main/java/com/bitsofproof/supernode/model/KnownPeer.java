package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

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
