package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.hibernate.annotations.Index;

import com.bitsofproof.supernode.core.WireFormat.Writer;

@Entity
@Table (name = "txa")
public class TxArchive implements Serializable, HasToWire
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	private long ix;

	// this is not unique since a transaction copy might be on different branches.
	@Column (length = 64, nullable = false)
	@Index (name = "txahash")
	private String hash;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	private byte[] wire;

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

	public byte[] getWire ()
	{
		return wire;
	}

	public void setWire (byte[] wire)
	{
		this.wire = wire;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
	}

	@Override
	public void toWire (Writer writer)
	{
		writer.writeBytes (wire);
	}
}
