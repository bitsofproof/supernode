package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table (name = "head")
public class Head implements Serializable
{
	private static final long serialVersionUID = 1L;
	@Id
	@GeneratedValue
	private Long id;
	private double chainWork;
	private int height;

	@ManyToOne (fetch = FetchType.LAZY, optional = true)
	private Head previous;

	@Column (length = 64, nullable = false)
	private String leaf;

	@Column (length = 64, nullable = true)
	private String trunk;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public double getChainWork ()
	{
		return chainWork;
	}

	public void setChainWork (double chainWork)
	{
		this.chainWork = chainWork;
	}

	public int getHeight ()
	{
		return height;
	}

	public void setHeight (int height)
	{
		this.height = height;
	}

	public Head getPrevious ()
	{
		return previous;
	}

	public void setPrevious (Head previous)
	{
		this.previous = previous;
	}

	public String getLeaf ()
	{
		return leaf;
	}

	public void setLeaf (String leaf)
	{
		this.leaf = leaf;
	}

	public String getTrunk ()
	{
		return trunk;
	}

	public void setTrunk (String trunk)
	{
		this.trunk = trunk;
	}
}
