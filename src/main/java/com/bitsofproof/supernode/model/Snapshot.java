package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
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

	@Lob
	@Basic (fetch = FetchType.LAZY)
	private byte[] utxo;

	@Lob
	@Basic (fetch = FetchType.LAZY)
	private byte[] spendable;

	@Lob
	@Basic (fetch = FetchType.LAZY)
	private byte[] spent;

	@Lob
	@Basic (fetch = FetchType.LAZY)
	private byte[] received;

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

	public byte[] getSpendable ()
	{
		return spendable;
	}

	public void setSpendable (byte[] spendable)
	{
		this.spendable = spendable;
	}

	public byte[] getUtxo ()
	{
		return utxo;
	}

	public void setUtxo (byte[] utxo)
	{
		this.utxo = utxo;
	}

	public byte[] getSpent ()
	{
		return spent;
	}

	public void setSpent (byte[] spent)
	{
		this.spent = spent;
	}

	public byte[] getReceived ()
	{
		return received;
	}

	public void setReceived (byte[] received)
	{
		this.received = received;
	}
}
