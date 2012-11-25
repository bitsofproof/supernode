package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
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

	@Column (length = 64, nullable = false, unique = true)
	private String block;

	@OneToMany (cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<TxOut> utxo;

	@OneToMany (cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Spent> spendt;

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

	public String getBlock ()
	{
		return block;
	}

	public void setBlock (String block)
	{
		this.block = block;
	}

	public List<TxOut> getUtxo ()
	{
		return utxo;
	}

	public void setUtxo (List<TxOut> utxo)
	{
		this.utxo = utxo;
	}

	public List<Spent> getSpendt ()
	{
		return spendt;
	}

	public void setSpendt (List<Spent> spendt)
	{
		this.spendt = spendt;
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
