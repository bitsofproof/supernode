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
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.json.JSONException;
import org.json.JSONObject;

import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;

@Entity
@Table (name = "txout")
public class TxOut implements Serializable, HasId
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

	@ManyToOne (fetch = FetchType.LAZY, cascade = { CascadeType.MERGE, CascadeType.DETACH, CascadeType.PERSIST, CascadeType.REFRESH }, optional = false)
	private Tx transaction;

	@Column (nullable = false)
	private Long ix = new Long (0L);

	private Long votes;

	@Column (length = 40, nullable = true)
	@Index (name = "addr1index")
	private String owner1;

	@Column (length = 40, nullable = true)
	@Index (name = "own2index")
	private String owner2;

	@Column (length = 40, nullable = true)
	@Index (name = "own3index")
	private String owner3;

	public JSONObject toJSON ()
	{
		JSONObject o = new JSONObject ();
		try
		{
			o.put ("value", value);
			try
			{
				o.put ("script", Script.toReadable (script));
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
		writer.writeUint64 (getValue ());
		writer.writeVarBytes (getScript ());
	}

	public void fromWire (WireFormat.Reader reader)
	{
		value = reader.readUint64 ();
		script = reader.readVarBytes ();
	}

	@Override
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

	public Long getIx ()
	{
		return ix;
	}

	public void setIx (Long ix)
	{
		this.ix = ix;
	}

	public Long getVotes ()
	{
		return votes;
	}

	public void setVotes (Long votes)
	{
		this.votes = votes;
	}

	public String getOwner1 ()
	{
		return owner1;
	}

	public void setOwner1 (String owner1)
	{
		this.owner1 = owner1;
	}

	public String getOwner2 ()
	{
		return owner2;
	}

	public void setOwner2 (String owner2)
	{
		this.owner2 = owner2;
	}

	public String getOwner3 ()
	{
		return owner3;
	}

	public void setOwner3 (String owner3)
	{
		this.owner3 = owner3;
	}

	protected TxOut flatCopy (Tx tc)
	{
		TxOut c = new TxOut ();
		c.ix = ix;
		c.script = script;
		c.transaction = tc;
		c.value = value;

		return c;
	}

}
