/*
 * Copyright 2013 bits of proof zrt.
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
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.api.ByteUtils;
import com.bitsofproof.supernode.api.ScriptFormat;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;

@Entity
@Table (name = "txout")
public class TxOut implements Serializable
{
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue
	private Long id;

	private long value;

	@Lob
	@Basic (fetch = FetchType.EAGER)
	// scriptPubKey
	private byte[] script;

	@ManyToOne (fetch = FetchType.LAZY, optional = false)
	private Tx transaction;

	@Column (nullable = false)
	private Long ix = new Long (0L);

	private boolean coinbase = false;

	// indicate if available (UTXO)
	private boolean available = false;

	private String color;

	// this is redundant but saves a join at finding UTXO that is critical for speed
	@Column (length = 64, nullable = false)
	@Index (name = "outthash")
	private String txHash;

	// this is redundant for a check
	private long height;

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("value", value);
			try
			{
				o.put ("script", ScriptFormat.toReadable (script));
			}
			catch ( ValidationException e )
			{
				o.put ("invalidScript", ByteUtils.toHex (script));
			}
		}
		catch ( JSONException e )
		{
		}
		return o;
	}

	public void toWire (WireFormat.Writer writer)
	{
		writer.writeUint64 (value);
		writer.writeVarBytes (script);
	}

	public void fromWire (WireFormat.Reader reader)
	{
		value = reader.readUint64 ();
		script = reader.readVarBytes ();
	}

	public Long getId ()
	{
		return id;
	}

	public void setId (Long id)
	{
		this.id = id;
	}

	public long getValue ()
	{
		return value;
	}

	public void setValue (long value)
	{
		this.value = value;
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

	public Long getIx ()
	{
		return ix;
	}

	public void setIx (Long ix)
	{
		this.ix = ix;
	}

	public boolean isCoinbase ()
	{
		return coinbase;
	}

	public void setCoinbase (boolean coinbase)
	{
		this.coinbase = coinbase;
	}

	protected TxOut flatCopy (Tx tc)
	{
		TxOut c = new TxOut ();
		c.id = id;
		c.ix = ix;
		c.script = script;
		c.transaction = tc;
		c.txHash = txHash;
		c.value = value;
		c.available = available;
		c.height = height;
		c.coinbase = coinbase;
		c.color = color;
		return c;
	}

	public boolean isAvailable ()
	{
		return available;
	}

	public void setAvailable (boolean available)
	{
		this.available = available;
	}

	public String getTxHash ()
	{
		return txHash;
	}

	public void setTxHash (String txHash)
	{
		this.txHash = txHash;
	}

	public long getHeight ()
	{
		return height;
	}

	public void setHeight (long height)
	{
		this.height = height;
	}

	public String getColor ()
	{
		return color;
	}

	public void setColor (String color)
	{
		this.color = color;
	}
}
