package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Index;

@Entity
@Table (name = "own")
public class Owner implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 40, nullable = false)
	@Index (name = "addrindex")
	private String address;

	@ManyToOne
	private TxOut outpoint;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public TxOut getOutpoint ()
	{
		return outpoint;
	}

	public void setOutpoint (TxOut outpoint)
	{
		this.outpoint = outpoint;
	}

	public String getAddress ()
	{
		return address;
	}

	public void setAddress (String address)
	{
		this.address = address;
	}

}
