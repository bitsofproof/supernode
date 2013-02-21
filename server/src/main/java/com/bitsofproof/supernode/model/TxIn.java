/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.model;

import java.io.Serializable;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;

@Entity
@Table (name = "txin")
public class TxIn implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64)
	private String sourceHash;

	@Column (nullable = false)
	private Long ix = new Long (0L);

	@ManyToOne (fetch = FetchType.LAZY, optional = false)
	private Tx transaction;

	@OneToOne (fetch = FetchType.LAZY, optional = true)
	private TxOut source;

	private long sequence = 0xFFFFFFFFL;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	// scriptSig
	private byte[] script;

	// this is redundant for joinless account balance
	@Index (name = "intime")
	private long blockTime;

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public Long getIx ()
	{
		return ix;
	}

	public void setIx (Long ix)
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
		if ( script != null )
		{
			byte[] s = new byte[script.length];
			System.arraycopy (script, 0, s, 0, script.length);
			return s;
		}
		return script;
	}

	public void setScript (byte[] script)
	{
		if ( script != null )
		{
			this.script = new byte[script.length];
			System.arraycopy (script, 0, this.script, 0, script.length);
		}
		else
		{
			script = null;
		}
	}

	public Tx getTransaction ()
	{
		return transaction;
	}

	public void setTransaction (Tx transaction)
	{
		this.transaction = transaction;
	}

	public long getBlockTime ()
	{
		return blockTime;
	}

	public void setBlockTime (long blockTime)
	{
		this.blockTime = blockTime;
	}

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("sourceHash", sourceHash);
			o.put ("sourceIx", ix);
			try
			{
				o.put ("script", ScriptFormat.toReadable (script));
			}
			catch ( ValidationException e )
			{
				o.put ("invalidScript", ByteUtils.toHex (script));
			}
			o.put ("sequence", sequence);
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public void toWire (WireFormat.Writer writer)
	{
		if ( sourceHash != null && !sourceHash.equals (Hash.ZERO_HASH.toString ()) )
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

	@Override
	public int hashCode ()
	{
		int hash = 13;
		hash = 37 * hash + sourceHash.hashCode ();
		hash = 37 * hash + ix.hashCode ();
		return hash;
	}

	@Override
	public boolean equals (Object obj)
	{
		if ( obj == this )
		{
			return true;
		}
		if ( obj == null )
		{
			return false;
		}

		if ( obj instanceof TxIn )
		{
			TxIn ot = (TxIn) obj;
			return ix.equals (ot.ix) && sourceHash.equals (ot.sourceHash);
		}

		return false;
	}

	protected TxIn flatCopy (Tx tc)
	{
		TxIn c = new TxIn ();
		c.id = id;
		c.ix = ix;
		c.script = script;
		c.sequence = sequence;
		c.source = source;
		c.sourceHash = sourceHash;
		c.transaction = tc;

		return c;
	}
}
