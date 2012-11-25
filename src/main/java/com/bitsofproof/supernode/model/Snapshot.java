package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table (name = "snap")
public class Snapshot implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne (fetch = FetchType.LAZY)
	private Blk block;

	@OneToMany (cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<UTxOut> utxo;

	@OneToMany (cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Spent> spent;

	@OneToMany (cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Received> received;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public Blk getBlock ()
	{
		return block;
	}

	public void setBlock (Blk block)
	{
		this.block = block;
	}

	public List<UTxOut> getUtxo ()
	{
		return utxo;
	}

	public void setUtxo (List<UTxOut> utxo)
	{
		this.utxo = utxo;
	}

	public List<Spent> getSpent ()
	{
		return spent;
	}

	public void setSpent (List<Spent> spent)
	{
		this.spent = spent;
	}

	public List<Received> getReceived ()
	{
		return received;
	}

	public void setReceived (List<Received> received)
	{
		this.received = received;
	}
}
