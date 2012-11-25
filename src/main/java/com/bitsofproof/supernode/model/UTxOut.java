package com.bitsofproof.supernode.model;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

@Entity
@Table (name = "utxo")
public class UTxOut implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false)
	private String txhash;

	@ManyToMany (cascade = { CascadeType.DETACH, CascadeType.REFRESH }, fetch = FetchType.EAGER)
	private List<TxOut> outputs;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public List<TxOut> getOutputs ()
	{
		return outputs;
	}

	public void setOutputs (List<TxOut> outputs)
	{
		this.outputs = outputs;
	}

	public String getTxhash ()
	{
		return txhash;
	}

	public void setTxhash (String txhash)
	{
		this.txhash = txhash;
	}

}
