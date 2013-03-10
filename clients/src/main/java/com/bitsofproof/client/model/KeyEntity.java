package com.bitsofproof.client.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table (name = "keystore")
public class KeyEntity
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false)
	private String priv;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getPriv ()
	{
		return priv;
	}

	public void setPriv (String priv)
	{
		this.priv = priv;
	}
}
