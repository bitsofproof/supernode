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
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "txin")
public class TxIn implements Serializable, Cloneable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	@Column (length = 64)
	private String sourceHash;
	private long ix;

	@ManyToOne (fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.DETACH, CascadeType.PERSIST, CascadeType.REFRESH }, optional = false)
	private Tx transaction;

	@OneToOne (fetch = FetchType.LAZY, optional = true)
	private TxOut source;

	private long sequence = 0xFFFFFFFFL;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	// scriptSig
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

	public JSONObject toJSON () throws ValidationException
	{
		JSONObject o = new JSONObject ();
		try
		{
			if ( sourceHash != null )
			{
				o.put ("sourceHash", sourceHash);
				o.put ("sourceIx", ix);
				if ( !sourceHash.equals (Hash.ZERO_HASH.toString ()) )
				{
					o.put ("script", Script.toReadable (script));
				}
				else
				{
					o.put ("script", JSONObject.NULL);
				}
			}
			else if ( source != null )
			{
				o.put ("sourceHash", source.getTransaction ().getHash ());
				o.put ("sourceIx", source.getIx ());
				o.put ("script", Script.toReadable (script));
			}
			else
			{
				o.put ("sourceHash", Hash.ZERO_HASH.toString ());
				o.put ("sourceIx", -1);
				o.put ("script", JSONObject.NULL);
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
		if ( source != null )
		{
			writer.writeHash (new Hash (source.getTransaction ().getHash ()));
			writer.writeUint32 (source.getIx ());
		}
		else
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

	protected TxIn flatCopy (Tx tc)
	{
		TxIn c = new TxIn ();

		c.ix = ix;
		c.script = script;
		c.sequence = sequence;
		c.source = source;
		c.sourceHash = sourceHash;
		c.transaction = tc;

		return c;
	}

}
