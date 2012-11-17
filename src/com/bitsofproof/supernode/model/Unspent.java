package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

@Entity
@Table (name = "uns")
public class Unspent implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false, unique = true)
	private String upto;

	@ManyToMany (fetch = FetchType.LAZY)
	private Set<TxOut> outputs;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getUpto ()
	{
		return upto;
	}

	public void setUpto (String upto)
	{
		this.upto = upto;
	}

	public Set<TxOut> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (Set<TxOut> outputs)
	{
		this.outputs = outputs;
	}

}
