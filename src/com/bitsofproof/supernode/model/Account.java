package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity
@Table (name = "acc")
public class Account implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	private final int minVote = 1;

	@OneToMany (fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private List<Owner> owners;

	public List<Owner> getOwners ()
	{
		return owners;
	}

	public void setOwners (List<Owner> owners)
	{
		this.owners = owners;
	}

	public int getMinVote ()
	{
		return minVote;
	}
}
