package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table (name = "stab")
public class Stable implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = true)
	private String upto;

	@OneToMany (fetch = FetchType.LAZY)
	private List<TxOut> out;

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

	public List<TxOut> getOut ()
	{
		return out;
	}

	public void setOut (List<TxOut> out)
	{
		this.out = out;
	}
}
