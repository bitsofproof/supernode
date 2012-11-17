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

	@Column (length = 32, nullable = false, unique = true)
	private String address;

	private long lastSeen;

	private long version;

	private long height;

	@Column (length = 256, nullable = true)
	private String name;

	@Column (length = 32, nullable = false)
	private String agent;

	private long responseTime;

	private long traffic;

	private long banned;

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

	public long getLastSeen ()
	{
		return lastSeen;
	}

	public void setLastSeen (long lastSeen)
	{
		this.lastSeen = lastSeen;
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

	public long getTraffic ()
	{
		return traffic;
	}

	public void setTraffic (long traffic)
	{
		this.traffic = traffic;
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

}
