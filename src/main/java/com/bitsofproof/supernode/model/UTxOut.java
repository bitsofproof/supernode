package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

@Entity
@Table (name = "utxout")
public class UTxOut implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64, nullable = false)
	@Index (name = "utxhash")
	private String hash;

	private long ix;

	// this is really one/zero-to-one
	@ManyToOne (fetch = FetchType.LAZY, optional = false)
	private TxOut txout;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public String getHash ()
	{
		return hash;
	}

	public void setHash (String hash)
	{
		this.hash = hash;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
	}

	public TxOut getTxout ()
	{
		return txout;
	}

	public void setTxout (TxOut txout)
	{
		this.txout = txout;
	}

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.append ("tx", hash);
			o.append ("ix", ix);
			o.append ("out", txout.toJSON ());
		}
		catch ( JSONException e )
		{
		}
		return o;
	}
}
