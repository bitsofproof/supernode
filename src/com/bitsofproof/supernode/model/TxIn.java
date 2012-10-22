package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "txin")
public class TxIn implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	transient private String sourceHash;
	transient private long ix;

	@ManyToOne (fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.DETACH, CascadeType.PERSIST, CascadeType.REFRESH }, optional = false)
	private Tx transaction;

	@OneToOne (fetch = FetchType.LAZY, optional = true)
	private TxOut source;

	private long sequence;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	private byte[] script;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getIx ()
	{
		return ix;
	}

	public void setIx (long ix)
	{
		this.ix = ix;
	}

	public String getSourceHash ()
	{
		return sourceHash;
	}

	public void setSourceHash (String sourceHash)
	{
		this.sourceHash = sourceHash;
	}

	public TxOut getSource ()
	{
		return source;
	}

	public void setSource (TxOut source)
	{
		this.source = source;
	}

	public long getSequence ()
	{
		return sequence;
	}

	public void setSequence (long sequence)
	{
		this.sequence = sequence;
	}

	public byte[] getScript ()
	{
		return script;
	}

	public void setScript (byte[] script)
	{
		this.script = script;
	}

	public Tx getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (Tx transaction)
	{
		this.transaction = transaction;
	}

	public String toJSON ()
	{
		StringBuffer b = new StringBuffer ();
		b.append ("{");
		if ( source != null )
		{
			b.append ("sourceHash:\"" + source.getTransaction ().getHash () + "\",");
			b.append ("sourceIx:" + source.getIx () + ",");
		}
		else if ( sourceHash != null )
		{
			b.append ("sourceHash:\"" + sourceHash + "\",");
			b.append ("sourceIx:" + ix + ",");
		}
		else
		{
			b.append ("sourceHash:\"" + Hash.ZERO_HASH.toByteArray () + "\",");
			b.append ("sourceIx:" + -1 + ",");
		}
		b.append ("script:\"" + new Script (script).toString () + "\"" + ",");
		b.append ("sequence:" + String.valueOf (sequence));
		b.append ("}");
		return b.toString ();
	}

	public void toWire (WireFormat.Writer writer)
	{
		if ( source != null )
		{
			writer.writeHash (new Hash (source.getTransaction ().getHash ()));
			writer.writeUint32 (source.getIx ());
		}
		else if ( sourceHash != null )
		{
			writer.writeHash (new Hash (sourceHash));
			writer.writeUint32 (ix);
		}
		else
		{
			writer.writeBytes (Hash.ZERO_HASH.toByteArray ());
			writer.writeUint32 (-1);
		}
		writer.writeVarBytes (script);
		writer.writeUint32 (sequence);
	}

	public void fromWire (WireFormat.Reader reader)
	{
		sourceHash = reader.readHash ().toString ();
		ix = reader.readUint32 ();
		source = null;
		script = reader.readVarBytes ();
		sequence = reader.readUint32 ();
	}
}
