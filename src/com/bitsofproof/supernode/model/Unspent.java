package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table (name = "uns")
public class Unspent implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne (optional = false, fetch = FetchType.LAZY)
	private Blk upto;

	@OneToMany (fetch = FetchType.LAZY)
	private List<TxOut> outputs;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public Blk getUpto ()
	{
		return upto;
	}

	public void setUpto (Blk upto)
	{
		this.upto = upto;
	}

	public List<TxOut> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (List<TxOut> outputs)
	{
		this.outputs = outputs;
	}

}
